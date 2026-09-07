package gregtech.api.recipes.logic.statemachine;

import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

/**
 * Parallel-execution limits for a {@link RecipeLogicConfig}: how many recipe instances may run at once, and how
 * much of that budget other in-flight runs have already claimed.
 */
public final class RecipeParallelConfig {

    /**
     * Maximum number of recipe instances this logic may run in parallel. {@code null} means no parallelism (effectively
     * 1).
     */
    public @Nullable IntSupplier parallelLimit;

    /**
     * If non-null, reports how much of {@link #parallelLimit} has already been claimed by other recipe runs sharing
     * this logic's parallel budget, so a newly-considered run's budget check accounts for it.
     */
    public @Nullable IntSupplier consumedParallelSupplier;

    /**
     * Produces the operator that limits a matched recipe's parallel count to what {@link #parallelLimit} (minus
     * {@link #consumedParallelSupplier}) allows. {@code null} means "use the standard limiting operator" once one
     * exists; there is no standard implementation yet (pending {@code RecipeLookupTrackBuilder}).
     */
    public @Nullable ParallelLimitFactory parallelLimitFactory;
}
