package gregtech.api.recipes.logic.statemachine;

import gregtech.api.statemachine.GTStateMachineTransientOperator;

/**
 * Factory for the operator that limits a matched recipe's parallel count. Defined for the same reason as
 * {@link OverclockFactory}: its shape does not depend on {@code RecipePowerConfig.properties}, only on the plain
 * {@code boolean} it's given.
 */
@FunctionalInterface
public interface ParallelLimitFactory {

    GTStateMachineTransientOperator produce(boolean downTransform);
}
