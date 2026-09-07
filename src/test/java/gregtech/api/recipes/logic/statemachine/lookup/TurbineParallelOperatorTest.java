package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.FuelRecipeBuilder;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TurbineParallelOperatorTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    /** A fuel recipe requiring 100mB of a fresh, uniquely-named fluid per copy, at the given EU/t. */
    private static Recipe fuelRecipe(long euT) {
        RecipeMap<FuelRecipeBuilder> map = new RecipeMapBuilder<>("turbine_parallel_test_" + System.nanoTime(),
                new FuelRecipeBuilder()).fluidInputs(1).build();
        Fluid fluid = new Fluid("turbine_parallel_test_fuel_" + System.nanoTime(), null, null);
        FluidRegistry.registerFluid(fluid);
        return map.recipeBuilder().fluidInputs(new FluidStack(fluid, 100)).duration(10).EUt(euT).build().getResult();
    }

    /**
     * As {@link #fuelRecipe(long)}'s transientData, but with an effectively unlimited fluid supply available, so
     * ingredient availability never binds -- for tests focused purely on the ceiling/banking math.
     */
    private static Map<String, Object> transientDataWithAmpleFuel(Recipe candidate) {
        Map<String, Object> transientData = new HashMap<>();
        transientData.put(RecipeSelectionOperator.SELECTED_RECIPE_KEY, candidate);
        transientData.put(RecipeSearchOperator.ITEMS_SNAPSHOT_KEY, Collections.<ItemStack>emptyList());
        FluidStack ample = candidate.getFluidInputs().get(0).getInputFluidStack().copy();
        ample.amount = Integer.MAX_VALUE;
        transientData.put(RecipeSearchOperator.FLUIDS_SNAPSHOT_KEY, Collections.singletonList(ample));
        return transientData;
    }

    @Test
    void ceilsTheParallelCountToAtLeastCoverTheDeficit() {
        Recipe candidate = fuelRecipe(30);
        TurbineParallelOperator op = new TurbineParallelOperator(() -> 100L, () -> 1.0, () -> 0L);
        Map<String, Object> transientData = transientDataWithAmpleFuel(candidate);
        NBTTagCompound data = new NBTTagCompound();

        op.operate(data, transientData);

        // deficit = 100 - 0 = 100; ceil(100 / 30) = 4
        assertThat(data.getBoolean(RecipeParallelOperator.SUCCESS_KEY), is(true));
        assertThat(transientData.get(RecipeParallelOperator.ACHIEVED_PARALLEL_KEY), is(4));
    }

    @Test
    void reducesTheNeededParallelByWhateverIsAlreadyBanked() {
        Recipe candidate = fuelRecipe(30);
        TurbineParallelOperator op = new TurbineParallelOperator(() -> 100L, () -> 1.0, () -> 90L);
        Map<String, Object> transientData = transientDataWithAmpleFuel(candidate);
        NBTTagCompound data = new NBTTagCompound();

        op.operate(data, transientData);

        // deficit = 100 - 90 = 10; ceil(10 / 30) = 1
        assertThat(data.getBoolean(RecipeParallelOperator.SUCCESS_KEY), is(true));
        assertThat(transientData.get(RecipeParallelOperator.ACHIEVED_PARALLEL_KEY), is(1));
    }

    @Test
    void appliesEfficiencyWhenComputingHowMuchParallelIsNeeded() {
        Recipe candidate = fuelRecipe(30);
        // 50% efficiency: each unit of parallel is only worth 15 EU toward the target
        TurbineParallelOperator op = new TurbineParallelOperator(() -> 90L, () -> 0.5, () -> 0L);
        Map<String, Object> transientData = transientDataWithAmpleFuel(candidate);
        NBTTagCompound data = new NBTTagCompound();

        op.operate(data, transientData);

        // deficit = 90; ceil(90 / (30*0.5)) = ceil(90/15) = 6
        assertThat(data.getBoolean(RecipeParallelOperator.SUCCESS_KEY), is(true));
        assertThat(transientData.get(RecipeParallelOperator.ACHIEVED_PARALLEL_KEY), is(6));
    }

    @Test
    void failsWhenTheBankAlreadyCoversTheTarget() {
        Recipe candidate = fuelRecipe(30);
        TurbineParallelOperator op = new TurbineParallelOperator(() -> 100L, () -> 1.0, () -> 150L);
        Map<String, Object> transientData = transientDataWithAmpleFuel(candidate);
        NBTTagCompound data = new NBTTagCompound();

        op.operate(data, transientData);

        assertFalse(data.getBoolean(RecipeParallelOperator.SUCCESS_KEY));
    }

    @Test
    void failsWhenTheTargetVoltageIsZero() {
        Recipe candidate = fuelRecipe(30);
        TurbineParallelOperator op = new TurbineParallelOperator(() -> 0L, () -> 1.0, () -> 0L);
        Map<String, Object> transientData = transientDataWithAmpleFuel(candidate);
        NBTTagCompound data = new NBTTagCompound();

        op.operate(data, transientData);

        assertFalse(data.getBoolean(RecipeParallelOperator.SUCCESS_KEY));
    }

    @Test
    void failsWhenSomethingWasAlreadyClaimedEarlierInTheSameSearchPass() {
        Recipe candidate = fuelRecipe(30);
        TurbineParallelOperator op = new TurbineParallelOperator(() -> 100L, () -> 1.0, () -> 0L);
        Map<String, Object> transientData = transientDataWithAmpleFuel(candidate);
        // a prior candidate in this same pass already committed 90 raw EU worth -- even though that leaves a
        // remaining deficit of 10 (which alone would want ceil(10/30) = 1 more copy), this pass has already
        // admitted one entry and must not admit a second.
        transientData.put(RecipeSearchOperator.EUT_CONSUMED_KEY, 90L);
        NBTTagCompound data = new NBTTagCompound();

        op.operate(data, transientData);

        assertFalse(data.getBoolean(RecipeParallelOperator.SUCCESS_KEY));
    }

    @Test
    void failsOutrightWhenAvailableFuelIngredientsCantCoverTheFullComputedParallel() {
        Recipe candidate = fuelRecipe(30); // needs 100mB fuel per copy
        TurbineParallelOperator op = new TurbineParallelOperator(() -> 1000L, () -> 1.0, () -> 0L);
        Map<String, Object> transientData = new HashMap<>();
        transientData.put(RecipeSelectionOperator.SELECTED_RECIPE_KEY, candidate);
        transientData.put(RecipeSearchOperator.ITEMS_SNAPSHOT_KEY, Collections.<ItemStack>emptyList());
        // only enough for 2 copies (200mB), even though the deficit alone would want ceil(1000/30) = 34
        FluidStack limited = new FluidStack(candidate.getFluidInputs().get(0).getInputFluidStack().getFluid(), 200);
        transientData.put(RecipeSearchOperator.FLUIDS_SNAPSHOT_KEY, Collections.singletonList(limited));
        NBTTagCompound data = new NBTTagCompound();

        op.operate(data, transientData);

        assertFalse(data.getBoolean(RecipeParallelOperator.SUCCESS_KEY));
    }

    /** As the previous test, but with just enough fuel to exactly cover the computed parallel -- must still succeed. */
    @Test
    void succeedsWhenAvailableFuelIngredientsExactlyCoverTheFullComputedParallel() {
        Recipe candidate = fuelRecipe(30); // needs 100mB fuel per copy
        TurbineParallelOperator op = new TurbineParallelOperator(() -> 100L, () -> 1.0, () -> 0L);
        Map<String, Object> transientData = new HashMap<>();
        transientData.put(RecipeSelectionOperator.SELECTED_RECIPE_KEY, candidate);
        transientData.put(RecipeSearchOperator.ITEMS_SNAPSHOT_KEY, Collections.<ItemStack>emptyList());
        // deficit = 100; ceil(100/30) = 4 copies needed; exactly 400mB available
        FluidStack exact = new FluidStack(candidate.getFluidInputs().get(0).getInputFluidStack().getFluid(), 400);
        transientData.put(RecipeSearchOperator.FLUIDS_SNAPSHOT_KEY, Collections.singletonList(exact));
        NBTTagCompound data = new NBTTagCompound();

        op.operate(data, transientData);

        assertThat(data.getBoolean(RecipeParallelOperator.SUCCESS_KEY), is(true));
        assertThat(transientData.get(RecipeParallelOperator.ACHIEVED_PARALLEL_KEY), is(4));
    }
}
