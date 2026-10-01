package gregtech.api.recipes.roll;

import gregtech.api.GTValues;
import gregtech.api.recipes.chance.boost.ChanceBoostFunction;

/**
 * Computes the {@code boostStrength} a {@link RollInterpreter} should use for overclock-driven yield boosting,
 * given a recipe's own tier and the (possibly higher) tier it's actually being run at.
 * <p>
 * This mirrors GregTech's pre-existing {@link gregtech.api.recipes.chance.boost.ChanceBoostFunction#OVERCLOCK} exactly
 * (including its ULV special case, below), so that bridging existing chance data into the new
 * {@link RollInterpreter} model (see {@link gregtech.api.recipes.output.StandardItemOutput}) does not silently
 * change existing recipe balance.
 */
public final class OverclockRollBoost {

    private OverclockRollBoost() {}

    /**
     * @param recipeTier  the recipe's own voltage tier.
     * @param machineTier the voltage tier the recipe is actually being run at.
     * @return the boost strength to pass to {@link RollInterpreter#interpretAndRoll}. {@code 0} (no boost) if
     *         {@code machineTier} does not exceed {@code recipeTier}. LV does not boost over ULV, matching
     *         {@code ChanceBoostFunction.OVERCLOCK}'s existing behavior.
     */
    public static int boostStrength(int recipeTier, int machineTier) {
        int tierDiff = machineTier - recipeTier;
        if (tierDiff <= 0) return 0;
        if (recipeTier == GTValues.ULV) tierDiff--;
        return tierDiff;
    }

    /**
     * As {@link #boostStrength(int, int)}, but respecting a {@link ChanceBoostFunction} (e.g. a {@code RecipeMap}'s
     * configurable {@code chanceFunction}) rather than always assuming {@link ChanceBoostFunction#OVERCLOCK}.
     * {@link ChanceBoostFunction#NONE} maps to {@code 0} (no boosting at all); any other function (including
     * {@code OVERCLOCK} itself) falls back to {@link #boostStrength(int, int)}'s formula, since a
     * {@link RollInterpreter}'s {@code boostStrength} is a single linear scalar and can't represent an arbitrary
     * custom function exactly &mdash; this is the best available approximation for one.
     */
    public static int boostStrength(ChanceBoostFunction function, int recipeTier, int machineTier) {
        if (function == ChanceBoostFunction.NONE) return 0;
        return boostStrength(recipeTier, machineTier);
    }
}
