package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.logic.statemachine.experimental.ExperimentalConfigExtensions;
import gregtech.api.recipes.logic.statemachine.experimental.ExperimentalRecipeLogicRegistry;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Top-level configuration for a machine's recipe-driven {@link gregtech.api.statemachine.GTStateMachine} logic.
 * <p>
 * Grouped by concern into nested config objects ({@link #io}, {@link #power}, {@link #parallel},
 * {@link #overclock}, {@link #hooks}, {@link #callbacks}), so a machine author only needs to read the JavaDoc of
 * the group(s) relevant to their customization instead of one large class.
 * <p>
 * Fields (including nested groups' fields) are mutated directly rather than through fluent setters. This is
 * deliberate: with this many independent, mostly-optional knobs, a {@code setXxx()} wrapper around each one adds
 * ceremony without adding safety (there is no invariant between fields to protect). Direct field access also makes
 * it trivial to see every customization point of a config instance at a glance in an IDE.
 * <p>
 * {@link #hooks}/{@link #callbacks} are assembled into a {@link gregtech.api.statemachine.GTStateMachine} by
 * {@link RecipeLogicGraphBuilder}, which also drives the resulting machine tick-by-tick.
 */
public final class RecipeLogicConfig {

    /**
     * Finds candidate recipes for this logic. See {@link RecipeLookup}'s implementations ({@code RecipeMapLookup}
     * for a plain linear scan, {@code lookup.bitflag.BitflagRecipeLookup} for the indexed alternative) for the
     * available choices.
     */
    public final @NotNull Supplier<RecipeLookup> lookup;

    /** Item/fluid input & output plumbing. See {@link RecipeIOConfig}. */
    public final RecipeIOConfig io = new RecipeIOConfig();

    /** Power-budget bookkeeping. See {@link RecipePowerConfig}. */
    public final RecipePowerConfig power = new RecipePowerConfig();

    /** Parallel-execution limits. See {@link RecipeParallelConfig}. */
    public final RecipeParallelConfig parallel = new RecipeParallelConfig();

    /** Overclock-calculation configuration. See {@link RecipeOverclockConfig}. */
    public final RecipeOverclockConfig overclock = new RecipeOverclockConfig();

    /** Control-flow-shaping predicates and overrides. See {@link RecipeLogicHooks}. */
    public final RecipeLogicHooks hooks = new RecipeLogicHooks();

    /** Pure notification hooks. See {@link RecipeLogicCallbacks}. */
    public final RecipeLogicCallbacks callbacks = new RecipeLogicCallbacks();

    /**
     * Read-only view of whatever {@link ExperimentalRecipeLogicRegistry#putExtensionFactory} entries applied to
     * this specific machine instance, resolved once during construction (see {@link RecipeLogicGraphBuilder#build
     * build}'s {@code owner}-taking overload). Empty (every {@code getExtension} call returns {@code null}) unless
     * an addon actually registered something and GTST's own experimental-extension gate is open for it &mdash; see
     * {@link ExperimentalRecipeLogicRegistry}'s own JavaDoc, which this field is explicitly outside the normal
     * compatibility contract of.
     */
    public final @NotNull ExperimentalConfigExtensions experimental = new ExperimentalConfigExtensions();

    public RecipeLogicConfig(@NotNull Supplier<RecipeLookup> lookup) {
        this.lookup = lookup;
    }

    /**
     * Discards every queued/active recipe outright, for use on events like multiblock structure deformation, where
     * a partial refund or output isn't worth the complexity. This is a deliberately blunt operation:
     * {@link PreparedRecipeQueue}'s reserved-but-not-yet-consumed items were never actually removed from the real
     * inventory (see that class's JavaDoc), so discarding it loses nothing but planning work; {@link ActiveRecipeList}
     * entries, however, {@code did} already consume real inputs on admission, and those are lost here.
     * <p>
     * Runs {@link RecipeLogicHooks#additionalCleanup} afterward, if set, for machine-specific state this framework
     * doesn't itself track.
     */
    public void invalidate(@NotNull NBTTagCompound data) {
        data.removeTag(PreparedRecipeQueue.LIST_KEY);
        data.removeTag(ActiveRecipeList.LIST_KEY);
        data.removeTag(ActiveRecipeList.INDEX_KEY);
        data.removeTag(ActiveRecipeList.SELECTED_KEY);
        data.removeTag(ActiveRecipeList.BONUS_PROGRESS_KEY);
        if (hooks.additionalCleanup != null) hooks.additionalCleanup.accept(data);
    }
}
