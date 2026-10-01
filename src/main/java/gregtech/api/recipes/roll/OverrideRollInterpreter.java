package gregtech.api.recipes.roll;

import gregtech.api.GTValues;

import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Interprets roll data as chance, but ignores each entry's own {@code rollValue}/{@code rollBoost} in favor of a
 * single chance (and boost) applied uniformly across the whole list: on each of the {@code parallel} independent
 * attempts, either every rollable entry yields its full max yield, or none of them do. Entries marked not-rollable
 * (see {@link RollInterpreter#interpretAndRoll}) are still excluded regardless.
 * <p>
 * Useful for recipes where a single "did this attempt succeed at all" chance should govern several outputs
 * together, without each output needing its own (necessarily identical) chance value configured.
 */
public final class OverrideRollInterpreter implements RollInterpreter {

    private final int chance;
    private final int chanceBoost;
    private final @NotNull Random random;

    public OverrideRollInterpreter(int chance, int chanceBoost) {
        this(chance, chanceBoost, GTValues.RNG);
    }

    /**
     * @param random the random source to roll against. Exposed so tests can supply a deterministic source.
     */
    public OverrideRollInterpreter(int chance, int chanceBoost, @NotNull Random random) {
        this.chance = chance;
        this.chanceBoost = chanceBoost;
        this.random = random;
    }

    @Override
    public long @NotNull [] interpretAndRoll(long @NotNull [] maxYield, long @NotNull [] rollValue,
                                             long @NotNull [] rollBoost, int boostStrength, int parallel) {
        long[] roll = new long[maxYield.length];
        if (maxYield.length == 0) return roll;
        int boostedChance = chance + chanceBoost * boostStrength;
        for (int p = 0; p < parallel; p++) {
            if (random.nextInt(10_000) < boostedChance) {
                for (int i = 0; i < maxYield.length; i++) {
                    if (rollValue[i] == Long.MIN_VALUE) continue;
                    roll[i] += maxYield[i];
                }
            }
        }
        return roll;
    }
}
