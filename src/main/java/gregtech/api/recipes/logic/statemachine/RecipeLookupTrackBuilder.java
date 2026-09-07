package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.logic.statemachine.lookup.RecipeFinalCheckOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeOutputSpaceCheckOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeOverclockOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeParallelOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeQueueCommitOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeRunBuildOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeSearchOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeSelectionOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeViewBuildOperator;
import gregtech.api.statemachine.GTStateMachineBuilder;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import org.jetbrains.annotations.NotNull;

/**
 * Assembles the "lookup" half of a recipe logic's {@link gregtech.api.statemachine.GTStateMachine} graph:
 * finding candidate recipes, working out how many can actually run, and queuing them (via
 * {@link PreparedRecipeQueue}) for {@code RecipeProgressTrackBuilder}'s admission step to eventually pick up.
 * <p>
 * <b>Why this track can find and queue <i>several different</i> recipes per search pass:</b> it loops back to
 * {@link RecipeSelectionOperator} after each successfully-queued candidate, trying the next one from the same
 * {@link RecipeLookup} iterator, until the iterator is exhausted or the parallel budget runs out &mdash; so a
 * single machine can have several genuinely independent, concurrently-progressing recipes (each its own
 * {@code ActiveRecipeList} entry) at once.
 * <p>
 * <b>Distinct input groups:</b> when {@code config.io.distinctInputGroups} is set, the graph wraps this whole
 * candidate-selection loop in an outer loop over groups: once a group's {@link RecipeLookup} iterator is exhausted
 * (see {@link RecipeSelectionOperator}), rather than ending the tick's search outright, it advances to the next
 * group (if any) and restarts {@link RecipeSearchOperator} fresh for that group, before finally ending once every
 * group has been tried. The per-tick parallel/power budget accumulators are reset exactly once, before the first
 * group, specifically so they stay shared across every group searched this tick (a machine's overall
 * {@code parallelLimit} applies across all of its distinct groups combined, not per group) &mdash; see
 * {@link RecipeSearchOperator}'s JavaDoc for why that reset can't live inside {@link RecipeSearchOperator} itself
 * once it may run more than once per tick. This shape leaves room for a future cache-hit optimization (checking a
 * remembered previous group/recipe pairing before falling back to the full group loop, mirroring
 * {@code MultiblockRecipeLogic#trySearchNewRecipeDistinct}'s own cache) without restructuring the graph again; that
 * optimization is not implemented yet.
 * <p>
 * <b>Why consumption is deferred to admission, and how staleness is avoided:</b> this track never touches the real
 * input inventory; it only ever matches against a snapshot (see {@link RecipeSearchOperator}). A queued
 * candidate's planned consumption could otherwise go stale between being queued and eventually being admitted
 * (blocked on output space for several ticks) if something else took the same items out from under it. This is
 * avoided for the common case &mdash; this track's <i>own</i> repeated searching &mdash; by every search treating
 * {@link PreparedRecipeQueue}'s already-queued entries' planned consumption as unavailable (see
 * {@link PreparedRecipeQueue#subtractReservedItems}/{@link PreparedRecipeQueue#subtractReservedFluids}), so the
 * same physical items are never queued twice. Truly external interference (another mod, a player, a shared
 * distinct-bus machine) is not preventable this way; the admission operator re-verifies before actually consuming
 * and discards just that one entry if it no longer holds.
 * <p>
 * Every operator in this track is registered as {@link gregtech.api.statemachine.GTStateMachineTransientOperator
 * transient}: none of this track's intermediate state needs to survive being interrupted (a cut-off search simply
 * restarts next tick), and {@link PreparedRecipeQueue}'s append is a real, persisted mutation of {@code data}
 * regardless of the operator's transient status &mdash; only {@code transientData} clearing is affected by that
 * flag, not writes to {@code data} itself. This also means this track can run entirely offthread when
 * {@code config.hooks.asyncSearchAndSetup} is set, since nothing here blocks on anything but computation.
 */
public final class RecipeLookupTrackBuilder {

    private RecipeLookupTrackBuilder() {}

    public static void build(@NotNull GTStateMachineBuilder builder, int startOp, @NotNull RecipeLogicConfig config) {
        boolean async = config.hooks.asyncSearchAndSetup;
        boolean distinct = config.io.distinctInputGroups != null;

        // config.hooks.shouldStartRecipeLookup gates this entire track: if it fails, the walk simply has nowhere
        // to go from startOp and ends here for this tick, exactly like legacy AbstractRecipeLogic's
        // shouldSearchForRecipes() only being consulted while idle (progressTime == 0) -- an already-queued/active
        // recipe (handled by RecipeProgressTrackBuilder, not this track) is unaffected either way. Defaults to
        // always passing, so this is a no-op for every machine that doesn't set it.
        //
        // Reset the per-tick budget accumulators exactly once, before any group is searched -- see
        // RecipeSearchOperator's JavaDoc for why this can't live inside RecipeSearchOperator itself once distinct
        // groups mean it may run more than once per tick.
        builder.setPointer(startOp).andThenIf(data -> config.hooks.shouldStartRecipeLookup.test(data),
                "shouldStartRecipeLookup gate passed", (data, transientData) -> {
                    transientData.put(RecipeSearchOperator.PARALLEL_CONSUMED_KEY, 0);
                    transientData.put(RecipeSearchOperator.EUT_CONSUMED_KEY, 0L);
                    data.setInteger(RecipeSearchOperator.DISTINCT_GROUP_INDEX_KEY, 0);
                }, async, "resetSearchBudget");
        int resetOp = builder.getPointer();

        builder.setPointer(resetOp)
                .andThenDefault(new RecipeSearchOperator(config), async, "search");
        int searchOp = builder.getPointer();

        builder.setPointer(searchOp).andThenDefault(new RecipeSelectionOperator(config), async, "selectCandidate");
        int selectionOp = builder.getPointer();

        // config.parallel.parallelLimitFactory, if set, replaces RecipeParallelOperator entirely -- mirrors
        // config.overclock.overclockFactory below exactly (same contract: a custom factory's operator must still
        // honor RecipeParallelOperator.SUCCESS_KEY/CANDIDATE_RECIPE_KEY/ACHIEVED_PARALLEL_KEY so the rest of this
        // graph keeps working).
        GTStateMachineTransientOperator parallelOperator = config.parallel.parallelLimitFactory != null ?
                config.parallel.parallelLimitFactory.produce(config.power.downTransformForParallels) :
                new RecipeParallelOperator(config);
        builder.setPointer(selectionOp).andThenIf(RecipeSelectionOperator.SUCCESS_PREDICATE, "a candidate was found",
                parallelOperator, async, "computeParallel");
        int parallelOp = builder.getPointer();

        if (distinct) {
            // else if more distinct groups remain this tick: advance to the next one and restart the search fresh
            // for it. The shared budget reset above (not per-group) means it carries over automatically, since
            // it's tracked in transientData across this whole walk.
            builder.setPointer(selectionOp).andThenIf(
                    d -> d.getInteger(RecipeSearchOperator.DISTINCT_GROUP_INDEX_KEY) + 1 <
                            config.io.distinctInputGroups.get().size(),
                    "more distinct groups to search this tick",
                    (data, transientData) -> data.setInteger(RecipeSearchOperator.DISTINCT_GROUP_INDEX_KEY,
                            data.getInteger(RecipeSearchOperator.DISTINCT_GROUP_INDEX_KEY) + 1),
                    async, "advanceDistinctGroup")
                    .andThenToDefault(searchOp);
        }
        // else (no more candidates, and no more distinct groups, or not in distinct mode): end this tick's search
        // here.

        builder.setPointer(parallelOp).andThenIf(RecipeParallelOperator.SUCCESS_PREDICATE,
                "parallel budget/ingredients allow at least one copy", new RecipeViewBuildOperator(), async,
                "buildView");
        int viewOp = builder.getPointer();
        // else (no parallel budget left at all): end this tick's search here, further candidates would fare no
        // better since the budget is shared across all of them.

        builder.setPointer(viewOp).andThenDefault(new RecipeOutputSpaceCheckOperator(config), async,
                "checkOutputSpace");
        int outputCheckOp = builder.getPointer();

        // config.overclock.overclockFactory, if set, replaces RecipeOverclockOperator entirely -- see that field's
        // JavaDoc for the key contract a custom factory's operator must honor (RecipeOverclockOperator.SUCCESS_KEY/
        // RESULT_KEY) so the rest of this graph keeps working. A machine that only needs to customize part of the
        // calculation should prefer config.overclock.ocAmountCalculator/ocAlgorithm instead (see
        // RecipeOverclockConfig's JavaDoc) -- both are already consulted by the standard RecipeOverclockOperator
        // below, which also already implements upTransformForOverclocks itself (config.overclock
        // .upTransformForOverclocks just toggles it on; no factory is needed to use it). Replacing the whole
        // factory is only worthwhile when even that standard calculation isn't enough, in which case the custom
        // operator becomes responsible for reimplementing whichever of overclocking/up-transform this machine
        // still needs.
        GTStateMachineTransientOperator overclockOperator = config.overclock.overclockFactory != null ?
                config.overclock.overclockFactory.produce(config.overclock.costFactor, config.overclock.speedFactor,
                        config.overclock.upTransformForOverclocks, config.overclock.durationDiscount) :
                new RecipeOverclockOperator(config);

        builder.setPointer(outputCheckOp).andThenIf(RecipeOutputSpaceCheckOperator.SUCCESS_PREDICATE,
                "worst-case outputs could fit", overclockOperator, async, "overclock");
        int overclockOp = builder.getPointer();
        builder.setPointer(outputCheckOp).andThenToDefault(selectionOp); // else: this candidate can't fit, try another.

        builder.setPointer(overclockOp).andThenIf(RecipeOverclockOperator.SUCCESS_PREDICATE,
                "required voltage available", new RecipeRunBuildOperator(config), async, "buildRun");
        int buildRunOp = builder.getPointer();
        builder.setPointer(overclockOp).andThenToDefault(selectionOp); // else: not enough power, try another.

        // config.hooks.finalCheck, if set, gets one last look at the fully-resolved run before it's queued.
        builder.setPointer(buildRunOp).andThenDefault(new RecipeFinalCheckOperator(config), async, "finalCheck");
        int finalCheckOp = builder.getPointer();

        builder.setPointer(finalCheckOp).andThenIf(RecipeFinalCheckOperator.SUCCESS_PREDICATE,
                "finalCheck passed (or isn't set)", new RecipeQueueCommitOperator(config), async, "queueRun");
        int commitOp = builder.getPointer();
        builder.setPointer(finalCheckOp).andThenToDefault(selectionOp); // else: rejected by finalCheck, try another.

        builder.setPointer(commitOp).andThenToDefault(selectionOp); // try another candidate with the remaining budget.
    }
}
