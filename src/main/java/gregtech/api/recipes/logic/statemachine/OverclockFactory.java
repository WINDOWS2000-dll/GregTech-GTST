package gregtech.api.recipes.logic.statemachine;

import gregtech.api.statemachine.GTStateMachineTransientOperator;

import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;

/**
 * Factory for the operator that performs overclock calculation. This interface's own shape does not depend on
 * {@code RecipePowerConfig.properties} &mdash; a factory just needs the usual scalar knobs to produce an operator,
 * and only that operator's internals (see {@code RecipeOverclockOperator}) actually read the power properties.
 * <p>
 * <b>Contract a custom factory's operator must honor</b> (checked only by convention, not by the type system --
 * {@code RecipeLookupTrackBuilder} wires whichever operator this produces directly into the same graph slot
 * {@link gregtech.api.recipes.logic.statemachine.lookup.RecipeOverclockOperator} would otherwise occupy, with no
 * adapter in between): on {@code data}, set {@code RecipeOverclockOperator.SUCCESS_KEY} (a {@code boolean}: whether
 * this candidate fits within the available voltage at all); on {@code transientData}, when successful, set
 * {@code RecipeOverclockOperator.RESULT_KEY} to an {@code OverclockOutcome} describing the post-overclock
 * voltage/duration/amperage. Every downstream operator ({@code RecipeRunBuildOperator}, etc.) reads only those two
 * keys, never this class's own {@code produce} parameters again, so a factory that forgets to set either one fails
 * silently (a {@code null}/absent read, not a compile error) rather than throwing where the mistake was made.
 */
@FunctionalInterface
public interface OverclockFactory {

    GTStateMachineTransientOperator produce(double costFactor, double speedFactor, boolean canUpTransform,
                                            @Nullable DoubleSupplier durationDiscount);
}
