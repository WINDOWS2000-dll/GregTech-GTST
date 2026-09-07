package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.GTValues;
import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.recipes.logic.RecipeView;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeOverclockConfig;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerCapacityProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.statemachine.GTStateMachineTransientOperator;
import gregtech.api.util.GTUtility;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Predicate;

/**
 * The standard overclock calculation: wraps GregTech's existing {@link OverclockingLogic#standardOC} (same tier math,
 * same {@code STD_VOLTAGE_FACTOR}/{@code STD_DURATION_FACTOR_INV}-equivalent defaults via
 * {@code RecipeOverclockConfig}), adapted to this transient-operator pipeline.
 * <p>
 * This operates on the recipe's EU/t magnitude regardless of sign: generating
 * recipes (negative EU/t) are overclocked using their absolute value, and the sign is reapplied only via
 * {@code StandardRecipeRun#isGenerating()} (from the recipe itself), not by carrying a negative
 * {@code requiredVoltage} through the math &mdash; {@code standardOC}'s own break conditions
 * (`potentialEUt > maxVoltage`) are only meaningful for positive magnitudes, so passing a negative EU/t through
 * directly would silently disable the voltage cap for generators.
 * <p>
 * Operates purely on {@link RecipeView#getActualEUt()} (per-unit voltage): overclocking never touches amperage
 * &mdash; {@link RecipeView}'s {@code recipe} always represents exactly one copy of itself, so
 * {@link RecipeView#getActualAmperage()} (already scaled by parallel) simply passes through into
 * {@link OverclockOutcome#requiredAmperage()} unchanged by however many overclocks get applied to the voltage side.
 * <p>
 * <b>{@link RecipeOverclockConfig#upTransformForOverclocks} ("up-transform"):</b> when enabled, a
 * candidate's overclock count/ceiling is computed from the supply/capacity's <i>total</i> EU/t (voltage &times;
 * amperage), via {@code log(availableEUt / recipeEUt) / log(costFactor)}, instead of the
 * standard tier-difference count capped at a single amp's voltage. This lets surplus amperage substitute for
 * voltage headroom &mdash; e.g. a candidate whose own voltage exceeds a single amp's rating can still run
 * (down-transformed to fewer, higher-voltage-equivalent amps) as long as the total EU/t fits. Disabled (the
 * default) leaves every existing machine's behavior unchanged; only a machine that explicitly opts in is affected.
 * {@code RecipeSearchOperator} deliberately prefilters against {@code getMaxSearchVoltage()} (also
 * amperage-inclusive), not {@code getMaxVoltage()}, specifically so a candidate this feature could rescue is never
 * excluded before reaching this operator.
 */
public final class RecipeOverclockOperator implements GTStateMachineTransientOperator {

    public static final String RESULT_KEY = "OverclockOutcome";

    /** On {@code data}: whether this candidate fits within the available voltage at all (before overclocking). */
    public static final String SUCCESS_KEY = "OverclockSuccess";

    public static final Predicate<NBTTagCompound> SUCCESS_PREDICATE = d -> d.getBoolean(SUCCESS_KEY);

    private final @NotNull RecipeLogicConfig config;

    public RecipeOverclockOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        RecipeView view = (RecipeView) transientData.get(RecipeViewBuildOperator.VIEW_KEY);
        if (view == null) throw new IllegalStateException("RecipeOverclockOperator ran without a recipe view");

        long baseEUt = view.getActualEUt();
        int baseDuration = applyDurationBonusPreOverclock(view.getActualDuration());
        long requiredAmperage = view.getActualAmperage();

        if (baseEUt == 0) {
            // no power requirement; nothing to overclock against.
            transientData.put(RESULT_KEY,
                    new OverclockOutcome(0, applyDurationDiscount(baseDuration), 0, requiredAmperage));
            data.setBoolean(SUCCESS_KEY, true);
            return;
        }

        long maxVoltage = config.power.getMaxVoltage();
        long magnitude = Math.abs(baseEUt);
        boolean upTransform = config.overclock.upTransformForOverclocks;
        long ceiling;
        if (upTransform) {
            ceiling = availableEUt(view);
        } else if (config.power.downTransformForParallels) {
            // config.power.downTransformForParallels lets RecipeParallelOperator borrow voltage headroom to
            // justify an achieved amperage (parallel count) beyond what the supply's own raw amperage would allow
            // (see that field's JavaDoc) -- computed against this candidate's tiny *pre-overclock* voltage, since
            // parallel is determined before this operator runs. If this operator's own ceiling stayed the plain
            // per-amp maxVoltage (as if only 1 amp were ever drawn), the *total* draw once overclocked
            // (perCopyVoltage * requiredAmperage) could exceed the supply's real total EU/t (maxVoltage * its own
            // amperage) by however much amperage was borrowed -- observed in-game as a machine demanding several
            // times its actual supply and never progressing. GregTech deliberately keeps getActualEUt() per-copy-only
            // (see RecipeParallelOperator's JavaDoc on why merging parallel into EUt corrupts tier calculations),
            // rather than making it parallel-scaled up front (which would make this ceiling check trivially "total
            // so far vs. total available" by construction), so the same invariant has to be restored here instead:
            // dividing the total budget by the amperage already committed keeps requiredAmperage * perCopyCeiling
            // bounded by the real total, exactly as it already is (for free) in the non-down-transformed case,
            // where achieved amperage can never exceed the supply's own raw amperage in the first place.
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
                upTransform ? upTransformOcAmount(magnitude, ceiling) : standardOcAmount(magnitude, maxVoltage);

        OCResult result = new OCResult();
        if (ocAmount <= 0) {
            result.init(magnitude, baseDuration);
        } else {
            OCParams params = new OCParams();
            params.initialize(magnitude, baseDuration, ocAmount);
            RecipeOverclockConfig.OcAlgorithm algorithm = config.overclock.ocAlgorithm != null ?
                    config.overclock.ocAlgorithm : OverclockingLogic::standardOC;
            algorithm.apply(params, result, ceiling, 1.0 / config.overclock.speedFactor,
                    config.overclock.costFactor);
        }

        long requiredVoltage = applyVoltageDiscount(result.eut());
        double duration = applyDurationDiscount(result.duration());
        int overclocksApplied = countOverclocks(magnitude, result.eut(), config.overclock.costFactor);

        transientData.put(RESULT_KEY,
                new OverclockOutcome(overclocksApplied, duration, requiredVoltage, requiredAmperage));
        data.setBoolean(SUCCESS_KEY, true);
    }

    /**
     * {@link RecipeOverclockConfig#ocAmountCalculator}'s default: one overclock per tier, minus one for ULV recipes.
     * Package-private (not {@code private}) so {@link RecipeCoilOverclockOperator} can reuse it verbatim: it's a pure
     * function of the two voltages, with no dependency on this class's own {@code config} field.
     */
    static int standardOcAmount(long recipeVoltage, long maxVoltage) {
        int maxTier = GTUtility.getOCTierByVoltage(maxVoltage);
        if (maxTier <= GTValues.LV) return 0;
        int recipeTier = GTUtility.getOCTierByVoltage(recipeVoltage);
        int ocAmount = maxTier - recipeTier;
        if (recipeTier == GTValues.ULV) ocAmount--; // no ULV overclocking
        return Math.max(0, ocAmount);
    }

    /**
     * {@link RecipeOverclockConfig#upTransformForOverclocks}'s calculation: the
     * overclock count that would bring {@code recipeEUt} up to (but not over) {@code availableEUt}, derived
     * directly from the ratio rather than a fixed per-tier count &mdash; unlike {@link #standardOcAmount}, this
     * naturally accounts for however much extra headroom surplus amperage provides.
     */
    private int upTransformOcAmount(long recipeEUt, long availableEUt) {
        if (availableEUt <= recipeEUt) return 0;
        int ocAmount = (int) (Math.log((double) availableEUt / recipeEUt) / Math.log(config.overclock.costFactor));
        return Math.max(0, ocAmount);
    }

    /**
     * @return the total EU/t (voltage &times; amperage) {@code view}'s supply/capacity side (selected by
     *         {@link gregtech.api.recipes.Recipe#isGenerating()}) can deliver right now, or 0 if this logic
     *         doesn't track power at all. Only consulted when {@link RecipeOverclockConfig#upTransformForOverclocks}
     *         is set; {@link RecipeLogicConfig#power}'s other consumers only ever need one side at a time via
     *         {@link gregtech.api.recipes.logic.statemachine.RecipePowerConfig#getMaxVoltage()}/
     *         {@link gregtech.api.recipes.logic.statemachine.RecipePowerConfig#getAvailableAmperage}.
     */
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

    /**
     * Recovers how many overclock steps {@code standardOC} actually applied, from the EU/t it multiplied by.
     * Package-private (not {@code private}) so {@link RecipeCoilOverclockOperator} can reuse it verbatim; see
     * {@link #standardOcAmount}'s identical note.
     */
    static int countOverclocks(long baseEUt, long resultEUt, double costFactor) {
        if (baseEUt <= 0 || resultEUt <= baseEUt) return 0;
        return (int) Math.round(Math.log((double) resultEUt / baseEUt) / Math.log(costFactor));
    }
}
