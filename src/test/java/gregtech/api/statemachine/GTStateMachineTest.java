package gregtech.api.statemachine;

import net.minecraft.nbt.NBTTagCompound;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

class GTStateMachineTest {

    private static GTStateMachineOperator incrementBy(String key, int amount) {
        return data -> data.setInteger(key, data.getInteger(key) + amount);
    }

    @Test
    void linearWalkRunsEveryOperatorInOrderAndStopsAtEndOfGraph() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(incrementBy("value", 1), false, "start")
                .andThenDefault(incrementBy("value", 10), false, "addTen")
                .andThenDefault(incrementBy("value", 100), false, "addHundred");
        GTStateMachine machine = builder.getConstructing();

        NBTTagCompound data = new NBTTagCompound();
        Map<String, Object> transientData = new Object2ObjectOpenHashMap<>();
        GTSMWalkCompletionData result = machine.walk(0, data, transientData, false);

        assertThat(data.getInteger("value"), is(111));
        // ran off the end of the graph (no link registered on the last operator)
        assertThat(result.nextOpID(), is(-1));
    }

    @Test
    void andThenIfRoutesToTheConditionalBranchWhenPredicateMatches() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(data -> data.setInteger("n", 5), false, "start")
                .andThenIf(data -> data.getInteger("n") < 10, "n < 10",
                        data -> data.setString("path", "low"), false, "lowBranch")
                .movePointerBack()
                .andThenDefault(data -> data.setString("path", "high"), false, "highBranch");
        GTStateMachine machine = builder.getConstructing();

        NBTTagCompound data = new NBTTagCompound();
        machine.walk(0, data, new Object2ObjectOpenHashMap<>(), false);

        assertThat(data.getString("path"), is("low"));
    }

    @Test
    void andThenIfFallsBackToTheElseBranchWhenPredicateDoesNotMatch() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(data -> data.setInteger("n", 20), false, "start")
                .andThenIf(data -> data.getInteger("n") < 10, "n < 10",
                        data -> data.setString("path", "low"), false, "lowBranch")
                .movePointerBack()
                .andThenDefault(data -> data.setString("path", "high"), false, "highBranch");
        GTStateMachine machine = builder.getConstructing();

        NBTTagCompound data = new NBTTagCompound();
        machine.walk(0, data, new Object2ObjectOpenHashMap<>(), false);

        assertThat(data.getString("path"), is("high"));
    }

    @Test
    void transientDataSurvivesBetweenConsecutiveTransientOperatorsButIsClearedByANonTransientOne() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator((data, transientData) -> transientData.put("temp", "hot"), false, "writeTemp")
                .andThenDefault(
                        (data, transientData) -> data.setBoolean("sawTemp", transientData.containsKey("temp")),
                        false, "readTemp")
                .andThenDefault(data -> {}, false, "finalize");
        GTStateMachine machine = builder.getConstructing();

        NBTTagCompound data = new NBTTagCompound();
        Map<String, Object> transientData = new Object2ObjectOpenHashMap<>();
        machine.walk(0, data, transientData, false);

        assertThat(data.getBoolean("sawTemp"), is(true));
        // the non-transient "finalize" step at the end of the walk must have cleared it
        assertThat(transientData.entrySet(), empty());
    }

    @Test
    void walkRespectsStepLimit() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(incrementBy("value", 1), false, "op0");
        // link op0 back to itself, to make an infinite loop without a step limit
        builder.getConstructing().modifyLink(0, l -> l.elseLink(0));
        GTStateMachine machine = builder.getConstructing();

        NBTTagCompound data = new NBTTagCompound();
        GTSMWalkCompletionData result = machine.walk(0, data, new Object2ObjectOpenHashMap<>(), 5, false);

        assertThat(data.getInteger("value"), is(5));
        // still pointing at op0, ready to continue on the next walk call
        assertThat(result.nextOpID(), is(0));
    }

    @Test
    void walkStopsBeforeAnAsyncCompatibleOperatorWhenRequested() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(incrementBy("value", 1), false, "sync")
                .andThenDefault(incrementBy("value", 100), true, "async");
        GTStateMachine machine = builder.getConstructing();

        NBTTagCompound data = new NBTTagCompound();
        GTSMWalkCompletionData result = machine.walk(0, data, new Object2ObjectOpenHashMap<>(), true);

        assertThat(data.getInteger("value"), is(1));
        assertThat(result.nextOpID(), is(1));
    }

    @Test
    void walkInvokesTraceSinkWithDebugNamesInOrder() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(GTStateMachineOperator.emptyOp(), false, "first")
                .andThenDefault(GTStateMachineOperator.emptyOp(), false, "second")
                .andThenDefault(GTStateMachineOperator.emptyOp(), false, "third");
        GTStateMachine machine = builder.getConstructing();

        List<String> trace = new ArrayList<>();
        machine.walk(0, new NBTTagCompound(), new Object2ObjectOpenHashMap<>(), false, trace::add);

        assertThat(trace, contains("first", "second", "third"));
    }

    @Test
    void dumpGraphIncludesDebugNamesFlagsAndConditionDescriptions() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperatorTransient(GTStateMachineOperator.emptyOp(), true, "checkStatus")
                .andThenIf(data -> true, "status is ready", GTStateMachineOperator.emptyOp(), false, "proceed")
                .movePointerBack()
                .andThenDefault(GTStateMachineOperator.emptyOp(), false, "wait");
        GTStateMachine machine = builder.getConstructing();

        String dump = machine.dumpGraph();

        assertThat(dump, containsString("checkStatus"));
        assertThat(dump, containsString("[transient]"));
        assertThat(dump, containsString("[async]"));
        assertThat(dump, containsString("status is ready"));
        assertThat(dump, containsString("proceed"));
        assertThat(dump, containsString("wait"));
    }

    @Test
    void copyProducesAnIndependentGraphThatCanBeModifiedWithoutAffectingTheOriginal() {
        GTStateMachineBuilder builder = new GTStateMachineBuilder()
                .newOperator(incrementBy("value", 1), false, "start");
        GTStateMachine original = builder.getConstructing();

        GTStateMachineBuilder copyBuilder = GTStateMachineBuilder.copy(builder);
        copyBuilder.andThenDefault(incrementBy("value", 100), false, "onlyInCopy");

        assertThat(original.operatorCount(), is(1));
        assertThat(copyBuilder.getConstructing().operatorCount(), is(2));
    }
}
