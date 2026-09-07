package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.logic.statemachine.progress.RecipeBonusCarryOperator;
import gregtech.api.recipes.logic.statemachine.progress.RecipeOutputOperator;
import gregtech.api.recipes.logic.statemachine.progress.RecipeProgressOperator;
import gregtech.api.recipes.logic.statemachine.progress.RecipeStallOperator;
import gregtech.api.statemachine.GTStateMachineBuilder;
import gregtech.api.statemachine.GTStateMachineOperator;

import net.minecraft.nbt.NBTTagCompound;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.NotNull;

/**
 * Assembles the "progress" half of a recipe logic's {@link gregtech.api.statemachine.GTStateMachine} graph:
 * cycling through every currently-{@link ActiveRecipeList active recipe}, advancing or stalling its progress per
 * tick, and delivering outputs once one finishes.
 * <p>
 * This class is deliberately independent of the lookup machinery (see {@link RecipeLogicConfig}'s JavaDoc): it
 * only knows how to admit already-finalized entries (via the externally supplied {@code admissionOperator}) into
 * the {@link ActiveRecipeList} format and cycle through them. Whatever turns a matched candidate into an
 * {@link ActiveRecipeList} entry (the lookup track's job) is free to be designed independently of this class.
 * <p>
 * <b>A subtlety this design avoids:</b> a naive implementation might reuse the operator that runs when the
 * active-recipe index is out of bounds (which just removes the {@code Index} tag and, having no further link, ends
 * the walk) as the jump target after a completed recipe is removed from the active list. Since that operator
 * unconditionally ends the walk, completing any one active recipe would then silently abandon processing of every
 * other active recipe for the rest of that tick. This class avoids that by keeping the bounds check
 * ({@code checkIndexInRange}, see {@link #buildActiveRecipeLoop}) as its own side-effect-free branch point,
 * distinct from the operator that increments the index — so re-entering the loop after a removal re-checks bounds
 * at the (now-shifted) current index without skipping an entry or ending early.
 */
public final class RecipeProgressTrackBuilder {

    private static final String TICK_CHECK_KEY = "TickCheckSuccess";

    /**
     * Key, on {@code data}, of the boolean flag the per-tick check operator sets before handing off to either the
     * standard progress/stall branch or a {@link RecipeProgressOverride} (public specifically so an override can
     * read it, per that interface's own contract).
     */
    public static final String RECIPE_CHECK_KEY = "RecipeCheckSuccess";

    private RecipeProgressTrackBuilder() {}

    /**
     * Builds the progress track starting at {@code startOp} (which should be a fresh, unlinked operator registered
     * by the caller — see {@code RecipeLogicGraphBuilder}).
     *
     * @param admissionOperator run once per tick, before the active-recipe loop; expected to move newly-accepted
     *                          candidates into the {@link ActiveRecipeList} format this class reads (via
     *                          {@link ActiveRecipeList#append}). Kept as an injected operator, rather than a type
     *                          this class knows about, so this class does not need to depend on the not-yet-designed
     *                          lookup/finalization machinery that produces those candidates.
     */
    public static void build(@NotNull GTStateMachineBuilder builder, int startOp, @NotNull RecipeLogicConfig config,
                             @NotNull GTStateMachineOperator admissionOperator) {
        GTStateMachineOperator outputOperator = new RecipeOutputOperator(config);

        builder.setPointer(startOp).andThenDefault(admissionOperator, false, "admitPreparedRecipes");
        builder.andThenDefault(d -> d.setBoolean(TICK_CHECK_KEY, config.hooks.perTickWorkerCheck.test(d)), false,
                "checkWorkerTick");
        int afterTickCheckOp = builder.getPointer();

        LoopAnchors anchors = buildActiveRecipeLoop(builder, afterTickCheckOp);
        int selectActiveRecipeOp = builder.getPointer();
        buildProgressStallAndCompletion(builder, selectActiveRecipeOp, config, anchors, outputOperator);
    }

    /**
     * Builds the index-management portion of the loop: advance to the next index, check it against the active
     * list's current size, and either end this tick's processing (if out of range) or stage the entry at that
     * index for the caller to continue processing from.
     * <p>
     * Starts at {@code fromOp} (linked as its default/only outgoing edge). Leaves {@code builder}'s pointer at the
     * "select active recipe" operator, ready for the caller to chain the per-tick recipe check directly after it.
     */
    @NotNull
    private static LoopAnchors buildActiveRecipeLoop(@NotNull GTStateMachineBuilder builder, int fromOp) {
        builder.setPointer(fromOp).andThenDefault(d -> d.setInteger(ActiveRecipeList.INDEX_KEY,
                d.hasKey(ActiveRecipeList.INDEX_KEY) ? d.getInteger(ActiveRecipeList.INDEX_KEY) + 1 : 0), false,
                "advanceIndex");
        int indexIncrementOp = builder.getPointer();

        // A dedicated, side-effect-free branch point. Re-entering here (rather than indexIncrementOp) after a
        // completed recipe is removed lets us re-check bounds at the *same* index without skipping an entry or
        // incrementing past the end of the (now-shorter) list. See this class's JavaDoc.
        builder.newOperator(GTStateMachineOperator.emptyOp(), false, "checkIndexInRange");
        int indexCheckOp = builder.getPointer();
        builder.setPointer(indexIncrementOp).andThenToDefault(indexCheckOp);

        builder.setPointer(indexCheckOp).andThenIf(
                d -> d.getInteger(ActiveRecipeList.INDEX_KEY) >= ActiveRecipeList.count(d),
                "no more active recipes to process this tick",
                d -> d.removeTag(ActiveRecipeList.INDEX_KEY), false, "endOfActiveRecipesThisTick");
        builder.setPointer(indexCheckOp).andThenDefault(
                d -> d.setTag(ActiveRecipeList.SELECTED_KEY,
                        ActiveRecipeList.entryAt(d.getInteger(ActiveRecipeList.INDEX_KEY), d)),
                false, "selectActiveRecipe");

        return new LoopAnchors(indexIncrementOp, indexCheckOp);
    }

    /**
     * Builds the per-tick check, progress-or-stall branch, and (on completion) output/cleanup/removal handling for
     * whichever active recipe {@link #buildActiveRecipeLoop} just staged.
     * <p>
     * Starts at {@code fromOp} (the "select active recipe" operator). Ends by looping back to
     * {@code anchors.indexCheckOp()} (after a completion) or {@code anchors.indexIncrementOp()} (otherwise), so it
     * does not leave the builder positioned anywhere meaningful for further chaining.
     */
    private static void buildProgressStallAndCompletion(@NotNull GTStateMachineBuilder builder, int fromOp,
                                                         @NotNull RecipeLogicConfig config,
                                                         @NotNull LoopAnchors anchors,
                                                         @NotNull GTStateMachineOperator outputOperator) {
        builder.setPointer(fromOp).andThenDefault(
                d -> d.setBoolean(RECIPE_CHECK_KEY, d.getBoolean(TICK_CHECK_KEY) &&
                        config.hooks.perTickRecipeCheck.test(ActiveRecipeList.selected(d))),
                false, "checkRecipeThisTick");
        int recipeCheckOp = builder.getPointer();

        if (config.hooks.progressOperationOverride != null) {
            config.hooks.progressOperationOverride.wire(builder.setPointer(recipeCheckOp), config.hooks.stallType,
                    anchors.indexIncrementOp());
        } else {
            builder.setPointer(recipeCheckOp)
                    .andThenDefault(RecipeStallOperator.of(config.hooks.stallType), false, "stallRecipe");
            int stallOp = builder.getPointer();
            builder.setPointer(stallOp).andThenToDefault(anchors.indexIncrementOp());

            builder.setPointer(recipeCheckOp).andThenIf(d -> d.getBoolean(RECIPE_CHECK_KEY),
                    "per-tick checks passed", RecipeProgressOperator.INSTANCE, false, "progressRecipe");
        }
        int postProgressOp = builder.getPointer();

        builder.setPointer(postProgressOp).andThenToDefault(anchors.indexIncrementOp());
        builder.setPointer(postProgressOp).andThenIf(ActiveRecipeList::isSelectedComplete,
                "recipe finished this tick", outputOperator, false, "outputCompletedRecipe");
        int outputOp = builder.getPointer();

        // See RecipeBonusCarryOperator's JavaDoc for why this is registered transient specifically when there is a
        // completion callback to run afterward: its mutation only "commits" once the walk proceeds past that
        // callback successfully.
        if (config.callbacks.onRecipeCompleted != null) {
            builder.setPointer(outputOp)
                    .andThenDefaultTransient(RecipeBonusCarryOperator.INSTANCE, false, "carryBonusProgress");
            int bonusOp = builder.getPointer();
            builder.setPointer(bonusOp).andThenDefault(
                    d -> config.callbacks.onRecipeCompleted.accept(ActiveRecipeList.selected(d)), false,
                    "notifyRecipeCompleted");
        } else {
            builder.setPointer(outputOp)
                    .andThenDefault(RecipeBonusCarryOperator.INSTANCE, false, "carryBonusProgress");
        }
        int afterCompletionNotifyOp = builder.getPointer();

        builder.setPointer(afterCompletionNotifyOp).andThenDefault(
                d -> ActiveRecipeList.list(d).removeTag(d.getInteger(ActiveRecipeList.INDEX_KEY)), false,
                "removeCompletedRecipe");
        int removeOp = builder.getPointer();
        builder.setPointer(removeOp).andThenToDefault(anchors.indexCheckOp());
    }

    @Desugar
    private record LoopAnchors(int indexIncrementOp, int indexCheckOp) {}
}
