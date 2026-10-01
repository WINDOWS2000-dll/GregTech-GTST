package gregtech.api.recipes.roll;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class WeightedRollInterpreterTest {

    private static final class ScriptedRandom extends Random {

        private final int[] nextIntScript;
        private final long[] nextLongScript;
        private int intCalls = 0;
        private int longCalls = 0;

        ScriptedRandom(int[] nextIntScript, long[] nextLongScript) {
            this.nextIntScript = nextIntScript;
            this.nextLongScript = nextLongScript;
        }

        @Override
        public int nextInt(int bound) {
            return nextIntScript[Math.min(intCalls++, nextIntScript.length - 1)];
        }

        @Override
        public long nextLong() {
            return nextLongScript[Math.min(longCalls++, nextLongScript.length - 1)];
        }
    }

    @Test
    void picksTheFirstEntryWhenTheDrawLandsInItsWeightRange() {
        // weights [30, 70], totalWeight = 1 + 30 + 70 = 101; a draw of 0 falls within [0, 30) -> entry 0
        RollInterpreter interpreter = new WeightedRollInterpreter(1, 10_000, new ScriptedRandom(new int[] { 0 },
                new long[] { 0 }));
        long[] roll = interpreter.interpretAndRoll(new long[] { 30, 70 }, new long[] { 30, 70 }, new long[] { 0, 0 },
                0, 1);
        assertThat(roll[0], is(30L));
        assertThat(roll[1], is(0L));
    }

    @Test
    void picksTheSecondEntryWhenTheDrawLandsInItsWeightRange() {
        // a draw of 50 falls within [30, 100) -> entry 1
        RollInterpreter interpreter = new WeightedRollInterpreter(1, 10_000, new ScriptedRandom(new int[] { 0 },
                new long[] { 50 }));
        long[] roll = interpreter.interpretAndRoll(new long[] { 30, 70 }, new long[] { 30, 70 }, new long[] { 0, 0 },
                0, 1);
        assertThat(roll[0], is(0L));
        assertThat(roll[1], is(70L));
    }

    @Test
    void excludesEntriesMarkedNotRollableFromBothWeightAndSelection() {
        RollInterpreter interpreter = new WeightedRollInterpreter(1, 10_000, new ScriptedRandom(new int[] { 0 },
                new long[] { 0 }));
        long[] roll = interpreter.interpretAndRoll(new long[] { 30, 100 }, new long[] { Long.MIN_VALUE, 100 },
                new long[] { 0, 0 }, 0, 1);
        assertThat(roll[0], is(0L));
        assertThat(roll[1], is(100L));
    }

    @Test
    void aFailedChancePerRollGateProducesNoPick() {
        RollInterpreter interpreter = new WeightedRollInterpreter(1, 5_000,
                new ScriptedRandom(new int[] { 9999 }, new long[] { 0 }));
        long[] roll = interpreter.interpretAndRoll(new long[] { 100 }, new long[] { 100 }, new long[] { 0 }, 0, 1);
        assertThat(roll[0], is(0L));
    }

    @Test
    void eachParallelUnitResetsAvailablePicksIndependently() {
        // a single entry, picked once per parallel unit since remaining weight resets each round
        RollInterpreter interpreter = new WeightedRollInterpreter(1, 10_000,
                new ScriptedRandom(new int[] { 0, 0 }, new long[] { 0, 0 }));
        long[] roll = interpreter.interpretAndRoll(new long[] { 100 }, new long[] { 100 }, new long[] { 0 }, 0, 2);
        assertThat(roll[0], is(200L));
    }
}
