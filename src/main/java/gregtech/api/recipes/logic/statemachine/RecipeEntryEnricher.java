package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.Recipe;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

/**
 * Writes machine-specific data into a queue entry at admission time, while the originating {@link Recipe} is still
 * available. {@link #enrich} runs once, in {@code RecipeQueueCommitOperator}, right after
 * {@link PreparedRecipeQueue#newEntry} builds the entry and before it's queued; whatever keys it writes survive
 * into the eventual {@link ActiveRecipeList} entry via {@link PreparedRecipeQueue#toActiveEntry}, which copies the
 * whole prepared entry forward (minus the queue-only bookkeeping keys) rather than an explicit allowlist.
 * <p>
 * Exists so a machine that needs cheap per-tick access to some property of its own recipes (e.g. Research
 * Station's required/total CWU &mdash; {@code perTickRecipeCheck}/{@code progressOperationOverride} only ever see
 * the entry's {@link NBTTagCompound}, never the {@link Recipe} itself, since {@link PreparedRecipeQueue} entries
 * are persisted NBT that may still be sitting queued many ticks after the {@link Recipe} reference that produced
 * them was last available) can stash exactly the fields it needs under its own key names, without
 * {@link ActiveRecipeList}/{@link PreparedRecipeQueue} needing to know that property exists at all &mdash; unlike
 * those two classes' own {@code Voltage}/{@code Amperage}/{@code Generating} fields, which are hardcoded because
 * the generic power model itself (not just one machine) reads them.
 * <p>
 * <b>Not a registry</b>: like every other {@link RecipeLogicConfig} field, this is a single nullable slot a
 * machine's own {@code createConfig()} sets directly &mdash; it does not let an addon attach new behavior to an
 * <i>existing</i> GregTech machine the way legacy's {@code GregTechAPI.RECIPE_PROPERTIES} registry lets an addon add a
 * wholly new {@code RecipeProperty} type. A true registry-based extension point for
 * {@link ActiveRecipeList}/{@link RecipeLogicConfig} remains a documented future concern, not solved here.
 * <p>
 * <b>Chain, don't overwrite, if more than one concern needs this slot</b>: since {@code createConfig()} may already
 * have set this (a trait layered on top, e.g. {@code RecipeWorkable} itself for {@code getPreviousRecipe()}, or a
 * subclass's own override), read whatever is already installed first and delegate to it after doing your own work,
 * rather than assigning over it outright and silently dropping the earlier one. See {@code RecipeWorkable}'s
 * constructor for the established pattern.
 */
@FunctionalInterface
public interface RecipeEntryEnricher {

    /**
     * @param recipe the recipe this entry was built from.
     * @param entry  the freshly-built queue entry (not yet queued); write additional keys onto it directly.
     */
    void enrich(@NotNull Recipe recipe, @NotNull NBTTagCompound entry);
}
