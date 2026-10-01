package gregtech.common.metatileentities.multi.electric;

import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.machines.RecipeMapFurnace;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.BlockWireCoil.CoilType;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;

public class MetaTileEntityMultiSmelter extends RecipeWorkableMultiblockController {

    protected int heatingCoilLevel;
    protected int heatingCoilDiscount = 1;

    public MetaTileEntityMultiSmelter(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.FURNACE_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityMultiSmelter(metaTileEntityId);
    }

    /**
     * A {@code parallelLimit > 1} machine: legacy's own
     * {@code ParallelLogicType.APPEND_ITEMS} + {@code applyParallelBonus} (which merged N copies into one
     * synthetic recipe with custom, discretely-stepped EUt/duration formulas) is fully replaced by the standard
     * engine's per-copy-amperage parallel model (see {@code RecipeParallelOperator}'s JavaDoc), now that voltage/
     * amperage separation means "N copies" no longer needs a hand-written recipe-merging formula.
     * <p>
     * <b>{@link RecipeLogicConfig#power}'s {@code downTransformForParallels = true} is load-bearing, not optional:</b>
     * {@code RecipeMaps.FURNACE_RECIPES}'s dynamically-synthesized recipes (see {@code RecipeMapFurnace}) run at a
     * tiny fixed voltage ({@link RecipeMapFurnace#RECIPE_EUT}, 4 EU/t) and 1 amp. Without down-transform, this
     * logic's achievable parallel count would be capped by the raw amperage its energy hatches supply (at most 2
     * basic hatches per {@link #autoAbilities()}'s default, e.g. 32A from two 16A hatches) regardless of coil
     * tier &mdash; making higher coil tiers pointless, since {@link #getMaxParallel} demands up to 192 (Naquadah).
     * With it enabled, {@code RecipePowerConfig#getAvailableAmperage} instead converts the hatches' <i>total</i>
     * EU/t (voltage &times; amperage) into however many amps' worth of the candidate's own (tiny) voltage that
     * represents, so a modest voltage-tier upgrade on the same 2 hatches (not more of them) unlocks the higher
     * coil tiers' full parallel count &mdash; exactly PR's own reasoning for setting this flag on this specific
     * machine.
     */
    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        config.parallel.parallelLimit = () -> getMaxParallel(heatingCoilLevel);
        config.overclock.voltageDiscount = () -> 1.0 / heatingCoilDiscount;
        config.power.downTransformForParallels = true;
        return config;
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        // addRecipeOutputLine dropped: RecipeWorkable has no getPreviousRecipe() equivalent to back it -- see
        // RecipeWorkableMultiblockController's JavaDoc "Recipe-output preview line intentionally omitted".
        builder.setWorkingStatus(workable.isWorkingEnabled(), workable.isActive())
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(getEnergyContainer().getInputVoltage()))
                .addCustom((richText, syncer) -> {
                    if (!isStructureFormed()) return;

                    int discount = syncer.syncInt(heatingCoilDiscount);
                    if (discount > 1) {
                        IKey coilDiscount = KeyUtil.number(TextFormatting.AQUA,
                                (long) (100.0 / discount), "%");

                        IKey base = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.multi_furnace.heating_coil_discount",
                                coilDiscount);

                        IKey hoverText = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.multi_furnace.heating_coil_discount_hover");

                        richText.add(KeyUtil.setHover(base, hoverText));
                    }

                    int pLimit = syncer.syncInt(getMaxParallel(heatingCoilLevel));
                    if (pLimit > 0) {
                        IKey parallels = KeyUtil.number(TextFormatting.DARK_PURPLE, pLimit);

                        IKey bodyText = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.parallel",
                                parallels);

                        IKey hoverText = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.multi_furnace.parallel_hover");

                        richText.add(KeyUtil.setHover(bodyText, hoverText));
                    }
                })
                .addWorkingStatusLine()
                .addProgressLine(workable.getProgress(), workable.getMaxProgress());
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        IHeatingCoilBlockStats coilType = context.getOrDefault("CoilType", CoilType.CUPRONICKEL);
        this.heatingCoilLevel = coilType.getLevel();
        this.heatingCoilDiscount = coilType.getEnergyDiscount();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.heatingCoilLevel = 0;
        // 1, not 0 (legacy's value): config.overclock.voltageDiscount divides by this, and a recipe can only ever
        // be running while formed anyway, but there is no reason to leave a 1.0/0 (Infinity) trap around for a
        // future caller that reads this field directly.
        this.heatingCoilDiscount = 1;
    }

    @NotNull
    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
                .aisle("XXX", "CCC", "XXX")
                .aisle("XXX", "C#C", "XMX")
                .aisle("XSX", "CCC", "XXX")
                .where('S', selfPredicate())
                .where('X',
                        states(getCasingState()).setMinGlobalLimited(9)
                                .or(autoAbilities(true, true, true, true, true, true, false)))
                .where('M', abilities(MultiblockAbility.MUFFLER_HATCH))
                .where('C', heatingCoils())
                .where('#', air())
                .build();
    }

    public IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(MetalCasingType.INVAR_HEATPROOF);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.HEAT_PROOF_CASING;
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.MULTI_FURNACE_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }

    /**
     * @param parallel the amount of parallel recipes
     * @param discount the energy discount
     * @return the un-overclocked total EU/t for an amount of parallel recipes (tooltip use only, e.g.
     *         {@link gregtech.common.blocks.BlockWireCoil}/{@link gregtech.api.block.coil.CustomCoilBlock}).
     *         Rewritten from legacy's discretely-stepped
     *         {@code RECIPE_EUT * max(1, (parallel/8)/discount)} (an artifact of legacy's "merge N copies into one
     *         synthetic recipe" model) to the new engine's actual per-copy-amperage model, where {@code parallel}
     *         copies at 1 amp each simply sum to {@code parallel} times one copy's (discounted) EU/t.
     */
    public static int getEUtForParallel(int parallel, int discount) {
        return RecipeMapFurnace.RECIPE_EUT * parallel / discount;
    }

    /**
     * @param heatingCoilLevel the level to get the parallel for
     * @return the max parallel for the heating coil level
     */
    public static int getMaxParallel(int heatingCoilLevel) {
        return 32 * heatingCoilLevel;
    }

    /**
     * @param parallel      the amount of parallel recipes
     * @param parallelLimit the maximum limit on parallel recipes
     * @return the un-overclocked duration for an amount of parallel recipes
     * @deprecated Unused: the new engine's
     *             per-copy-amperage parallel model needs no separate duration-stretching formula (duration is the
     *             recipe's own, same at any parallel count -- only the amperage drawn scales with {@code parallel}).
     *             Kept, unreferenced, in case a future machine needs this exact legacy formula again.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public static int getDurationForParallel(int parallel, int parallelLimit) {
        return (int) Math.max(1.0, RecipeMapFurnace.RECIPE_DURATION * 2 * parallel / Math.max(1, parallelLimit * 1.0));
    }
}
