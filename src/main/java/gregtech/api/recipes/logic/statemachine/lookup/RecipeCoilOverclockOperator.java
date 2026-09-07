package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.recipes.logic.RecipeView;
import gregtech.api.recipes.logic.statemachine.OverclockFactory;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerCapacityProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.TemperatureCapacityProperty;
import gregtech.api.recipes.properties.impl.TemperatureProperty;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Predicate;

/**
 * The Electric Blast Furnace's overclock calculation. A full {@link OverclockFactory}-style replacement of
 * {@link RecipeOverclockOperator}, not just a {@code RecipeOverclockConfig#ocAlgorithm}/{@code ocAmountCalculator}
 * customization: both of those seams' fixed signatures (a plain {@code (magnitude, maxVoltage)} or
 * {@code (durationFactor, voltageFactor)}) have no room to carry a <i>per-recipe</i> value like
 * {@link TemperatureProperty}'s required temperature through to the algorithm, which
 * {@link OverclockingLogic#heatingCoilOC} needs directly. Wired via a factory closure that captures {@code config}
 * directly (see {@code MetaTileEntityElectricBlastFurnace#createConfig()}), ignoring {@link OverclockFactory#produce}'s
 * own four scalar parameters entirely -- exactly the pattern that interface's own JavaDoc anticipates for factories
 * that need more context than those four values alone provide.
 * <p>
 * Otherwise mirrors {@link RecipeOverclockOperator} closely (reusing its {@link RecipeOverclockOperator#SUCCESS_KEY}/
 * {@link RecipeOverclockOperator#RESULT_KEY} contract, ceiling computation, and the two pure static helpers it
 * exposes package-private for this purpose) &mdash; the only real differences are the pre-overclock EU/t discount
 * ({@link OverclockingLogic#applyCoilEUtDiscount}) and the OC algorithm itself
 * ({@link OverclockingLogic#heatingCoilOC} instead of {@link OverclockingLogic#standardOC}).
 * <p>
 * <b>The temperature gate:</b> a candidate whose required temperature exceeds the coil's current temperature is
 * hard-rejected, unconditionally for every candidate regardless of what the search stage already filtered out.
 * This operator keeps that unconditional gate (rather than relying solely on {@code CoilTemperatureFilter}'s
 * search-time pre-filter): {@code OverclockingLogic#applyCoilEUtDiscount} alone would silently just skip the
 * discount for an under-temperature candidate rather than rejecting it outright, which would let an under-heated
 * furnace run a recipe it shouldn't be able to at all.
 * <p>
 * <b>Reads the coil's current temperature from {@code config.power.properties} (its {@link TemperatureCapacityProperty}
 * entry), not a separately-injected supplier:</b> this is the same property bag used for voltage/amperage
 * ({@code properties.getDefaultable(TemperatureMaximumProperty.EMPTY)}) -- there is exactly one channel carrying the
 * host machine's current temperature into the search/overclock pipeline, not two. An earlier version of this class
 * took a constructor-injected {@code IntSupplier} instead (mirroring how Multi Smelter's {@code createConfig()}
 * reads {@code heatingCoilLevel}/{@code heatingCoilDiscount} directly), which worked but left the same value
 * reachable through two independent paths -- {@code config.power.properties} (already populated with
 * {@link TemperatureCapacityProperty} for {@link gregtech.api.recipes.logic.statemachine.lookup.bitflag.CoilTemperatureFilter}'s
 * benefit) and this operator's own supplier. Consolidating onto the property bag removes that redundancy and matches
 * {@code RecipeOverclockOperator#availableEUt} and this class's own {@link #availableEUt}, which already read
 * everything else through the same channel.
 */
public final class RecipeCoilOverclockOperator implements GTStateMachineTransientOperator {

    public static final String RESULT_KEY = RecipeOverclockOperator.RESULT_KEY;
    public static final String SUCCESS_KEY = RecipeOverclockOperator.SUCCESS_KEY;
    public static final Predicate<NBTTagCompound> SUCCESS_PREDICATE = RecipeOverclockOperator.SUCCESS_PREDICATE;

    private final @NotNull RecipeLogicConfig config;

    public RecipeCoilOverclockOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        RecipeView view = (RecipeView) transientData.get(RecipeViewBuildOperator.VIEW_KEY);
        if (view == null) throw new IllegalStateException("RecipeCoilOverclockOperator ran without a recipe view");

        long baseEUt = view.getActualEUt();
        int baseDuration = applyDurationBonusPreOverclock(view.getActualDuration());
        long requiredAmperage = view.getActualAmperage();

        if (baseEUt == 0) {
            // no power requirement; nothing to overclock (or gate on temperature) against.
            transientData.put(RESULT_KEY,
                    new OverclockOutcome(0, applyDurationDiscount(baseDuration), 0, requiredAmperage));
            data.setBoolean(SUCCESS_KEY, true);
            return;
        }

        int requiredTemp = view.getRecipe().getProperty(TemperatureProperty.getInstance(), 0);
        int providedTemp = config.power.properties == null ? 0 :
                config.power.properties.get().getOrDefault(TemperatureCapacityProperty.EMPTY).temperature();
        if (providedTemp < requiredTemp) {
            // The hard gate legacy's checkRecipe() enforced unconditionally: CoilTemperatureFilter should already
            // exclude this candidate at search time, but this operator doesn't trust that alone (see this class's
            // JavaDoc) -- applyCoilEUtDiscount below would otherwise just skip the discount, not reject the recipe.
            data.setBoolean(SUCCESS_KEY, false);
            return;
        }

        // Coil EU/t discount, applied before overclocking begins (legacy HeatingCoilRecipeLogic#modifyOverclockPre):
        // 5% cheaper per COIL_EUT_DISCOUNT_TEMPERATURE (900K) of excess coil heat above the recipe's requirement.
        long magnitude = OverclockingLogic.applyCoilEUtDiscount(Math.abs(baseEUt), providedTemp, requiredTemp);

        long maxVoltage = config.power.getMaxVoltage();
        boolean upTransform = config.overclock.upTransformForOverclocks;
        long ceiling;
        if (upTransform) {
            ceiling = availableEUt(view);
        } else if (config.power.downTransformForParallels) {
            ceiling = Math.max(1, config.power.getMaxSearchVoltage() / requiredAmperage);
        } else {
            ceiling = maxVoltage;
        }
        if (magnitude > ceiling) {
            data.setBoolean(SUCCESS_KEY, false);
            return;
        }

        int ocAmount = config.overclock.ocAmountCalculator != null ?
                config.overclock.ocAmountCalculator.calculate(magnitude, maxVoltage) :
                RecipeOverclockOperator.standardOcAmount(magnitude, maxVoltage);

        OCResult result = new OCResult();
        if (ocAmount <= 0) {
            result.init(magnitude, baseDuration);
        } else {
            OCParams params = new OCParams();
            params.initialize(magnitude, baseDuration, ocAmount);
            // The whole reason this operator exists instead of RecipeOverclockConfig#ocAlgorithm: heatingCoilOC's
            // perfect-overclock count/sub-tick parallelization needs providedTemp/requiredTemp directly, which that
            // seam's fixed (durationFactor, voltageFactor) signature has no room for.
            OverclockingLogic.heatingCoilOC(params, result, ceiling, providedTemp, requiredTemp);
        }

        long requiredVoltage = applyVoltageDiscount(result.eut());
        double duration = applyDurationDiscount(result.duration());
        int overclocksApplied = RecipeOverclockOperator.countOverclocks(magnitude, result.eut(),
                config.overclock.costFactor);

        transientData.put(RESULT_KEY,
                new OverclockOutcome(overclocksApplied, duration, requiredVoltage, requiredAmperage));
        data.setBoolean(SUCCESS_KEY, true);
    }

    /** As {@code RecipeOverclockOperator#availableEUt}, verbatim (duplicated: that method is a private instance
     *  helper reading the same config fields, not worth cross-instance sharing for three lines). */
    private long availableEUt(@NotNull RecipeView view) {
        if (config.power.properties == null) return 0;
        RecipePropertySet set = config.power.properties.get();
        return view.getRecipe().isGenerating() ?
                set.getOrDefault(PowerCapacityProperty.EMPTY).getEUt() :
                set.getOrDefault(PowerSupplyProperty.EMPTY).getEUt();
    }

    private int applyDurationBonusPreOverclock(int duration) {
        return config.overclock.durationBonusPreOverclock == null ? duration :
                (int) Math.round(duration * config.overclock.durationBonusPreOverclock.getAsDouble());
    }

    private double applyDurationDiscount(double duration) {
        return config.overclock.durationDiscount == null ? duration :
                duration * config.overclock.durationDiscount.getAsDouble();
    }

    private long applyVoltageDiscount(long voltage) {
        return config.overclock.voltageDiscount == null ? voltage :
                (long) (voltage * config.overclock.voltageDiscount.getAsDouble());
    }
}
