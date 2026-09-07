package gregtech.api.statemachine;

import net.minecraft.nbt.NBTTagCompound;

/**
 * A single step in a {@link GTStateMachine}. Operators only have access to serializable data, and any changes they
 * make to it are expected to be persisted after execution.
 * <p>
 * Operators are registered onto a {@link GTStateMachine} via {@link GTStateMachineBuilder}, which always requires a
 * debug name at registration time (see {@link GTStateMachineBuilder#newOperator(GTStateMachineOperator, boolean,
 * String)}). There is deliberately no {@code getDebugName()} default on this interface: since operators are
 * frequently implemented as lambdas (which have no meaningful class name), the name is tracked externally by the
 * state machine instead of relying on the operator to supply one.
 *
 * @see GTStateMachineTransientOperator for operators that also need access to non-serializable runtime data.
 */
@FunctionalInterface
public interface GTStateMachineOperator {

    void operate(NBTTagCompound data);

    /**
     * @return an operator that does nothing. Useful as a placeholder/junction point in a graph.
     */
    static GTStateMachineOperator emptyOp() {
        return data -> {};
    }
}
