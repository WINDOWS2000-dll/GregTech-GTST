package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.CurrentDimensionProperty;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.recipes.properties.impl.DimensionProperty;
import gregtech.api.recipes.properties.impl.DimensionProperty.DimensionPropertyList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Bridges {@link DimensionProperty} (a recipe's own dimension whitelist/blacklist) to a location-aware machine's
 * {@link BitflagRecipeLookup} via {@link CurrentDimensionProperty}. A recipe passes iff its own
 * {@link DimensionPropertyList} accepts the machine's current dimension.
 * <p>
 * Register with {@code lookup.registerFilter(DimensionFilter.INSTANCE)} on any {@link BitflagRecipeLookup} whose
 * {@code RecipeMap} has dimension-gated recipes.
 */
public final class DimensionFilter implements RecipePredicateFilter<DimensionPropertyList> {

    public static final DimensionFilter INSTANCE = new DimensionFilter();

    private DimensionFilter() {}

    @Override
    public @NotNull RecipeProperty<DimensionPropertyList> recipeProperty() {
        return DimensionProperty.getInstance();
    }

    @Override
    public @Nullable Predicate<DimensionPropertyList> extractQuery(@NotNull RecipePropertySet properties) {
        CurrentDimensionProperty current = properties.getNullable(CurrentDimensionProperty.EMPTY);
        if (current == null) return null;
        int dimension = current.dimension();
        return list -> list.checkDimension(dimension);
    }
}
