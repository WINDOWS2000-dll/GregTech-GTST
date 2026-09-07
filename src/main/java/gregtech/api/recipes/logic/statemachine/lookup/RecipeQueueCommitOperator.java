package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.logic.RecipeRun;
import gregtech.api.recipes.logic.statemachine.PreparedRecipeQueue;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The last operator of one loop iteration: appends {@link RecipeRunBuildOperator}'s finished run to
 * {@link PreparedRecipeQueue} (a real, persistent mutation of {@code data} regardless of this operator being
 * transient &mdash; see {@code RecipeLookupTrackBuilder}'s JavaDoc), and accounts for the parallel/power it
 * consumed so the next loop iteration's {@link RecipeParallelOperator} sees an accurate remaining budget.
 * <p>
 * Power is tracked in EU/t ({@link RecipeRun#getRequiredEUt()}, i.e. post-overclock voltage &times; amperage) rather
 * than raw amperage: EU/t stays comparable even across runs at different voltage tiers within the same search pass,
 * which raw amperage figures alone would not (see {@code RecipePowerConfig}'s JavaDoc).
 * <p>
 * <b>Not a singleton:</b> unlike most transient operators in this pipeline, this one needs
 * {@code config} itself (to reach {@code config.hooks.entryEnricher}), so each machine's own graph gets its own
 * instance, exactly like {@link RecipeParallelOperator}.
 */
public final class RecipeQueueCommitOperator implements GTStateMachineTransientOperator {

    private final @NotNull RecipeLogicConfig config;

    public RecipeQueueCommitOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        RecipeRun run = (RecipeRun) transientData.get(RecipeRunBuildOperator.RUN_KEY);
        if (run == null) throw new IllegalStateException("RecipeQueueCommitOperator ran without a finished run");

        int groupIndex = data.getInteger(RecipeSearchOperator.DISTINCT_GROUP_INDEX_KEY);
        NBTTagCompound entry = PreparedRecipeQueue.newEntry(run, groupIndex);
        if (config.hooks.entryEnricher != null) {
            config.hooks.entryEnricher.enrich(run.getRecipeView().getRecipe(), entry);
        }
        PreparedRecipeQueue.append(data, entry);

        int parallelSoFar = (int) transientData.getOrDefault(RecipeSearchOperator.PARALLEL_CONSUMED_KEY, 0);
        transientData.put(RecipeSearchOperator.PARALLEL_CONSUMED_KEY, parallelSoFar + run.getParallel());

        long eutSoFar = (long) transientData.getOrDefault(RecipeSearchOperator.EUT_CONSUMED_KEY, 0L);
        transientData.put(RecipeSearchOperator.EUT_CONSUMED_KEY, eutSoFar + run.getRequiredEUt());
    }
}
