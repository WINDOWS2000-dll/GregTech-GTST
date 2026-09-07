package gregtech.api.statemachine;

import net.minecraft.nbt.NBTTagCompound;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A graph of {@link GTStateMachineOperator}s (steps) connected by {@link GTStateMachineLink}s (branching rules for
 * "what runs next"), identified by integer operator IDs.
 * <p>
 * Construct one via {@link GTStateMachineBuilder} rather than registering operators directly; the builder is
 * responsible for enforcing that every operator is registered with a debug name (see
 * {@link #registerOperator(GTStateMachineOperator, boolean, String)}), which this class relies on for
 * {@link #dumpGraph()} and for the trace hooks on {@link #walk}.
 */
public final class GTStateMachine {

    private static final Executor executor = Executors.newWorkStealingPool(4);

    private final List<OperatorHolder> operators;

    private GTStateMachine(int c) {
        operators = new ObjectArrayList<>(c);
    }

    @NotNull
    public static GTStateMachine create() {
        return new GTStateMachine(8);
    }

    public int operatorCount() {
        return operators.size();
    }

    /**
     * Registers a non-transient operator under the given debug name.
     *
     * @return the operator ID it was registered under.
     */
    public int registerOperator(@NotNull GTStateMachineOperator operator, boolean asyncCompatible,
                                @NotNull String debugName) {
        operators.add(new OperatorHolder(operator, asyncCompatible, debugName));
        return operators.size() - 1;
    }

    /**
     * Registers a plain operator as transient (see {@link GTStateMachineTransientOperator}), under the given debug
     * name.
     *
     * @return the operator ID it was registered under.
     */
    public int registerOperatorTransient(@NotNull GTStateMachineOperator operator, boolean asyncCompatible,
                                         @NotNull String debugName) {
        return registerOperatorTransient(GTStateMachineTransientOperator.of(operator), asyncCompatible, debugName);
    }

    /**
     * Registers a transient operator under the given debug name.
     *
     * @return the operator ID it was registered under.
     */
    public int registerOperatorTransient(@NotNull GTStateMachineTransientOperator operator, boolean asyncCompatible,
                                         @NotNull String debugName) {
        operators.add(new OperatorHolder(operator, asyncCompatible, debugName));
        return operators.size() - 1;
    }

    public boolean isOperatorTransient(int operatorID) {
        if (operatorID < 0) return false;
        return operators.get(operatorID).isTransient();
    }

    public boolean isAsyncCompatible(int operatorID) {
        if (operatorID < 0) return false;
        return operators.get(operatorID).isAsyncCompatible();
    }

    /**
     * @param operatorID the id of an operator, or a negative "end of graph" sentinel.
     * @return the debug name that operator was registered under, or a placeholder if {@code operatorID} is
     *         negative. Used by {@link #dumpGraph()} and by trace-enabled {@link #walk} calls.
     */
    @NotNull
    public String getDebugName(int operatorID) {
        if (operatorID < 0) return "<end>";
        return operators.get(operatorID).getDebugName();
    }

    /**
     * Sets the link of the state machine operator at the specified ID.
     *
     * @param operatorID the id of the operator whose link should be overwritten.
     * @param link       the link the operator's link should be set to.
     */
    public void setLink(int operatorID, @NotNull GTStateMachineLink link) {
        operators.get(operatorID).setLink(link);
    }

    /**
     * Gets the link of the state machine operator at the specified ID.
     *
     * @param operatorID the id of the operator whose link should be returned.
     * @return the link of the specified operator.
     */
    @NotNull
    public GTStateMachineLink getLink(int operatorID) {
        return operators.get(operatorID).getLink();
    }

    /**
     * Modifies the link of the state machine operator at the specified ID.
     *
     * @param operatorID the id of the operator whose link should be modified.
     * @param link       the modification operator for the operator's link.
     */
    public void modifyLink(int operatorID, @NotNull UnaryOperator<GTStateMachineLink> link) {
        OperatorHolder holder = operators.get(operatorID);
        holder.setLink(link.apply(holder.getLink()));
    }

    /**
     * Operates on the state machine operator at the specified ID.
     *
     * @param operatorID    the id of the operator to operate.
     * @param data          the data to provide to the operator. May be mutated.
     * @param transientData the transient data to provide to the operator, if it is transient.
     *                      If it is not transient, this will be cleared.
     * @return the id of the next operator to operate. -1 is a special case of not knowing what next to operate.
     */
    public int operate(int operatorID, @NotNull NBTTagCompound data, @NotNull Map<String, Object> transientData) {
        if (operatorID < 0) return -1;
        OperatorHolder holder = operators.get(operatorID);
        if (holder.isTransient()) {
            holder.getOperatorTransient().operate(data, transientData);
        } else {
            holder.getOperator().operate(data);
            transientData.clear();
        }
        return holder.getLink().getLink(data);
    }

    /**
     * @see #walk(int, NBTTagCompound, Map, int, boolean, Consumer)
     */
    @NotNull
    public GTSMWalkCompletionData walk(int operatorID, @NotNull NBTTagCompound data,
                                       @NotNull Map<String, Object> transientData, boolean stopOnAsync) {
        return walk(operatorID, data, transientData, 100, stopOnAsync, null);
    }

    /**
     * @see #walk(int, NBTTagCompound, Map, int, boolean, Consumer)
     */
    @NotNull
    public GTSMWalkCompletionData walk(int operatorID, @NotNull NBTTagCompound data,
                                       @NotNull Map<String, Object> transientData, boolean stopOnAsync,
                                       @Nullable Consumer<String> traceSink) {
        return walk(operatorID, data, transientData, 100, stopOnAsync, traceSink);
    }

    /**
     * @see #walk(int, NBTTagCompound, Map, int, boolean, Consumer)
     */
    @NotNull
    public GTSMWalkCompletionData walk(int operatorID, @NotNull NBTTagCompound data,
                                       @NotNull Map<String, Object> transientData, int stepLimit,
                                       boolean stopOnAsync) {
        return walk(operatorID, data, transientData, stepLimit, stopOnAsync, null);
    }

    /**
     * Moves along the state machine until the next state is unknown, the step limit is reached, or (if
     * {@code stopOnAsync} is true) it hits an async-compatible operator.
     *
     * @param operatorID    the operator ID to start at.
     * @param data          the data for the walk.
     * @param transientData the transient data for the walk.
     * @param stepLimit     the maximum number of steps the walk can execute before exiting.
     * @param stopOnAsync   whether the walk should terminate before starting an async-compatible operation.
     * @param traceSink     if non-null, called with the debug name of every operator this walk passes through, in
     *                      order. Intended for the "trace this machine" dev tool (see project design notes); pass
     *                      {@code null} in normal operation to avoid the overhead entirely.
     * @return completion data associated with the walk.
     */
    @NotNull
    public GTSMWalkCompletionData walk(int operatorID, @NotNull NBTTagCompound data,
                                       @NotNull Map<String, Object> transientData, int stepLimit, boolean stopOnAsync,
                                       @Nullable Consumer<String> traceSink) {
        if (operatorID < 0 || (stopOnAsync && isAsyncCompatible(operatorID))) {
            return new GTSMWalkCompletionData(operatorID, data, transientData, -2, null);
        }
        int nextAfterlastSerializable = -2;
        NBTTagCompound lastSerializableNBT = null;
        int count = 0;
        while (count < stepLimit && !(operatorID < 0 || stopOnAsync && isAsyncCompatible(operatorID))) {
            if (traceSink != null) traceSink.accept(getDebugName(operatorID));
            int next = operate(operatorID, data, transientData);
            if (!isOperatorTransient(operatorID) || next < 0) {
                transientData.clear();
                nextAfterlastSerializable = next;
                lastSerializableNBT = data.copy();
            }
            operatorID = next;
            count++;
        }
        return new GTSMWalkCompletionData(operatorID, data, transientData, nextAfterlastSerializable,
                lastSerializableNBT);
    }

    /**
     * Dispatches an offthread worker that progresses along the state machine until it hits an operator that is not
     * async compatible.
     * <p>
     * Trace hooks are not supported for offthread walks; trace the machine's synchronous progress instead.
     *
     * @param operatorID    the operator ID to start at.
     * @param data          the data for the offthread execution. Should not be interacted with until the worker exits!
     * @param transientData the transient data for the offthread execution. Should not be interacted with until the
     *                      worker exits!
     * @return a completable future associated with the worker. When complete, information related to the work will be
     *         provided.
     */
    @NotNull
    public CompletableFuture<GTSMWalkCompletionData> dispatchAsync(int operatorID, @NotNull NBTTagCompound data,
                                                                    @NotNull Map<String, Object> transientData) {
        return dispatchAsync(operatorID, data, transientData, 100);
    }

    /**
     * Dispatches an offthread worker that progresses along the state machine until it hits an operator that is not
     * async compatible.
     *
     * @param operatorID    the operator ID to start at.
     * @param data          the data for the offthread execution. Should not be interacted with until the worker exits!
     * @param transientData the transient data for the offthread execution. Should not be interacted with until the
     *                      worker exits!
     * @param stepLimit     the maximum number of steps the worker can execute before exiting.
     * @return a completable future associated with the worker. When complete, information related to the work will be
     *         provided.
     */
    @NotNull
    public CompletableFuture<GTSMWalkCompletionData> dispatchAsync(int operatorID, @NotNull NBTTagCompound data,
                                                                    @NotNull Map<String, Object> transientData,
                                                                    int stepLimit) {
        if (!isAsyncCompatible(operatorID)) {
            return CompletableFuture
                    .completedFuture(new GTSMWalkCompletionData(operatorID, data, transientData, -2, null));
        }
        return CompletableFuture.supplyAsync(() -> {
            int operator = operatorID;
            int nextAfterlastSerializable = -2;
            NBTTagCompound lastSerializableNBT = null;
            int count = 0;
            while (count < stepLimit && isAsyncCompatible(operator)) {
                int next = operate(operator, data, transientData);
                if (!isOperatorTransient(operator) || next < 0) {
                    transientData.clear();
                    nextAfterlastSerializable = next;
                    lastSerializableNBT = data.copy();
                }
                operator = next;
                count++;
            }
            return new GTSMWalkCompletionData(operator, data, transientData, nextAfterlastSerializable,
                    lastSerializableNBT);
        }, executor);
    }

    /**
     * @return a human-readable dump of every operator and its outgoing links, suitable for writing to a log file
     *         from a debug command. See project design notes for the intended `/gtst dumpstatemachine`-style
     *         command that surfaces this.
     */
    @NotNull
    public String dumpGraph() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < operators.size(); i++) {
            OperatorHolder holder = operators.get(i);
            sb.append('#').append(i).append(": ").append(holder.getDebugName());
            if (holder.isTransient()) sb.append(" [transient]");
            if (holder.isAsyncCompatible()) sb.append(" [async]");
            sb.append('\n');
            sb.append(holder.getLink().describe(this::getDebugName));
        }
        return sb.toString();
    }

    @NotNull
    public GTStateMachine copy() {
        GTStateMachine create = new GTStateMachine(this.operatorCount());
        for (OperatorHolder holder : this.operators) {
            create.operators.add(holder.copy());
        }
        return create;
    }
}
