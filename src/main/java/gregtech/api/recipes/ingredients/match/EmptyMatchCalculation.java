package gregtech.api.recipes.ingredients.match;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.Collections;
import java.util.List;

/**
 * The trivial {@link MatchCalculation} for zero {@link Matcher}s: always succeeds, at any scale, needing nothing.
 * Avoids building a graph at all for the common case of a recipe side with no items (or no fluids).
 */
public final class EmptyMatchCalculation<T> implements MatchCalculation<T> {

    private static final EmptyMatchCalculation<Object> INSTANCE = new EmptyMatchCalculation<>();
    private static final long[] EMPTY = new long[0];

    @SuppressWarnings("unchecked")
    public static <T> EmptyMatchCalculation<T> get() {
        return (EmptyMatchCalculation<T>) INSTANCE;
    }

    private EmptyMatchCalculation() {}

    @Override
    public boolean attemptScale(@Range(from = 1, to = Integer.MAX_VALUE) int scale) {
        return true;
    }

    @Override
    public int largestSucceedingScale(int maximum) {
        return maximum;
    }

    @Override
    public long[] getMatchResultsForScale(int scale) {
        return EMPTY;
    }

    @Override
    public @NotNull List<T> getMatched(int scale) {
        return Collections.emptyList();
    }
}
