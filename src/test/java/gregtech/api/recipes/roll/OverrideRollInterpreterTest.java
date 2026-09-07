package gregtech.api.recipes.roll;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OverrideRollInterpreterTest {

    private static Random fixed(int nextIntValue) {
        return new Random() {

            @Override
            public int nextInt(int bound) {
                return nextIntValue;
            }
        };
    }

    @Test
    void allEntriesYieldTogetherWhenTheOverrideChanceSucceeds() {
        RollInterpreter interpreter = new OverrideRollInterpreter(5000, 0, fixed(0));
        long[] roll = interpreter.interpretAndRoll(new long[] { 10, 20 }, new long[] { 0, 0 }, new long[] { 0, 0 }, 0,
                1);
        assertThat(roll[0], is(10L));
        assertThat(roll[1], is(20L));
    }

    @Test
    void noEntryYieldsWhenTheOverrideChanceFails() {
        RollInterpreter interpreter = new OverrideRollInterpreter(5000, 0, fixed(9999));
        long[] roll = interpreter.interpretAndRoll(new long[] { 10, 20 }, new long[] { 0, 0 }, new long[] { 0, 0 }, 0,
                1);
        assertThat(roll[0], is(0L));
        assertThat(roll[1], is(0L));
    }

    @Test
    void ignoresPerEntryRollValueButStillRespectsNotRollableMarker() {
        RollInterpreter interpreter = new OverrideRollInterpreter(5000, 0, fixed(0));
        long[] roll = interpreter.interpretAndRoll(new long[] { 10, 20 }, new long[] { Long.MIN_VALUE, 0 },
                new long[] { 0, 0 }, 0, 1);
        assertThat(roll[0], is(0L));
        assertThat(roll[1], is(20L));
    }

    @Test
    void chanceIsBoostedByBoostStrength() {
        Random rng = fixed(150);
        RollInterpreter interpreter = new OverrideRollInterpreter(100, 50, rng);
        long[] roll = interpreter.interpretAndRoll(new long[] { 1 }, new long[] { 0 }, new long[] { 0 }, 2, 1);
        assertThat(roll[0], is(1L));
    }
}
