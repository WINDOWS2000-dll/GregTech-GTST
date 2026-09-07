package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.RecipeLookup;
import gregtech.api.recipes.machines.RecipeMapFurnace;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeMapLookupTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_map_lookup_test_" + System.nanoTime(),
                new SimpleRecipeBuilder()).itemInputs(2).itemOutputs(2).build();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();
        map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT, 2)).outputs(new ItemStack(Items.DIAMOND))
                .duration(100).EUt(480).buildAndRegister();
        return map;
    }

    private static List<Recipe> collect(Iterator<Recipe> iterator) {
        List<Recipe> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    @Test
    void findsARecipeMatchingAvailableItems() {
        RecipeLookup lookup = new RecipeMapLookup(newMap());

        List<Recipe> found = collect(lookup.findRecipes(30, Collections.singletonList(new ItemStack(Items.IRON_INGOT, 4)),
                Collections.emptyList()));

        assertThat(found.size(), is(1));
        assertThat(found.get(0).getGuaranteedItemOutputs().get(0).getItem(), is(Items.GOLD_INGOT));
    }

    @Test
    void findsMultipleMatchingRecipesWhenSeveralInputsAreAvailable() {
        RecipeLookup lookup = new RecipeMapLookup(newMap());

        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Items.IRON_INGOT, 4));
        items.add(new ItemStack(Items.GOLD_INGOT, 2));
        List<Recipe> found = collect(lookup.findRecipes(480, items, Collections.emptyList()));

        assertThat(found.size(), is(2));
    }

    @Test
    void excludesRecipesRequiringMoreVoltageThanAvailable() {
        RecipeLookup lookup = new RecipeMapLookup(newMap());

        List<Recipe> found = collect(lookup.findRecipes(30,
                Collections.singletonList(new ItemStack(Items.GOLD_INGOT, 2)), Collections.emptyList()));

        assertThat(found.isEmpty(), is(true)); // the diamond recipe needs 480 EU/t, only 30 is available
    }

    @Test
    void reportsTypeMatchesRegardlessOfQuantitySufficiency() {
        RecipeLookup lookup = new RecipeMapLookup(newMap());

        List<Recipe> found = collect(lookup.findRecipes(30,
                Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)), Collections.emptyList()));

        assertThat(found.size(), is(1)); // needs 4, only 1 available -- still reported as a type match
    }

    @Test
    void fallsBackToRecipeMapFindRecipeForSubclassesThatSynthesizeRecipesOnDemand() {
        RecipeLookup lookup = new RecipeMapLookup(RecipeMaps.FURNACE_RECIPES);

        List<Recipe> found = collect(lookup.findRecipes(RecipeMapFurnace.RECIPE_EUT,
                Collections.singletonList(new ItemStack(Blocks.IRON_ORE)), Collections.emptyList()));

        assertThat(found.size(), is(1));
        assertThat(found.get(0).getGuaranteedItemOutputs().get(0).getItem(), is(Items.IRON_INGOT));
    }
}
