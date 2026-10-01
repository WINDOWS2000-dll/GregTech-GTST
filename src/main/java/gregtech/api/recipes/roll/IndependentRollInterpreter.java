package gregtech.api.recipes.roll;

import gregtech.api.GTValues;

import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Interprets roll data as a chance, ranging from 0 to 10 000, of getting the full max yield. Each entry (and each
 * of the {@code parallel} independent attempts per entry) is rolled independently of the others.
 * <p>
 * This is the direct successor to GregTech's pre-existing
 * {@link gregtech.api.recipes.chance.output.ChancedOutputLogic#OR}
 * (rolled per-entry independence) combined with {@link gregtech.api.recipes.chance.boost.ChanceBoostFunction#OVERCLOCK}
 * (linear chance boost per overclock tier) &mdash; the same statistical behavior, expressed as a
 * {@link RollInterpreter} instead.
 */
public final class IndependentRollInterpreter implements RollInterpreter {

    /** The standard instance, using GregTech's shared random source ({@link GTValues#RNG}). */
    public static final IndependentRollInterpreter INSTANCE = new IndependentRollInterpreter(GTValues.RNG);

    private final @NotNull Random random;

    /**
     * @param random the random source to roll against. Exposed (rather than hardcoding {@link GTValues#RNG}) so
     *               tests can supply a deterministic source; production code should use {@link #INSTANCE}.
     */
    public IndependentRollInterpreter(@NotNull Random random) {
        this.random = random;
    }

    @Override
    public long @NotNull [] interpretAndRoll(long @NotNull [] maxYield, long @NotNull [] rollValue,
                                             long @NotNull [] rollBoost, int boostStrength, int parallel) {
        long[] roll = new long[maxYield.length];
        for (int i = 0; i < maxYield.length; i++) {
            if (rollValue[i] == Long.MIN_VALUE) continue;
            long chance = rollValue[i] + rollBoost[i] * boostStrength;
            for (int p = 0; p < parallel; p++) {
                if (random.nextInt(10_000) < chance) roll[i] += maxYield[i];
            }
        }
        return roll;
    }
}
