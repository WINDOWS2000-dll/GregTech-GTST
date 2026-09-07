package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.CleanroomFulfillmentProperty;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.recipes.properties.impl.CleanroomProperty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Bridges {@link CleanroomProperty} (a recipe's own required {@link CleanroomType}) to a cleanroom-aware machine's
 * {@link BitflagRecipeLookup} via {@link CleanroomFulfillmentProperty}. A recipe passes iff the
 * machine's current fulfillment predicate accepts the recipe's own required {@link CleanroomType}.
 * <p>
 * Register with {@code lookup.registerFilter(CleanroomFilter.INSTANCE)} on any {@link BitflagRecipeLookup} whose
 * {@code RecipeMap} has cleanroom-gated recipes.
 */
public final class CleanroomFilter implements RecipePredicateFilter<CleanroomType> {

    public static final CleanroomFilter INSTANCE = new CleanroomFilter();

    private CleanroomFilter() {}

    @Override
    public @NotNull RecipeProperty<CleanroomType> recipeProperty() {
        return CleanroomProperty.getInstance();
    }

    @Override
    public @Nullable Predicate<CleanroomType> extractQuery(@NotNull RecipePropertySet properties) {
        CleanroomFulfillmentProperty fulfillment = properties.getNullable(CleanroomFulfillmentProperty.EMPTY);
        return fulfillment == null ? null : fulfillment.fulfillment();
    }
}
