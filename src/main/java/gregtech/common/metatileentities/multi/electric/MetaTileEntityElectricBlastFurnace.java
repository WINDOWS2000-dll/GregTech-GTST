package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.GregTechAPI;
import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.capability.IHeatingCoil;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeCoilOverclockOperator;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.CoilTemperatureFilter;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.EnergyContainerProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.TemperatureCapacityProperty;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.BlockWireCoil.CoilType;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static gregtech.api.util.RelativeDirection.*;

/**
 * Migrated from {@code RecipeMapMultiblockController} to
 * {@link RecipeWorkableMultiblockController}. Replaces legacy's {@code HeatingCoilRecipeLogic}
 * ({@code modifyOverclockPre}/{@code runOverclockingLogic}) with {@link RecipeCoilOverclockOperator}, a full
 * {@code config.overclock.overclockFactory} replacement (see that class's JavaDoc for why the narrower
 * {@code ocAlgorithm}/{@code ocAmountCalculator} seams aren't enough: the coil overclock algorithm needs the
 * recipe's own required temperature, which those seams' fixed signatures have no room to carry).
 * <p>
 * Legacy's {@code checkRecipe} hard gate (rejecting any candidate whose required temperature exceeds
 * {@link #blastFurnaceTemperature}) is replaced by two layers, matching this migration's established "search-time
 * pre-filter + authoritative operator-level gate" pattern: {@link CoilTemperatureFilter} (registered on
 * {@link RecipeMaps#BLAST_RECIPES}'s shared {@code BitflagRecipeLookup}, so candidates that are too cold for this
 * furnace's current coil temperature are excluded before they're even considered) and
 * {@link RecipeCoilOverclockOperator}'s own unconditional re-check (in case the search-time filter is ever bypassed,
 * e.g. a future dynamic-recipe {@code RecipeMap} fallback).
 * <p>
 * <b>{@link #blastFurnaceTemperature} vs. {@code createConfig()}'s construction-order trap:</b> exactly the same
 * pitfall {@link MetaTileEntityDistillationTower}'s JavaDoc documents for its own {@code handler} field, and Multi
 * Smelter's {@code heatingCoilLevel}/{@code heatingCoilDiscount} sidestep the same way:
 * {@link #blastFurnaceTemperature}
 * is set in {@link #formStructure}, long after {@code createConfig()} (called from the constructor) has already run,
 * so {@code config.power.properties} (the only place this class threads the current temperature through --
 * {@link RecipeCoilOverclockOperator} reads it back out from there, not from a separate supplier; see that class's
 * JavaDoc) reads it only from inside a lazily-invoked lambda, never eagerly.
 */
public class MetaTileEntityElectricBlastFurnace extends RecipeWorkableMultiblockController implements IHeatingCoil {

    private int blastFurnaceTemperature;

    public MetaTileEntityElectricBlastFurnace(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.BLAST_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityElectricBlastFurnace(metaTileEntityId);
    }

    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        config.power.properties = () -> {
            RecipePropertySet properties = EnergyContainerProperties.of(getEnergyContainer());
            properties.add(new TemperatureCapacityProperty(blastFurnaceTemperature));
            // This replaces (not adds to) RecipeWorkableMultiblockController's own default properties supplier,
            // so cleanroom/dimension advertising has to be repeated here explicitly -- see that class's JavaDoc.
            properties.add(DimensionProperties.of(this));
            properties.add(CleanroomProperties.of(this));
            return properties;
        };
        // Ignores overclockFactory's own four scalar parameters entirely -- see RecipeCoilOverclockOperator's
        // JavaDoc for why this factory closure needs config directly instead (it reads temperature back out of
        // config.power.properties, set just above, rather than needing a separate supplier).
        config.overclock.overclockFactory = (costFactor, speedFactor, canUpTransform,
                                             durationDiscount) -> new RecipeCoilOverclockOperator(config);
        // Idempotent: registerFilter adds to a Set keyed by filter identity, and RecipeMaps.BLAST_RECIPES's
        // BitflagRecipeLookup is shared by every Electric Blast Furnace instance (see RecipeMap#getBitflagLookup()).
        RecipeMaps.BLAST_RECIPES.getBitflagLookup().registerFilter(CoilTemperatureFilter.INSTANCE);
        return config;
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        // addParallelsLine dropped: parallelLimit stays 1 (no parallel support). addRecipeOutputLine dropped:
        // RecipeWorkable has no getPreviousRecipe() equivalent to back it -- see RecipeWorkableMultiblockController's
        // JavaDoc "Recipe-output preview line intentionally omitted".
        builder.setWorkingStatus(workable.isWorkingEnabled(), workable.isActive())
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(getEnergyContainer().getInputVoltage()))
                .addCustom(this::addHeatCapacity)
                .addWorkingStatusLine()
                .addProgressLine(workable.getProgress(), workable.getMaxProgress());
    }

    private void addHeatCapacity(KeyManager keyManager, UISyncer syncer) {
        if (isStructureFormed()) {
            var heatString = KeyUtil.number(TextFormatting.RED,
                    syncer.syncInt(getCurrentTemperature()), "K");

            keyManager.add(KeyUtil.lang(TextFormatting.GRAY,
                    "gregtech.multiblock.blast_furnace.max_temperature", heatString));
        }
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        IHeatingCoilBlockStats type = context.getOrDefault("CoilType", CoilType.CUPRONICKEL);
        this.blastFurnaceTemperature = type.getCoilTemperature();
        // the subtracted tier gives the starting level (exclusive) of the +100K heat bonus
        this.blastFurnaceTemperature += 100 *
                Math.max(0, GTUtility.getFloorTierByVoltage(getEnergyContainer().getInputVoltage()) - GTValues.MV);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.blastFurnaceTemperature = 0;
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
                .aisle("XXX", "CCC", "CCC", "XXX")
                .aisle("XXX", "C#C", "C#C", "XMX")
                .aisle("XSX", "CCC", "CCC", "XXX")
                .where('S', selfPredicate())
                .where('X', states(getCasingState()).setMinGlobalLimited(9)
                        .or(autoAbilities(true, true, true, true, true, true, false)))
                .where('M', abilities(MultiblockAbility.MUFFLER_HATCH))
                .where('C', heatingCoils())
                .where('#', air())
                .build();
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(MetalCasingType.INVAR_HEATPROOF);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.HEAT_PROOF_CASING;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.electric_blast_furnace.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.electric_blast_furnace.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.electric_blast_furnace.tooltip.3"));
    }

    @Override
    public int getCurrentTemperature() {
        return this.blastFurnaceTemperature;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.BLAST_FURNACE_OVERLAY;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @Override
    public List<MultiblockShapeInfo> getMatchingShapes() {
        ArrayList<MultiblockShapeInfo> shapeInfo = new ArrayList<>();
        MultiblockShapeInfo.Builder builder = MultiblockShapeInfo.builder(RIGHT, DOWN, FRONT)
                .aisle("EEM", "CCC", "CCC", "XXX")
                .aisle("FXD", "C#C", "C#C", "XHX")
                .aisle("ISO", "CCC", "CCC", "XXX")
                .where('X', MetaBlocks.METAL_CASING.getState(MetalCasingType.INVAR_HEATPROOF))
                .where('S', MetaTileEntities.ELECTRIC_BLAST_FURNACE, EnumFacing.SOUTH)
                .where('#', Blocks.AIR.getDefaultState())
                .where('E', MetaTileEntities.ENERGY_INPUT_HATCH[GTValues.LV], EnumFacing.NORTH)
                .where('I', MetaTileEntities.ITEM_IMPORT_BUS[GTValues.LV], EnumFacing.SOUTH)
                .where('O', MetaTileEntities.ITEM_EXPORT_BUS[GTValues.LV], EnumFacing.SOUTH)
                .where('F', MetaTileEntities.FLUID_IMPORT_HATCH[GTValues.LV], EnumFacing.WEST)
                .where('D', MetaTileEntities.FLUID_EXPORT_HATCH[GTValues.LV], EnumFacing.EAST)
                .where('H', MetaTileEntities.MUFFLER_HATCH[GTValues.LV], EnumFacing.UP)
                .where('M', () -> ConfigHolder.machines.enableMaintenance ? MetaTileEntities.MAINTENANCE_HATCH :
                        MetaBlocks.METAL_CASING.getState(MetalCasingType.INVAR_HEATPROOF), EnumFacing.NORTH);
        GregTechAPI.HEATING_COILS.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getValue().getTier()))
                .forEach(entry -> shapeInfo.add(builder.where('C', entry.getKey()).build()));
        return shapeInfo;
    }

    @NotNull
    @Override
    public List<ITextComponent> getDataInfo() {
        List<ITextComponent> list = super.getDataInfo();
        list.add(new TextComponentTranslation("gregtech.multiblock.blast_furnace.max_temperature",
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(blastFurnaceTemperature) + "K")
                        .setStyle(new Style().setColor(TextFormatting.RED))));
        return list;
    }
}
