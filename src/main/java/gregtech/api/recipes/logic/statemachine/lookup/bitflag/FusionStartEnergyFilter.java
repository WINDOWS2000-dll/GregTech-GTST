package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.FusionStartCapacityProperty;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.recipes.properties.impl.FusionEUToStartProperty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges {@link FusionEUToStartProperty} (a recipe's own minimum required starting EU) to the Fusion Reactor's
 * {@link BitflagRecipeLookup} via {@link FusionStartCapacityProperty}. A recipe passes iff its own required starting EU is
 * no more than the machine's currently available starting EU.
 * <p>
 * Register with {@code lookup.registerFilter(FusionStartEnergyFilter.INSTANCE)} on the Fusion Reactor's
 * {@link BitflagRecipeLookup}.
 */
public final class FusionStartEnergyFilter implements RecipeNumericFilter<Long> {

    public static final FusionStartEnergyFilter INSTANCE = new FusionStartEnergyFilter();

    private FusionStartEnergyFilter() {}

    @Override
    public @NotNull RecipeProperty<Long> recipeProperty() {
        return FusionEUToStartProperty.getInstance();
    }

    @Override
    public @NotNull RecipeThresholdIndex.Comparison comparison() {
        return RecipeThresholdIndex.Comparison.AT_MOST;
    }

    @Override
    public long extractThreshold(@NotNull Long value) {
        return value;
    }

    @Override
    public @Nullable Long extractQuery(@NotNull RecipePropertySet properties) {
        FusionStartCapacityProperty capacity = properties.getNullable(FusionStartCapacityProperty.EMPTY);
        return capacity == null ? null : capacity.euAvailable();
    }
}
