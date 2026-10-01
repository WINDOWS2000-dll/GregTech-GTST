package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.ComputationCapacityProperty;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.recipes.properties.impl.ComputationProperty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges {@link ComputationProperty} (a recipe's own required CWU/t) to an HPCA-like machine's
 * {@link BitflagRecipeLookup} via {@link ComputationCapacityProperty}. A recipe passes iff its own required
 * CWU/t is no more than the machine's currently available CWU/t.
 * <p>
 * Register with {@code lookup.registerFilter(ComputationFilter.INSTANCE)} on any {@link BitflagRecipeLookup} whose
 * {@code RecipeMap} has computation-gated recipes.
 */
public final class ComputationFilter implements RecipeNumericFilter<Integer> {

    public static final ComputationFilter INSTANCE = new ComputationFilter();

    private ComputationFilter() {}

    @Override
    public @NotNull RecipeProperty<Integer> recipeProperty() {
        return ComputationProperty.getInstance();
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
        ComputationCapacityProperty capacity = properties.getNullable(ComputationCapacityProperty.EMPTY);
        return capacity == null ? null : (long) capacity.cwut();
    }
}
