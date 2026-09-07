package gregtech.api.recipes.roll;

import org.jetbrains.annotations.NotNull;

/**
 * Interprets what an entry's roll data ({@code rollValue}/{@code rollBoost}) means, and performs the actual rolling
 * against its {@code maxYield}, for a whole list of entries at once.
 * <p>
 * This intentionally covers only the rolling computation itself; JEI/tooltip display strings are a separate,
 * deferred concern and are not part of this interface.
 * <p>
 * A {@code RollInterpreter} decides how much each entry yields <i>independently of the others</i> (with the
 * exception of {@link WeightedRollInterpreter}, whose entries compete against each other by design). To additionally
 * correlate entries against each other (e.g. "all or nothing"), see
 * {@link gregtech.api.recipes.chance.output.ChancedOutputLogic#applyCorrelation}, which is applied as a second,
 * independent layer on top of whatever a {@code RollInterpreter} computes &mdash; see
 * {@link RollableOutputList#roll} for where the two are combined.
 */
public interface RollInterpreter {

    /**
     * Interprets the roll data arrays and returns the result of rolling. All arrays must be the same length.
     *
     * @param maxYield      the maximum yield array. Values in the returned array should not exceed their respective
     *                      value in this array, times {@code parallel}.
     * @param rollValue     the roll value array. {@link Long#MIN_VALUE} marks an entry as not rollable (e.g. a
     *                      not-consumable ingredient), which should always yield {@code 0}.
     * @param rollBoost     the roll boost array, scaled by {@code boostStrength} in an interpreter-specific way.
     * @param boostStrength the boost strength (e.g. the number of overclocks performed).
     * @param parallel      how many independent copies of this roll to perform and sum, before any correlation is
     *                      applied (see this interface's JavaDoc).
     * @return the rolled yields, same length and order as the input arrays.
     */
    long @NotNull [] interpretAndRoll(long @NotNull [] maxYield, long @NotNull [] rollValue,
                                      long @NotNull [] rollBoost, int boostStrength, int parallel);
}
