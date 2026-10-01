package gregtech.api.recipes.logic.statemachine;

import gregtech.api.statemachine.GTStateMachineTransientOperator;

/**
 * Factory for the operator that limits a matched recipe's parallel count. Defined for the same reason as
 * {@link OverclockFactory}: its shape does not depend on {@code RecipePowerConfig.properties}, only on the plain
 * {@code boolean} it's given.
 * <p>
 * <b>Contract a custom factory's operator must honor</b> (see {@link OverclockFactory}'s identical note --
 * checked only by convention, not by the type system): on {@code data}, set
 * {@code RecipeParallelOperator.SUCCESS_KEY} (a {@code boolean}: whether any parallel budget/ingredients/amperage
 * remained to run at least one copy); on {@code transientData}, when successful, set
 * {@code RecipeParallelOperator.CANDIDATE_RECIPE_KEY} (the matched {@code Recipe}, unchanged -- see that field's
 * own JavaDoc on why it is never merged into a synthetic N&times;-wide recipe) and
 * {@code RecipeParallelOperator.ACHIEVED_PARALLEL_KEY} (the {@code int} copy count). {@code RecipeViewBuildOperator}
 * and everything after it reads only those three keys, never this class's own {@code produce} parameter again.
 */
@FunctionalInterface
public interface ParallelLimitFactory {

    GTStateMachineTransientOperator produce(boolean downTransform);
}
