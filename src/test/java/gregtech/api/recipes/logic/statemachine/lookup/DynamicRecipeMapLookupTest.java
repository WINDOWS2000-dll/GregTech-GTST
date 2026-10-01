package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.RecipeLookup;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.CleanroomFilter;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.CleanroomFulfillmentProperty;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DynamicRecipeMapLookupTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeMap<SimpleRecipeBuilder> newMap(String name) {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>(name, new SimpleRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(1).EUt(1).buildAndRegister();
        return map;
    }

    @Test
    void returnsNoRecipesWhenTheSuppliedMapIsNull() {
        RecipeLookup lookup = new DynamicRecipeMapLookup(() -> null);

        Iterator<Recipe> found = lookup.findRecipes(30, Collections.singletonList(new ItemStack(Items.IRON_INGOT)),
                Collections.emptyList());

        assertThat(found.hasNext(), is(false));
    }

    @Test
    void delegatesToWhicheverMapTheSupplierCurrentlyReturns() {
        RecipeMap<SimpleRecipeBuilder> mapA = newMap("dynamic_lookup_test_a_" + System.nanoTime());
        RecipeMap<SimpleRecipeBuilder> mapB = newMap("dynamic_lookup_test_b_" + System.nanoTime());
        RecipeMap<?>[] current = { mapA };
        RecipeLookup lookup = new DynamicRecipeMapLookup(() -> current[0]);

        Iterator<Recipe> foundA = lookup.findRecipes(1, Collections.singletonList(new ItemStack(Items.IRON_INGOT)),
                Collections.emptyList());
        assertThat(foundA.hasNext(), is(true));

        current[0] = null; // simulate the machine hatch becoming empty
        Iterator<Recipe> foundNone = lookup.findRecipes(1, Collections.singletonList(new ItemStack(Items.IRON_INGOT)),
                Collections.emptyList());
        assertThat(foundNone.hasNext(), is(false));

        current[0] = mapB; // simulate a different machine being inserted
        Iterator<Recipe> foundB = lookup.findRecipes(1, Collections.singletonList(new ItemStack(Items.IRON_INGOT)),
                Collections.emptyList());
        assertThat(foundB.hasNext(), is(true));
    }

    @Test
    void forwardsPropertiesToTheUnderlyingRecipeMapLookup() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>(
                "dynamic_lookup_test_cleanroom_" + System.nanoTime(), new SimpleRecipeBuilder())
                        .itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(1).EUt(1).cleanroom(CleanroomType.CLEANROOM).buildAndRegister();
        map.getBitflagLookup().registerFilter(CleanroomFilter.INSTANCE);

        RecipeLookup lookup = new DynamicRecipeMapLookup(() -> map);
        List<ItemStack> items = Collections.singletonList(new ItemStack(Items.IRON_INGOT));

        RecipePropertySet unfulfilled = RecipePropertySet.empty();
        unfulfilled.add(new CleanroomFulfillmentProperty(type -> false));
        assertThat("a machine not satisfying the recipe's cleanroom requirement should not find it",
                lookup.findRecipes(1, unfulfilled, items, Collections.emptyList()).hasNext(), is(false));

        RecipePropertySet fulfilled = RecipePropertySet.empty();
        fulfilled.add(new CleanroomFulfillmentProperty(type -> type == CleanroomType.CLEANROOM));
        assertThat("a machine satisfying the recipe's cleanroom requirement should find it",
                lookup.findRecipes(1, fulfilled, items, Collections.emptyList()).hasNext(), is(true));
    }
}
