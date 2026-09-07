package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.ValidationResult;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class IngredientBitflagIndexTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("ingredient_bitflag_index_test_" + System.nanoTime(), new SimpleRecipeBuilder())
                .itemInputs(70).itemOutputs(3).fluidInputs(2).fluidOutputs(1).build();
    }

    /**
     * Registers {@code builder}'s recipe into {@code map} and returns it, since {@code buildAndRegister()} itself
     * returns {@code void}.
     */
    private static Recipe register(RecipeMap<SimpleRecipeBuilder> map, SimpleRecipeBuilder builder) {
        ValidationResult<Recipe> result = builder.build();
        map.addRecipe(result);
        return result.getResult();
    }

    @Test
    void matchFindsARecipeWhoseIngredientTypesAreAllPresent() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        Recipe recipe = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(1));

        IngredientBitflagIndex index = IngredientBitflagIndex.build(map.getRecipeList());
        List<Recipe> matches = index.match(Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)),
                Collections.emptyList(), new BitSet());

        assertThat(matches, is(Collections.singletonList(recipe)));
    }

    @Test
    void matchExcludesARecipeMissingOneOfItsIngredientTypes() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        register(map,
                map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.GOLD_NUGGET, 1))
                        .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(1));

        IngredientBitflagIndex index = IngredientBitflagIndex.build(map.getRecipeList());
        // only iron present, gold nugget is missing entirely
        List<Recipe> matches = index.match(Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)),
                Collections.emptyList(), new BitSet());

        assertThat(matches.isEmpty(), is(true));
    }

    @Test
    void matchDeliberatelyIgnoresQuantityInsufficiency() {
        // documents the class's contract: type presence only, quantity is RecipeParallelOperator's job downstream
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        Recipe recipe = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 10))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(1));

        IngredientBitflagIndex index = IngredientBitflagIndex.build(map.getRecipeList());
        // only 1 iron present, recipe needs 10 -- still reported as a match at this stage
        List<Recipe> matches = index.match(Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)),
                Collections.emptyList(), new BitSet());

        assertThat(matches, is(Collections.singletonList(recipe)));
    }

    @Test
    void matchRespectsTheExcludedBitSet() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(1));

        IngredientBitflagIndex index = IngredientBitflagIndex.build(map.getRecipeList());
        BitSet excluded = new BitSet();
        excluded.set(0); // the only recipe in this map

        List<Recipe> matches = index.match(Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1)),
                Collections.emptyList(), excluded);

        assertThat(matches.isEmpty(), is(true));
    }

    @Test
    void matchWorksWithFluidIngredients() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        Recipe recipe = register(map, map.recipeBuilder().fluidInputs(new FluidStack(FluidRegistry.WATER, 100))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(1));

        IngredientBitflagIndex index = IngredientBitflagIndex.build(map.getRecipeList());
        List<Recipe> matches = index.match(Collections.emptyList(),
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100)), new BitSet());

        assertThat(matches, is(Collections.singletonList(recipe)));
    }

    @Test
    void recipesWithMoreThan64IngredientsFallBackToFullMatching() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        List<ItemStack> sixtyFiveApples = new ArrayList<>();
        for (int i = 0; i < 65; i++) sixtyFiveApples.add(new ItemStack(Items.APPLE, 1));
        Recipe recipe = register(map, map.recipeBuilder().inputs(sixtyFiveApples.toArray(new ItemStack[0]))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(1));

        IngredientBitflagIndex index = IngredientBitflagIndex.build(map.getRecipeList());

        // the fallback path uses Recipe#matches, which (unlike the bitflag fast path) DOES check quantity: 64
        // apples is one short of the 65 the recipe needs, so this must NOT match.
        List<ItemStack> insufficientApples = new ObjectArrayList<>();
        insufficientApples.add(new ItemStack(Items.APPLE, 64));
        assertThat(index.match(insufficientApples, Collections.emptyList(), new BitSet()).isEmpty(), is(true));

        List<ItemStack> sufficientApples = new ObjectArrayList<>();
        sufficientApples.add(new ItemStack(Items.APPLE, 65));
        assertThat(index.match(sufficientApples, Collections.emptyList(), new BitSet()),
                is(Collections.singletonList(recipe)));
    }

    @Test
    void recipeCountAndRecipeAtExposeTheBuiltIndexOrder() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        Recipe recipe = register(map, map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1))
                .outputs(new ItemStack(Items.GOLD_INGOT)).duration(1).EUt(1));

        IngredientBitflagIndex index = IngredientBitflagIndex.build(map.getRecipeList());

        assertThat(index.recipeCount(), is(1));
        assertThat(index.recipeAt(0), is(recipe));
    }
}
