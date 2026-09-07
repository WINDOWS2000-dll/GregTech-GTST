package gregtech.api.recipes.ingredients.match;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.List;

/**
 * The result of matching a list of {@link Matcher}s against a pool of available values at a given parallel scale.
 * Computing a match can be expensive (a bipartite
 * maximum-flow solve, see {@link GraphMatchCalculation}), so this holds onto per-scale results rather than
 * recomputing them, and {@link #largestSucceedingScale} lets a caller binary-search for the highest parallel
 * multiplier that still matches instead of trying every scale linearly.
 * <p>
 * There is no separate "match" vs. "consume" result or roll-boost parameter: GregTEch recipes have
 * no concept of a chance-based <i>input</i> (only chance <i>outputs</i>, see
 * {@code gregtech.api.recipes.roll.RollInterpreter}), so a successful match's own result <i>is</i> what gets
 * consumed &mdash; there's nothing further to roll.
 *
 * @param <T> the type of value being matched (e.g. {@code ItemStack} or {@code FluidStack}).
 */
public interface MatchCalculation<T> {

    /**
     * Attempts to match at the given scale (e.g. parallel multiplier), caching the result either way.
     *
     * @return whether the match succeeded at this scale.
     */
    boolean attemptScale(@Range(from = 1, to = Integer.MAX_VALUE) int scale);

    /** @return the largest scale from 1 to {@code maximum} (inclusive) that still succeeds, or 0 if none does. */
    int largestSucceedingScale(int maximum);

    /**
     * @return how much of each matchable (by its original position in the list passed to
     *         {@link IngredientMatchHelper#match}) this match draws on at {@code scale}, or {@code null} if
     *         {@code scale} doesn't match.
     */
    long @Nullable [] getMatchResultsForScale(int scale);

    /**
     * @return {@link #getMatchResultsForScale}'s result, applied to the original matchable values via this
     *         calculation's {@link Counter} (empty if {@code scale} doesn't match).
     */
    @NotNull
    List<T> getMatched(int scale);
}
