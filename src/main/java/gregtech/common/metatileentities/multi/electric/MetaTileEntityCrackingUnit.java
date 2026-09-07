package gregtech.common.metatileentities.multi.electric;

import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityCrackingUnit extends RecipeWorkableMultiblockController {

    private int coilTier;

    public MetaTileEntityCrackingUnit(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.CRACKING_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityCrackingUnit(metaTileEntityId);
    }

    /**
     * Coil-tier energy discount: legacy overrode
     * {@code MultiblockRecipeLogic#modifyOverclockPost} to shave 10% off the already-overclocked EU/t per coil tier
     * above cupronickel (floor of 1 EU/t via {@code Math.max(1, ...)}). GregTech's existing
     * {@link RecipeLogicConfig}'s {@code overclock.voltageDiscount} field (a multiplier applied to the
     * already-overclocked required voltage, see {@code RecipeOverclockOperator#applyVoltageDiscount}) is the same
     * seam this collapses onto (a single {@code setVoltageDiscount(() -> max(0.1, 1 - coilTier * 0.1))}), so no new
     * engine machinery is needed. The {@code max(0.1, ...)} clamp itself keeps the
     * discount from ever reaching (or crossing) zero, making the old {@code Math.max(1, ...)} floor on the result
     * unnecessary here. {@code coilTier <= 0} (cupronickel, or the -1 {@link #invalidateStructure()} sets while
     * unformed) applies no discount at all, matching legacy's early return.
     */
    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        config.overclock.voltageDiscount = () -> coilTier <= 0 ? 1.0 : Math.max(0.1, 1.0 - coilTier * 0.1);
        return config;
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
                .aisle("HCHCH", "HCHCH", "HCHCH")
                .aisle("HCHCH", "H###H", "HCHCH")
                .aisle("HCHCH", "HCOCH", "HCHCH")
                .where('O', selfPredicate())
                .where('H', states(getCasingState()).setMinGlobalLimited(12).or(autoAbilities()))
                .where('#', air())
                .where('C', heatingCoils())
                .build();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.CLEAN_STAINLESS_STEEL_CASING;
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(BlockMetalCasing.MetalCasingType.STAINLESS_CLEAN);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(workable.isWorkingEnabled(), workable.isActive())
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(getEnergyContainer().getInputVoltage()))
                .addCustom((textList, syncer) -> {
                    if (!isStructureFormed()) return;

                    // Coil energy discount line
                    IKey energyDiscount = KeyUtil.number(TextFormatting.AQUA,
                            syncer.syncLong(100 - 10L * getCoilTier()), "%");

                    IKey base = KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.cracking_unit.energy",
                            energyDiscount);

                    IKey hover = KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.cracking_unit.energy_hover");

                    textList.add(KeyUtil.setHover(base, hover));
                })
                .addWorkingStatusLine()
                .addProgressLine(workable.getProgress(), workable.getMaxProgress());
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.cracker.tooltip.1"));
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.CRACKING_UNIT_OVERLAY;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        Object type = context.get("CoilType");
        if (type instanceof IHeatingCoilBlockStats) {
            this.coilTier = ((IHeatingCoilBlockStats) type).getTier();
        } else {
            this.coilTier = 0;
        }
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.coilTier = -1;
    }

    protected int getCoilTier() {
        return this.coilTier;
    }
}
