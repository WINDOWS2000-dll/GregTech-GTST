package gregtech.api.recipes.logic.statemachine;

import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.statemachine.GTStateMachineTransientOperator;
import gregtech.api.util.GTUtility;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Item/fluid input & output plumbing for a {@link RecipeLogicConfig}: where to read candidate ingredients from,
 * where to send recipe outputs, and the trims/limits that constrain output placement.
 * <p>
 * Fields default to safe no-ops ("no input", "discard output", "no limit") so a machine only needs to
 * touch the handful of fields it actually cares about. See {@link RecipeLogicConfig} for why fields are mutated
 * directly rather than through fluent setters.
 */
public final class RecipeIOConfig {

    /** Supplies the item input inventory to search recipes against, or {@code null} if this logic has no item input. */
    public @Nullable Supplier<IItemHandlerModifiable> itemInput;

    /**
     * Supplies the "flattened" view of {@link #itemInput} used for recipe search. Defaults to
     * {@link GTUtility#itemHandlerToList}; override only if the machine needs a non-standard view (e.g. skipping
     * a ghost-circuit slot).
     */
    public Supplier<List<ItemStack>> itemInputView = () -> GTUtility.itemHandlerToList(itemInput.get());

    /** Item input handlers that should trigger a "recheck inputs" pass when their contents change. */
    public Supplier<List<IItemHandlerModifiable>> notifiedItemInputs = Collections::emptyList;

    /**
     * If non-null, an externally-owned flag the input-invalidation check can consume to force-clear a pending
     * "inputs changed" notification without actually re-running search setup (e.g. right after a structure
     * reformat, when the notification is stale).
     */
    public @Nullable AtomicBoolean forceResetInvalidInputs;

    /** Supplies the fluid input tanks to search recipes against, or {@code null} if this logic has no fluid input. */
    public @Nullable Supplier<IMultipleTankHandler> fluidInput;

    /** As {@link #itemInputView}, but for fluids. Defaults to {@link GTUtility#fluidHandlerToList}. */
    public Supplier<List<FluidStack>> fluidInputView = () -> GTUtility.fluidHandlerToList(fluidInput.get());

    /** As {@link #notifiedItemInputs}, but for fluids. */
    public Supplier<List<IFluidHandler>> notifiedFluidInputs = Collections::emptyList;

    /** Receives finished-recipe item outputs. Defaults to discarding them, which is almost never what you actually want. */
    public Consumer<List<ItemStack>> itemOutput = outputs -> {};

    /** Caps how many distinct item output slots a single recipe run may claim. Defaults to unlimited. */
    public IntSupplier itemTrim = () -> Integer.MAX_VALUE;

    /** If non-null, caps the total item output amount a parallel batch may produce. */
    public @Nullable IntSupplier itemOutAmountLimit;

    /** If non-null, caps the number of distinct item output stacks a parallel batch may produce. */
    public @Nullable IntSupplier itemOutStackLimit;

    /** As {@link #itemOutput}, but for fluids. */
    public Consumer<List<FluidStack>> fluidOutput = outputs -> {};

    /**
     * Tests whether the given item outputs would currently fit into the real output inventory, <i>without</i>
     * actually delivering them. Used to gate a recipe run before it's committed (see
     * {@code RecipeLookupTrackBuilder}), so a chance-output roll isn't wasted on a run that can't be output.
     * Defaults to always {@code true} (no space checking).
     */
    public Predicate<List<ItemStack>> itemOutputSpace = items -> true;

    /** As {@link #itemOutputSpace}, but for fluids. */
    public Predicate<List<FluidStack>> fluidOutputSpace = fluids -> true;

    /** As {@link #itemTrim}, but for fluids. */
    public IntSupplier fluidTrim = () -> Integer.MAX_VALUE;

    /** As {@link #itemOutAmountLimit}, but for fluids. */
    public @Nullable IntSupplier fluidOutAmountLimit;

    /** As {@link #itemOutStackLimit}, but for fluids. */
    public @Nullable IntSupplier fluidOutStackLimit;

    /**
     * Overrides how candidate recipes are matched against {@link #itemInputView}. Currently unused by the standard
     * graph ({@code RecipeLookupTrackBuilder} embeds matching directly in its {@code RecipeLookup} implementations,
     * e.g. {@code RecipeMapLookup}, rather than as a separate operator) &mdash; reserved for a future
     * {@code RecipeLookup}/matching redesign that needs this seam; do not rely on it being consulted yet.
     */
    public @Nullable GTStateMachineTransientOperator itemMatchOperator;

    /** As {@link #itemMatchOperator}, but for fluids. Also currently unused; see that field's JavaDoc. */
    public @Nullable GTStateMachineTransientOperator fluidMatchOperator;

    /**
     * If non-null, this logic searches several independent {@link DistinctInputGroup}s in turn (formalizes GregTech's
     * existing "distinct bus mode") instead of treating {@link #itemInputView}/{@link #fluidInputView} as one
     * combined pool. Re-evaluated at the start of every search pass, like {@link #itemInputView} itself; see
     * {@link DistinctInputGroup}'s JavaDoc for the one stability requirement its result must satisfy.
     * <p>
     * When set, {@link #itemInput}/{@link #itemInputView}/{@link #fluidInput}/{@link #fluidInputView} are ignored
     * for search purposes (each group supplies its own instead) but still apply to anything outside searching that
     * doesn't make sense to scope per-group (there is none today, but e.g. a future "notify on any input change"
     * hook would likely still want a combined view).
     */
    public @Nullable Supplier<List<DistinctInputGroup>> distinctInputGroups;
}
