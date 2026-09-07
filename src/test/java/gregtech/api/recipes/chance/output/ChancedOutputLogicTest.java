package gregtech.api.recipes.chance.output;

import gregtech.api.recipes.chance.ChanceEntry;
import gregtech.api.recipes.chance.boost.ChanceBoostFunction;

import com.google.common.collect.ImmutableList;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ChancedOutputLogicTest {

    private static class TestChancedOutput extends ChancedOutput<String> {

        public TestChancedOutput(@NotNull String ingredient, int chance) {
            super(ingredient, chance);
        }

        @Override
        public @NotNull ChanceEntry<String> copy() {
            return new TestChancedOutput(getIngredient(), getChance());
        }
    }

    private static <I, T extends ChancedOutput<I>> void listsMatch(@NotNull List<T> original,
                                                                   @Nullable List<T> rolled) {
        MatcherAssert.assertThat(rolled, CoreMatchers.notNullValue());
        MatcherAssert.assertThat(rolled.size(), CoreMatchers.is(original.size()));
        for (int i = 0; i < original.size(); i++) {
            MatcherAssert.assertThat(rolled.get(i).getIngredient(), CoreMatchers.is(original.get(i).getIngredient()));
        }
    }

    @Test
    public void testORLogic() {
        List<TestChancedOutput> chanceEntries = ImmutableList.of(
                new TestChancedOutput("a", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("b", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("c", ChancedOutputLogic.getMaxChancedValue()));

        List<TestChancedOutput> list = ChancedOutputLogic.OR.roll(chanceEntries, ChanceBoostFunction.NONE, 0, 0);
        listsMatch(chanceEntries, list);
    }

    @Test
    public void testANDLogic() {
        List<TestChancedOutput> chanceEntries = ImmutableList.of(
                new TestChancedOutput("a", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("b", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("c", 0));

        List<TestChancedOutput> list = ChancedOutputLogic.AND.roll(chanceEntries, ChanceBoostFunction.NONE, 0, 0);
        MatcherAssert.assertThat(list, CoreMatchers.nullValue());

        chanceEntries = ImmutableList.of(
                new TestChancedOutput("a", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("b", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("c", ChancedOutputLogic.getMaxChancedValue()));

        list = ChancedOutputLogic.AND.roll(chanceEntries, ChanceBoostFunction.NONE, 0, 0);
        listsMatch(chanceEntries, list);
    }

    @Test
    public void testXORLogic() {
        List<TestChancedOutput> chanceEntries = ImmutableList.of(
                new TestChancedOutput("a", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("b", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("c", ChancedOutputLogic.getMaxChancedValue()));

        List<TestChancedOutput> list = ChancedOutputLogic.XOR.roll(chanceEntries, ChanceBoostFunction.NONE, 0, 0);
        MatcherAssert.assertThat(list, CoreMatchers.notNullValue());
        MatcherAssert.assertThat(list.size(), CoreMatchers.is(1));
        MatcherAssert.assertThat(list.get(0).getIngredient(), CoreMatchers.is(chanceEntries.get(0).getIngredient()));
    }

    @Test
    public void testNONELogic() {
        List<TestChancedOutput> chanceEntries = ImmutableList.of(
                new TestChancedOutput("a", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("b", ChancedOutputLogic.getMaxChancedValue()),
                new TestChancedOutput("c", ChancedOutputLogic.getMaxChancedValue()));

        List<TestChancedOutput> list = ChancedOutputLogic.NONE.roll(chanceEntries, ChanceBoostFunction.NONE, 0, 0);
        MatcherAssert.assertThat(list, CoreMatchers.nullValue());
    }

    // applyCorrelation(long[]) is the correlation layer used alongside gregtech.api.recipes.roll.RollInterpreter
    // (see that package's design doc entry); it operates on already-rolled yields rather than rolling entries
    // itself, but should express the same OR/AND/XOR/NONE semantics as roll(...) above.

    @Test
    public void testORCorrelationLeavesEveryYieldUnchanged() {
        long[] yields = { 5, 0, 3 };
        MatcherAssert.assertThat(ChancedOutputLogic.OR.applyCorrelation(yields), CoreMatchers.is(yields));
    }

    @Test
    public void testANDCorrelationZeroesEverythingIfAnyYieldFailed() {
        long[] allSucceeded = { 5, 3, 7 };
        MatcherAssert.assertThat(ChancedOutputLogic.AND.applyCorrelation(allSucceeded), CoreMatchers.is(allSucceeded));

        long[] oneFailed = { 5, 0, 7 };
        MatcherAssert.assertThat(ChancedOutputLogic.AND.applyCorrelation(oneFailed), CoreMatchers.is(new long[] { 0, 0, 0 }));
    }

    @Test
    public void testXORCorrelationKeepsOnlyTheFirstSuccessfulYield() {
        long[] yields = { 0, 5, 7 };
        MatcherAssert.assertThat(ChancedOutputLogic.XOR.applyCorrelation(yields), CoreMatchers.is(new long[] { 0, 5, 0 }));
    }

    @Test
    public void testXORCorrelationYieldsNothingWhenAllFailed() {
        long[] yields = { 0, 0, 0 };
        MatcherAssert.assertThat(ChancedOutputLogic.XOR.applyCorrelation(yields), CoreMatchers.is(new long[] { 0, 0, 0 }));
    }

    @Test
    public void testNONECorrelationAlwaysZeroesEverything() {
        long[] yields = { 5, 3, 7 };
        MatcherAssert.assertThat(ChancedOutputLogic.NONE.applyCorrelation(yields), CoreMatchers.is(new long[] { 0, 0, 0 }));
    }
}
