package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import com.github.bsideup.jabel.Desugar;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeThresholdIndexTest {

    @Desugar
    private record Threshold(int recipeIndex, long threshold) implements RecipeThresholdIndex.Entry {}

    private static RecipeThresholdIndex atMost(int... recipeIndexToThreshold) {
        return RecipeThresholdIndex.build(RecipeThresholdIndex.Comparison.AT_MOST, toEntries(recipeIndexToThreshold));
    }

    private static RecipeThresholdIndex atLeast(int... recipeIndexToThreshold) {
        return RecipeThresholdIndex.build(RecipeThresholdIndex.Comparison.AT_LEAST, toEntries(recipeIndexToThreshold));
    }

    /** @param recipeIndexToThreshold flattened (recipeIndex, threshold) pairs, e.g. {@code 0, 10, 1, 20} */
    private static List<RecipeThresholdIndex.Entry> toEntries(int... recipeIndexToThreshold) {
        Threshold[] entries = new Threshold[recipeIndexToThreshold.length / 2];
        for (int i = 0; i < entries.length; i++) {
            entries[i] = new Threshold(recipeIndexToThreshold[i * 2], recipeIndexToThreshold[i * 2 + 1]);
        }
        return Arrays.asList(entries);
    }

    private static BitSet setOf(int... bits) {
        BitSet set = new BitSet();
        for (int bit : bits) set.set(bit);
        return set;
    }

    @Test
    void atMostExcludesRecipesRequiringMoreThanTheQuery() {
        // recipe 0 needs <=10, recipe 1 needs <=20, recipe 2 needs <=30
        RecipeThresholdIndex index = atMost(0, 10, 1, 20, 2, 30);

        assertThat(index.excluded(15), is(setOf(1, 2)));
    }

    @Test
    void atMostIncludesARecipeWhoseThresholdExactlyMatchesTheQuery() {
        RecipeThresholdIndex index = atMost(0, 10, 1, 20, 2, 30);

        assertThat(index.excluded(20), is(setOf(2))); // recipe 1's 20 <= 20 passes; only recipe 2 (30) fails
    }

    @Test
    void atMostExcludesEverythingWhenQueryIsBelowAllThresholds() {
        RecipeThresholdIndex index = atMost(0, 10, 1, 20, 2, 30);

        assertThat(index.excluded(5), is(setOf(0, 1, 2)));
    }

    @Test
    void atMostExcludesNothingWhenQueryIsAboveAllThresholds() {
        RecipeThresholdIndex index = atMost(0, 10, 1, 20, 2, 30);

        assertThat(index.excluded(100).isEmpty(), is(true));
    }

    @Test
    void atLeastExcludesRecipesRequiringLessThanTheQuery() {
        // recipe 0 needs >=10, recipe 1 needs >=20, recipe 2 needs >=30
        RecipeThresholdIndex index = atLeast(0, 10, 1, 20, 2, 30);

        assertThat(index.excluded(15), is(setOf(0)));
    }

    @Test
    void atLeastIncludesARecipeWhoseThresholdExactlyMatchesTheQuery() {
        RecipeThresholdIndex index = atLeast(0, 10, 1, 20, 2, 30);

        assertThat(index.excluded(20), is(setOf(0))); // recipe 1's 20 >= 20 passes; only recipe 0 (10) fails
    }

    @Test
    void recipesWithMultipleSharingTheSameThresholdAreGroupedCorrectly() {
        RecipeThresholdIndex index = atMost(0, 10, 1, 10, 2, 20);

        assertThat(index.excluded(15), is(setOf(2)));
        assertThat(index.excluded(5), is(setOf(0, 1, 2)));
    }

    @Test
    void anEmptyIndexExcludesNothing() {
        RecipeThresholdIndex index = RecipeThresholdIndex.build(RecipeThresholdIndex.Comparison.AT_MOST,
                Arrays.asList());

        assertThat(index.excluded(50).isEmpty(), is(true));
    }
}
