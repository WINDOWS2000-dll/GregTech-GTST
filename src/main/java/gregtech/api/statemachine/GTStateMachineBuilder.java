package gregtech.api.statemachine;

import net.minecraft.nbt.NBTTagCompound;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.function.Predicate;

/**
 * A DSL for constructing (or modifying a copy of) a {@link GTStateMachine} graph: register an operator, then chain
 * {@code andThen...} calls to link it to the next one, moving an internal "current position" pointer forward as you
 * go. {@link #movePointerBack()} lets you return to an earlier point to add a second branch from it.
 * <p>
 * Every method that registers a new operator requires a {@code debugName} argument &mdash; there is no
 * name-less overload. This is enforced deliberately: operators are frequently implemented as lambdas, which have no
 * meaningful name on their own, and {@link GTStateMachine#dumpGraph()} / the execution-trace dev tool are only
 * useful if every operator in the graph can be identified. See project design notes for the rationale.
 * <p>
 * Methods that add a conditional branch (the {@code andThenIf}/{@code andThenToIf} family) additionally accept an
 * optional {@code conditionDescription}; unlike {@code debugName}, this is recommended but not required.
 */
public class GTStateMachineBuilder {

    private final @NotNull GTStateMachine constructing;
    private final IntList pointerStack;

    @NotNull
    public static GTStateMachineBuilder copy(@NotNull GTStateMachineBuilder builder) {
        return new GTStateMachineBuilder(builder.getConstructing().copy(), new IntArrayList(builder.pointerStack));
    }

    @NotNull
    public static GTStateMachineBuilder copy(@NotNull GTStateMachine machine) {
        return new GTStateMachineBuilder(machine.copy());
    }

    @NotNull
    public static GTStateMachineBuilder modify(@NotNull GTStateMachine machine) {
        return new GTStateMachineBuilder(machine);
    }

    public GTStateMachineBuilder() {
        constructing = GTStateMachine.create();
        pointerStack = new IntArrayList();
    }

    private GTStateMachineBuilder(@NotNull GTStateMachine toModify) {
        constructing = toModify;
        pointerStack = new IntArrayList();
    }

    private GTStateMachineBuilder(@NotNull GTStateMachine toModify, IntList pointerStack) {
        constructing = toModify;
        this.pointerStack = pointerStack;
    }

    @NotNull
    public GTStateMachine getConstructing() {
        return constructing;
    }

    public GTStateMachineBuilder setPointer(@Range(from = 0, to = Integer.MAX_VALUE) int pointer) {
        if (pointer < constructing.operatorCount()) {
            this.pointerStack.add(pointer);
        }
        return this;
    }

    public int getPointer() {
        if (this.pointerStack.isEmpty()) {
            return -1;
        }
        return this.pointerStack.get(this.pointerStack.size() - 1);
    }

    public GTStateMachineBuilder movePointerBack() {
        if (!this.pointerStack.isEmpty()) {
            this.pointerStack.remove(this.pointerStack.size() - 1);
        }
        return this;
    }

    // ============================================================================================================
    // Register a new operator without linking it from the current pointer.
    // ============================================================================================================

    public GTStateMachineBuilder newOperator(@NotNull GTStateMachineOperator operator, boolean async,
                                             @NotNull String debugName) {
        return setPointer(constructing.registerOperator(operator, async, debugName));
    }

    public GTStateMachineBuilder newOperatorTransient(@NotNull GTStateMachineOperator operator, boolean async,
                                                      @NotNull String debugName) {
        return setPointer(constructing.registerOperatorTransient(operator, async, debugName));
    }

    public GTStateMachineBuilder newOperator(@NotNull GTStateMachineTransientOperator operator, boolean async,
                                             @NotNull String debugName) {
        return setPointer(constructing.registerOperatorTransient(operator, async, debugName));
    }

    // ============================================================================================================
    // Register a new operator, and link it as the default ("else") destination from the current pointer.
    // ============================================================================================================

    public GTStateMachineBuilder andThenDefault(@NotNull GTStateMachineOperator operator, boolean async,
                                                @NotNull String debugName) {
        int id = constructing.registerOperator(operator, async, debugName);
        constructing.modifyLink(getPointer(), l -> l.elseLink(id));
        return setPointer(id);
    }

    public GTStateMachineBuilder andThenDefaultTransient(@NotNull GTStateMachineOperator operator, boolean async,
                                                         @NotNull String debugName) {
        int id = constructing.registerOperatorTransient(operator, async, debugName);
        constructing.modifyLink(getPointer(), l -> l.elseLink(id));
        return setPointer(id);
    }

    public GTStateMachineBuilder andThenDefault(@NotNull GTStateMachineTransientOperator operator, boolean async,
                                                @NotNull String debugName) {
        int id = constructing.registerOperatorTransient(operator, async, debugName);
        constructing.modifyLink(getPointer(), l -> l.elseLink(id));
        return setPointer(id);
    }

    // ============================================================================================================
    // Register a new operator, and link it as a conditional destination from the current pointer.
    // conditionDescription is recommended (shown in GTStateMachine.dumpGraph()) but optional.
    // ============================================================================================================

    public GTStateMachineBuilder andThenIf(@NotNull Predicate<NBTTagCompound> predicate,
                                           @NotNull GTStateMachineOperator operator, boolean async,
                                           @NotNull String debugName) {
        return andThenIf(predicate, null, operator, async, debugName);
    }

    public GTStateMachineBuilder andThenIf(@NotNull Predicate<NBTTagCompound> predicate,
                                           @Nullable String conditionDescription,
                                           @NotNull GTStateMachineOperator operator, boolean async,
                                           @NotNull String debugName) {
        int id = constructing.registerOperator(operator, async, debugName);
        constructing.modifyLink(getPointer(), l -> l.elseIf(predicate, conditionDescription, id));
        return setPointer(id);
    }

    public GTStateMachineBuilder andThenIfTransient(@NotNull Predicate<NBTTagCompound> predicate,
                                                    @NotNull GTStateMachineOperator operator, boolean async,
                                                    @NotNull String debugName) {
        return andThenIfTransient(predicate, null, operator, async, debugName);
    }

    public GTStateMachineBuilder andThenIfTransient(@NotNull Predicate<NBTTagCompound> predicate,
                                                    @Nullable String conditionDescription,
                                                    @NotNull GTStateMachineOperator operator, boolean async,
                                                    @NotNull String debugName) {
        int id = constructing.registerOperatorTransient(operator, async, debugName);
        constructing.modifyLink(getPointer(), l -> l.elseIf(predicate, conditionDescription, id));
        return setPointer(id);
    }

    public GTStateMachineBuilder andThenIf(@NotNull Predicate<NBTTagCompound> predicate,
                                           @NotNull GTStateMachineTransientOperator operator, boolean async,
                                           @NotNull String debugName) {
        return andThenIf(predicate, null, operator, async, debugName);
    }

    public GTStateMachineBuilder andThenIf(@NotNull Predicate<NBTTagCompound> predicate,
                                           @Nullable String conditionDescription,
                                           @NotNull GTStateMachineTransientOperator operator, boolean async,
                                           @NotNull String debugName) {
        int id = constructing.registerOperatorTransient(operator, async, debugName);
        constructing.modifyLink(getPointer(), l -> l.elseIf(predicate, conditionDescription, id));
        return setPointer(id);
    }

    // ============================================================================================================
    // Link the current pointer to an *already-registered* operator (no new registration, so no debugName needed).
    // ============================================================================================================

    public GTStateMachineBuilder andThenToDefault(int id) {
        constructing.modifyLink(getPointer(), l -> l.elseLink(id));
        return setPointer(id);
    }

    public GTStateMachineBuilder andThenToIf(@NotNull Predicate<NBTTagCompound> predicate, int id) {
        return andThenToIf(predicate, null, id);
    }

    public GTStateMachineBuilder andThenToIf(@NotNull Predicate<NBTTagCompound> predicate,
                                             @Nullable String conditionDescription, int id) {
        constructing.modifyLink(getPointer(), l -> l.elseIf(predicate, conditionDescription, id));
        return setPointer(id);
    }
}
