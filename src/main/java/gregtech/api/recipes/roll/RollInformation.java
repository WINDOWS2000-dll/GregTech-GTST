package gregtech.api.recipes.roll;

import com.github.bsideup.jabel.Desugar;

/**
 * A single rollable entry: a value paired with the roll data a {@link RollInterpreter} interprets to decide its
 * yield. See {@link RollInterpreter#interpretAndRoll} for what {@code rollValue}/{@code rollBoost} mean for a given
 * interpreter, and {@link Long#MIN_VALUE} as a {@code rollValue} sentinel for "not rollable".
 */
@Desugar
public record RollInformation<T>(T value, long rollValue, long rollBoost) {}
