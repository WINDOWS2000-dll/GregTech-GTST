package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import com.github.bsideup.jabel.Desugar;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.BitSet;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeCategoryIndexTest {

    @Desugar
    private record Entry(int recipeIndex, String value) implements RecipeCategoryIndex.Entry<String> {}

    @Test
    void excludesEveryRecipeWhoseValueFailsTheQuery() {
        RecipeCategoryIndex<String> index = RecipeCategoryIndex.build(ImmutableList.of(
                new Entry(0, "a"), new Entry(1, "b"), new Entry(2, "a")));

        BitSet excluded = index.excluded(value -> value.equals("a"));

        assertThat(excluded.get(0), is(false));
        assertThat(excluded.get(1), is(true));
        assertThat(excluded.get(2), is(false));
    }

    @Test
    void includesEverythingWhenTheQueryAlwaysPasses() {
        RecipeCategoryIndex<String> index = RecipeCategoryIndex.build(ImmutableList.of(
                new Entry(0, "a"), new Entry(1, "b")));

        BitSet excluded = index.excluded(value -> true);

        assertThat(excluded.isEmpty(), is(true));
    }

    @Test
    void excludesEverythingWhenTheQueryAlwaysFails() {
        RecipeCategoryIndex<String> index = RecipeCategoryIndex.build(ImmutableList.of(
                new Entry(0, "a"), new Entry(1, "b")));

        BitSet excluded = index.excluded(value -> false);

        assertThat(excluded.get(0), is(true));
        assertThat(excluded.get(1), is(true));
    }

    @Test
    void recipesSharingTheSameValueAreGroupedIntoOneBucket() {
        // three recipes share "a": a single failing test on "a" should exclude all three at once, proving they
        // were consolidated into one bucket rather than each getting their own query evaluation.
        RecipeCategoryIndex<String> index = RecipeCategoryIndex.build(ImmutableList.of(
                new Entry(0, "a"), new Entry(1, "a"), new Entry(2, "a"), new Entry(3, "b")));

        BitSet excluded = index.excluded(value -> !value.equals("a"));

        assertThat(excluded.get(0), is(true));
        assertThat(excluded.get(1), is(true));
        assertThat(excluded.get(2), is(true));
        assertThat(excluded.get(3), is(false));
    }

    @Test
    void emptyIndexExcludesNothing() {
        RecipeCategoryIndex<String> index = RecipeCategoryIndex.build(ImmutableList.of());

        BitSet excluded = index.excluded(value -> false);

        assertThat(excluded.isEmpty(), is(true));
    }
}
