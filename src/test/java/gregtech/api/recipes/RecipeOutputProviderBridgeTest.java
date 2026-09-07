package gregtech.api.recipes;

import gregtech.Bootstrap;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.roll.OverrideRollInterpreter;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeOutputProviderBridgeTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static final RecipePropertySet NO_POWER = RecipePropertySet.empty();

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("output_provider_bridge_test_" + System.nanoTime(), new SimpleRecipeBuilder())
                .itemOutputs(3).fluidOutputs(3).build();
    }

    @Test
    void guaranteedAndAlwaysSucceedingChancedOutputsBothAppear() {
        Recipe recipe = newMap().recipeBuilder()
                .outputs(new ItemStack(Blocks.STONE))
                .chancedOutput(new ItemStack(Items.GOLD_INGOT), 10_000, 0)
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> result = recipe.getItemOutputProvider().computeOutputs(Collections.emptyList(),
                Collections.emptyList(), NO_POWER, 0, 0, 1, Integer.MAX_VALUE);

        assertThat(result.size(), is(2));
    }

    @Test
    void alwaysFailingChancedOutputDoesNotAppear() {
        Recipe recipe = newMap().recipeBuilder()
                .outputs(new ItemStack(Blocks.STONE))
                .chancedOutput(new ItemStack(Items.GOLD_INGOT), 0, 0)
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> result = recipe.getItemOutputProvider().computeOutputs(Collections.emptyList(),
                Collections.emptyList(), NO_POWER, 0, 0, 1, Integer.MAX_VALUE);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getItem(), is(Item.getItemFromBlock(Blocks.STONE)));
    }

    @Test
    void reproducesGetResultItemOutputsAtParallelOne() {
        Recipe recipe = newMap().recipeBuilder()
                .outputs(new ItemStack(Blocks.STONE))
                .chancedOutput(new ItemStack(Items.GOLD_INGOT), 10_000, 0)
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> bridged = recipe.getItemOutputProvider().computeOutputs(Collections.emptyList(),
                Collections.emptyList(), NO_POWER, 0, 0, 1, Integer.MAX_VALUE);

        assertThat(bridged.size(), is(2));
    }

    @Test
    void supportsParallelDirectlyUnlikeGetResultItemOutputs() {
        Recipe recipe = newMap().recipeBuilder()
                .outputs(new ItemStack(Blocks.STONE, 2))
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> result = recipe.getItemOutputProvider().computeOutputs(Collections.emptyList(),
                Collections.emptyList(), NO_POWER, 0, 0, 5, Integer.MAX_VALUE);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getCount(), is(10)); // 2 * parallel(5)
    }

    @Test
    void respectsTheRecipesConfiguredChancedOutputLogic() {
        Recipe recipe = newMap().recipeBuilder()
                .chancedOutput(new ItemStack(Items.GOLD_INGOT), 10_000, 0)
                .chancedOutput(new ItemStack(Items.DIAMOND), 10_000, 0)
                .chancedOutputLogic(ChancedOutputLogic.XOR)
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> result = recipe.getItemOutputProvider().computeOutputs(Collections.emptyList(),
                Collections.emptyList(), NO_POWER, 0, 0, 1, Integer.MAX_VALUE);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getItem(), is(Items.GOLD_INGOT));
    }

    @Test
    void isCachedAcrossCalls() {
        Recipe recipe = newMap().recipeBuilder().outputs(new ItemStack(Blocks.STONE)).duration(1).EUt(1)
                .build().getResult();

        assertThat(recipe.getItemOutputProvider(), sameInstance(recipe.getItemOutputProvider()));
    }

    @Test
    void chancedOutputRollInterpreterIsActuallyUsedInsteadOfAlwaysDefaultingToIndependent() {
        Recipe recipe = newMap().recipeBuilder()
                .outputs(new ItemStack(Blocks.STONE))
                .chancedOutput(new ItemStack(Items.GOLD_INGOT), 10_000, 0)
                .chancedOutput(new ItemStack(Items.DIAMOND), 10_000, 0)
                .chancedOutputRollInterpreter(new OverrideRollInterpreter(0, 0))
                .duration(1).EUt(1)
                .build().getResult();

        List<ItemStack> result = recipe.getItemOutputProvider().computeOutputs(Collections.emptyList(),
                Collections.emptyList(), NO_POWER, 0, 0, 1, Integer.MAX_VALUE);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getItem(), is(Item.getItemFromBlock(Blocks.STONE)));
        assertThat(recipe.getItemOutputRollInterpreter(), is(instanceOf(OverrideRollInterpreter.class)));
    }

    @Test
    void chancedFluidOutputRollInterpreterIsActuallyUsed() {
        Recipe recipe = newMap().recipeBuilder()
                .fluidOutputs(new FluidStack(FluidRegistry.WATER, 100))
                .chancedFluidOutput(new FluidStack(FluidRegistry.LAVA, 50), 10_000, 0)
                .chancedFluidOutputRollInterpreter(new OverrideRollInterpreter(0, 0))
                .duration(1).EUt(1)
                .build().getResult();

        List<FluidStack> result = recipe.getFluidOutputProvider().computeOutputs(Collections.emptyList(),
                Collections.emptyList(), NO_POWER, 0, 0, 1, Integer.MAX_VALUE);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getFluid(), is(FluidRegistry.WATER));
        assertThat(recipe.getFluidOutputRollInterpreter(), is(instanceOf(OverrideRollInterpreter.class)));
    }

    @Test
    void chancedOutputRollInterpreterSurvivesTheRecipeCopyConstructor() {
        // RecipeBuilder(Recipe, RecipeMap) is used by e.g. Recipe#trimRecipeOutputs and CraftTweaker's recipe
        // copy/modify flows; the interpreter choice must round-trip through it like chancedOutputLogic already does.
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        Recipe original = map.recipeBuilder()
                .outputs(new ItemStack(Blocks.STONE))
                .chancedOutputRollInterpreter(new OverrideRollInterpreter(10_000, 0))
                .duration(1).EUt(1)
                .build().getResult();

        Recipe copy = new SimpleRecipeBuilder(original, map).build().getResult();

        assertThat(copy.getItemOutputRollInterpreter(), is(instanceOf(OverrideRollInterpreter.class)));
    }

    @Test
    void fluidBridgeWorksSymmetrically() {
        Recipe recipe = newMap().recipeBuilder()
                .fluidOutputs(new FluidStack(FluidRegistry.WATER, 100))
                .chancedFluidOutput(new FluidStack(FluidRegistry.LAVA, 50), 10_000, 0)
                .duration(1).EUt(1)
                .build().getResult();

        List<FluidStack> result = recipe.getFluidOutputProvider().computeOutputs(Collections.emptyList(),
                Collections.emptyList(), NO_POWER, 0, 0, 1, Integer.MAX_VALUE);

        assertThat(result.size(), is(2));
    }
}
