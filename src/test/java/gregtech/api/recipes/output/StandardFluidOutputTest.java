package gregtech.api.recipes.output;

import gregtech.Bootstrap;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.roll.RollInterpreter;
import gregtech.api.recipes.roll.RollableOutputList;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class StandardFluidOutputTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static final RecipePropertySet NO_POWER = RecipePropertySet.empty();

    private static RollInterpreter alwaysSucceed() {
        return (maxYield, rollValue, rollBoost, boostStrength, parallel) -> {
            long[] roll = new long[maxYield.length];
            for (int i = 0; i < maxYield.length; i++) {
                roll[i] = rollValue[i] == Long.MIN_VALUE ? 0 : maxYield[i] * parallel;
            }
            return roll;
        };
    }

    @Test
    void guaranteedOutputsAlwaysAppearScaledByParallel() {
        RollableOutputList<FluidStack> outputs = new RollableOutputList<>(stack -> stack.amount,
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100)), Collections.emptyList(),
                alwaysSucceed(), ChancedOutputLogic.OR);
        StandardFluidOutput provider = new StandardFluidOutput(outputs);

        List<FluidStack> result = provider.computeOutputs(Collections.emptyList(), Collections.emptyList(), NO_POWER,
                0, 0, 3, Integer.MAX_VALUE);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).amount, is(300)); // 100 * parallel(3)
    }

    @Test
    void mergesSameFluidEntriesIntoOneStack() {
        List<FluidStack> merged = new ObjectArrayList<>();
        StandardFluidOutput.addStackToList(merged, new FluidStack(FluidRegistry.WATER, 100), 50);
        StandardFluidOutput.addStackToList(merged, new FluidStack(FluidRegistry.WATER, 100), 25);

        assertThat(merged.size(), is(1));
        assertThat(merged.get(0).amount, is(75));
    }

    @Test
    void distinctFluidsAreNotMerged() {
        List<FluidStack> merged = new ObjectArrayList<>();
        StandardFluidOutput.addStackToList(merged, new FluidStack(FluidRegistry.WATER, 100), 50);
        StandardFluidOutput.addStackToList(merged, new FluidStack(FluidRegistry.LAVA, 100), 25);

        assertThat(merged.size(), is(2));
    }

    @Test
    void getMaximumOutputsCountsDistinctEntriesTimesParallel() {
        RollableOutputList<FluidStack> outputs = new RollableOutputList<>(stack -> stack.amount,
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100)), Collections.emptyList(),
                alwaysSucceed(), ChancedOutputLogic.OR);
        StandardFluidOutput provider = new StandardFluidOutput(outputs);

        assertThat(provider.getMaximumOutputs(4), is(4));
    }

    @Test
    void isValidRejectsZeroAmountStacks() {
        RollableOutputList<FluidStack> valid = new RollableOutputList<>(stack -> stack.amount,
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100)), Collections.emptyList(),
                alwaysSucceed(), ChancedOutputLogic.OR);
        assertThat(new StandardFluidOutput(valid).isValid(), is(true));

        RollableOutputList<FluidStack> invalid = new RollableOutputList<>(stack -> stack.amount,
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 0)), Collections.emptyList(),
                alwaysSucceed(), ChancedOutputLogic.OR);
        assertThat(new StandardFluidOutput(invalid).isValid(), is(false));
    }
}
