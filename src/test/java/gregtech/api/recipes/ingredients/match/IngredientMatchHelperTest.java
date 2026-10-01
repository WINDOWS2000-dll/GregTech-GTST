package gregtech.api.recipes.ingredients.match;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link IngredientMatchHelper#match} against plain {@link String}s (no need for real
 * {@code ItemStack}/{@code FluidStack} instances or a Minecraft bootstrap to exercise the underlying algorithm).
 */
class IngredientMatchHelperTest {

    private static final Counter<String> LENGTH_ONE_COUNTER = new Counter<String>() {

        @Override
        public long count(String value) {
            return 1;
        }

        @Override
        public String withCount(String value, long count) {
            return value;
        }
    };

    @Test
    void aSingleMatcherMatchesASingleSatisfyingValue() {
        MatchCalculation<String> calc = IngredientMatchHelper.match(
                ImmutableList.of(Matcher.simpleMatcher((String s) -> s.equals("a"), 1)),
                ImmutableList.of("a"), LENGTH_ONE_COUNTER);

        assertTrue(calc.attemptScale(1));
    }

    @Test
    void failsWhenNoValueSatisfiesTheMatcher() {
        MatchCalculation<String> calc = IngredientMatchHelper.match(
                ImmutableList.of(Matcher.simpleMatcher((String s) -> s.equals("a"), 1)),
                ImmutableList.of("b"), LENGTH_ONE_COUNTER);

        assertFalse(calc.attemptScale(1));
    }

    @Test
    void resolvesAnAmbiguousAssignmentThatGreedyFirstFitWouldGetWrong() {
        Matcher<String> acceptsAnything = Matcher.simpleMatcher(s -> true, 1);
        Matcher<String> needsRed = Matcher.simpleMatcher((String s) -> s.equals("red"), 1);

        MatchCalculation<String> calc = IngredientMatchHelper.match(
                ImmutableList.of(acceptsAnything, needsRed), ImmutableList.of("red", "blue"), LENGTH_ONE_COUNTER);

        assertTrue(calc.attemptScale(1));
    }

    @Test
    void getMatchedReturnsOneEntryPerOriginalMatchablePositionIncludingUnused() {
        Counter<String> counter = new Counter<String>() {

            @Override
            public long count(String value) {
                return 5;
            }

            @Override
            public String withCount(String value, long count) {
                return value + ":" + count;
            }
        };

        MatchCalculation<String> calc = IngredientMatchHelper.match(
                ImmutableList.of(Matcher.simpleMatcher((String s) -> s.equals("a"), 3)),
                ImmutableList.of("a", "b"), counter);

        assertTrue(calc.attemptScale(1));
        List<String> matched = calc.getMatched(1);

        assertThat(matched.size(), is(2)); // one per matchable position, "b" included even though unused
        assertThat(matched.get(0), is("a:3")); // 3 drawn from "a" (the matcher's requirement)
        assertThat(matched.get(1), is("b:0")); // nothing drawn from "b"
    }

    @Test
    void largestSucceedingScaleFindsTheHighestScaleThatStillMatches() {
        // requires 2 per scale, 7 available -> scale 3 needs 6 (fits), scale 4 needs 8 (doesn't)
        MatchCalculation<String> calc = IngredientMatchHelper.match(
                ImmutableList.of(Matcher.simpleMatcher((String s) -> s.equals("a"), 2)),
                ImmutableList.of("a"), new Counter<String>() {

                    @Override
                    public long count(String value) {
                        return 7;
                    }

                    @Override
                    public String withCount(String value, long count) {
                        return value;
                    }
                });

        assertThat(calc.largestSucceedingScale(10), is(3));
    }

    @Test
    void largestSucceedingScaleReturnsZeroWhenEvenScaleOneFails() {
        MatchCalculation<String> calc = IngredientMatchHelper.match(
                ImmutableList.of(Matcher.simpleMatcher((String s) -> s.equals("a"), 1)),
                ImmutableList.of("b"), LENGTH_ONE_COUNTER);

        assertThat(calc.largestSucceedingScale(10), is(0));
    }

    @Test
    void emptyMatchersAlwaysSucceedsWithoutBuildingAGraph() {
        MatchCalculation<String> calc = IngredientMatchHelper.match(ImmutableList.of(), ImmutableList.of("a"),
                LENGTH_ONE_COUNTER);

        assertTrue(calc instanceof EmptyMatchCalculation);
        assertTrue(calc.attemptScale(1000));
        assertThat(calc.largestSucceedingScale(1000), is(1000));
    }

    @Test
    void nullAndZeroCountMatchablesAreSkippedNotTreatedAsAvailable() {
        MatchCalculation<String> calc = IngredientMatchHelper.match(
                ImmutableList.of(Matcher.simpleMatcher((String s) -> true, 1)),
                java.util.Arrays.asList(null, "usable"), new Counter<String>() {

                    @Override
                    public long count(String value) {
                        return "usable".equals(value) ? 1 : 0;
                    }

                    @Override
                    public String withCount(String value, long count) {
                        return value;
                    }
                });

        assertTrue(calc.attemptScale(1));
        List<String> matched = calc.getMatched(1);
        assertThat(matched.get(0), is((String) null)); // skipped, never assigned
        assertThat(matched.get(1), is("usable"));
    }
}
