package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.logic.OCParams;
import gregtech.api.recipes.logic.OCResult;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeOverclockOperator;

import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;

/**
 * Overclock-calculation configuration for a {@link RecipeLogicConfig}.
 * <p>
 * {@link #overclockFactory} produces the operator that actually performs the calculation; for machines that only
 * need to customize <i>one</i> piece of that calculation, {@link #ocAmountCalculator}/{@link #ocAlgorithm} are
 * finer-grained seams into {@link RecipeOverclockOperator}'s own standard implementation, so such machines don't
 * need to reimplement everything {@link #overclockFactory} would otherwise force them to.
 * <p>
 * <b>Background:</b> real machines often only need to customize how the tier ceiling/OC count is derived (Fusion
 * Reactor clamps it by a recipe property; Processing Array derives it from an inserted sub-machine's own tier,
 * {@link #ocAmountCalculator}'s seam), or which underlying OC algorithm variant runs (Processing Array uses
 * {@link OverclockingLogic#subTickNonParallelOC} instead of {@link OverclockingLogic#standardOC},
 * {@link #ocAlgorithm}'s seam).
 */
public final class RecipeOverclockConfig {

    /**
     * Multiplier applied to power cost per overclock. Defaults to GregTech's current standard
     * ({@link OverclockingLogic#STD_VOLTAGE_FACTOR}).
     */
    public double costFactor = OverclockingLogic.STD_VOLTAGE_FACTOR;

    /**
     * Multiplier applied to speed (inverse duration) per overclock. Defaults to GregTech's current standard
     * ({@link OverclockingLogic#STD_DURATION_FACTOR_INV}).
     */
    public double speedFactor = OverclockingLogic.STD_DURATION_FACTOR_INV;

    /**
     * Whether overclocking may transform excess amperage into voltage tiers beyond the recipe's own tier (PR
     * #2755's "up-transform"), rather than being capped at the recipe's voltage tier the way GregTech currently caps
     * overclocks. Not yet implemented by {@link RecipeOverclockOperator} (unlike the rest of this class's fields,
     * this one is still a placeholder pending further design) &mdash; exact semantics will be revisited separately
     * from voltage/amperage separation.
     */
    public boolean upTransformForOverclocks = false;

    /**
     * If non-null, an additional multiplier applied to a recipe's <i>post</i>-overclock duration (e.g. GregTech's
     * existing maintenance penalty, {@code +10%} duration per problem, applied after overclocking via
     * {@code MultiblockRecipeLogic#modifyOverclockPost}). See {@link #durationBonusPreOverclock} for the
     * pre-overclock counterpart &mdash; the two are not interchangeable, since a duration change applied before
     * overclocking can affect how many overclock steps actually fit (each step halves duration and stops once it
     * would drop below one tick), while one applied after cannot.
     */
    public @Nullable DoubleSupplier durationDiscount;

    /**
     * If non-null, an additional multiplier applied to a recipe's duration <i>before</i> overclocking is
     * calculated (e.g. GregTech's existing maintenance bonus from an auto-maintenance hatch, applied via
     * {@code MultiblockRecipeLogic#modifyOverclockPre}). See {@link #durationDiscount}'s JavaDoc for why this is a
     * separate field rather than folded into it.
     */
    public @Nullable DoubleSupplier durationBonusPreOverclock;

    /** If non-null, an additional multiplier applied to a recipe's required voltage before overclocking is calculated. */
    public @Nullable DoubleSupplier voltageDiscount;

    /**
     * Derives how many overclock steps are available, from the recipe's own voltage and the machine's maximum
     * voltage. {@code null} (the default) means {@link RecipeOverclockOperator}'s standard derivation: one
     * overclock per voltage tier between the two (mirroring {@code AbstractRecipeLogic#getNumberOfOCs}), minus one
     * if the recipe's own tier is ULV (matching GregTech's long-standing "no ULV overclocking" rule.
     * <p>
     * Processing Array overrides this to derive the ceiling from its inserted sub-machine's own tier instead of the
     * recipe's nominal voltage.
     */
    public @Nullable OcAmountCalculator ocAmountCalculator;

    /**
     * The underlying OC algorithm variant to run once {@link #ocAmountCalculator} has determined how many
     * overclocks are available. {@code null} (the default) means {@link OverclockingLogic#standardOC}.
     * <p>
     * Processing Array overrides this to {@link OverclockingLogic#subTickNonParallelOC}.
     */
    public @Nullable OcAlgorithm ocAlgorithm;

    /**
     * Produces the operator that performs the overclock calculation. {@code null} (the default) means "use
     * {@link RecipeOverclockOperator}, GregTech's standard implementation".
     * <p>
     * <b>Contract:</b> the produced operator must, on success, write a boolean {@code true} to
     * {@link RecipeOverclockOperator#SUCCESS_KEY} on {@code data} and an {@code OverclockOutcome} to
     * {@link RecipeOverclockOperator#RESULT_KEY} on {@code transientData} (or {@code false}/nothing on failure,
     * e.g. insufficient voltage) &mdash; {@code RecipeLookupTrackBuilder}'s graph and
     * {@code RecipeRunBuildOperator} both depend on those exact keys regardless of which operator produced them.
     * A machine that only needs to customize part of the calculation should prefer {@link #ocAmountCalculator}/
     * {@link #ocAlgorithm} over replacing this entirely.
     */
    public @Nullable OverclockFactory overclockFactory;

    /** See {@link #ocAmountCalculator}. */
    @FunctionalInterface
    public interface OcAmountCalculator {

        /**
         * @param recipeVoltage the recipe's own (pre-overclock) voltage.
         * @param maxVoltage    the maximum voltage the searching machine can currently supply/accept.
         * @return how many overclock steps are available, {@code >= 0}.
         */
        int calculate(long recipeVoltage, long maxVoltage);
    }

    /**
     * See {@link #ocAlgorithm}. Matches {@link OverclockingLogic#standardOC}'s signature exactly, so any of its
     * sibling algorithms (e.g. {@link OverclockingLogic#subTickNonParallelOC}) can be assigned directly by method
     * reference.
     */
    @FunctionalInterface
    public interface OcAlgorithm {

        void apply(OCParams params, OCResult result, long maxVoltage, double durationFactor, double voltageFactor);
    }
}
