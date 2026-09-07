package gregtech.api.recipes.roll;

import gregtech.api.GTValues;

import org.jetbrains.annotations.NotNull;

import java.util.function.DoubleSupplier;

/**
 * Interprets roll data as a minimum yield: the boosted min yield is clamped between {@code 0} and the entry's max
 * yield, then a value is selected uniformly between them (scaled up by {@code parallel}).
 * <p>
 * This has no equivalent in GregTech's pre-existing {@link gregtech.api.recipes.chance.output.ChancedOutputLogic}
 * family, which only ever produces an entry's full stack or nothing at all &mdash; a continuously-scaled output
 * amount is a new capability this interpreter introduces.
 */
public final class RangedRollInterpreter implements RollInterpreter {

    /** The standard instance, drawing from GregTech's shared random source ({@link GTValues#RNG}). */
    public static final RangedRollInterpreter INSTANCE = new RangedRollInterpreter(GTValues.RNG::nextDouble);

    private final @NotNull DoubleSupplier distribution;

    /**
     * @param distribution a supplier of doubles in {@code [0, 1)}, used to pick where in the min-max range each
     *                     roll lands. Exposed so tests can supply a deterministic source, and so callers can use a
     *                     non-uniform distribution if desired.
     */
    public RangedRollInterpreter(@NotNull DoubleSupplier distribution) {
        this.distribution = distribution;
    }

    @Override
    public long @NotNull [] interpretAndRoll(long @NotNull [] maxYield, long @NotNull [] rollValue,
                                             long @NotNull [] rollBoost, int boostStrength, int parallel) {
        long[] roll = new long[maxYield.length];
        for (int i = 0; i < maxYield.length; i++) {
            if (rollValue[i] == Long.MIN_VALUE) continue;
            long minYield = rollValue[i] + rollBoost[i] * boostStrength;
            if (minYield >= maxYield[i]) {
                roll[i] = maxYield[i] * (long) parallel;
            } else {
                minYield = Math.max(minYield, 0);
                roll[i] = Math.round(parallel * (maxYield[i] - minYield) * distribution.getAsDouble()) +
                        minYield * parallel;
            }
        }
        return roll;
    }
}
