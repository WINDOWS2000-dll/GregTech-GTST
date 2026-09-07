package gregtech.api.recipes.output;

import gregtech.Bootstrap;
import gregtech.api.recipes.chance.output.ChancedOutputLogic;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.roll.RollInformation;
import gregtech.api.recipes.roll.RollInterpreter;
import gregtech.api.recipes.roll.RollableOutputList;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class StandardItemOutputTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static final RecipePropertySet NO_POWER = RecipePropertySet.empty();

    /** An interpreter that always rolls to the entry's full max yield, for deterministic tests. */
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
        RollableOutputList<ItemStack> outputs = new RollableOutputList<>(ItemStack::getCount,
                Collections.singletonList(new ItemStack(Items.APPLE, 2)), Collections.emptyList(), alwaysSucceed(),
                ChancedOutputLogic.OR);
        StandardItemOutput provider = new StandardItemOutput(outputs);

        List<ItemStack> result = provider.computeOutputs(Collections.emptyList(), Collections.emptyList(), NO_POWER,
                0, 0, 3, Integer.MAX_VALUE);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getItem(), is(Items.APPLE));
        assertThat(result.get(0).getCount(), is(6)); // 2 * parallel(3)
    }

    @Test
    void chancedOutputsAreIncludedWhenTheInterpreterSucceeds() {
        RollableOutputList<ItemStack> outputs = new RollableOutputList<>(ItemStack::getCount, Collections.emptyList(),
                Collections.singletonList(new RollInformation<>(new ItemStack(Items.GOLD_INGOT, 1), 10_000, 0)),
                alwaysSucceed(), ChancedOutputLogic.OR);
        StandardItemOutput provider = new StandardItemOutput(outputs);

        List<ItemStack> result = provider.computeOutputs(Collections.emptyList(), Collections.emptyList(), NO_POWER,
                0, 0, 1, Integer.MAX_VALUE);

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getItem(), is(Items.GOLD_INGOT));
    }

    @Test
    void multipleEntriesOfTheSameItemAreMergedRespectingMaxStackSize() {
        List<ItemStack> merged = new ObjectArrayList<>();
        StandardItemOutput.addStackToList(merged, new ItemStack(Items.APPLE, 60), 10);
        StandardItemOutput.addStackToList(merged, new ItemStack(Items.APPLE, 60), 5);

        assertThat(merged.size(), is(1));
        assertThat(merged.get(0).getCount(), is(15));
    }

    @Test
    void addStackToListSplitsAcrossMultipleStacksBeyondMaxStackSize() {
        List<ItemStack> merged = new ObjectArrayList<>();
        StandardItemOutput.addStackToList(merged, new ItemStack(Items.APPLE), 80); // apples max stack at 64

        int total = merged.stream().mapToInt(ItemStack::getCount).sum();
        assertThat(total, is(80));
        assertThat(merged.size(), is(2));
    }

    @Test
    void getCompleteOutputsIgnoresChanceAndAssumesEverySuccess() {
        RollableOutputList<ItemStack> outputs = new RollableOutputList<>(ItemStack::getCount, Collections.emptyList(),
                Collections.singletonList(new RollInformation<>(new ItemStack(Items.DIAMOND, 2), 1, 0)),
                (maxYield, rollValue, rollBoost, boostStrength, parallel) -> new long[maxYield.length], // never
                                                                                                        // succeeds
                ChancedOutputLogic.OR);
        StandardItemOutput provider = new StandardItemOutput(outputs);

        List<ItemStack> complete = provider.getCompleteOutputs(2, Integer.MAX_VALUE);

        assertThat(complete.size(), is(1));
        assertThat(complete.get(0).getCount(), is(4)); // 2 * parallel(2), regardless of the never-succeeding
                                                       // interpreter
    }

    @Test
    void getMaximumOutputsCountsDistinctEntriesTimesParallel() {
        RollableOutputList<ItemStack> outputs = new RollableOutputList<>(ItemStack::getCount,
                Arrays.asList(new ItemStack(Items.APPLE), new ItemStack(Items.GOLD_INGOT)), Collections.emptyList(),
                alwaysSucceed(), ChancedOutputLogic.OR);
        StandardItemOutput provider = new StandardItemOutput(outputs);

        assertThat(provider.getMaximumOutputs(3), is(6));
    }

    @Test
    void isValidRejectsEmptyOrAirStacks() {
        RollableOutputList<ItemStack> valid = new RollableOutputList<>(ItemStack::getCount,
                Collections.singletonList(new ItemStack(Items.APPLE)), Collections.emptyList(), alwaysSucceed(),
                ChancedOutputLogic.OR);
        assertThat(new StandardItemOutput(valid).isValid(), is(true));

        RollableOutputList<ItemStack> invalid = new RollableOutputList<>(ItemStack::getCount,
                Collections.singletonList(ItemStack.EMPTY), Collections.emptyList(), alwaysSucceed(),
                ChancedOutputLogic.OR);
        assertThat(new StandardItemOutput(invalid).isValid(), is(false));
    }
}
