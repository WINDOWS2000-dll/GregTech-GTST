package gregtech.api.gui.widgets;

import gregtech.api.GregTechAPI;
import gregtech.api.gui.resources.TextureArea;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.integration.IntegrationModule;
import gregtech.integration.jei.JustEnoughItemsModule;
import gregtech.integration.jei.recipe.RecipeMapCategory;
import gregtech.modules.GregTechModules;

import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.DoubleSupplier;

public class RecipeProgressWidget extends ProgressWidget {

    private final RecipeMap<?> recipeMap;

    public RecipeProgressWidget(DoubleSupplier progressSupplier, int x, int y, int width, int height,
                                RecipeMap<?> recipeMap) {
        super(progressSupplier, x, y, width, height);
        this.recipeMap = recipeMap;
        setHoverTextConsumer(
                list -> list.add(new TextComponentTranslation("gui.widget.recipeProgressWidget.default_tooltip")));
    }

    public RecipeProgressWidget(DoubleSupplier progressSupplier, int x, int y, int width, int height,
                                TextureArea fullImage, MoveType moveType, RecipeMap<?> recipeMap) {
        super(progressSupplier, x, y, width, height, fullImage, moveType);
        this.recipeMap = recipeMap;
        setHoverTextConsumer(
                list -> list.add(new TextComponentTranslation("gui.widget.recipeProgressWidget.default_tooltip")));
    }

    public RecipeProgressWidget(int ticksPerCycle, int x, int y, int width, int height, TextureArea fullImage,
                                MoveType moveType, RecipeMap<?> recipeMap) {
        super(ticksPerCycle, x, y, width, height, fullImage, moveType);
        this.recipeMap = recipeMap;
        setHoverTextConsumer(
                list -> list.add(new TextComponentTranslation("gui.widget.recipeProgressWidget.default_tooltip")));
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (!GregTechAPI.moduleManager.isModuleEnabled(GregTechModules.MODULE_JEI)) {
            return false;
        }
        if (isMouseOverElement(mouseX, mouseY)) {
            List<String> categoryID = new ArrayList<>();
            if (recipeMap == RecipeMaps.FURNACE_RECIPES) {
                // furnace recipes are never registered as GT recipe categories (they're converted from vanilla
                // smelting recipes on the fly), so there's no RecipeMapCategory to fall back on here.
                categoryID.add("minecraft.smelting");
            } else {
                Collection<RecipeMapCategory> categories = RecipeMapCategory.getCategoriesFor(recipeMap);
                if (categories == null || categories.isEmpty()) {
                    return false;
                }
                for (RecipeMapCategory category : categories) {
                    categoryID.add(category.getUid());
                }
            }

            if (JustEnoughItemsModule.jeiRuntime == null) {
                IntegrationModule.logger.error("GTCEu JEI integration has crashed, this is not a good thing");
                return false;
            }
            JustEnoughItemsModule.jeiRuntime.getRecipesGui().showCategories(categoryID);
            return true;
        }
        return false;
    }
}
