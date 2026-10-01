package gregtech.api.recipes.roll;

import gregtech.api.recipes.chance.output.ChancedOutputLogic;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RollableOutputListTest {

    private static RollInterpreter alwaysFullYield() {
        return (maxYield, rollValue, rollBoost, boostStrength, parallel) -> {
            long[] result = new long[maxYield.length];
            for (int i = 0; i < maxYield.length; i++) {
                if (rollValue[i] != Long.MIN_VALUE) result[i] = maxYield[i] * parallel;
            }
            return result;
        };
    }

    @Test
    void unrolledEntriesAlwaysYieldTheirFullAmountScaledByParallel() {
        RollableOutputList<Long> list = new RollableOutputList<>(v -> v, Arrays.asList(5L, 3L),
                Collections.emptyList(), alwaysFullYield(), ChancedOutputLogic.OR);

        long[] yield = list.comprehensiveRoll(0, Integer.MAX_VALUE, 4);

        assertThat(yield.length, is(2));
        assertThat(yield[0], is(20L)); // 5 * 4
        assertThat(yield[1], is(12L)); // 3 * 4
    }

    @Test
    void rolledEntriesFollowAfterUnrolledEntriesInComprehensiveRoll() {
        RollableOutputList<Long> list = new RollableOutputList<>(v -> v, Arrays.asList(5L),
                Collections.singletonList(new RollInformation<>(10L, 5000, 0)), alwaysFullYield(),
                ChancedOutputLogic.OR);

        long[] yield = list.comprehensiveRoll(0, Integer.MAX_VALUE, 1);

        assertThat(yield.length, is(2));
        assertThat(yield[0], is(5L));
        assertThat(yield[1], is(10L));
    }

    @Test
    void correlationIsAppliedOnTopOfWhateverTheInterpreterComputes() {
        // AND correlation should zero everything out if any rolled entry's interpreted yield was 0
        RollInterpreter oneSucceedsOneFails = (maxYield, rollValue, rollBoost, boostStrength, parallel) -> new long[] {
                maxYield[0], 0
        };
        RollableOutputList<Long> list = new RollableOutputList<>(v -> v, Collections.emptyList(),
                Arrays.asList(new RollInformation<>(10L, 0, 0), new RollInformation<>(20L, 0, 0)),
                oneSucceedsOneFails, ChancedOutputLogic.AND);

        long[] yield = list.roll(0, Integer.MAX_VALUE, 1);

        assertThat(yield[0], is(0L));
        assertThat(yield[1], is(0L));
    }

    @Test
    void trimLimitCapsHowManyRolledEntriesAreConsideredInComprehensiveRoll() {
        RollableOutputList<Long> list = new RollableOutputList<>(v -> v, Collections.emptyList(),
                Arrays.asList(new RollInformation<>(10L, 5000, 0), new RollInformation<>(20L, 5000, 0)),
                alwaysFullYield(), ChancedOutputLogic.OR);

        long[] yield = list.comprehensiveRoll(0, 1, 1);

        assertThat(yield.length, is(1));
        assertThat(yield[0], is(10L));
    }

    @Test
    void emptyListRollsToNothing() {
        RollableOutputList<Long> list = RollableOutputList.empty();

        assertThat(list.size(), is(0));
        assertThat(list.comprehensiveRoll(0, Integer.MAX_VALUE, 1).length, is(0));
    }
}
