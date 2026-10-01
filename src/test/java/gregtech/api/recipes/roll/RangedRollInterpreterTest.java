package gregtech.api.recipes.roll;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RangedRollInterpreterTest {

    @Test
    void yieldsExactlyMinYieldWhenDistributionIsAtItsFloor() {
        RollInterpreter interpreter = new RangedRollInterpreter(() -> 0.0);
        long[] roll = interpreter.interpretAndRoll(new long[] { 100 }, new long[] { 20 }, new long[] { 0 }, 0, 1);
        assertThat(roll[0], is(20L));
    }

    @Test
    void yieldsExactlyMaxYieldWhenDistributionIsAtItsCeiling() {
        // distribution just under 1.0, since the contract is [0, 1)
        RollInterpreter interpreter = new RangedRollInterpreter(() -> 0.999999999);
        long[] roll = interpreter.interpretAndRoll(new long[] { 100 }, new long[] { 20 }, new long[] { 0 }, 0, 1);
        assertThat(roll[0], is(100L));
    }

    @Test
    void scalesLinearlyWithTheDistributionValue() {
        RollInterpreter interpreter = new RangedRollInterpreter(() -> 0.5);
        // min 20, max 100 -> midpoint 60
        long[] roll = interpreter.interpretAndRoll(new long[] { 100 }, new long[] { 20 }, new long[] { 0 }, 0, 1);
        assertThat(roll[0], is(60L));
    }

    @Test
    void clampsToMaxYieldWhenBoostedMinYieldMeetsOrExceedsIt() {
        RollInterpreter interpreter = new RangedRollInterpreter(() -> 0.0);
        long[] roll = interpreter.interpretAndRoll(new long[] { 100 }, new long[] { 100 }, new long[] { 0 }, 0, 1);
        assertThat(roll[0], is(100L));
    }

    @Test
    void scalesTheEntireRangeByParallel() {
        RollInterpreter interpreter = new RangedRollInterpreter(() -> 0.5);
        long[] roll = interpreter.interpretAndRoll(new long[] { 100 }, new long[] { 20 }, new long[] { 0 }, 0, 3);
        assertThat(roll[0], is(180L)); // 60 per unit * 3
    }

    @Test
    void appliesBoostToTheMinimumYield() {
        RollInterpreter interpreter = new RangedRollInterpreter(() -> 0.0);
        long[] roll = interpreter.interpretAndRoll(new long[] { 100 }, new long[] { 20 }, new long[] { 10 }, 3, 1);
        // boosted min = 20 + 10*3 = 50
        assertThat(roll[0], is(50L));
    }

    @Test
    void skipsEntriesMarkedNotRollable() {
        RollInterpreter interpreter = new RangedRollInterpreter(() -> 0.5);
        long[] roll = interpreter.interpretAndRoll(new long[] { 100 }, new long[] { Long.MIN_VALUE },
                new long[] { 0 }, 0, 1);
        assertThat(roll[0], is(0L));
    }
}
