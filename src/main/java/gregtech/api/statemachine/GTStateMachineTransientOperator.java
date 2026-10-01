package gregtech.api.statemachine;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;

/**
 * A {@link GTStateMachine} operator that also has access to transient, non-serializable runtime data (e.g. matched
 * {@code Recipe} instances that shouldn't be written to NBT). Transient data is cleared automatically whenever a
 * non-transient {@link GTStateMachineOperator} is executed, so it should only be relied upon across a run of
 * consecutive transient operators.
 */
@FunctionalInterface
public interface GTStateMachineTransientOperator {

    void operate(NBTTagCompound data, Map<String, Object> transientData);

    /**
     * @return a transient operator that does nothing.
     */
    static GTStateMachineTransientOperator emptyOp() {
        return (data, transientData) -> {};
    }

    /**
     * Wraps a plain (non-transient) operator so it can be registered as transient. Used internally when a caller
     * asks to register a {@link GTStateMachineOperator} as transient.
     */
    static GTStateMachineTransientOperator of(GTStateMachineOperator operator) {
        return (data, transientData) -> operator.operate(data);
    }
}
