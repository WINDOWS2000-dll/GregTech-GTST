package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Answers "which recipes fail a numeric threshold requirement, given a query value?" in {@code O(log m)} (where
 * {@code m} is the number of <i>distinct</i> threshold values among the indexed recipes), without checking each
 * recipe's own threshold one at a time (see this class's package JavaDoc for why it's a plain sorted array rather
 * than an AVL tree).
 * <p>
 * Two comparison directions are supported: {@link Comparison#AT_MOST} (a recipe passes iff its own threshold is
 * {@code <=} the query, e.g. "this recipe needs at most this much voltage") and {@link Comparison#AT_LEAST} (passes
 * iff {@code >=}, e.g. a hypothetical "this recipe needs at least this much heat"). Only recipes actually given to
 * {@link #build} are considered at all &mdash; a recipe that doesn't declare this property is never excluded by it,
 * which falls out naturally since it simply never appears in any of the precomputed exclusion sets.
 * <p>
 * <b>Build once, query many times:</b> like {@code IngredientBitflagIndex}, this is rebuilt wholesale whenever the
 * owning {@code RecipeMap}'s recipes change (see {@code BitflagRecipeLookup}), not incrementally maintained across
 * individual recipe additions/removals &mdash; so unlike PR's AVL tree (which supports efficient one-at-a-time
 * insertion), a plain sorted array built fresh each time is simpler to verify correct and just as fast to query.
 */
public final class RecipeThresholdIndex {

    public enum Comparison {
        /** A recipe passes iff its own threshold is {@code <=} the query value. */
        AT_MOST,
        /** A recipe passes iff its own threshold is {@code >=} the query value. */
        AT_LEAST
    }

    private static final BitSet EMPTY = new BitSet();

    private final Comparison comparison;

    /**
     * Ascending distinct threshold values, in the "AT_MOST" orientation (negated already if {@link #comparison} is
     * AT_LEAST).
     */
    private final long[] values;

    /**
     * {@code suffixExcluded[k]} = every recipe whose (possibly negated) threshold is {@code > values[k - 1]}, i.e.
     * {@code >= values[k]}.
     */
    private final BitSet[] suffixExcluded;

    private RecipeThresholdIndex(Comparison comparison, long[] values, BitSet[] suffixExcluded) {
        this.comparison = comparison;
        this.values = values;
        this.suffixExcluded = suffixExcluded;
    }

    /**
     * @param comparison which direction a recipe's own threshold is compared against the query value.
     * @param entries    every (recipe index, threshold) pair to index. A recipe index may appear at most once;
     *                   recipes with no requirement for this property should simply be omitted, not given some
     *                   sentinel value.
     */
    @NotNull
    public static RecipeThresholdIndex build(@NotNull Comparison comparison, @NotNull Iterable<Entry> entries) {
        // AT_LEAST(value >= query) is exactly AT_MOST(-value <= -query): negating lets both directions share one
        // algorithm instead of duplicating it (and risking the two drifting out of sync with each other).
        long sign = comparison == Comparison.AT_LEAST ? -1 : 1;

        TreeMap<Long, BitSet> byValue = new TreeMap<>();
        for (Entry entry : entries) {
            byValue.computeIfAbsent(entry.threshold() * sign, k -> new BitSet()).set(entry.recipeIndex());
        }

        int distinctCount = byValue.size();
        long[] values = new long[distinctCount];
        BitSet[] suffixExcluded = new BitSet[distinctCount];

        // Walk descending so each snapshot only needs to fold in one more group on top of the previous (larger)
        // one, rather than re-scanning everything from scratch at every distinct value.
        BitSet running = new BitSet();
        int k = distinctCount - 1;
        for (Map.Entry<Long, BitSet> group : byValue.descendingMap().entrySet()) {
            running.or(group.getValue());
            values[k] = group.getKey();
            suffixExcluded[k] = (BitSet) running.clone();
            k--;
        }

        return new RecipeThresholdIndex(comparison, values, suffixExcluded);
    }

    /**
     * @return every indexed recipe that fails this filter for {@code queryValue} (i.e. would need to be excluded
     *         from a search run at that value). Always an internally-cached {@link BitSet} (built once, up front,
     *         in {@link #build}), never a fresh copy &mdash; callers must treat the result as read-only.
     */
    @NotNull
    public BitSet excluded(long queryValue) {
        if (values.length == 0) return EMPTY;
        long query = comparison == Comparison.AT_LEAST ? -queryValue : queryValue;

        // First distinct value strictly greater than the (possibly negated) query: everything at or beyond that
        // point fails "own threshold <= query".
        int index = Arrays.binarySearch(values, query);
        int firstAbove = index >= 0 ? index + 1 : -(index + 1);
        return firstAbove >= values.length ? EMPTY : suffixExcluded[firstAbove];
    }

    /** One recipe's threshold requirement, for {@link #build}. */
    public interface Entry {

        int recipeIndex();

        long threshold();
    }
}
