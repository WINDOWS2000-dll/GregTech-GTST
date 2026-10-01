package gregtech.api.statemachine;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class GTStateMachineBuilderTest {

    @Test
    void setPointerRejectsANegativePointer() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(GTStateMachineOperator.emptyOp(), false, "start");
        int validPointer = builder.getPointer();

        builder.setPointer(-1);

        assertThat(builder.getPointer(), is(validPointer));
    }

    @Test
    void setPointerRejectsAPointerAtOrBeyondOperatorCount() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(GTStateMachineOperator.emptyOp(), false, "start");
        int validPointer = builder.getPointer();

        builder.setPointer(builder.getConstructing().operatorCount());

        assertThat(builder.getPointer(), is(validPointer));
    }

    @Test
    void setPointerAcceptsAnInRangePointer() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(GTStateMachineOperator.emptyOp(), false, "first")
                .newOperator(GTStateMachineOperator.emptyOp(), false, "second");
        int secondPointer = builder.getPointer();

        builder.setPointer(0);
        assertThat(builder.getPointer(), is(0));

        builder.setPointer(secondPointer);
        assertThat(builder.getPointer(), is(secondPointer));
    }
}
