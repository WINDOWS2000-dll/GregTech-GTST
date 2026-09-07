package gregtech.api.recipes.roll;

import gregtech.api.GTValues;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.Arrays;
import java.util.Random;

/**
 * Interprets roll data as weights, determining each entry's relative likelihood of being picked. On each of up to
 * {@code maximumRollAttempts} attempts (bounded by the number of rollable entries), if the attempt succeeds a
 * chance-per-roll check, one entry is picked at random weighted by its {@code rollValue} (boosted by
 * {@code rollBoost}), yields its full max yield, and is then removed from consideration for the remaining attempts
 * within that {@code parallel} unit.
 * <p>
 * Unlike the other {@link RollInterpreter}s, entries here are <i>not</i> independent of each other by design: this
 * is the direct successor to GregTech's pre-existing {@link gregtech.api.recipes.chance.output.ChancedOutputLogic#XOR}
 * (single winner among several candidates), generalized to weighted, multi-winner, without-replacement selection.
 */
public final class WeightedRollInterpreter implements RollInterpreter {

    /** A single weighted pick per parallel unit, always attempted. Equivalent in spirit to GregTech's old XOR logic. */
    public static final WeightedRollInterpreter INSTANCE = new WeightedRollInterpreter(1, 10_000);

    private final @Range(from = 1, to = Integer.MAX_VALUE) int maximumRollAttempts;
    private final @Range(from = 1, to = 10_000) int chancePerRoll;
    private final @NotNull Random random;

    public WeightedRollInterpreter(@Range(from = 1, to = Integer.MAX_VALUE) int maximumRollAttempts,
                                   @Range(from = 1, to = 10_000) int chancePerRoll) {
        this(maximumRollAttempts, chancePerRoll, GTValues.RNG);
    }

    /**
     * @param random the random source to roll against. Exposed so tests can supply a deterministic source.
     */
    public WeightedRollInterpreter(@Range(from = 1, to = Integer.MAX_VALUE) int maximumRollAttempts,
                                   @Range(from = 1, to = 10_000) int chancePerRoll, @NotNull Random random) {
        this.maximumRollAttempts = maximumRollAttempts;
        this.chancePerRoll = chancePerRoll;
        this.random = random;
    }

    @Override
    public long @NotNull [] interpretAndRoll(long @NotNull [] maxYield, long @NotNull [] rollValue,
                                             long @NotNull [] rollBoost, int boostStrength, int parallel) {
        long[] weights = new long[maxYield.length];
        if (maxYield.length == 0) return weights;
        long totalWeight = 1; // +1 avoids a zero-length pick range if every entry has zero weight
        boolean[] excluded = new boolean[maxYield.length];
        for (int i = 0; i < maxYield.length; i++) {
            if (rollValue[i] == Long.MIN_VALUE) {
                excluded[i] = true;
                continue;
            }
            totalWeight += (weights[i] = rollValue[i] + rollBoost[i] * boostStrength);
        }
        int attempts = Math.min(maxYield.length, maximumRollAttempts);
        long[] roll = new long[maxYield.length];
        for (int p = 0; p < parallel; p++) {
            long remainingWeight = totalWeight;
            boolean[] picked = Arrays.copyOf(excluded, excluded.length);
            attemptLoop:
            for (int attempt = 0; attempt < attempts; attempt++) {
                if (random.nextInt(10_000) >= chancePerRoll) continue;
                long pick = nextLong(random, remainingWeight);
                int index = 0;
                for (; index < weights.length; index++) {
                    if (picked[index]) continue;
                    pick -= weights[index];
                    if (pick < 0) break;
                }
                if (index == weights.length) continue attemptLoop; // shouldn't happen; defensive
                roll[index] += weights[index];
                remainingWeight -= weights[index];
                picked[index] = true;
            }
        }
        return roll;
    }

    /** @return a uniformly-distributed {@code long} in {@code [0, bound)}. */
    private static long nextLong(@NotNull Random random, long bound) {
        long bits, value;
        do {
            bits = random.nextLong() & Long.MAX_VALUE;
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }
}
