package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.RecipeRun;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.nbt.NBTTagCompound;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Predicates and overrides that customize a {@link RecipeLogicConfig}'s control flow: whether/when to search,
 * whether a found candidate is acceptable, and how progress/stall behaves. Distinct from
 * {@link RecipeLogicCallbacks}, whose fields are pure notifications with no return value and no effect on control
 * flow.
 * <p>
 * <b>Maintenance needs no dedicated field here</b>: it composes entirely from already-generic hooks: the "too
 * many problems, don't even search" gate is {@link #shouldStartRecipeLookup}, the post-overclock duration penalty
 * is {@code RecipeOverclockConfig#durationDiscount}, and the pre-overclock duration bonus is
 * {@code RecipeOverclockConfig#durationBonusPreOverclock}. A machine bridges GregTech's existing maintenance system by
 * plugging its own problem-count/multiplier logic into those three, rather than this class knowing about
 * maintenance at all.
 */
public final class RecipeLogicHooks {

    /** Gate on whether a new recipe search should even begin this tick. Defaults to always allowing it. */
    public Predicate<NBTTagCompound> shouldStartRecipeLookup = workerData -> true;

    /** If non-null, an additional filter a candidate recipe must pass (beyond matching inputs) to be selected. */
    public @Nullable Predicate<Recipe> recipeSearchPredicate;

    /**
     * If non-null, an additional check the fully-built {@link RecipeRun} must pass (beyond overclock/output-space
     * feasibility) before being queued. Unlike {@link #recipeSearchPredicate}
     * (evaluated against the bare candidate {@link Recipe}, before parallel/overclock/output rolling), this sees
     * the final, fully-resolved run &mdash; e.g. its actual post-overclock voltage/amperage or rolled outputs.
     * A run that fails this check is discarded and the next candidate is tried, exactly like an overclock or
     * output-space failure.
     */
    public @Nullable Predicate<RecipeRun> finalCheck;

    /**
     * Per-tick gate evaluated regardless of which recipe is active (e.g. "is this machine currently unpaused").
     * Defaults to always passing.
     */
    public Predicate<NBTTagCompound> perTickWorkerCheck = workerData -> true;

    /**
     * Per-tick gate evaluated against the currently-active recipe's data (e.g. "are maintenance problems blocking
     * progress"). Defaults to always passing.
     */
    public Predicate<NBTTagCompound> perTickRecipeCheck = recipeData -> true;

    /**
     * What happens to progress when {@link #perTickRecipeCheck}/{@link #perTickWorkerCheck} fails: gradually decay
     * it, or reset it outright. Ignored if {@link #progressOperationOverride} is set.
     */
    public RecipeStallType stallType = RecipeStallType.DEGRESS;

    /**
     * If non-null, replaces the standard progress/stall graph segment entirely. See {@link RecipeProgressOverride}
     * for the contract overrides must follow: notably, it passes through the active-recipe loop's "advance to next
     * index" operator ID, so a custom stall branch can loop back into that loop itself rather than dead-ending.
     */
    public @Nullable RecipeProgressOverride progressOperationOverride;

    /**
     * Transforms a just-completed recipe's outputs immediately before delivery.
     * See {@link RecipeFinalizer}'s JavaDoc for exactly when this runs relative to delivery/{@link
     * RecipeLogicCallbacks#onRecipeCompleted}.
     */
    public @Nullable RecipeFinalizer recipeFinalizer;

    /**
     * If non-null, additional cleanup run by {@link RecipeLogicConfig#invalidate} on top of the standard behavior
     * of discarding every queued/active recipe outright (e.g. clearing a machine-specific NBT field that isn't
     * part of this framework's own state). See {@link RecipeLogicConfig#invalidate}'s JavaDoc for when that's
     * called and why no partial output/refund is attempted.
     */
    public @Nullable Consumer<NBTTagCompound> additionalCleanup;

    /**
     * If non-null, writes machine-specific data into a queue entry at admission time (e.g. Research Station's
     * CWU/t and total-CWU needs). See {@link RecipeEntryEnricher}'s JavaDoc for the full contract and why this
     * exists instead of adding new hardcoded fields to {@link ActiveRecipeList}/{@link PreparedRecipeQueue}.
     */
    public @Nullable RecipeEntryEnricher entryEnricher;

    /**
     * Additional transient operators run during search setup, after the standard providers but before recipe search
     * itself.
     */
    public final List<GTStateMachineTransientOperator> additionalSearchSetupOperators = new ObjectArrayList<>();

    /**
     * If non-null, called every time a search pass finds no candidate at all, with a human-readable breakdown of
     * why (see {@link RecipeLookup#diagnoseNoMatch}). Left {@code null} (the default) skips computing that
     * breakdown entirely, rather than computing it and discarding it &mdash; wired to non-null only while the
     * execution-trace dev tool is actively tracing this machine (see
     * {@code RecipeWorkable#setTraceEnabled}), so this costs nothing for the overwhelming majority of machines
     * that are never traced.
     */
    public @Nullable Consumer<String> onNoMatchFound;

    /**
     * Whether recipe search and setup may run offthread via
     * {@link gregtech.api.statemachine.GTStateMachine#dispatchAsync}.
     */
    public boolean asyncSearchAndSetup = false;
}
