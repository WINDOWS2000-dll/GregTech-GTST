package gregtech.api.recipes.roll;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class IndependentRollInterpreterTest {

    /** A "random" source whose {@code nextInt(bound)} always returns a fixed value, for deterministic tests. */
    private static Random fixed(int nextIntValue) {
        return new Random() {

            @Override
            public int nextInt(int bound) {
                return nextIntValue;
            }
        };
    }

    @Test
    void yieldsFullMaxWhenRollBeatsChance() {
        RollInterpreter interpreter = new IndependentRollInterpreter(fixed(0)); // 0 always < any positive chance
        long[] roll = interpreter.interpretAndRoll(new long[] { 64 }, new long[] { 5000 }, new long[] { 0 }, 0, 1);
        assertThat(roll[0], is(64L));
    }

    @Test
    void yieldsNothingWhenRollFailsChance() {
        RollInterpreter interpreter = new IndependentRollInterpreter(fixed(9999)); // always >= any chance <= 9999
        long[] roll = interpreter.interpretAndRoll(new long[] { 64 }, new long[] { 5000 }, new long[] { 0 }, 0, 1);
        assertThat(roll[0], is(0L));
    }

    @Test
    void sumsIndependentAttemptsAcrossParallel() {
        RollInterpreter interpreter = new IndependentRollInterpreter(fixed(0));
        long[] roll = interpreter.interpretAndRoll(new long[] { 10 }, new long[] { 5000 }, new long[] { 0 }, 0, 3);
        assertThat(roll[0], is(30L));
    }

    @Test
    void appliesBoostScaledByBoostStrength() {
        // base chance 100 alone would fail against a fixed roll of 150, but +50*2 boost pushes it to 200, which passes
        Random rng = fixed(150);
        RollInterpreter interpreter = new IndependentRollInterpreter(rng);
        long[] roll = interpreter.interpretAndRoll(new long[] { 1 }, new long[] { 100 }, new long[] { 50 }, 2, 1);
        assertThat(roll[0], is(1L));
    }

    @Test
    void skipsEntriesMarkedNotRollable() {
        RollInterpreter interpreter = new IndependentRollInterpreter(fixed(0));
        long[] roll = interpreter.interpretAndRoll(new long[] { 64 }, new long[] { Long.MIN_VALUE },
                new long[] { 0 }, 0, 1);
        assertThat(roll[0], is(0L));
    }
}
