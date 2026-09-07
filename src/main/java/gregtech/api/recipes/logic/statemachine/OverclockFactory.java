package gregtech.api.recipes.logic.statemachine;

import gregtech.api.statemachine.GTStateMachineTransientOperator;

import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;

/**
 * Factory for the operator that performs overclock calculation. This interface's own shape does not depend on
 * {@code RecipePowerConfig.properties} &mdash; a factory just needs the usual scalar knobs to produce an operator,
 * and only that operator's internals (see {@code RecipeOverclockOperator}) actually read the power properties.
 */
@FunctionalInterface
public interface OverclockFactory {

    GTStateMachineTransientOperator produce(double costFactor, double speedFactor, boolean canUpTransform,
                                            @Nullable DoubleSupplier durationDiscount);
}
