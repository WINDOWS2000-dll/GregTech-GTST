package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.fluids.store.FluidStorageKeys;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.*;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.sync.FixedIntArraySyncValue;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeStallType;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerCapacityProperty;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.BlockMultiblockCasing.MultiblockCasingType;
import gregtech.common.blocks.BlockTurbineCasing.TurbineCasingType;
import gregtech.common.blocks.MetaBlocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Migrated from {@code RecipeMapMultiblockController}/
 * {@code FuelMultiblockController} (legacy {@code MultiblockFuelRecipeLogic}) to
 * {@link RecipeWorkableMultiblockController} directly &mdash; no shared "fuel multiblock" base class was
 * (re)introduced for this migration, unlike legacy's {@code FuelMultiblockController}: this class and
 * {@code MetaTileEntityLargeTurbine} diverge heavily in their core scaling logic (fixed-tier voltage + boost here,
 * rotor-driven variable voltage + banking there), and no concrete shared need has emerged yet to justify one
 * (anti-premature-abstraction).
 * <p>
 * <b>Parallel model maps directly onto the standard engine:</b> legacy's {@code ParallelLogicType.MULTIPLY} computes
 * {@code limitByVoltage = |maxVoltage / recipeEUt|}, which is exactly {@link RecipeWorkableMultiblockController}'s
 * own generic EU-budget parallel computation (already used by Multi Smelter/Processing Array). No custom parallel
 * operator is needed; {@link #createConfig()} simply raises {@code config.parallel.parallelLimit} to effectively
 * uncapped (matching legacy {@code MultiblockFuelRecipeLogic#getParallelLimit}'s {@code Integer.MAX_VALUE}) and
 * relies on the voltage/amperage budget (via {@link #initializeAbilities()}'s output-energy aggregation, below) to
 * do the actual limiting. Deliberately not tightened to a smaller finite cap: {@code shouldStartRecipeLookup}'s
 * default {@code committedParallel < parallelLimit} check can never saturate against
 * {@code Integer.MAX_VALUE}, so this machine re-runs a full recipe search every tick even while already running at
 * full fuel throughput (an avoidable but bounded inefficiency -- deferred until profiling shows it matters, per
 * this project's usual anti-premature-optimization stance, exactly like Multi Smelter's finite cap not being
 * generalized here).
 * <p>
 * <b>{@link #initializeAbilities()} aggregates {@code OUTPUT_ENERGY}, not {@code INPUT_ENERGY}:</b> this machine is
 * a generator, so {@link #getEnergyContainer()} must reflect the dynamo hatches' output side (mirroring legacy
 * {@code FuelMultiblockController}'s identical override) for both {@code RecipeOverclockOperator}/
 * {@code RecipePowerConfig} (which already correctly branch on {@code Recipe#isGenerating()} to read
 * {@link PowerCapacityProperty}, not {@code PowerSupplyProperty}, needing no changes there) and the oxygen-boosted
 * voltage ceiling override in {@link #createConfig()} below.
 * <p>
 * <b>Oxygen boost/lubricant are a self-contained {@link #drainRecipeEnergy} override</b>, not a new generic engine
 * mechanism (mirroring {@code ComputationRecipeHooks}'s "replace the whole per-tick hook" pattern): no other known
 * machine needs periodic secondary-fluid consumption tied to continuous running time, so this stays local. Absence
 * of lubricant returns {@code false} from {@link #drainRecipeEnergy}, letting {@link RecipeStallType#RESET} (set in
 * {@link #createConfig()}) discard progress through the engine's own safe per-tick stall path, rather than calling
 * {@code RecipeWorkable#invalidate()} directly from inside a per-entry callback (which runs mid-graph-walk over
 * {@code ActiveRecipeList}'s own index state -- see that class's JavaDoc -- and risks corrupting it). This
 * approximates legacy's harsher {@code invalidate()}-on-no-lubricant behavior (full progress loss) closely enough:
 * the only user-visible gap is that already-queued-but-not-yet-active candidates survive to the next search pass
 * instead of also being discarded, which is harmless. User-confirmed design decision (2026-09-04): keep this exact
 * behavior rather than softening to {@link RecipeStallType#PAUSE}.
 * <p>
 * <b>Dynamo-full behavior</b>: deliberately left exactly like the three already-migrated single-block generators
 * (silent overflow discard via {@link IEnergyContainer#addEnergy}, no new stall-on-full mechanic) -- user-confirmed
 * design decision (2026-09-04), for consistency across every generator in the mod.
 */
public class MetaTileEntityLargeCombustionEngine extends RecipeWorkableMultiblockController
                                                 implements ProgressBarMultiblock {

    private static final FluidStack OXYGEN_STACK = Materials.Oxygen.getFluid(20);
    private static final FluidStack LIQUID_OXYGEN_STACK = Materials.Oxygen.getFluid(FluidStorageKeys.LIQUID, 80);
    private static final FluidStack LUBRICANT_STACK = Materials.Lubricant.getFluid(1);

    private final int tier;
    private final boolean isExtreme;
    private boolean boostAllowed;
    /**
     * Display-only cache of {@link #isOxygenPresent()}'s last result, refreshed every {@link #drainRecipeEnergy} tick.
     */
    private boolean isOxygenBoosted = false;
    private long totalContinuousRunningTime;

    public MetaTileEntityLargeCombustionEngine(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, RecipeMaps.COMBUSTION_GENERATOR_FUELS);
        this.tier = tier;
        this.isExtreme = tier > GTValues.EV;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLargeCombustionEngine(metaTileEntityId, tier);
    }

    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        config.overclock.ocAmountCalculator = (v, m) -> 0; // isAllowOverclocking() == false, legacy-equivalent
        config.power.downTransformForParallels = true;
        // See this class's own JavaDoc for why this stays uncapped rather than a tighter finite value.
        config.parallel.parallelLimit = () -> Integer.MAX_VALUE;
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            // Fixed GTValues.V[tier] ceiling (x2 while oxygen-boosted), NOT the real attached dynamo hatch(es)'
            // getOutputVoltage()/getOutputAmperage() -- exactly mirroring legacy's getMaxVoltage()/
            // getMaxParallelVoltage(), which hardcodes this machine's own declared tier regardless of what's
            // physically plugged in. Amperage is fixed at 1 for the same reason: legacy's parallel ceiling
            // (AbstractRecipeLogic#findParallelRecipe -> ParallelLogic#doParallelRecipes's
            // limitByVoltage = |maxVoltage / recipeEUt|) never multiplies by the dynamo's declared amperage at
            // all -- using the real hatch's full
            // voltage*amperage here (e.g. a 3A EV dynamo) would let downTransformForParallels turn a low-EU/t fuel
            // recipe into a wildly oversized parallel batch (up to 3x too many copies, more with multiple
            // hatches), dumping an entire tank of fuel in a single admission. The dynamo's own higher declared
            // amperage only matters for how fast its *output side* can drain an already-filled internal buffer to
            // the power grid -- it was never meant to scale how fast this recipe itself burns fuel.
            long voltage = GTValues.V[tier] * (isOxygenPresent() ? 2 : 1);
            properties.add(new PowerCapacityProperty(voltage, 1));
            properties.add(DimensionProperties.of(this));
            properties.add(CleanroomProperties.of(this));
            return properties;
        };
        config.hooks.stallType = RecipeStallType.RESET;
        config.callbacks.onRecipeStarted = entry -> totalContinuousRunningTime = 0;
        return config;
    }

    @Override
    protected void initializeAbilities() {
        super.initializeAbilities();
        List<IEnergyContainer> outputEnergy = new ArrayList<>(getAbilities(MultiblockAbility.OUTPUT_ENERGY));
        outputEnergy.addAll(getAbilities(MultiblockAbility.SUBSTATION_OUTPUT_ENERGY));
        outputEnergy.addAll(getAbilities(MultiblockAbility.OUTPUT_LASER));
        this.energyContainer = new EnergyContainerList(outputEnergy);
    }

    @Override
    protected boolean drainRecipeEnergy(@NotNull NBTTagCompound recipeData) {
        if (!checkLubricant()) return false;

        this.isOxygenBoosted = isOxygenPresent();
        totalContinuousRunningTime++;
        drainLubricant();
        drainOxygen();

        double progress = recipeData.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY);
        double maxProgress = recipeData.getDouble(ActiveRecipeList.ENTRY_DURATION_KEY);
        long voltage = recipeData.getLong(ActiveRecipeList.ENTRY_VOLTAGE_KEY);
        long amperage = recipeData.getLong(ActiveRecipeList.ENTRY_AMPERAGE_KEY);
        long eut = (long) (Math.min(1, maxProgress - progress) * voltage * amperage);
        getEnergyContainer().addEnergy(boostProduction(eut));
        return true;
    }

    /** @return true if a lubricant unit currently sits in the input tanks (simulate-only, no side effect). */
    private boolean checkLubricant() {
        return LUBRICANT_STACK.isFluidStackIdentical(getInputFluidInventory().drain(LUBRICANT_STACK, false));
    }

    private void drainLubricant() {
        if (totalContinuousRunningTime == 1 || totalContinuousRunningTime % 72 == 0) {
            getInputFluidInventory().drain(LUBRICANT_STACK, true);
        }
    }

    /** @return true if enough oxygen (or liquid oxygen, for the extreme tier) currently sits in the input tanks. */
    private boolean isOxygenPresent() {
        if (!boostAllowed) return false;
        FluidStack boosterStack = isExtreme ? LIQUID_OXYGEN_STACK : OXYGEN_STACK;
        return boosterStack.isFluidStackIdentical(getInputFluidInventory().drain(boosterStack, false));
    }

    private void drainOxygen() {
        if (isOxygenBoosted && totalContinuousRunningTime % 20 == 0) {
            FluidStack boosterStack = isExtreme ? LIQUID_OXYGEN_STACK : OXYGEN_STACK;
            getInputFluidInventory().drain(boosterStack, true);
        }
    }

    /** Boosts actual EU delivery without affecting fuel consumption, exactly like legacy {@code boostProduction}. */
    private long boostProduction(long production) {
        if (!isOxygenBoosted) return production;
        // recipe gives 2A EV/2A IV and we want 3A EV (150%)/4A IV (200%) -- see this class's own JavaDoc for why
        // this differs from the uniform x2 voltage ceiling above.
        return isExtreme ? production * 2 : production * 3 / 2;
    }

    public boolean isDynamoFull() {
        return getEnergyContainer().getEnergyCanBeInserted() < getEnergyContainer().getOutputVoltage();
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(workable.isWorkingEnabled(), workable.isActive() && !isDynamoFull());

        long currentEUt = workable.getActiveRecipeCount() > 0 ? boostProduction(workable.getRequiredEUt(0)) : 0;
        if (isExtreme) {
            builder.addEnergyProductionLine(GTValues.V[tier + 1], currentEUt);
        } else {
            builder.addEnergyProductionAmpsLine(GTValues.V[tier] * 3, 3);
        }

        builder.addCustom((richText, syncer) -> {
            if (isStructureFormed() && syncer.syncBoolean(isOxygenBoosted)) {
                String key = isExtreme ?
                        "gregtech.multiblock.large_combustion_engine.liquid_oxygen_boosted" :
                        "gregtech.multiblock.large_combustion_engine.oxygen_boosted";
                richText.add(KeyUtil.lang(TextFormatting.AQUA, key));
            }
        }).addWorkingStatusLine();
    }

    @Override
    protected void configureErrorText(MultiblockUIBuilder builder) {
        super.configureErrorText(builder);
        builder.addCustom((keyList, syncer) -> {
            if (!isStructureFormed()) return;

            if (syncer.syncBoolean(checkIntakesObstructed())) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.large_combustion_engine.obstructed"));
                keyList.add(KeyUtil.lang(TextFormatting.GRAY,
                        "gregtech.multiblock.large_combustion_engine.obstructed.desc"));
            }

            if (syncer.syncBoolean(!checkLubricant())) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.large_combustion_engine.no_lubricant"));
            }
        });
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        // Deliberately not calling super (RecipeWorkableMultiblockController.configureWarningText adds
        // addLowPowerLine, meaningful for a consuming machine's *input* power -- legacy's FuelMultiblockController
        // never included it for the same reason and only adds the dynamo-tier/maintenance lines below).
        builder.addLowDynamoTierLine(getEnergyContainer().getOutputVoltage() < workable.getTotalRequiredEUt());
        if (hasMaintenanceMechanics()) builder.addMaintenanceProblemLines(getMaintenanceProblems(), true);
        builder.addCustom((manager, syncer) -> {
            if (syncer.syncBoolean(this::isDynamoFull)) {
                manager.add(KeyUtil.lang(TextFormatting.YELLOW,
                        "gregtech.multiblock.large_combustion_engine.dynamo_hatch_full"));
            }
        });
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.base_production_eut", GTValues.V[tier]));
        tooltip.add(I18n.format("gregtech.universal.tooltip.uses_per_hour_lubricant", 1000));
        if (isExtreme) {
            tooltip.add(I18n.format("gregtech.machine.large_combustion_engine.tooltip.boost_extreme",
                    GTValues.V[tier] * 4));
        } else {
            tooltip.add(I18n.format("gregtech.machine.large_combustion_engine.tooltip.boost_regular",
                    GTValues.V[tier] * 3));
        }
    }

    @Override
    protected @NotNull BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
                .aisle("XXX", "XDX", "XXX")
                .aisle("XCX", "CGC", "XCX")
                .aisle("XCX", "CGC", "XCX")
                .aisle("AAA", "AYA", "AAA")
                .where('X', states(getCasingState()))
                .where('G', states(getGearboxState()))
                .where('C',
                        states(getCasingState()).setMinGlobalLimited(3)
                                .or(autoAbilities(false, true, true, true, true, true, true)))
                .where('D', metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.OUTPUT_ENERGY).stream()
                        .filter(mte -> {
                            IEnergyContainer container = mte
                                    .getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER, null);
                            return container != null &&
                                    container.getOutputVoltage() * container.getOutputAmperage() >= GTValues.V[tier];
                        })
                        .toArray(MetaTileEntity[]::new))
                                .addTooltip("gregtech.multiblock.pattern.error.limited.1", GTValues.VN[tier]))
                .where('A', states(getIntakeState()).addTooltips("gregtech.multiblock.pattern.clear_amount_1"))
                .where('Y', selfPredicate())
                .build();
    }

    public IBlockState getCasingState() {
        return isExtreme ? MetaBlocks.METAL_CASING.getState(MetalCasingType.TUNGSTENSTEEL_ROBUST) :
                MetaBlocks.METAL_CASING.getState(MetalCasingType.TITANIUM_STABLE);
    }

    public IBlockState getGearboxState() {
        return isExtreme ? MetaBlocks.TURBINE_CASING.getState(TurbineCasingType.TUNGSTENSTEEL_GEARBOX) :
                MetaBlocks.TURBINE_CASING.getState(TurbineCasingType.TITANIUM_GEARBOX);
    }

    public IBlockState getIntakeState() {
        return isExtreme ? MetaBlocks.MULTIBLOCK_CASING.getState(MultiblockCasingType.EXTREME_ENGINE_INTAKE_CASING) :
                MetaBlocks.MULTIBLOCK_CASING.getState(MultiblockCasingType.ENGINE_INTAKE_CASING);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return isExtreme ? Textures.ROBUST_TUNGSTENSTEEL_CASING : Textures.STABLE_TITANIUM_CASING;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return isExtreme ? Textures.EXTREME_COMBUSTION_ENGINE_OVERLAY : Textures.LARGE_COMBUSTION_ENGINE_OVERLAY;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }

    @Override
    public boolean isStructureObstructed() {
        return super.isStructureObstructed() || checkIntakesObstructed();
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        IEnergyContainer container = getEnergyContainer();
        // Bounds-checked: GTValues.V[tier + 1] would throw
        // ArrayIndexOutOfBoundsException for a hypothetical LCE registered at the array's top tier. No such machine
        // exists today (only EV/IV are registered), but nothing here prevents one from being added later without
        // noticing the risk. If there's no next tier to compare against, treat boost as unreachable (Long.MAX_VALUE
        // threshold) rather than guessing.
        int nextTierIndex = this.tier + 1;
        long nextTierVoltage = nextTierIndex < GTValues.V.length ? GTValues.V[nextTierIndex] : Long.MAX_VALUE;
        this.boostAllowed = container.getOutputVoltage() >= nextTierVoltage;
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.boostAllowed = false;
        this.isOxygenBoosted = false;
        this.totalContinuousRunningTime = 0;
    }

    private boolean checkIntakesObstructed() {
        // Null-guarded: every other world/inventory-facing
        // helper in this class defensively null-checks; getWorld() is called unconditionally, so this must too.
        if (getWorld() == null) return false;
        for (int left = -1; left <= 1; left++) {
            for (int up = -1; up <= 1; up++) {
                if (left == 0 && up == 0) {
                    // Skip the controller block itself
                    continue;
                }

                final BlockPos checkPos = RelativeDirection.offsetPos(
                        getPos(), getFrontFacing(), getUpwardsFacing(), isFlipped(), up, left, 1);
                final IBlockState state = getWorld().getBlockState(checkPos);
                if (!state.getBlock().isAir(state, getWorld(), checkPos)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    public boolean isBoostAllowed() {
        return boostAllowed;
    }

    @Override
    public int getProgressBarCount() {
        return 3;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        FixedIntArraySyncValue fuelValue = new FixedIntArraySyncValue(this::getFuelAmount, null);
        syncManager.syncValue("fuel_amount", fuelValue);
        StringSyncValue fuelNameValue = new StringSyncValue(() -> {
            FluidStack stack = getCurrentOrLikelyFuelStack();
            if (stack == null) return null;
            Fluid fluid = stack.getFluid();
            return fluid == null ? null : fluid.getName();
        });
        syncManager.syncValue("fuel_name", fuelNameValue);
        FixedIntArraySyncValue lubricantValue = new FixedIntArraySyncValue(this::getLubricantAmount, null);
        syncManager.syncValue("lubricant_amount", lubricantValue);
        FixedIntArraySyncValue oxygenValue = new FixedIntArraySyncValue(this::getOxygenAmount, null);
        syncManager.syncValue("oxygen_amount", oxygenValue);
        BooleanSyncValue boostValue = new BooleanSyncValue(this::isBoostAllowed);
        syncManager.syncValue("boost_allowed", boostValue);

        bars.add(barTest -> barTest
                .progress(() -> fuelValue.getValue(1) == 0 ? 0 :
                        1.0 * fuelValue.getValue(0) / fuelValue.getValue(1))
                .texture(GTGuiTextures.PROGRESS_BAR_LCE_FUEL)
                .tooltipBuilder(t -> createFuelTooltip(t, fuelValue, fuelNameValue)));

        bars.add(barTest -> barTest
                .progress(() -> lubricantValue.getValue(1) == 0 ? 0 :
                        1.0 * lubricantValue.getValue(0) / lubricantValue.getValue(1))
                .texture(GTGuiTextures.PROGRESS_BAR_LCE_LUBRICANT)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        if (lubricantValue.getValue(0) == 0) {
                            t.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.no_lubricant"));
                        } else {
                            t.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.lubricant_amount",
                                    lubricantValue.getValue(0), lubricantValue.getValue(1)));
                        }
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));

        bars.add(barTest -> barTest
                .progress(() -> oxygenValue.getValue(1) == 0 ? 0 :
                        1.0 * oxygenValue.getValue(0) / oxygenValue.getValue(1))
                .texture(GTGuiTextures.PROGRESS_BAR_LCE_OXYGEN)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        if (boostValue.getBoolValue()) {
                            if (oxygenValue.getValue(0) == 0) {
                                t.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.oxygen_none"));
                            } else if (isExtreme) {
                                t.addLine(IKey.lang(
                                        "gregtech.multiblock.large_combustion_engine.liquid_oxygen_amount",
                                        oxygenValue.getValue(0), oxygenValue.getValue(1)));
                            } else {
                                t.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.oxygen_amount",
                                        oxygenValue.getValue(0), oxygenValue.getValue(1)));
                            }
                        } else if (isExtreme) {
                            t.addLine(IKey.lang(
                                    "gregtech.multiblock.large_combustion_engine.liquid_oxygen_boost_disallowed"));
                        } else {
                            t.addLine(IKey.lang(
                                    "gregtech.multiblock.large_combustion_engine.oxygen_boost_disallowed"));
                        }
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));
    }

    private void createFuelTooltip(com.cleanroommc.modularui.screen.RichTooltip tooltip,
                                   FixedIntArraySyncValue amounts, StringSyncValue fuelNameValue) {
        if (isStructureFormed()) {
            Fluid fluid = fuelNameValue.getStringValue() == null ? null :
                    net.minecraftforge.fluids.FluidRegistry.getFluid(fuelNameValue.getStringValue());
            if (fluid == null) {
                tooltip.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.fuel_none"));
            } else {
                tooltip.addLine(
                        IKey.lang("gregtech.multiblock.large_combustion_engine.fuel_amount", amounts.getValue(0),
                                amounts.getValue(1), fluid.getLocalizedName(new FluidStack(fluid, 1))));
            }
        } else {
            tooltip.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
        }
    }

    /**
     * @return the currently-active recipe's fuel, or (if idle) whatever fuel a fresh search would currently find --
     *         mirrors legacy {@code MultiblockFuelRecipeLogic#getInputFluidStack}'s fallback, without needing a
     *         {@code previousRecipe} concept this engine doesn't have.
     */
    @Nullable
    private FluidStack getCurrentOrLikelyFuelStack() {
        if (workable.getActiveRecipeCount() > 0) {
            // the active entry doesn't retain which specific fluid it consumed by name, only voltage/amperage --
            // fall back to whatever's currently in the tank, which is necessarily the fuel already being burned.
            return firstNonEmptyTank();
        }
        return firstNonEmptyTank();
    }

    @Nullable
    private FluidStack firstNonEmptyTank() {
        if (getInputFluidInventory() == null) return null;
        for (var tank : getInputFluidInventory()) {
            FluidStack contents = tank.getFluid();
            if (contents != null && contents.amount > 0 &&
                    !LUBRICANT_STACK.isFluidEqual(contents) &&
                    !OXYGEN_STACK.isFluidEqual(contents) && !LIQUID_OXYGEN_STACK.isFluidEqual(contents)) {
                return contents;
            }
        }
        return null;
    }

    /**
     * @return an array of [fuel stored, fuel capacity]
     */
    private int[] getFuelAmount() {
        if (getInputFluidInventory() != null) {
            FluidStack fuelStack = getCurrentOrLikelyFuelStack();
            if (fuelStack != null) {
                FluidStack testStack = fuelStack.copy();
                testStack.amount = Integer.MAX_VALUE;
                return getTotalFluidAmount(testStack, getInputFluidInventory());
            }
        }
        return new int[2];
    }

    /**
     * @return an array of [lubricant stored, lubricant capacity]
     */
    private int[] getLubricantAmount() {
        if (getInputFluidInventory() != null) {
            return getTotalFluidAmount(Materials.Lubricant.getFluid(Integer.MAX_VALUE), getInputFluidInventory());
        }
        return new int[2];
    }

    /**
     * @return an array of [oxygen stored, oxygen capacity]
     */
    private int[] getOxygenAmount() {
        if (getInputFluidInventory() != null && isBoostAllowed()) {
            FluidStack oxygenStack = isExtreme ?
                    Materials.Oxygen.getFluid(FluidStorageKeys.LIQUID, Integer.MAX_VALUE) :
                    Materials.Oxygen.getFluid(Integer.MAX_VALUE);
            return getTotalFluidAmount(oxygenStack, getInputFluidInventory());
        }
        return new int[2];
    }

    /** As {@code FuelMultiblockController#getTotalFluidAmount}, verbatim (that base class is no longer extended). */
    private int[] getTotalFluidAmount(FluidStack testStack, IMultipleTankHandler multiTank) {
        int fluidAmount = 0;
        int fluidCapacity = 0;
        for (var tank : multiTank) {
            if (tank != null) {
                FluidStack drainStack = tank.drain(testStack, false);
                if (drainStack != null && drainStack.amount > 0) {
                    fluidAmount += drainStack.amount;
                    fluidCapacity += tank.getCapacity();
                }
            }
        }
        return new int[] { fluidAmount, fluidCapacity };
    }
}
