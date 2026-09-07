package gregtech.api.recipes.logic.statemachine.lookup;

import com.github.bsideup.jabel.Desugar;

/**
 * The result of {@link RecipeOverclockOperator}: everything {@link RecipeRunBuildOperator} needs to construct a
 * {@code StandardRecipeRun}, beyond what the {@code RecipeView} itself already provides.
 *
 * @param overclocks       the number of overclocks applied.
 * @param duration         the post-overclock duration, in ticks.
 * @param requiredVoltage  the post-overclock required voltage. Always positive; see
 *                         {@link RecipeOverclockOperator}'s JavaDoc for how generating recipes (negative EU/t) are
 *                         handled.
 * @param requiredAmperage the required amperage, i.e. {@code RecipeView#getActualAmperage()} &mdash; unaffected by
 *                         overclocking (see {@link RecipeOverclockOperator}'s JavaDoc), just threaded through.
 */
@Desugar
public record OverclockOutcome(int overclocks, double duration, long requiredVoltage, long requiredAmperage) {}
