package gregtech.api.recipes.logic.statemachine;

import gregtech.Bootstrap;
import gregtech.api.recipes.logic.RecipeRun;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class PreparedRecipeQueueTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static NBTTagCompound entryConsuming(ItemStack... items) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setDouble(PreparedRecipeQueue.ENTRY_DURATION_KEY, 100.0);
        PreparedRecipeQueue.setItemsConsumed(entry, Arrays.asList(items));
        PreparedRecipeQueue.setFluidsConsumed(entry, Collections.emptyList());
        return entry;
    }

    @Test
    void appendedEntriesAreReturnedInFifoOrder() {
        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data, entryConsuming(new ItemStack(Items.IRON_INGOT)));
        PreparedRecipeQueue.append(data, entryConsuming(new ItemStack(Items.GOLD_INGOT)));

        assertThat(PreparedRecipeQueue.count(data), is(2));
        assertThat(PreparedRecipeQueue.itemsConsumed(PreparedRecipeQueue.peekFirst(data)).get(0).getItem(),
                is(Items.IRON_INGOT));

        PreparedRecipeQueue.removeFirst(data);
        assertThat(PreparedRecipeQueue.itemsConsumed(PreparedRecipeQueue.peekFirst(data)).get(0).getItem(),
                is(Items.GOLD_INGOT));
    }

    @Test
    void subtractReservedItemsRemovesExactlyWhatIsQueued() {
        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data, entryConsuming(new ItemStack(Items.IRON_INGOT, 4)));

        List<ItemStack> real = Collections.singletonList(new ItemStack(Items.IRON_INGOT, 10));
        List<ItemStack> available = PreparedRecipeQueue.subtractReservedItems(real, data);

        assertThat(available.get(0).getCount(), is(6));
        // the real snapshot passed in must not be mutated
        assertThat(real.get(0).getCount(), is(10));
    }

    @Test
    void subtractReservedItemsAccumulatesAcrossMultipleQueuedEntries() {
        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data, entryConsuming(new ItemStack(Items.IRON_INGOT, 4)));
        PreparedRecipeQueue.append(data, entryConsuming(new ItemStack(Items.IRON_INGOT, 3)));

        List<ItemStack> real = Collections.singletonList(new ItemStack(Items.IRON_INGOT, 10));
        List<ItemStack> available = PreparedRecipeQueue.subtractReservedItems(real, data);

        assertThat(available.get(0).getCount(), is(3)); // 10 - 4 - 3
    }

    @Test
    void subtractReservedItemsNeverGoesNegativeAndIgnoresUnrelatedItems() {
        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data, entryConsuming(new ItemStack(Items.IRON_INGOT, 4)));

        List<ItemStack> real = Collections.singletonList(new ItemStack(Items.GOLD_INGOT, 10));
        List<ItemStack> available = PreparedRecipeQueue.subtractReservedItems(real, data);

        assertThat(available.get(0).getCount(), is(10));
    }

    @Test
    void subtractReservedFluidsWorksLikeItems() {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setDouble(PreparedRecipeQueue.ENTRY_DURATION_KEY, 1);
        PreparedRecipeQueue.setItemsConsumed(entry, Collections.emptyList());
        PreparedRecipeQueue.setFluidsConsumed(entry, Collections.singletonList(new FluidStack(FluidRegistry.WATER, 300)));
        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data, entry);

        List<FluidStack> real = Collections.singletonList(new FluidStack(FluidRegistry.WATER, 1000));
        List<FluidStack> available = PreparedRecipeQueue.subtractReservedFluids(real, data);

        assertThat(available.get(0).amount, is(700));
    }

    @Test
    void subtractReservedItemsIsScopedToTheGivenDistinctGroup() {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound group0Entry = entryConsuming(new ItemStack(Items.IRON_INGOT, 4));
        group0Entry.setInteger(PreparedRecipeQueue.ENTRY_DISTINCT_GROUP_KEY, 0);
        NBTTagCompound group1Entry = entryConsuming(new ItemStack(Items.IRON_INGOT, 3));
        group1Entry.setInteger(PreparedRecipeQueue.ENTRY_DISTINCT_GROUP_KEY, 1);
        PreparedRecipeQueue.append(data, group0Entry);
        PreparedRecipeQueue.append(data, group1Entry);

        List<ItemStack> realForGroup1 = Collections.singletonList(new ItemStack(Items.IRON_INGOT, 10));
        List<ItemStack> availableForGroup1 = PreparedRecipeQueue.subtractReservedItems(realForGroup1, data, 1);

        // only group 1's own 3-iron reservation is subtracted, not group 0's 4-iron one -- the two groups are
        // independent pools even though they happen to hold the same item type.
        assertThat(availableForGroup1.get(0).getCount(), is(7));
    }

    @Test
    void newEntryStoresTheGivenDistinctGroupIndex() {
        RecipeRun run = new FakeRecipeRun(50.0, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        NBTTagCompound entry = PreparedRecipeQueue.newEntry(run, 2);

        assertThat(entry.getInteger(PreparedRecipeQueue.ENTRY_DISTINCT_GROUP_KEY), is(2));
    }

    @Test
    void newEntryWithoutAGroupDefaultsToGroupZero() {
        RecipeRun run = new FakeRecipeRun(50.0, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        NBTTagCompound entry = PreparedRecipeQueue.newEntry(run);

        assertThat(entry.getInteger(PreparedRecipeQueue.ENTRY_DISTINCT_GROUP_KEY), is(0));
    }

    @Test
    void newEntryAndToActiveEntryRoundTripTheEssentialFields() {
        RecipeRun run = new FakeRecipeRun(50.0, Collections.singletonList(new ItemStack(Items.APPLE)),
                Collections.emptyList(), Collections.singletonList(new ItemStack(Items.IRON_INGOT, 2)),
                Collections.emptyList());

        NBTTagCompound entry = PreparedRecipeQueue.newEntry(run);
        NBTTagCompound active = PreparedRecipeQueue.toActiveEntry(entry);

        assertThat(active.getDouble(ActiveRecipeList.ENTRY_DURATION_KEY), is(50.0));
        assertThat(active.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(0));
        assertThat(ActiveRecipeList.itemsOut(active).get(0).getItem(), is(Items.APPLE));
        assertThat(PreparedRecipeQueue.itemsConsumed(entry).get(0).getItem(), is(Items.IRON_INGOT));
    }

    @Test
    void toActiveEntryStripsQueueOnlyBookkeepingKeys() {
        RecipeRun run = new FakeRecipeRun(50.0, Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(new ItemStack(Items.IRON_INGOT)), Collections.emptyList());

        NBTTagCompound entry = PreparedRecipeQueue.newEntry(run, 3);
        NBTTagCompound active = PreparedRecipeQueue.toActiveEntry(entry);

        assertThat("ItemsConsumed is queue-only bookkeeping, not part of the active format",
                active.hasKey(PreparedRecipeQueue.ENTRY_ITEMS_CONSUMED_KEY), is(false));
        assertThat("FluidsConsumed is queue-only bookkeeping, not part of the active format",
                active.hasKey(PreparedRecipeQueue.ENTRY_FLUIDS_CONSUMED_KEY), is(false));
        assertThat("RequiredEUt is queue-only bookkeeping, not part of the active format",
                active.hasKey(PreparedRecipeQueue.ENTRY_REQUIRED_EUT_KEY), is(false));
        assertThat("DistinctGroupIndex is queue-only bookkeeping, not part of the active format",
                active.hasKey(PreparedRecipeQueue.ENTRY_DISTINCT_GROUP_KEY), is(false));
    }

    @Test
    void toActiveEntryCarriesForwardCustomKeysAnEnricherWrote() {
        RecipeRun run = new FakeRecipeRun(50.0, Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        NBTTagCompound entry = PreparedRecipeQueue.newEntry(run);
        entry.setInteger("SomeMachineSpecificCustomKey", 42);
        NBTTagCompound active = PreparedRecipeQueue.toActiveEntry(entry);

        assertThat(active.getInteger("SomeMachineSpecificCustomKey"), is(42));
    }

    /** A minimal {@link RecipeRun} stand-in, avoiding the need to construct a real matched recipe for this test. */
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
