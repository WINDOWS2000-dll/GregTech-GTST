package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.logic.RecipeRun;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The NBT format {@code RecipeLookupTrackBuilder} uses to hold fully-resolved, but not-yet-{@link ActiveRecipeList
 * admitted}, recipe runs: entries here have already had their outputs rolled, but real inventory consumption is
 * <i>deferred</i> until admission actually succeeds (see {@code RecipeLookupTrackBuilder}'s JavaDoc for why, and
 * how staleness is avoided in the meantime).
 * <p>
 * Because consumption is deferred, every search attempt (including ones on later ticks, while earlier entries are
 * still waiting here for output space) must treat this queue's entries' {@link #itemsConsumed}/
 * {@link #fluidsConsumed} as already spoken for, via {@link #subtractReservedItems}/{@link #subtractReservedFluids}
 * &mdash; otherwise the same physical items could be matched into two different queued entries at once. This is
 * the queue's whole reason for existing as persisted NBT (rather than transient, in-memory-only state): the
 * reservation has to survive exactly as long as the entry it protects does, which may span many ticks while
 * output space is unavailable.
 */
public final class PreparedRecipeQueue {

    public static final String LIST_KEY = "PreparedRecipes";

    public static final String ENTRY_DURATION_KEY = ActiveRecipeList.ENTRY_DURATION_KEY;
    public static final String ENTRY_ITEMS_OUT_KEY = ActiveRecipeList.ENTRY_ITEMS_OUT_KEY;
    public static final String ENTRY_FLUIDS_OUT_KEY = ActiveRecipeList.ENTRY_FLUIDS_OUT_KEY;
    public static final String ENTRY_VOLTAGE_KEY = ActiveRecipeList.ENTRY_VOLTAGE_KEY;
    public static final String ENTRY_AMPERAGE_KEY = ActiveRecipeList.ENTRY_AMPERAGE_KEY;
    public static final String ENTRY_GENERATING_KEY = ActiveRecipeList.ENTRY_GENERATING_KEY;
    public static final String ENTRY_ITEMS_CONSUMED_KEY = "ItemsConsumed";
    public static final String ENTRY_FLUIDS_CONSUMED_KEY = "FluidsConsumed";

    /**
     * The recipe run's post-overclock required EU/t, re-checked (not just trusted) at admission time. Kept as its
     * own combined field (distinct from {@link #ENTRY_VOLTAGE_KEY}/{@link #ENTRY_AMPERAGE_KEY}, which this queue
     * also stores) since search-time power budget accounting ({@code RecipeParallelOperator}/
     * {@code RecipeQueueCommitOperator}'s {@code EUT_CONSUMED_KEY}) only ever needs the product, never the two
     * factors separately.
     */
    public static final String ENTRY_REQUIRED_EUT_KEY = "RequiredEUt";

    /**
     * Which {@code DistinctInputGroup} (by index into {@code RecipeIOConfig#distinctInputGroups}) this entry's
     * reservation was matched against; {@code 0} (NBT's default for a missing key) for logics that don't use
     * distinct groups at all, which is indistinguishable from &mdash; and behaves identically to &mdash; every
     * entry belonging to a single implicit "group 0". See {@link #subtractReservedItems(List, NBTTagCompound, int)}
     * for why this matters: a reservation from one group must not be subtracted from a different group's snapshot,
     * since distinct groups are independent item/fluid pools that may happen to hold the same item types.
     */
    public static final String ENTRY_DISTINCT_GROUP_KEY = "DistinctGroupIndex";

    private PreparedRecipeQueue() {}

    @NotNull
    public static NBTTagList list(@NotNull NBTTagCompound data) {
        if (!data.hasKey(LIST_KEY)) data.setTag(LIST_KEY, new NBTTagList());
        return data.getTagList(LIST_KEY, Constants.NBT.TAG_COMPOUND);
    }

    public static int count(@NotNull NBTTagCompound data) {
        return list(data).tagCount();
    }

    public static boolean isEmpty(@NotNull NBTTagCompound data) {
        return count(data) == 0;
    }

    @NotNull
    public static NBTTagCompound peekFirst(@NotNull NBTTagCompound data) {
        return list(data).getCompoundTagAt(0);
    }

    public static void removeFirst(@NotNull NBTTagCompound data) {
        list(data).removeTag(0);
    }

    public static void append(@NotNull NBTTagCompound data, @NotNull NBTTagCompound entry) {
        list(data).appendTag(entry);
    }

    /**
     * As {@link #newEntry(RecipeRun, int)}, defaulting to distinct group {@code 0} (i.e. "no distinct groups").
     */
    @NotNull
    public static NBTTagCompound newEntry(@NotNull RecipeRun run) {
        return newEntry(run, 0);
    }

    /**
     * Builds a queue entry from a resolved {@link RecipeRun}: outputs (already rolled) and the exact items/fluids
     * to consume once admitted.
     *
     * @param distinctGroupIndex see {@link #ENTRY_DISTINCT_GROUP_KEY}.
     */
    @NotNull
    public static NBTTagCompound newEntry(@NotNull RecipeRun run, int distinctGroupIndex) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setDouble(ENTRY_DURATION_KEY, run.getDuration());
        entry.setLong(ENTRY_REQUIRED_EUT_KEY, run.getRequiredEUt());
        entry.setLong(ENTRY_VOLTAGE_KEY, run.getRequiredVoltage());
        entry.setLong(ENTRY_AMPERAGE_KEY, run.getRequiredAmperage());
        entry.setBoolean(ENTRY_GENERATING_KEY, run.isGenerating());
        entry.setInteger(ENTRY_DISTINCT_GROUP_KEY, distinctGroupIndex);
        ActiveRecipeList.setItemsOut(entry, run.getItemsOut());
        ActiveRecipeList.setFluidsOut(entry, run.getFluidsOut());
        setItemsConsumed(entry, run.getItemsConsumed());
        setFluidsConsumed(entry, run.getFluidsConsumed());
        return entry;
    }

    /**
     * Converts an admitted queue entry into an {@link ActiveRecipeList} entry (progress reset to 0).
     * <p>
     * <b>Denylist, not allowlist:</b> copies the <i>whole</i> prepared entry forward and strips only
     * the queue-only bookkeeping keys below, rather than explicitly copying each known field across. This is what
     * lets {@link gregtech.api.recipes.logic.statemachine.RecipeEntryEnricher}-written custom keys (e.g. Research
     * Station's required/total CWU) survive into the active entry automatically, with neither this class nor
     * {@link ActiveRecipeList} needing to know those keys exist. The tradeoff: a <i>new</i> queue-only key must be
     * added to the strip list below, or it will leak into the active entry (harmlessly unused there, but present)
     * &mdash; the previous allowlist approach had the opposite failure mode (a new <i>shared</i> field silently
     * failing to carry over unless this method was updated to copy it).
     */
    @NotNull
    public static NBTTagCompound toActiveEntry(@NotNull NBTTagCompound preparedEntry) {
        NBTTagCompound active = preparedEntry.copy();
        active.removeTag(ENTRY_ITEMS_CONSUMED_KEY);
        active.removeTag(ENTRY_FLUIDS_CONSUMED_KEY);
        active.removeTag(ENTRY_REQUIRED_EUT_KEY);
        active.removeTag(ENTRY_DISTINCT_GROUP_KEY);
        active.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, 0);
        return active;
    }

    public static long requiredEUt(@NotNull NBTTagCompound entry) {
        return entry.getLong(ENTRY_REQUIRED_EUT_KEY);
    }

    @NotNull
    public static List<ItemStack> itemsConsumed(@NotNull NBTTagCompound entry) {
        return readItems(entry, ENTRY_ITEMS_CONSUMED_KEY);
    }

    public static void setItemsConsumed(@NotNull NBTTagCompound entry, @NotNull List<ItemStack> items) {
        writeItems(entry, ENTRY_ITEMS_CONSUMED_KEY, items);
    }

    @NotNull
    public static List<FluidStack> fluidsConsumed(@NotNull NBTTagCompound entry) {
        return readFluids(entry, ENTRY_FLUIDS_CONSUMED_KEY);
    }

    public static void setFluidsConsumed(@NotNull NBTTagCompound entry, @NotNull List<FluidStack> fluids) {
        writeFluids(entry, ENTRY_FLUIDS_CONSUMED_KEY, fluids);
    }

    /**
     * As {@link #subtractReservedItems(List, NBTTagCompound, int)}, defaulting to distinct group {@code 0} (i.e.
     * "no distinct groups").
     */
    @NotNull
    public static List<ItemStack> subtractReservedItems(@NotNull List<ItemStack> realItems,
                                                         @NotNull NBTTagCompound data) {
        return subtractReservedItems(realItems, data, 0);
    }

    /**
     * @return a deep copy of {@code realItems} with every already-queued entry <i>belonging to
     *         {@code distinctGroupIndex}</i>'s {@link #itemsConsumed} subtracted out, i.e. what a new search of that
     *         group should treat as actually available. See this class's JavaDoc for why this is essential, not
     *         just an optimization, and {@link #ENTRY_DISTINCT_GROUP_KEY} for why this is scoped per group: two
     *         distinct groups are independent pools that may hold the same item types, so an entry reserved from
     *         one group must never be subtracted from a different group's snapshot.
     */
    @NotNull
    public static List<ItemStack> subtractReservedItems(@NotNull List<ItemStack> realItems,
                                                         @NotNull NBTTagCompound data, int distinctGroupIndex) {
        List<ItemStack> working = new ObjectArrayList<>(realItems.size());
        for (ItemStack stack : realItems) working.add(stack.copy());

        NBTTagList queue = list(data);
        for (int i = 0; i < queue.tagCount(); i++) {
            NBTTagCompound entry = queue.getCompoundTagAt(i);
            if (entry.getInteger(ENTRY_DISTINCT_GROUP_KEY) != distinctGroupIndex) continue;
            for (ItemStack consumed : itemsConsumed(entry)) {
                subtractItem(working, consumed);
            }
        }
        return working;
    }

    private static void subtractItem(@NotNull List<ItemStack> working, @NotNull ItemStack consumed) {
        int remaining = consumed.getCount();
        for (ItemStack stack : working) {
            if (remaining <= 0) break;
            if (stack.isEmpty() || !ItemStack.areItemsEqual(stack, consumed) ||
                    !ItemStack.areItemStackTagsEqual(stack, consumed))
                continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    /**
     * As {@link #subtractReservedFluids(List, NBTTagCompound, int)}, defaulting to distinct group {@code 0} (i.e.
     * "no distinct groups").
     */
    @NotNull
    public static List<FluidStack> subtractReservedFluids(@NotNull List<FluidStack> realFluids,
                                                           @NotNull NBTTagCompound data) {
        return subtractReservedFluids(realFluids, data, 0);
    }

    /**
     * @return a deep copy of {@code realFluids} with every already-queued entry <i>belonging to
     *         {@code distinctGroupIndex}</i>'s {@link #fluidsConsumed} subtracted out. See
     *         {@link #subtractReservedItems(List, NBTTagCompound, int)} (its exact item counterpart) for why this
     *         is scoped per group.
     */
    @NotNull
    public static List<FluidStack> subtractReservedFluids(@NotNull List<FluidStack> realFluids,
                                                           @NotNull NBTTagCompound data, int distinctGroupIndex) {
        List<FluidStack> working = new ObjectArrayList<>(realFluids.size());
        for (FluidStack stack : realFluids) working.add(stack == null ? null : stack.copy());

        NBTTagList queue = list(data);
        for (int i = 0; i < queue.tagCount(); i++) {
            NBTTagCompound entry = queue.getCompoundTagAt(i);
            if (entry.getInteger(ENTRY_DISTINCT_GROUP_KEY) != distinctGroupIndex) continue;
            for (FluidStack consumed : fluidsConsumed(entry)) {
                subtractFluid(working, consumed);
            }
        }
        return working;
    }

    private static void subtractFluid(@NotNull List<FluidStack> working, @NotNull FluidStack consumed) {
        int remaining = consumed.amount;
        for (FluidStack stack : working) {
            if (remaining <= 0) break;
            if (stack == null || !stack.isFluidEqual(consumed)) continue;
            int take = Math.min(remaining, stack.amount);
            stack.amount -= take;
            remaining -= take;
        }
    }

    @NotNull
    private static List<ItemStack> readItems(@NotNull NBTTagCompound entry, @NotNull String key) {
        NBTTagList tag = entry.getTagList(key, Constants.NBT.TAG_COMPOUND);
        List<ItemStack> result = new ObjectArrayList<>(tag.tagCount());
        for (int i = 0; i < tag.tagCount(); i++) {
            result.add(new ItemStack(tag.getCompoundTagAt(i)));
        }
        return result;
    }

    private static void writeItems(@NotNull NBTTagCompound entry, @NotNull String key, @NotNull List<ItemStack> items) {
        NBTTagList tag = new NBTTagList();
        for (ItemStack stack : items) {
            tag.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        entry.setTag(key, tag);
    }

    @NotNull
    private static List<FluidStack> readFluids(@NotNull NBTTagCompound entry, @NotNull String key) {
        NBTTagList tag = entry.getTagList(key, Constants.NBT.TAG_COMPOUND);
        List<FluidStack> result = new ObjectArrayList<>(tag.tagCount());
        for (int i = 0; i < tag.tagCount(); i++) {
            FluidStack stack = FluidStack.loadFluidStackFromNBT(tag.getCompoundTagAt(i));
            if (stack != null) result.add(stack);
        }
        return result;
    }

    private static void writeFluids(@NotNull NBTTagCompound entry, @NotNull String key,
                                    @NotNull List<FluidStack> fluids) {
        NBTTagList tag = new NBTTagList();
        for (FluidStack stack : fluids) {
            tag.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        entry.setTag(key, tag);
    }
}
