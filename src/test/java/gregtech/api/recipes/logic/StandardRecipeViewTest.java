package gregtech.api.recipes.logic;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandardRecipeViewTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static final RecipePropertySet NO_POWER = RecipePropertySet.empty();

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("standard_recipe_view_test_" + System.nanoTime(), new SimpleRecipeBuilder())
                .itemInputs(3).itemOutputs(3).fluidInputs(2).fluidOutputs(2).build();
    }

    private static Recipe simpleRecipe(RecipeMap<SimpleRecipeBuilder> map) {
        return map.recipeBuilder()
                .inputs(new ItemStack(Items.IRON_INGOT, 4))
                .notConsumable(new ItemStack(Blocks.COBBLESTONE))
                .fluidInputs(new FluidStack(FluidRegistry.WATER, 100))
                .outputs(new ItemStack(Items.GOLD_INGOT))
                .chancedOutput(new ItemStack(Items.DIAMOND), 10_000, 0)
                .duration(100).EUt(30)
                .build().getResult();
    }

    @Test
    void onlyConsumableItemIngredientsAreReportedAsConsumed() {
        Recipe recipe = simpleRecipe(newMap());
        List<ItemStack> matchedItems = new ObjectArrayList<>(
                Arrays.asList(new ItemStack(Items.IRON_INGOT, 10), new ItemStack(Blocks.COBBLESTONE, 1)));
        List<FluidStack> matchedFluids = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 1000));

        StandardRecipeView view = new StandardRecipeView(recipe, matchedItems, matchedFluids, 1);

        List<ItemStack> consumed = view.getConsumedItems();
        assertThat(consumed.size(), is(1));
        assertThat(consumed.get(0).getItem(), is(Items.IRON_INGOT));
        assertThat(consumed.get(0).getCount(), is(4));

        // the original snapshot passed in must not be mutated by computing consumption
        assertThat(matchedItems.get(0).getCount(), is(10));
        assertThat(matchedItems.get(1).getCount(), is(1));
    }

    @Test
    void fluidConsumptionIsComputedFromTheActualRecipeRequirement() {
        Recipe recipe = simpleRecipe(newMap());
        List<ItemStack> matchedItems = Arrays.asList(new ItemStack(Items.IRON_INGOT, 10),
                new ItemStack(Blocks.COBBLESTONE, 1));
        List<FluidStack> matchedFluids = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 1000));

        StandardRecipeView view = new StandardRecipeView(recipe, matchedItems, matchedFluids, 1);

        List<FluidStack> consumed = view.getConsumedFluids();
        assertThat(consumed.size(), is(1));
        assertThat(consumed.get(0).amount, is(100));
        assertThat(matchedFluids.get(0).amount, is(1000)); // original snapshot untouched
    }

    @Test
    void consumptionResultIsCachedAcrossCalls() {
        Recipe recipe = simpleRecipe(newMap());
        List<ItemStack> matchedItems = Arrays.asList(new ItemStack(Items.IRON_INGOT, 10),
                new ItemStack(Blocks.COBBLESTONE, 1));
        List<FluidStack> matchedFluids = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 1000));
        StandardRecipeView view = new StandardRecipeView(recipe, matchedItems, matchedFluids, 1);

        List<ItemStack> first = view.getConsumedItems();
        List<ItemStack> second = view.getConsumedItems();
        assertThat(first, org.hamcrest.CoreMatchers.sameInstance(second));
    }

    @Test
    void rollItemsIncludesGuaranteedAndAlwaysSucceedingChancedOutputs() {
        Recipe recipe = simpleRecipe(newMap());
        List<ItemStack> matchedItems = Arrays.asList(new ItemStack(Items.IRON_INGOT, 4),
                new ItemStack(Blocks.COBBLESTONE, 1));
        List<FluidStack> matchedFluids = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100));
        StandardRecipeView view = new StandardRecipeView(recipe, matchedItems, matchedFluids, 1);

        List<ItemStack> result = view.rollItems(NO_POWER, 0, 0, Integer.MAX_VALUE);

        assertThat(result.size(), is(2)); // guaranteed gold ingot + always-succeeding chanced diamond
    }

    @Test
    void rollItemsRespectsItemTrimLimit() {
        Recipe recipe = simpleRecipe(newMap());
        List<ItemStack> matchedItems = Arrays.asList(new ItemStack(Items.IRON_INGOT, 4),
                new ItemStack(Blocks.COBBLESTONE, 1));
        List<FluidStack> matchedFluids = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100));
        StandardRecipeView view = new StandardRecipeView(recipe, matchedItems, matchedFluids, 1);

        List<ItemStack> result = view.rollItems(NO_POWER, 0, 0, 1);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getItem(), is(Items.GOLD_INGOT));
    }

    @Test
    void rollItemsScalesOutputsByParallel() {
        Recipe recipe = simpleRecipe(newMap());
        List<ItemStack> matchedItems = Arrays.asList(new ItemStack(Items.IRON_INGOT, 20),
                new ItemStack(Blocks.COBBLESTONE, 1));
        List<FluidStack> matchedFluids = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 500));
        StandardRecipeView view = new StandardRecipeView(recipe, matchedItems, matchedFluids, 5);

        assertThat(view.getParallel(), is(5));

        List<ItemStack> result = view.rollItems(NO_POWER, 0, 0, Integer.MAX_VALUE);

        ItemStack gold = null;
        for (ItemStack stack : result) {
            if (stack.getItem() == Items.GOLD_INGOT) {
                gold = stack;
                break;
            }
        }
        assertThat(gold, org.hamcrest.CoreMatchers.notNullValue());
        assertThat(gold.getCount(), is(5)); // recipe's own configured output (1) * parallel(5)
    }

    @Test
    void consumedItemsAndFluidsAreScaledByParallel() {
        Recipe recipe = simpleRecipe(newMap());
        List<ItemStack> matchedItems = Arrays.asList(new ItemStack(Items.IRON_INGOT, 20),
                new ItemStack(Blocks.COBBLESTONE, 1));
        List<FluidStack> matchedFluids = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 500));
        StandardRecipeView view = new StandardRecipeView(recipe, matchedItems, matchedFluids, 5);

        List<ItemStack> consumedItems = view.getConsumedItems();
        assertThat(consumedItems.size(), is(1));
        assertThat(consumedItems.get(0).getCount(), is(20)); // 4 per copy * parallel(5)

        List<FluidStack> consumedFluids = view.getConsumedFluids();
        assertThat(consumedFluids.size(), is(1));
        assertThat(consumedFluids.get(0).amount, is(500)); // 100 per copy * parallel(5)
    }

    @Test
    void getActualAmperageScalesRecipeAmperageByParallel() {
        Recipe recipe = simpleRecipe(newMap());
        List<ItemStack> matchedItems = Arrays.asList(new ItemStack(Items.IRON_INGOT, 20),
                new ItemStack(Blocks.COBBLESTONE, 1));
        List<FluidStack> matchedFluids = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 500));
        StandardRecipeView view = new StandardRecipeView(recipe, matchedItems, matchedFluids, 5);

        assertThat(view.getActualAmperage(), is(5L)); // recipe's own amperage (1, default) * parallel(5)
    }

    @Test
    void throwsIfConstructedAgainstInputsTheRecipeDoesNotActuallyMatch() {
        Recipe recipe = simpleRecipe(newMap());
        // not enough iron, and missing the notConsumable cobblestone entirely
        List<ItemStack> matchedItems = Collections.singletonList(new ItemStack(Items.IRON_INGOT, 1));
        List<FluidStack> matchedFluids = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100));
        StandardRecipeView view = new StandardRecipeView(recipe, matchedItems, matchedFluids, 1);

        assertThrows(IllegalStateException.class, view::getConsumedItems);
    }
}
