package gregtech.api.statemachine;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class GTStateMachineStandardWorkerTest {

    private static GTStateMachine incrementingMachine() {
        return new GTStateMachineBuilder()
                .newOperator(data -> data.setInteger("value", data.getInteger("value") + 1), false, "increment")
                .getConstructing();
    }

    @Test
    void walkAdvancesLogicDataAndPositionAndStopsAtEndOfGraph() {
        GTStateMachineStandardWorker worker = new GTStateMachineStandardWorker(incrementingMachine());
        worker.setPosition(0);

        worker.walk(false);

        assertThat(worker.logicData().getInteger("value"), is(1));
        assertThat(worker.logicPosition(), is(-1));
    }

    @Test
    void serializeAndDeserializeRoundTripsPositionDataAndEnabledFlag() {
        GTStateMachine machine = incrementingMachine();
        GTStateMachineStandardWorker worker = new GTStateMachineStandardWorker(machine);
        worker.setPosition(0);
        worker.walk(false);
        worker.setLogicEnabled(false);

        NBTTagCompound saved = worker.serializeNBT();

        GTStateMachineStandardWorker restored = new GTStateMachineStandardWorker(machine);
        restored.deserializeNBT(saved);

        assertThat(restored.logicData().getInteger("value"), is(1));
        assertThat(restored.logicPosition(), is(-1));
        assertThat(restored.isLogicEnabled(), is(false));
    }

    @Test
    void clearResetsToDefaultTagAndUnstartedPosition() {
        NBTTagCompound defaultTag = new NBTTagCompound();
        defaultTag.setInteger("value", 42);
        GTStateMachineStandardWorker worker = new GTStateMachineStandardWorker(incrementingMachine(), defaultTag);
        worker.setPosition(0);
        worker.walk(false);

        worker.clear();

        assertThat(worker.logicData().getInteger("value"), is(42));
        assertThat(worker.logicPosition(), is(-1));
    }

    @Test
    void changeListenerFiresOnlyWhenLogicEnabledActuallyChanges() {
        GTStateMachineStandardWorker worker = new GTStateMachineStandardWorker(incrementingMachine());
        List<GTStateMachineStandardWorker> notifications = new ArrayList<>();
        worker.registerChangeListener(notifications::add);

        worker.setLogicEnabled(true); // already true by default, should not notify
        assertThat(notifications.size(), is(0));

        worker.setLogicEnabled(false);
        assertThat(notifications.size(), is(1));
    }

    @Test
    void enablingTraceDoesNotAlterWalkResults() {
        GTStateMachineStandardWorker traced = new GTStateMachineStandardWorker(incrementingMachine());
        traced.setPosition(0);
        traced.setTraceEnabled(true, "test-machine");
        traced.walk(false);

        assertThat(traced.isTraceEnabled(), is(true));
        assertThat(traced.logicData().getInteger("value"), is(1));
        assertThat(traced.logicPosition(), is(-1));
    }

    /** Polls {@link GTStateMachineStandardWorker#hasAsyncWalkCompleted()} until it returns {@code true}. */
    private static void awaitAsyncWalk(GTStateMachineStandardWorker worker) {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (!worker.hasAsyncWalkCompleted()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Async walk did not complete within the timeout");
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    @Test
    void dispatchAsyncWalkRunsTheAsyncPortionAndLeavesTheRestForASynchronousWalk() {
        GTStateMachine machine = new GTStateMachineBuilder()
                .newOperator(data -> data.setInteger("value", data.getInteger("value") + 1), true, "asyncStep")
                .andThenDefault(data -> data.setInteger("value", data.getInteger("value") + 100), false, "syncStep")
                .getConstructing();

        GTStateMachineStandardWorker worker = new GTStateMachineStandardWorker(machine);
        worker.setPosition(0);
        worker.dispatchAsyncWalk();
        awaitAsyncWalk(worker);

        assertThat(worker.logicData().getInteger("value"), is(1));
        assertThat(worker.logicPosition(), is(1));

        // the async portion left off exactly where a normal synchronous walk can pick up from.
        worker.walk(false);
        assertThat(worker.logicData().getInteger("value"), is(101));
    }

    @Test
    void abortAsyncWalkClearsTheTrackedFutureSoTheWorkerIsImmediatelyAvailableAgain() {
        GTStateMachine machine = new GTStateMachineBuilder()
                .newOperator(data -> data.setInteger("value", data.getInteger("value") + 1), true, "asyncStep")
                .getConstructing();

        GTStateMachineStandardWorker worker = new GTStateMachineStandardWorker(machine);
        worker.setPosition(0);
        worker.dispatchAsyncWalk();
        worker.abortAsyncWalk();

        // abortAsyncWalk discards the tracked future outright (rather than waiting for/consuming its result), so
        // this must read as "no async walk pending" immediately, regardless of whether the dispatched work had
        // already finished.
        assertThat(worker.hasAsyncWalkCompleted(), is(true));
    }
}
