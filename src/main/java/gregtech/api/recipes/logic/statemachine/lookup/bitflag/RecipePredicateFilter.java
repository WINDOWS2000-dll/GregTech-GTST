package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.properties.RecipeProperty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * As {@link RecipeNumericFilter}, but for a recipe requirement that isn't a numeric threshold: a category, a
 * whitelist/blacklist, or any other test a recipe's own declared value must pass.
 * {@link RecipeNumericFilter} can't express these: a dimension
 * whitelist/blacklist or a cleanroom-strictness check isn't a single sortable threshold, it's an arbitrary
 * yes/no test of the recipe's declared value.
 * <p>
 * Only recipes that actually declare {@link #recipeProperty()} (per {@link Recipe#hasProperty}) are affected;
 * recipes without it are never excluded by this filter, exactly like {@link RecipeNumericFilter}'s contract.
 *
 * @param <T> the type {@link #recipeProperty()} stores its value as.
 */
public interface RecipePredicateFilter<T> {

    /** @return the recipe-side property this filter reads a requirement from. */
    @NotNull
    RecipeProperty<T> recipeProperty();

    /**
     * @return a test a recipe declaring {@link #recipeProperty()} must pass its own value through to remain a
     *         candidate this search, or {@code null} if this filter doesn't apply at all this search (e.g. the
     *         searching machine doesn't track whatever {@link gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty}
     *         this needs) &mdash; in which case every recipe declaring {@link #recipeProperty()} is left
     *         unfiltered by this check for this search, not excluded.
     */
    @Nullable
    Predicate<T> extractQuery(@NotNull RecipePropertySet properties);
}
