package gregtech.api.recipes.builders;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeBuilder;
import gregtech.api.recipes.RecipeMap;

/**
 * Always {@link Recipe#isGenerating()}; voltage is still registered as a plain positive magnitude
 * (e.g. {@code .EUt(V[LV])} in {@code FuelRecipes}), direction is carried entirely by this flag.
 */
public class FuelRecipeBuilder extends RecipeBuilder<FuelRecipeBuilder> {

    public FuelRecipeBuilder() {
        setGenerating();
    }

    public FuelRecipeBuilder(Recipe recipe, RecipeMap<FuelRecipeBuilder> recipeMap) {
        super(recipe, recipeMap);
    }

    public FuelRecipeBuilder(RecipeBuilder<FuelRecipeBuilder> recipeBuilder) {
        super(recipeBuilder);
    }

    @Override
    public FuelRecipeBuilder copy() {
        return new FuelRecipeBuilder(this);
    }
}
