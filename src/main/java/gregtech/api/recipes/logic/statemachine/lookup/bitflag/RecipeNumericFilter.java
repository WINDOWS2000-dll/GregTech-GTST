package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.properties.RecipeProperty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bridges an existing {@link RecipeProperty} (a recipe's own declared numeric requirement, e.g. a hypothetical
 * temperature requirement) to a {@link RecipeThresholdIndex}-backed pre-filter in a {@link BitflagRecipeLookup},
 * the same way {@link BitflagRecipeLookup}'s built-in voltage check already works internally. Registered
 * per-lookup instead of through a global registry &mdash; see
 * {@link BitflagRecipeLookup#registerFilter}'s JavaDoc for why.
 * <p>
 * Only recipes that actually declare {@link #recipeProperty()} (per {@link Recipe#hasProperty}) are affected;
 * recipes without it are never excluded by this filter, exactly like {@code RecipeThresholdIndex}'s own contract.
 *
 * @param <T> the type {@link #recipeProperty()} stores its value as.
 */
public interface RecipeNumericFilter<T> {

    /** @return the recipe-side property this filter reads a requirement from. */
    @NotNull
    RecipeProperty<T> recipeProperty();

    /** @return which direction a recipe's own threshold is compared against the search-side value. */
    @NotNull
    RecipeThresholdIndex.Comparison comparison();

    /** @return the numeric threshold a recipe declaring {@link #recipeProperty()} with {@code value} requires. */
    long extractThreshold(@NotNull T value);

    /**
     * @return the value to compare recipes' thresholds against for this search, or {@code null} if this filter
     *         doesn't apply at all this search (e.g. the searching machine doesn't track whatever
     *         {@link gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty} this needs) &mdash; in
     *         which case every recipe declaring {@link #recipeProperty()} is left unfiltered by this check for
     *         this search, not excluded.
     */
    @Nullable
    Long extractQuery(@NotNull RecipePropertySet properties);
}
