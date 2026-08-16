package gregtech.common.metatileentities.steam;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMaps;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.texture.Textures;

import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
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

public class SteamAlloySmelter extends SteamMetaTileEntity {

    public SteamAlloySmelter(ResourceLocation metaTileEntityId, boolean isHighPressure) {
        super(metaTileEntityId, RecipeMaps.ALLOY_SMELTER_RECIPES, Textures.ALLOY_SMELTER_OVERLAY, isHighPressure);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new SteamAlloySmelter(metaTileEntityId, isHighPressure);
    }

    @Override
    protected boolean isBrickedCasing() {
        return true;
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, 2, this, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new NotifiableItemStackHandler(this, 1, this, true);
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        UITexture slotBase = GTGuiTextures.SLOT_STEAM.get(isHighPressure);
        UITexture furnaceOverlay = GTGuiTextures.FURNACE_OVERLAY_STEAM.get(isHighPressure);
        return buildUITemplate(guiData, panelSyncManager)
                .child(new ItemSlot()
                        .pos(53, 25)
                        .background(slotBase, furnaceOverlay)
                        .slot(new ModularSlot(this.importItems, 0).accessibility(true, true)))
                .child(new ItemSlot()
                        .pos(35, 25)
                        .background(slotBase, furnaceOverlay)
                        .slot(new ModularSlot(this.importItems, 1).accessibility(true, true)))
                .child(workableHandler.getRecipeMap().getRecipeMapUI()
                        .createJeiProgressWidget(workableHandler::getProgressPercent)
                        .pos(79, 26)
                        .size(20, 16)
                        .texture(GTGuiTextures.PROGRESS_BAR_ARROW_STEAM.get(isHighPressure), 20)
                        .direction(ProgressWidget.Direction.RIGHT))
                .child(new ItemSlot()
                        .pos(107, 25)
                        .background(slotBase)
                        .slot(new ModularSlot(this.exportItems, 0).accessibility(false, true)));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        super.randomDisplayTick();
        if (isActive() && GTValues.RNG.nextBoolean()) {
            VanillaParticleEffects.defaultFrontEffect(this, 0.5F, EnumParticleTypes.SMOKE_NORMAL);
        }
    }
}
