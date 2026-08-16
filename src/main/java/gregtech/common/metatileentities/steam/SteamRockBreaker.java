package gregtech.common.metatileentities.steam;

import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.capability.impl.RecipeLogicSteam;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;

public class SteamRockBreaker extends SteamMetaTileEntity {

    private boolean hasValidFluids;

    public SteamRockBreaker(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, RecipeMaps.ROCK_BREAKER_RECIPES, Textures.ROCK_BREAKER_OVERLAY, isHighPressure);
        this.workableHandler = new SteamRockBreakerRecipeLogic(this,
                workableHandler.getRecipeMap(), isHighPressure, steamFluidTank, 1.0);
        if (getWorld() != null && !getWorld().isRemote) {
            checkAdjacentFluids();
        }
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamRockBreaker(metaTileEntityId, isHighPressure);
    }

    @Override
    public void onNeighborChanged() {
        super.onNeighborChanged();
        checkAdjacentFluids();
    }

    private void checkAdjacentFluids() {
        boolean hasLava = false;
        boolean hasWater = false;
        for (EnumFacing side : EnumFacing.VALUES) {
            if (hasLava && hasWater) {
                break;
            }

            if (side == frontFacing || side.getAxis().isVertical()) {
                continue;
            }

            Block block = getWorld().getBlockState(getPos().offset(side)).getBlock();
            if (block == Blocks.FLOWING_LAVA || block == Blocks.LAVA) {
                hasLava = true;
            } else if (block == Blocks.FLOWING_WATER || block == Blocks.WATER) {
                hasWater = true;
            }
        }
        this.hasValidFluids = hasLava && hasWater;
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, 1, this, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new NotifiableItemStackHandler(this, 4, this, true);
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        UITexture slotBase = GTGuiTextures.SLOT_STEAM.get(isHighPressure);
        UITexture crushedOreOverlay = GTGuiTextures.CRUSHED_ORE_OVERLAY_STEAM.get(isHighPressure);
        return buildUITemplate(guiData, panelSyncManager)
                .child(new ItemSlot()
                        .pos(53, 34)
                        .background(slotBase, GTGuiTextures.DUST_OVERLAY_STEAM.get(isHighPressure))
                        .slot(new ModularSlot(importItems, 0).accessibility(true, true)))
                .child(workableHandler.getRecipeMap().getRecipeMapUI()
                        .createJeiProgressWidget(workableHandler::getProgressPercent)
                        .pos(79, 35)
                        .size(21, 18)
                        .texture(GTGuiTextures.PROGRESS_BAR_MACERATE_STEAM.get(isHighPressure), 21)
                        .direction(ProgressWidget.Direction.RIGHT))
                .child(new ItemSlot()
                        .pos(107, 25)
                        .background(slotBase, crushedOreOverlay)
                        .slot(new ModularSlot(exportItems, 0).accessibility(false, true)))
                .child(new ItemSlot()
                        .pos(125, 25)
                        .background(slotBase, crushedOreOverlay)
                        .slot(new ModularSlot(exportItems, 1).accessibility(false, true)))
                .child(new ItemSlot()
                        .pos(107, 43)
                        .background(slotBase, crushedOreOverlay)
                        .slot(new ModularSlot(exportItems, 2).accessibility(false, true)))
                .child(new ItemSlot()
                        .pos(125, 43)
                        .background(slotBase, crushedOreOverlay)
                        .slot(new ModularSlot(exportItems, 3).accessibility(false, true)));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("hasValidFluids", hasValidFluids);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey("hasValidFluids")) {
            this.hasValidFluids = data.getBoolean("hasValidFluids");
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        if (isActive()) {
            VanillaParticleEffects.defaultFrontEffect(this, 0.4F, EnumParticleTypes.SMOKE_NORMAL);
        }
    }

    protected class SteamRockBreakerRecipeLogic extends RecipeLogicSteam {

        public SteamRockBreakerRecipeLogic(MetaTileEntity tileEntity, RecipeMap<?> recipeMap, boolean isHighPressure,
                                           IFluidTank steamFluidTank, double conversionRate) {
            super(tileEntity, recipeMap, isHighPressure, steamFluidTank, conversionRate);
        }

        @Override
        protected boolean shouldSearchForRecipes() {
            return hasValidFluids && super.shouldSearchForRecipes();
        }
    }
}
