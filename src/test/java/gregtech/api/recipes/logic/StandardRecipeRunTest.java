package gregtech.api.recipes.logic;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class StandardRecipeRunTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static final RecipePropertySet NO_POWER = RecipePropertySet.empty();

    private static RecipeView viewFor(long eut) {
        return viewFor(eut, false);
    }

    private static RecipeView viewFor(long eut, boolean generating) {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>(
                "standard_recipe_run_test_" + System.nanoTime(), new SimpleRecipeBuilder())
                        .itemInputs(2).itemOutputs(2).build();
        SimpleRecipeBuilder builder = map.recipeBuilder()
                .inputs(new ItemStack(Items.IRON_INGOT, 2))
                .outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(eut);
        if (generating) builder.setGenerating();
        Recipe recipe = builder.build().getResult();
        List<ItemStack> matchedItems = Arrays.asList(new ItemStack(Items.IRON_INGOT, 2));
        return new StandardRecipeView(recipe, matchedItems, Collections.emptyList(), 1);
    }

    @Test
    void exposesTheOverclockValuesItWasConstructedWith() {
        RecipeRun run = new StandardRecipeRun(viewFor(30), NO_POWER, 0, 2, 2, 25.0, 480, 1, Integer.MAX_VALUE,
                Integer.MAX_VALUE);

        assertThat(run.getOverclocks(), is(2));
        assertThat(run.getDuration(), is(25.0));
        assertThat(run.getRequiredVoltage(), is(480L));
        assertThat(run.getRequiredAmperage(), is(1L));
        assertThat(run.getRequiredEUt(), is(480L));
    }

    @Test
    void delegatesParallelToItsRecipeView() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>(
                "standard_recipe_run_test_parallel_" + System.nanoTime(), new SimpleRecipeBuilder())
                        .itemInputs(2).itemOutputs(2).build();
        Recipe recipe = map.recipeBuilder()
                .inputs(new ItemStack(Items.IRON_INGOT, 2))
                .outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30)
                .build().getResult();
        RecipeView view = new StandardRecipeView(recipe, Arrays.asList(new ItemStack(Items.IRON_INGOT, 2)),
                Collections.emptyList(), 4);

        RecipeRun run = new StandardRecipeRun(view, NO_POWER, 0, 0, 0, 100.0, 30, 1, Integer.MAX_VALUE,
                Integer.MAX_VALUE);

        assertThat(run.getParallel(), is(4));
    }

    @Test
    void copiesItemsOutConsumedAndFluidsFromTheView() {
        RecipeRun run = new StandardRecipeRun(viewFor(30), NO_POWER, 0, 0, 0, 100.0, 30, 1, Integer.MAX_VALUE,
                Integer.MAX_VALUE);

        assertThat(run.getItemsOut().size(), is(1));
        assertThat(run.getItemsOut().get(0).getItem(), is(Items.GOLD_INGOT));
        assertThat(run.getItemsConsumed().size(), is(1));
        assertThat(run.getItemsConsumed().get(0).getItem(), is(Items.IRON_INGOT));
        assertThat(run.getFluidsOut().isEmpty(), is(true));
        assertThat(run.getFluidsConsumed().isEmpty(), is(true));
    }

    @Test
    void aPlainRecipeIsNotGeneratingRegardlessOfEUtSign() {
        RecipeRun run = new StandardRecipeRun(viewFor(30), NO_POWER, 0, 0, 0, 100.0, 30, 1, Integer.MAX_VALUE,
                Integer.MAX_VALUE);
        assertThat(run.isGenerating(), is(false));

        RecipeRun negativeEUtRun = new StandardRecipeRun(viewFor(-30), NO_POWER, 0, 0, 0, 100.0, 30, 1,
                Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertThat(negativeEUtRun.isGenerating(), is(false));
    }

    @Test
    void setGeneratingRecipeIsGeneratingRegardlessOfEUtSign() {
        RecipeRun run = new StandardRecipeRun(viewFor(30, true), NO_POWER, 0, 0, 0, 100.0, 30, 1, Integer.MAX_VALUE,
                Integer.MAX_VALUE);
        assertThat(run.isGenerating(), is(true));
    }
}
