package gregtech.api.recipes.ingredients.match;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;

import java.util.Collections;
import java.util.List;

/**
 * Common per-scale result caching for {@link MatchCalculation} (see {@link MatchCalculation}'s JavaDoc for why
 * GregTech needs no separate consume-results/roll-boost caching layer). Subclasses only implement the actual
 * per-scale match attempt ({@link #attemptScaleInternal}) and how to turn a raw result array back into {@code T}
 * values ({@link #mapResults}); caching, {@link #largestSucceedingScale}'s binary search, and the "scale 1 already
 * failed, this can never succeed at any scale" short-circuit are handled here.
 */
public abstract class AbstractMatchCalculation<T> implements MatchCalculation<T> {

    private @Nullable Int2ObjectAVLTreeMap<long[]> cache = new Int2ObjectAVLTreeMap<>();
    private int scaling = 1;

    @Override
    public boolean attemptScale(@Range(from = 1, to = Integer.MAX_VALUE) int scale) {
        if (cache == null) return false; // scale 1 already failed; nothing can ever succeed (see reportNoValidScales)
        long[] cached = cache.get(scale);
        if (cached != null || cache.containsKey(scale)) return cached != null;

        if (scale != scaling) {
            rescale(scaling, scale);
            scaling = scale;
        }
        long[] attempt = attemptScaleInternal();
        if (cache == null) return false; // reportNoValidScales() may have run inside attemptScaleInternal()
        if (scaling == 1 && attempt == null) {
            reportNoValidScales();
            return false;
        }
        cache.put(scaling, attempt);
        return attempt != null;
    }

    /**
     * Called before {@link #attemptScaleInternal} whenever the requested scale differs from the last one attempted,
     * so a subclass can update whatever per-scale state its graph/requirement depends on.
     *
     * @param oldScale the scale being moved away from (equal to the field {@link #currentScale()} at call time).
     * @param newScale the scale about to be attempted.
     */
    protected abstract void rescale(int oldScale, int newScale);

    /**
     * @return the match result for whatever scale {@link #rescale} last moved to (see {@link #currentScale()}), or
     *         {@code null} if it doesn't match. Never called at the same scale twice in a row without an
     *         intervening {@link #rescale} call.
     */
    protected abstract long @Nullable [] attemptScaleInternal();

    /** @return the scale the next {@link #attemptScaleInternal} call (if any) would be evaluating. */
    protected final int currentScale() {
        return scaling;
    }

    /**
     * Called once scale 1 itself fails to match: since {@link Matcher#getRequiredCount} only grows with scale,
     * nothing at any larger scale can succeed either, so every subsequent {@link #attemptScale} short-circuits to
     * {@code false} without recomputing anything. Subclasses may override to release resources (e.g. a large graph)
     * they'll never need again, but must call {@code super}.
     */
    @MustBeInvokedByOverriders
    protected void reportNoValidScales() {
        cache = null;
    }

    @Override
    public int largestSucceedingScale(int maximum) {
        if (cache == null) return 0;
        if (maximum < 1) return 0;

        int low = 1;
        int high = searchCeiling(maximum);
        while (high - low > 1) {
            if (cache == null) return 0;
            int mid = (low + high) / 2;
            if (attemptScale(mid)) low = mid;
            else high = mid;
        }
        if (high != low && attemptScale(high)) low = high;
        return attemptScale(low) ? low : 0;
    }

    /**
     * Narrows the binary search's upper bound using any already-cached scales, so a known-failing scale below
     * {@code maximum} isn't searched past.
     */
    private int searchCeiling(int maximum) {
        if (cache == null) return maximum;
        for (var entry : cache.int2ObjectEntrySet()) {
            if (entry.getIntKey() >= maximum) return maximum;
            if (entry.getValue() == null) return entry.getIntKey();
        }
        return maximum;
    }

    /** @return {@code results} (one entry per original matchable position) mapped back to concrete {@code T} values. */
    protected abstract @NotNull List<T> mapResults(long @NotNull [] results);

    @Override
    public long @Nullable [] getMatchResultsForScale(int scale) {
        if (cache == null) return null;
        if (!cache.containsKey(scale)) attemptScale(scale); // may null out `cache` itself (reportNoValidScales())
        return cache == null ? null : cache.get(scale);
    }

    @Override
    public @NotNull List<T> getMatched(int scale) {
        long[] results = getMatchResultsForScale(scale);
        return results == null ? Collections.emptyList() : mapResults(results);
    }
}
