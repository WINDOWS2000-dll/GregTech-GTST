package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.RecipeRun;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.PreparedRecipeQueue;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeQueueAdmissionOperatorTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeLogicConfig newConfig(ItemStackHandler input) {
        RecipeLogicConfig config = new RecipeLogicConfig(
                () -> (maxVoltage, items, fluids) -> Collections.<Recipe>emptyList().iterator());
        config.io.itemInput = () -> input;
        return config;
    }

    private static RecipeRun fakeRun(ItemStack consumed, ItemStack produced) {
        return new FakeRecipeRun(100.0, Collections.singletonList(produced), Collections.emptyList(),
                Collections.singletonList(consumed), Collections.emptyList());
    }

    @Test
    void admitsAnEntryWhoseOutputFitsAndConsumesTheRealItems() {
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        RecipeLogicConfig config = newConfig(input);

        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT))));

        new RecipeQueueAdmissionOperator(config).operate(data);

        assertThat(PreparedRecipeQueue.isEmpty(data), is(true));
        assertThat(ActiveRecipeList.count(data), is(1));
        assertThat(input.getStackInSlot(0).getCount(), is(6)); // 10 - 4
    }

    /**
     * Regression test (Stage A-10): {@code config.callbacks.onRecipeStarted} was declared but never actually
     * invoked anywhere -- found while diagnosing a real-machine bug (Research Station locking its Object Holder
     * too early, from {@code config.hooks.finalCheck}, before this operator's own real consumption could ever
     * succeed). Must fire exactly once per newly-admitted entry, with that same active entry's data.
     */
    @Test
    void onRecipeStartedFiresOnceWithTheNewlyActiveEntryWhenAnEntryIsAdmitted() {
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        RecipeLogicConfig config = newConfig(input);
        List<NBTTagCompound> started = new java.util.ArrayList<>();
        config.callbacks.onRecipeStarted = started::add;

        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT))));

        new RecipeQueueAdmissionOperator(config).operate(data);

        assertThat(started.size(), is(1));
        assertThat(ActiveRecipeList.itemsOut(started.get(0)).get(0).getItem(), is(Items.GOLD_INGOT));
    }

    @Test
    void onRecipeStartedIsNotFiredWhenNothingIsActuallyAdmitted() {
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        RecipeLogicConfig config = newConfig(input);
        config.io.itemOutputSpace = items -> false; // always blocked
        List<NBTTagCompound> started = new java.util.ArrayList<>();
        config.callbacks.onRecipeStarted = started::add;

        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT))));

        new RecipeQueueAdmissionOperator(config).operate(data);

        assertThat(started.isEmpty(), is(true));
    }

    @Test
    void leavesTheEntryQueuedWhenOutputSpaceIsUnavailable() {
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        RecipeLogicConfig config = newConfig(input);
        config.io.itemOutputSpace = items -> false; // always blocked

        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT))));

        new RecipeQueueAdmissionOperator(config).operate(data);

        assertThat(PreparedRecipeQueue.count(data), is(1));
        assertThat(ActiveRecipeList.count(data), is(0));
        assertThat(input.getStackInSlot(0).getCount(), is(10)); // untouched
    }

    @Test
    void aBlockedHeadEntryLeavesLaterEntriesQueuedTooToPreserveOrder() {
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        RecipeLogicConfig config = newConfig(input);
        config.io.itemOutputSpace = items -> false;

        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT))));
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.DIAMOND))));

        new RecipeQueueAdmissionOperator(config).operate(data);

        assertThat(PreparedRecipeQueue.count(data), is(2));
    }

    @Test
    void aStaleEntryIsDiscardedAndDrainingContinuesToTheNextOne() {
        // the reserved item is no longer actually present in the real inventory (external interference)
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1)); // only 1, but the entry claims 4
        RecipeLogicConfig config = newConfig(input);

        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT))));
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 1), new ItemStack(Items.DIAMOND))));

        new RecipeQueueAdmissionOperator(config).operate(data);

        assertThat("the stale first entry should be discarded, not left queued forever",
                PreparedRecipeQueue.isEmpty(data), is(true));
        assertThat("the second entry should still be admitted despite the first being corrupted",
                ActiveRecipeList.count(data), is(1));
        assertThat(ActiveRecipeList.itemsOut(ActiveRecipeList.entryAt(0, data)).get(0).getItem(), is(Items.DIAMOND));
    }

    @Test
    void drainsMultipleAdmittableEntriesInOneCall() {
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        RecipeLogicConfig config = newConfig(input);

        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.GOLD_INGOT))));
        PreparedRecipeQueue.append(data,
                PreparedRecipeQueue.newEntry(fakeRun(new ItemStack(Items.IRON_INGOT, 2), new ItemStack(Items.DIAMOND))));

        new RecipeQueueAdmissionOperator(config).operate(data);

        assertThat(PreparedRecipeQueue.isEmpty(data), is(true));
        assertThat(ActiveRecipeList.count(data), is(2));
        assertThat(input.getStackInSlot(0).getCount(), is(4)); // 10 - 4 - 2
    }

    private static final class FakeRecipeRun implements RecipeRun {

        private final double duration;
        private final List<ItemStack> itemsOut;
        private final List<FluidStack> fluidsOut;
        private final List<ItemStack> itemsConsumed;
        private final List<FluidStack> fluidsConsumed;

        FakeRecipeRun(double duration, List<ItemStack> itemsOut, List<FluidStack> fluidsOut,
                     List<ItemStack> itemsConsumed, List<FluidStack> fluidsConsumed) {
            this.duration = duration;
            this.itemsOut = itemsOut;
            this.fluidsOut = fluidsOut;
            this.itemsConsumed = itemsConsumed;
            this.fluidsConsumed = fluidsConsumed;
        }

        @Override
        public gregtech.api.recipes.logic.RecipeView getRecipeView() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ItemStack> getItemsOut() {
            return itemsOut;
        }

        @Override
        public List<FluidStack> getFluidsOut() {
            return fluidsOut;
        }

        @Override
        public List<ItemStack> getItemsConsumed() {
            return itemsConsumed;
        }

        @Override
        public List<FluidStack> getFluidsConsumed() {
            return fluidsConsumed;
        }

        @Override
        public int getParallel() {
            return 1;
        }

        @Override
        public int getOverclocks() {
            return 0;
        }

        @Override
        public double getDuration() {
            return duration;
        }

        @Override
        public long getRequiredVoltage() {
            return 30;
        }

        @Override
        public long getRequiredAmperage() {
            return 1;
        }

        @Override
        public boolean isGenerating() {
            return false;
        }
    }
}
