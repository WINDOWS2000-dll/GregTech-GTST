package gregtech.api.recipes.chance.output;

import gregtech.api.GTValues;
import gregtech.api.recipes.chance.boost.BoostableChanceEntry;
import gregtech.api.recipes.chance.boost.ChanceBoostFunction;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

/**
 * Logic for determining which chanced outputs should be produced from a list
 */
public interface ChancedOutputLogic {

    /**
     * Chanced Output Logic where any ingredients succeeding their roll will be produced
     */
    ChancedOutputLogic OR = new ChancedOutputLogic() {

        @Override
        public @Nullable @Unmodifiable <I,
                T extends ChancedOutput<I>> List<@NotNull T> roll(@NotNull @Unmodifiable List<@NotNull T> chancedEntries,
                                                                  @NotNull ChanceBoostFunction boostFunction,
                                                                  int baseTier, int machineTier) {
            ImmutableList.Builder<T> builder = null;
            for (T entry : chancedEntries) {
                if (passesChance(getChance(entry, boostFunction, baseTier, machineTier))) {
                    if (builder == null) builder = ImmutableList.builder();
                    builder.add(entry);
                }
            }
            return builder == null ? null : builder.build();
        }

        @Override
        public long @NotNull [] applyCorrelation(long @NotNull [] yields) {
            return yields;
        }

        @Override
        public @NotNull String getTranslationKey() {
            return "gregtech.chance_logic.or";
        }

        @Override
        public String toString() {
            return "ChancedOutputLogic{OR}";
        }
    };

    /**
     * Chanced Output Logic where all ingredients must succeed their roll in order for any to be produced
     */
    ChancedOutputLogic AND = new ChancedOutputLogic() {

        @Override
        public @Nullable @Unmodifiable <I,
                T extends ChancedOutput<I>> List<@NotNull T> roll(@NotNull @Unmodifiable List<@NotNull T> chancedEntries,
                                                                  @NotNull ChanceBoostFunction boostFunction,
                                                                  int baseTier, int machineTier) {
            for (T entry : chancedEntries) {
                if (!passesChance(getChance(entry, boostFunction, baseTier, machineTier))) {
                    return null;
                }
            }
            return ImmutableList.copyOf(chancedEntries);
        }

        @Override
        public long @NotNull [] applyCorrelation(long @NotNull [] yields) {
            for (long yield : yields) {
                if (yield <= 0) return new long[yields.length];
            }
            return yields;
        }

        @Override
        public @NotNull String getTranslationKey() {
            return "gregtech.chance_logic.and";
        }

        @Override
        public String toString() {
            return "ChancedOutputLogic{AND}";
        }
    };

    /**
     * Chanced Output Logic where only the first ingredient succeeding its roll will be produced
     */
    ChancedOutputLogic XOR = new ChancedOutputLogic() {

        @Override
        public @Nullable @Unmodifiable <I,
                T extends ChancedOutput<I>> List<@NotNull T> roll(@NotNull @Unmodifiable List<@NotNull T> chancedEntries,
                                                                  @NotNull ChanceBoostFunction boostFunction,
                                                                  int baseTier, int machineTier) {
            for (T entry : chancedEntries) {
                if (passesChance(getChance(entry, boostFunction, baseTier, machineTier))) {
                    return Collections.singletonList(entry);
                }
            }
            return null;
        }

        @Override
        public long @NotNull [] applyCorrelation(long @NotNull [] yields) {
            long[] result = new long[yields.length];
            for (int i = 0; i < yields.length; i++) {
                if (yields[i] > 0) {
                    result[i] = yields[i];
                    break;
                }
            }
            return result;
        }

        @Override
        public @NotNull String getTranslationKey() {
            return "gregtech.chance_logic.xor";
        }

        @Override
        public String toString() {
            return "ChancedOutputLogic{XOR}";
        }
    };

    /**
     * Chanced Output Logic where nothing is produced
     */
    ChancedOutputLogic NONE = new ChancedOutputLogic() {

        @Override
        public @Nullable @Unmodifiable <I,
                T extends ChancedOutput<I>> List<@NotNull T> roll(@NotNull @Unmodifiable List<@NotNull T> chancedEntries,
                                                                  @NotNull ChanceBoostFunction boostFunction,
                                                                  int baseTier, int machineTier) {
            return null;
        }

        @Override
        public long @NotNull [] applyCorrelation(long @NotNull [] yields) {
            return new long[yields.length];
        }

        @Override
        public @NotNull String getTranslationKey() {
            return "gregtech.chance_logic.none";
        }

        @Override
        public String toString() {
            return "ChancedOutputLogic{NONE}";
        }
    };

    /**
     * @param entry         the entry to get the complete chance for
     * @param boostFunction the function boosting the entry's chance
     * @param baseTier      the base tier of the recipe
     * @param machineTier   the tier the recipe is run at
     * @return the total chance for the entry
     */
    static int getChance(@NotNull ChancedOutput<?> entry, @NotNull ChanceBoostFunction boostFunction, int baseTier,
                         int machineTier) {
        if (entry instanceof BoostableChanceEntry<?>boostableChanceEntry) {
            return boostFunction.getBoostedChance(boostableChanceEntry, baseTier, machineTier);
        }
        return entry.getChance();
    }

    /**
     * @param chance the chance to check
     * @return if the roll with the chance is successful
     */
    static boolean passesChance(int chance) {
        return chance > 0 && GTValues.RNG.nextInt(getMaxChancedValue()) <= chance;
    }

    /**
     * @return the upper bound for rolling chances
     */
    static int getMaxChancedValue() {
        return 10_000;
    }

    /**
     * Roll the chance and attempt to produce the output
     *
     * @param chancedEntries the list of entries to roll
     * @param boostFunction  the function to boost the entries' chances
     * @param baseTier       the base tier of the recipe
     * @param machineTier    the tier the recipe is run at
     * @return a list of the produced outputs
     */
    <I, T extends ChancedOutput<I>> @Nullable @Unmodifiable List<@NotNull T> roll(
                                                                                  @NotNull @Unmodifiable List<@NotNull T> chancedEntries,
                                                                                  @NotNull ChanceBoostFunction boostFunction,
                                                                                  int baseTier, int machineTier);

    /**
     * Applies this logic's correlation rule to a set of already-rolled per-entry yields (see
     * {@code gregtech.api.recipes.roll.RollInterpreter}), rather than rolling entries itself.
     * <p>
     * This exists alongside {@link #roll} (which independently rolls entries against a single fixed chance) so
     * that {@code ChancedOutputLogic}'s correlation rules (OR/AND/XOR/NONE) can also be layered on top of the
     * richer, per-entry {@code RollInterpreter} model (independent chance, weighted lottery, ranged yield, external
     * override): a {@code RollInterpreter} decides <i>how much</i> each entry would yield in isolation, and this
     * method decides which of those yields actually apply once the entries are considered together. A yield of
     * {@code <= 0} is treated as "this entry did not succeed".
     * <p>
     * The default implementation applies no correlation (equivalent to {@link #OR}'s independence), which is a
     * safe fallback for any custom {@link ChancedOutputLogic} that does not define one.
     *
     * @param yields the per-entry yields as computed by a {@code RollInterpreter}, in the same order as the
     *               entries that were rolled.
     * @return the adjusted yields after this logic's correlation rule is applied, same length and order as
     *         {@code yields}.
     */
    default long @NotNull [] applyCorrelation(long @NotNull [] yields) {
        return yields;
    }

    @NotNull
    String getTranslationKey();
}
