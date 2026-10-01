package gregtech.api.statemachine;

import org.jetbrains.annotations.NotNull;

/**
 * Internal bookkeeping for a single registered operator: which operator it is (transient or not), whether it's
 * async-compatible, its debug name, and its current {@link GTStateMachineLink}.
 */
final class OperatorHolder {

    private final @NotNull Object operator;
    private final boolean isTransient;
    private final boolean asyncCompatible;
    private final @NotNull String debugName;
    private @NotNull GTStateMachineLink link = GTStateMachineLink.UNKNOWN_LINK;

    OperatorHolder(@NotNull GTStateMachineOperator operator, boolean asyncCompatible, @NotNull String debugName) {
        this.operator = operator;
        this.asyncCompatible = asyncCompatible;
        this.isTransient = false;
        this.debugName = debugName;
    }

    OperatorHolder(@NotNull GTStateMachineTransientOperator operator, boolean asyncCompatible,
                   @NotNull String debugName) {
        this.operator = operator;
        this.asyncCompatible = asyncCompatible;
        this.isTransient = true;
        this.debugName = debugName;
    }

    boolean isTransient() {
        return isTransient;
    }

    boolean isAsyncCompatible() {
        return asyncCompatible;
    }

    @NotNull
    String getDebugName() {
        return debugName;
    }

    void setLink(@NotNull GTStateMachineLink link) {
        this.link = link;
    }

    @NotNull
    GTStateMachineLink getLink() {
        return link;
    }

    @NotNull
    GTStateMachineOperator getOperator() {
        return (GTStateMachineOperator) operator;
    }

    @NotNull
    GTStateMachineTransientOperator getOperatorTransient() {
        return (GTStateMachineTransientOperator) operator;
    }

    @NotNull
    OperatorHolder copy() {
        OperatorHolder holder;
        if (isTransient()) {
            holder = new OperatorHolder(getOperatorTransient(), asyncCompatible, debugName);
        } else {
            holder = new OperatorHolder(getOperator(), asyncCompatible, debugName);
        }
        holder.setLink(getLink());
        return holder;
    }
}
