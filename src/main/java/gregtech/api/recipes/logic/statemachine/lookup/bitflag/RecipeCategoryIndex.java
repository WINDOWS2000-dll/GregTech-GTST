package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * As {@link RecipeThresholdIndex}, but for {@link RecipePredicateFilter}'s non-numeric requirements: answers "which
 * recipes fail a {@link Predicate} test, given each recipe's own declared value?" without testing each recipe one
 * at a time.
 * <p>
 * Unlike a threshold (which sorts into a single ascending order every query can binary-search), an arbitrary
 * predicate has no such order to exploit: {@link #excluded} must evaluate the query predicate once per <i>distinct</i>
 * declared value among the indexed recipes (not once per recipe) and OR together the bitsets of every value that
 * fails. This is still a real optimization over testing every recipe individually whenever many recipes share the
 * same declared value (e.g. a dimension whitelist recipes copy-paste, or a handful of {@code CleanroomType}s reused
 * across hundreds of recipes) &mdash; the common case for these kinds of requirements.
 * <p>
 * {@code T}'s {@code equals()}/{@code hashCode()} determine how much sharing actually happens: a proper value-based
 * override lets recipes with an equal-but-distinct-instance requirement (e.g. two separately-constructed but
 * equal whitelists) share one bucket; falling back to identity (the default for a class that doesn't override
 * them) still produces a <i>correct</i> index, just with one bucket per distinct instance instead of per distinct
 * value.
 *
 * @param <T> the type of value each indexed recipe declares.
 */
public final class RecipeCategoryIndex<T> {

    private static final BitSet EMPTY = new BitSet();

    private final @NotNull Map<T, BitSet> byValue;

    private RecipeCategoryIndex(@NotNull Map<T, BitSet> byValue) {
        this.byValue = byValue;
    }

    /**
     * @param entries every (recipe index, declared value) pair to index. A recipe index may appear at most once;
     *                recipes with no requirement for this property should simply be omitted, not given some
     *                sentinel value.
     */
    @NotNull
    public static <T> RecipeCategoryIndex<T> build(@NotNull Iterable<Entry<T>> entries) {
        Map<T, BitSet> byValue = new LinkedHashMap<>();
        for (Entry<T> entry : entries) {
            byValue.computeIfAbsent(entry.value(), k -> new BitSet()).set(entry.recipeIndex());
        }
        return new RecipeCategoryIndex<>(byValue);
    }

    /**
     * @return every indexed recipe whose own declared value fails {@code query} (i.e. would need to be excluded
     *         from a search run against it). A fresh {@link BitSet} each call (unlike
     *         {@link RecipeThresholdIndex#excluded},
     *         since there's no single cached exclusion set per query here &mdash; every distinct value is
     *         re-evaluated against {@code query} each time); callers may freely mutate the result.
     */
    @NotNull
    public BitSet excluded(@NotNull Predicate<T> query) {
        if (byValue.isEmpty()) return (BitSet) EMPTY.clone();
        BitSet excluded = new BitSet();
        for (Map.Entry<T, BitSet> group : byValue.entrySet()) {
            if (!query.test(group.getKey())) excluded.or(group.getValue());
        }
        return excluded;
    }

    /** One recipe's declared value, for {@link #build}. */
    public interface Entry<T> {

        int recipeIndex();

        T value();
    }
}
