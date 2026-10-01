package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A cheap, worst-case gate run <i>before</i> overclocking/rolling: checks whether this candidate's outputs could
 * possibly fit, using {@code ItemOutputProvider#getCompleteOutputs}/{@code FluidOutputProvider#getCompleteOutputs}
 * (i.e. assuming every chance-based output succeeds), so an obviously-hopeless candidate doesn't waste a chance
 * roll or get queued only to sit blocked. This is optimistic (an entry can still be admission-blocked later if
 * output space is claimed by other admitted entries in the meantime), so admission re-checks with the entry's
 * actual (already-rolled) outputs regardless &mdash; this operator exists purely to cut losses early, not as the
 * authoritative check.
 */
public final class RecipeOutputSpaceCheckOperator implements GTStateMachineTransientOperator {

    /** On {@code data}: whether this candidate's worst-case outputs could fit. */
    public static final String SUCCESS_KEY = "OutputSpaceOk";

    public static final Predicate<NBTTagCompound> SUCCESS_PREDICATE = d -> d.getBoolean(SUCCESS_KEY);

    private final @NotNull RecipeLogicConfig config;

    public RecipeOutputSpaceCheckOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        Recipe candidate = (Recipe) transientData.get(RecipeParallelOperator.CANDIDATE_RECIPE_KEY);
        int parallel = (int) transientData.get(RecipeParallelOperator.ACHIEVED_PARALLEL_KEY);
        if (candidate == null) {
            throw new IllegalStateException("RecipeOutputSpaceCheckOperator ran without a candidate recipe");
        }

        // scale by the achieved parallel: unlike GregTech's older merged-recipe model, `candidate` always represents
        // exactly one copy of itself (see RecipeParallelOperator's JavaDoc).
        List<ItemStack> maxItems = candidate.getItemOutputProvider().getCompleteOutputs(parallel,
                config.io.itemTrim.getAsInt());
        List<FluidStack> maxFluids = candidate.getFluidOutputProvider().getCompleteOutputs(parallel,
                config.io.fluidTrim.getAsInt());

        boolean fits = config.io.itemOutputSpace.test(maxItems) && config.io.fluidOutputSpace.test(maxFluids);
        data.setBoolean(SUCCESS_KEY, fits);
    }
}
