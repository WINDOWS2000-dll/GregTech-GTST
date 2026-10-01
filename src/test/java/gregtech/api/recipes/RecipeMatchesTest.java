package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.ingredients.GTRecipeItemInput;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link Recipe#matches(boolean, List, List)} at the {@code Recipe} level (StateMachine migration roadmap,
 * Ingredients/Matching engine phase 2): {@code matchesItems}/{@code matchesFluid} were rewritten to solve ingredient
 * matching as a bipartite maximum-flow problem, exercised here through real {@link GTRecipeItemInput}/
 * {@link ItemStack}s rather than {@code IngredientMatchHelperTest}'s plain {@code String}s.
 */
class RecipeMatchesTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("recipe_matches_test_" + System.nanoTime(), new SimpleRecipeBuilder())
                .itemInputs(3).itemOutputs(3).build();
    }

    @Test
    void resolvesAnAmbiguousAssignmentThatGreedyDeclarationOrderWouldGetWrong() {
        // Ingredient A accepts either an iron or a gold ingot; ingredient B needs specifically a gold ingot.
        // Available stacks, in order: [gold, iron]. A naive greedy pass processing ingredients in declaration
        // order (A first) would let A claim the first stack it's offered that satisfies it - the gold ingot -
        // leaving only iron for B, which B can't accept. The correct assignment (A <- iron, B <- gold) does exist;
        // only a true bipartite match (not declaration-order-dependent greedy consumption) finds it.
        GTRecipeItemInput acceptsEitherIngot = new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.GOLD_INGOT));
        GTRecipeItemInput needsGoldIngot = new GTRecipeItemInput(new ItemStack(Items.GOLD_INGOT));

        Recipe recipe = newMap().recipeBuilder()
                .inputs(acceptsEitherIngot, needsGoldIngot)
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> availableInOrder = new ObjectArrayList<>();
        availableInOrder.add(new ItemStack(Items.GOLD_INGOT));
        availableInOrder.add(new ItemStack(Items.IRON_INGOT));

        assertTrue(recipe.matches(false, availableInOrder, Collections.emptyList()));
    }

    @Test
    void consumingAMatchActuallyReducesTheInputList() {
        Recipe recipe = newMap().recipeBuilder()
                .inputs(new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 2)))
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> inputs = new ObjectArrayList<>();
        inputs.add(new ItemStack(Items.IRON_INGOT, 5));

        assertTrue(recipe.matches(true, inputs, Collections.emptyList()));
        assertThat(inputs.get(0).getCount(), is(3)); // 5 - 2
    }

    @Test
    void nonConsumableIngredientIsNotActuallyDrawnDown() {
        Recipe recipe = newMap().recipeBuilder()
                .notConsumable(new ItemStack(Items.IRON_INGOT))
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> inputs = new ObjectArrayList<>();
        inputs.add(new ItemStack(Items.IRON_INGOT, 1));

        assertTrue(recipe.matches(true, inputs, Collections.emptyList()));
        assertThat(inputs.get(0).getCount(), is(1)); // untouched
    }

    @Test
    void nonConsumableIngredientFailsWhenNothingRemainsAfterConsumableDraws() {
        // The recipe needs 4 iron ingots consumed AND 1 iron ingot present-but-not-consumed, but only 4 total
        // exist: after the consumable draw takes all 4, nothing is left for the non-consumable check to find.
        Recipe recipe = newMap().recipeBuilder()
                .inputs(new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 4)))
                .notConsumable(new ItemStack(Items.IRON_INGOT))
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> inputs = new ObjectArrayList<>();
        inputs.add(new ItemStack(Items.IRON_INGOT, 4));

        assertFalse(recipe.matches(false, inputs, Collections.emptyList()));
    }

    @Test
    void nonConsumableIngredientSucceedsWhenEnoughRemainsAfterConsumableDraws() {
        Recipe recipe = newMap().recipeBuilder()
                .inputs(new GTRecipeItemInput(new ItemStack(Items.IRON_INGOT, 4)))
                .notConsumable(new ItemStack(Items.IRON_INGOT))
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> inputs = new ObjectArrayList<>();
        inputs.add(new ItemStack(Items.IRON_INGOT, 5)); // one extra beyond what's consumed

        assertTrue(recipe.matches(true, inputs, Collections.emptyList()));
        assertThat(inputs.get(0).getCount(), is(1)); // 5 - 4 consumed, non-consumable check didn't draw further
    }

    @Test
    void failsWhenNoAssignmentSatisfiesEveryIngredient() {
        Recipe recipe = newMap().recipeBuilder()
                .inputs(new GTRecipeItemInput(new ItemStack(Items.GOLD_INGOT, 1)))
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> inputs = new ObjectArrayList<>();
        inputs.add(new ItemStack(Items.IRON_INGOT, 5));

        assertFalse(recipe.matches(false, inputs, Collections.emptyList()));
    }
}
