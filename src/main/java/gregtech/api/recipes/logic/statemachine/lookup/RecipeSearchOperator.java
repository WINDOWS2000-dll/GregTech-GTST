package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.DistinctInputGroup;
import gregtech.api.recipes.logic.statemachine.PreparedRecipeQueue;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Snapshots the current inputs (minus whatever is already reserved by {@link PreparedRecipeQueue} entries awaiting
 * admission &mdash; see that class's JavaDoc for why this is essential, not optional) and starts a new
 * {@link gregtech.api.recipes.logic.statemachine.RecipeLookup} search against that adjusted snapshot.
 * <p>
 * When {@code config.io.distinctInputGroups} is set, this runs once <i>per group</i> (see
 * {@code RecipeLookupTrackBuilder}'s distinct-group loop), reading {@link #DISTINCT_GROUP_INDEX_KEY} to know which
 * group's items/fluids to snapshot this time, and scoping {@link PreparedRecipeQueue}'s reservation-subtraction to
 * that same group (see {@link PreparedRecipeQueue#ENTRY_DISTINCT_GROUP_KEY}) so groups never see each other's
 * reservations. Otherwise it behaves as a single implicit "group 0" &mdash; {@link #DISTINCT_GROUP_INDEX_KEY}
 * defaults to {@code 0} (NBT's default for a missing key) either way, so the group-scoped and non-distinct code
 * paths are the same code, not a branch duplicated per mode.
 * <p>
 * Does <i>not</i> reset the per-pass budget accumulators ({@link #PARALLEL_CONSUMED_KEY}/{@link #EUT_CONSUMED_KEY})
 * itself: unlike this operator (which runs once per group, i.e. possibly several times per tick), those must be
 * reset exactly once per tick regardless of how many groups get searched, since a machine's overall parallel/power
 * budget is shared across all of its groups. See {@code RecipeLookupTrackBuilder}'s graph for where that reset
 * actually happens.
 */
public final class RecipeSearchOperator implements GTStateMachineTransientOperator {

    public static final String ITERATOR_KEY = "SearchIterator";
    public static final String ITEMS_SNAPSHOT_KEY = "SearchItemsSnapshot";
    public static final String FLUIDS_SNAPSHOT_KEY = "SearchFluidsSnapshot";

    /**
     * On {@code transientData}: this search's {@code maxVoltage}/{@code RecipePropertySet}, kept around purely for
     * {@link RecipeSelectionOperator#operate}'s {@code config.hooks.onNoMatchFound} diagnostics &mdash; nothing in
     * the standard graph reads these back otherwise, since {@link #operate} already consumed them locally to build
     * {@link #ITERATOR_KEY}'s iterator.
     */
    public static final String MAX_VOLTAGE_KEY = "SearchMaxVoltage";

    /** @see #MAX_VOLTAGE_KEY */
    public static final String PROPERTIES_KEY = "SearchProperties";

    public static final String PARALLEL_CONSUMED_KEY = "SearchParallelConsumed";

    /** Per-pass accumulator: how much power (EU/t) this pass's own already-committed candidates have claimed. */
    public static final String EUT_CONSUMED_KEY = "SearchEUtConsumed";

    /**
     * On {@code data}: which {@code DistinctInputGroup} (by index) this and every subsequent operator in the
     * current candidate's pipeline should treat as "the" group, until the search loop advances it. See this
     * class's JavaDoc for why defaulting to {@code 0} makes non-distinct logics behave correctly for free.
     */
    public static final String DISTINCT_GROUP_INDEX_KEY = "SearchDistinctGroupIndex";

    private final @NotNull RecipeLogicConfig config;

    public RecipeSearchOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        int groupIndex = data.getInteger(DISTINCT_GROUP_INDEX_KEY);

        List<ItemStack> realItems;
        List<FluidStack> realFluids;
        if (config.io.distinctInputGroups != null) {
            DistinctInputGroup group = config.io.distinctInputGroups.get().get(groupIndex);
            realItems = group.items();
            realFluids = group.fluids();
        } else {
            realItems = config.io.itemInput != null ? config.io.itemInputView.get() : Collections.emptyList();
            realFluids = config.io.fluidInput != null ? config.io.fluidInputView.get() : Collections.emptyList();
        }

        List<ItemStack> availableItems = PreparedRecipeQueue.subtractReservedItems(realItems, data, groupIndex);
        List<FluidStack> availableFluids = PreparedRecipeQueue.subtractReservedFluids(realFluids, data, groupIndex);

        // getMaxSearchVoltage() (amperage-inclusive), not getMaxVoltage(): a candidate config.overclock
        // .upTransformForOverclocks could otherwise rescue must not be excluded before RecipeOverclockOperator
        // gets a chance to evaluate it -- see that method's JavaDoc.
        long maxVoltage = config.power.getMaxSearchVoltage();
        RecipePropertySet properties = config.power.properties == null ? null : config.power.properties.get();
        Iterator<Recipe> iterator = config.lookup.get().findRecipes(maxVoltage, properties, availableItems,
                availableFluids);

        transientData.put(ITERATOR_KEY, iterator);
        transientData.put(ITEMS_SNAPSHOT_KEY, availableItems);
        transientData.put(FLUIDS_SNAPSHOT_KEY, availableFluids);
        transientData.put(MAX_VOLTAGE_KEY, maxVoltage);
        if (properties != null) transientData.put(PROPERTIES_KEY, properties);
    }
}
