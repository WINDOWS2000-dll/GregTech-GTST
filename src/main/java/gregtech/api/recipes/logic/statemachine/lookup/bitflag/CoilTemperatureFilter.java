package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.TemperatureCapacityProperty;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.recipes.properties.impl.TemperatureProperty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges {@link TemperatureProperty} (a recipe's own minimum required coil temperature) to a coil-based machine's
 * {@link BitflagRecipeLookup} via {@link TemperatureCapacityProperty}. A recipe passes iff its own
 * required temperature is no more than the machine's current coil temperature, exactly like the built-in voltage
 * check.
 * <p>
 * Register with {@code lookup.registerFilter(CoilTemperatureFilter.INSTANCE)} on any {@link BitflagRecipeLookup}
 * whose {@code RecipeMap} has temperature-gated recipes (e.g. the Electric Blast Furnace's).
 */
public final class CoilTemperatureFilter implements RecipeNumericFilter<Integer> {

    public static final CoilTemperatureFilter INSTANCE = new CoilTemperatureFilter();

    private CoilTemperatureFilter() {}

    @Override
    public @NotNull RecipeProperty<Integer> recipeProperty() {
        return TemperatureProperty.getInstance();
    }

    @Override
    public @NotNull RecipeThresholdIndex.Comparison comparison() {
        return RecipeThresholdIndex.Comparison.AT_MOST;
    }

    @Override
    public long extractThreshold(@NotNull Integer value) {
        return value;
    }

    @Override
    public @Nullable Long extractQuery(@NotNull RecipePropertySet properties) {
        TemperatureCapacityProperty capacity = properties.getNullable(TemperatureCapacityProperty.EMPTY);
        return capacity == null ? null : (long) capacity.temperature();
    }
}
