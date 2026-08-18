package gregtech.api.recipes.category;

import gregtech.api.GTValues;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.recipes.RecipeMaps;

public final class RecipeCategories {

    public static final GTRecipeCategory ARC_FURNACE_RECYCLING = GTRecipeCategory.create(GTValues.MODID,
            "arc_furnace_recycling",
            "gregtech.recipe.category.arc_furnace_recycling",
            RecipeMaps.ARC_FURNACE_RECIPES)
            .jeiIcon(GTGuiTextures.ARC_FURNACE_RECYLCING_CATEGORY)
            .jeiSortToBack(true);

    public static final GTRecipeCategory MACERATOR_RECYCLING = GTRecipeCategory.create(GTValues.MODID,
            "macerator_recycling",
            "gregtech.recipe.category.macerator_recycling",
            RecipeMaps.MACERATOR_RECIPES)
            .jeiIcon(GTGuiTextures.MACERATOR_RECYLCING_CATEGORY)
            .jeiSortToBack(true);

    public static final GTRecipeCategory EXTRACTOR_RECYCLING = GTRecipeCategory.create(GTValues.MODID,
            "extractor_recycling",
            "gregtech.recipe.category.extractor_recycling",
            RecipeMaps.EXTRACTOR_RECIPES)
            .jeiIcon(GTGuiTextures.EXTRACTOR_RECYLCING_CATEGORY)
            .jeiSortToBack(true);

    private RecipeCategories() {}
}
