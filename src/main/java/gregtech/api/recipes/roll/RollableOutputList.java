package gregtech.api.recipes.roll;

import gregtech.api.recipes.chance.output.ChancedOutputLogic;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * A list of output values, some guaranteed ("unrolled") and some subject to rolling ("rolled"), together with how
 * to roll the latter: a {@link RollInterpreter} decides each rollable entry's yield in isolation, and a
 * {@link ChancedOutputLogic} then correlates those yields against each other (e.g. "all or nothing"). See
 * {@link #roll} for where the two are combined.
 *
 * @param <T> the type of value held (e.g. {@code ItemStack} or {@code FluidStack}).
 */
@Unmodifiable
public final class RollableOutputList<T> extends AbstractList<T> {

    private final @NotNull ToLongFunction<@NotNull T> yieldCounter;

    private final T @NotNull [] unrolled;

    private final T @NotNull [] rolled;
    private final long @NotNull [] rollValues;
    private final long @NotNull [] rollBoosts;

    private final @NotNull RollInterpreter interpreter;
    private final @NotNull ChancedOutputLogic correlation;

    @SuppressWarnings("unchecked")
    public RollableOutputList(@NotNull ToLongFunction<@NotNull T> yieldCounter,
                              @NotNull Collection<@NotNull T> unrolled,
                              @NotNull Collection<@NotNull RollInformation<@NotNull T>> rolled,
                              @NotNull RollInterpreter interpreter, @NotNull ChancedOutputLogic correlation) {
        this.yieldCounter = yieldCounter;
        this.unrolled = (T[]) unrolled.toArray();
        this.rolled = (T[]) rolled.stream().map(RollInformation::value).toArray();
        this.rollValues = rolled.stream().mapToLong(RollInformation::rollValue).toArray();
        this.rollBoosts = rolled.stream().mapToLong(RollInformation::rollBoost).toArray();
        this.interpreter = interpreter;
        this.correlation = correlation;
    }

    private long @NotNull [] maxYields(int trimLimit) {
        trimLimit = Math.min(trimLimit, rolled.length);
        long[] value = new long[trimLimit];
        for (int i = 0; i < trimLimit; i++) {
            value[i] = yieldCounter.applyAsLong(rolled[i]);
        }
        return value;
    }

    private long @NotNull [] rollValues(int trimLimit) {
        trimLimit = Math.min(trimLimit, rollValues.length);
        long[] value = new long[trimLimit];
        System.arraycopy(rollValues, 0, value, 0, trimLimit);
        return value;
    }

    private long @NotNull [] rollBoosts(int trimLimit) {
        trimLimit = Math.min(trimLimit, rollBoosts.length);
        long[] value = new long[trimLimit];
        System.arraycopy(rollBoosts, 0, value, 0, trimLimit);
        return value;
    }

    /**
     * @return the guaranteed (non-chance-based) entries, as a {@link List} rather than the backing {@code T[]}
     *         directly: {@code T[]} is only safely usable from inside this generically-typed class (see the
     *         {@code @SuppressWarnings("unchecked")} cast in the constructor) — its actual runtime component type
     *         is {@code Object[]}, so handing it out as {@code T[]} would let a caller's compiler insert a
     *         {@code checkcast} to the substituted array type (e.g. {@code ItemStack[]}) that fails at runtime.
     *         {@link Arrays#asList} sidesteps this since {@link List}'s erasure carries no such reified type.
     */
    public @NotNull List<T> getUnrolled() {
        return Arrays.asList(unrolled);
    }

    /** As {@link #getUnrolled()}, but for the chance-based entries. */
    public @NotNull List<T> getRolled() {
        return Arrays.asList(rolled);
    }

    @Contract("->new")
    public @NotNull ObjectArrayList<RollInformation<T>> recomposeRolled() {
        ObjectArrayList<RollInformation<T>> list = new ObjectArrayList<>(rolled.length);
        for (int i = 0; i < rolled.length; i++) {
            list.add(new RollInformation<>(rolled[i], rollValues[i], rollBoosts[i]));
        }
        return list;
    }

    /**
     * @return the yields of every entry, unrolled entries first (always at their full amount, times
     *         {@code parallel}) followed by the rolled entries' yields (see {@link #roll}).
     */
    public long @NotNull [] comprehensiveRoll(int boostStrength, int trimLimit, int parallel) {
        trimLimit = Math.min(trimLimit, size());
        long[] yield = new long[trimLimit];
        int m = Math.min(trimLimit, unrolled.length);
        for (int i = 0; i < m; i++) {
            yield[i] = yieldCounter.applyAsLong(unrolled[i]) * parallel;
        }
        trimLimit -= m;
        if (trimLimit == 0) return yield;
        long[] roll = roll(boostStrength, trimLimit, parallel);
        System.arraycopy(roll, 0, yield, m, roll.length);
        return yield;
    }

    /**
     * Rolls only the rolled entries: first interprets each entry's yield independently via this list's
     * {@link RollInterpreter}, then applies this list's {@link ChancedOutputLogic} to correlate those yields
     * against each other.
     */
    public long @NotNull [] roll(int boostStrength, int trimLimit, int parallel) {
        long[] interpreted = interpreter.interpretAndRoll(maxYields(trimLimit), rollValues(trimLimit),
                rollBoosts(trimLimit), boostStrength, parallel);
        return correlation.applyCorrelation(interpreted);
    }

    /** As {@link #roll(int, int, int)}, but rolling against an explicitly-supplied interpreter instead of this list's own. */
    public long @NotNull [] roll(@NotNull RollInterpreter interpreterOverride, int boostStrength, int trimLimit,
                                 int parallel) {
        long[] interpreted = interpreterOverride.interpretAndRoll(maxYields(trimLimit), rollValues(trimLimit),
                rollBoosts(trimLimit), boostStrength, parallel);
        return correlation.applyCorrelation(interpreted);
    }

    @Override
    public T get(int index) {
        return index >= unrolled.length ? rolled[index - unrolled.length] : unrolled[index];
    }

    public boolean isRolled(int index) {
        return index >= unrolled.length && index < size();
    }

    public long getMaxYield(int index) {
        return yieldCounter.applyAsLong(rolled[index - unrolled.length]);
    }

    public long getRollValue(int index) {
        return rollValues[index - unrolled.length];
    }

    public long getRollBoost(int index) {
        return rollBoosts[index - unrolled.length];
    }

    public @NotNull ToLongFunction<@NotNull T> getYieldCounter() {
        return yieldCounter;
    }

    public @NotNull RollInterpreter getInterpreter() {
        return interpreter;
    }

    public @NotNull ChancedOutputLogic getCorrelation() {
        return correlation;
    }

    @Override
    public int size() {
        return unrolled.length + rolled.length;
    }

    public boolean hasRolledEntries() {
        return rolled.length > 0;
    }

    private static final RollableOutputList<Object> EMPTY = new RollableOutputList<>(o -> 0,
            Collections.emptyList(), Collections.emptyList(), IndependentRollInterpreter.INSTANCE,
            ChancedOutputLogic.OR);

    @SuppressWarnings("unchecked")
    public static <T> RollableOutputList<T> empty() {
        return (RollableOutputList<T>) EMPTY;
    }
}
