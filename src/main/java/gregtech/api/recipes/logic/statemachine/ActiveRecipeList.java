package gregtech.api.recipes.logic.statemachine;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The NBT format {@code RecipeProgressTrackBuilder} (and, eventually, whatever admits newly-matched recipes into
 * it) uses to track recipe runs currently executing or awaiting execution within a {@link RecipeLogicConfig}'s
 * serializable {@code data}.
 * <p>
 * This is intentionally a flat, opaque-to-the-rest-of-the-graph NBT shape rather than a Java object: the whole
 * point of a {@link gregtech.api.statemachine.GTStateMachine} is that its serializable state is plain NBT, so an
 * in-progress recipe survives a chunk unload/world reload mid-execution. Entries here are written by whatever
 * finalizes a matched candidate, and are read and mutated by {@code RecipeProgressTrackBuilder} using only the
 * field names below.
 * <p>
 * <b>Important:</b> {@link #selected} returns the <i>same</i> {@link NBTTagCompound} instance stored inside the
 * list at the index {@code RecipeProgressTrackBuilder} last selected (NBT tags are stored and returned by
 * reference, not copied) — mutating the object it returns mutates the list entry directly, with no separate
 * write-back step needed. This aliasing is deliberate (it is how the progress/stall/bonus-carry operators persist
 * progress without an explicit save-back step) but is easy to break by accidentally inserting a {@code .copy()}
 * somewhere in a refactor, so don't.
 */
public final class ActiveRecipeList {

    /** Key, on the logic's top-level {@code data}, of the {@link NBTTagList} of active-recipe entries. */
    public static final String LIST_KEY = "ActiveRecipes";

    /** Key, on {@code data}, of the current loop index into {@link #LIST_KEY}. Absent means "not yet started". */
    public static final String INDEX_KEY = "Index";

    /**
     * Key, on {@code data}, under which the entry at {@link #INDEX_KEY} is staged for processing. See this class's
     * JavaDoc for the aliasing {@link #selected} relies on.
     */
    public static final String SELECTED_KEY = "ProcessingRecipe";

    /**
     * Key, on {@code data}, of the banked fractional progress carried over from a previous recipe's completion
     * (see {@code RecipeBonusCarryOperator}), so a fractional speed bonus that overshoots one recipe's completion
     * isn't discarded before the next recipe can use it.
     */
    public static final String BONUS_PROGRESS_KEY = "RecipeBonusProgress";

    /** Key, on an entry, of its accumulated integer progress. */
    public static final String ENTRY_PROGRESS_KEY = "Progress";

    /** Key, on an entry, of the (possibly fractional, post-overclock) duration it completes at. */
    public static final String ENTRY_DURATION_KEY = "Duration";

    /** Key, on an entry, of its serialized item outputs (see {@link #itemsOut}). */
    public static final String ENTRY_ITEMS_OUT_KEY = "ItemsOut";

    /** Key, on an entry, of its serialized fluid outputs (see {@link #fluidsOut}). */
    public static final String ENTRY_FLUIDS_OUT_KEY = "FluidsOut";

    /**
     * Key, on an entry, of the voltage (per amp) this specific run requires/produces. Carried forward from
     * {@code PreparedRecipeQueue.ENTRY_REQUIRED_EUT_KEY}'s two components rather than that single combined value,
     * specifically so a per-tick energy check can scale a partial final tick's draw by {@code voltage * amperage}
     * without losing precision to an already-multiplied total.
     */
    public static final String ENTRY_VOLTAGE_KEY = "Voltage";

    /** Key, on an entry, of the amperage this specific run requires/produces at {@link #ENTRY_VOLTAGE_KEY}. */
    public static final String ENTRY_AMPERAGE_KEY = "Amperage";

    /** Key, on an entry, of whether this run generates power rather than consuming it. */
    public static final String ENTRY_GENERATING_KEY = "Generating";

    private ActiveRecipeList() {}

    /** @return the active-recipe list on {@code data}, creating an empty one if absent. */
    @NotNull
    public static NBTTagList list(@NotNull NBTTagCompound data) {
        if (!data.hasKey(LIST_KEY)) data.setTag(LIST_KEY, new NBTTagList());
        return data.getTagList(LIST_KEY, Constants.NBT.TAG_COMPOUND);
    }

    /** @return how many recipes are currently active/queued. */
    public static int count(@NotNull NBTTagCompound data) {
        return list(data).tagCount();
    }

    /** @return the entry at {@code index}, by reference (see this class's JavaDoc on aliasing). */
    @NotNull
    public static NBTTagCompound entryAt(int index, @NotNull NBTTagCompound data) {
        return list(data).getCompoundTagAt(index);
    }

    /** Appends a newly-admitted entry to the active-recipe list. */
    public static void append(@NotNull NBTTagCompound data, @NotNull NBTTagCompound entry) {
        list(data).appendTag(entry);
    }

    /**
     * @return the entry currently staged under {@link #SELECTED_KEY} — the same instance stored in the list (see
     *         this class's JavaDoc on aliasing).
     */
    @NotNull
    public static NBTTagCompound selected(@NotNull NBTTagCompound data) {
        return data.getCompoundTag(SELECTED_KEY);
    }

    /** @return whether the currently-selected entry has accumulated enough progress to complete. */
    public static boolean isSelectedComplete(@NotNull NBTTagCompound data) {
        NBTTagCompound entry = selected(data);
        return entry.getInteger(ENTRY_PROGRESS_KEY) >= entry.getDouble(ENTRY_DURATION_KEY);
    }

    /** @return the deserialized item outputs stored on {@code entry}. */
    @NotNull
    public static List<ItemStack> itemsOut(@NotNull NBTTagCompound entry) {
        NBTTagList tag = entry.getTagList(ENTRY_ITEMS_OUT_KEY, Constants.NBT.TAG_COMPOUND);
        List<ItemStack> result = new ObjectArrayList<>(tag.tagCount());
        for (int i = 0; i < tag.tagCount(); i++) {
            result.add(new ItemStack(tag.getCompoundTagAt(i)));
        }
        return result;
    }

    /** Serializes {@code items} onto {@code entry} under {@link #ENTRY_ITEMS_OUT_KEY}. */
    public static void setItemsOut(@NotNull NBTTagCompound entry, @NotNull List<ItemStack> items) {
        NBTTagList tag = new NBTTagList();
        for (ItemStack stack : items) {
            tag.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        entry.setTag(ENTRY_ITEMS_OUT_KEY, tag);
    }

    /** @return the deserialized fluid outputs stored on {@code entry}. */
    @NotNull
    public static List<FluidStack> fluidsOut(@NotNull NBTTagCompound entry) {
        NBTTagList tag = entry.getTagList(ENTRY_FLUIDS_OUT_KEY, Constants.NBT.TAG_COMPOUND);
        List<FluidStack> result = new ObjectArrayList<>(tag.tagCount());
        for (int i = 0; i < tag.tagCount(); i++) {
            FluidStack stack = FluidStack.loadFluidStackFromNBT(tag.getCompoundTagAt(i));
            if (stack != null) result.add(stack);
        }
        return result;
    }

    /** Serializes {@code fluids} onto {@code entry} under {@link #ENTRY_FLUIDS_OUT_KEY}. */
    public static void setFluidsOut(@NotNull NBTTagCompound entry, @NotNull List<FluidStack> fluids) {
        NBTTagList tag = new NBTTagList();
        for (FluidStack stack : fluids) {
            tag.appendTag(stack.writeToNBT(new NBTTagCompound()));
        }
        entry.setTag(ENTRY_FLUIDS_OUT_KEY, tag);
    }
}
