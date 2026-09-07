package gregtech.api.statemachine;

import gregtech.api.util.GTLog;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.INBTSerializable;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Drives a single instance's progress through a {@link GTStateMachine}: holds the current position and serializable
 * data, handles (de)serialization, and exposes a change-listener hook so owners can react to state changes (e.g. to
 * sync to the client).
 * <p>
 * Supports opt-in execution tracing for the "trace this machine" dev tool: when {@link #setTraceEnabled(boolean)} is
 * turned on (typically by an owner reacting to the trace-selection item picking out this specific machine instance),
 * every operator this worker's walks pass through is logged to {@link GTStateMachineTraceLog}. Tracing is off by
 * default and costs nothing beyond a boolean check when disabled.
 */
public class GTStateMachineStandardWorker implements INBTSerializable<NBTTagCompound> {

    protected final GTStateMachine machine;

    private static final NBTTagCompound EMPTY = new NBTTagCompound();
    protected final NBTTagCompound defaultTag;
    protected boolean logicEnabled = true;
    protected NBTTagCompound logicData = new NBTTagCompound();
    protected int logicPosition;
    protected NBTTagCompound logicDataSafe = new NBTTagCompound();
    protected int logicPositionSafe;
    protected final Map<String, Object> logicTransientData = new Object2ObjectOpenHashMap<>();

    protected @Nullable CompletableFuture<GTSMWalkCompletionData> trackedFuture;

    protected final List<Consumer<GTStateMachineStandardWorker>> changeListeners = new ObjectArrayList<>();

    /**
     * Whether this worker's walks should be logged to {@link GTStateMachineTraceLog}. Intended to be toggled by the
     * owning machine when it is selected/deselected by the trace tool, not set once at construction time.
     */
    protected boolean traceEnabled = false;

    /**
     * A human-readable identifier for this worker instance (e.g. the owning machine's position and name), prefixed
     * onto trace log lines so traces from multiple selected machines can be told apart. Only used when
     * {@link #traceEnabled} is true.
     */
    protected @Nullable String traceLabel;

    public GTStateMachineStandardWorker(GTStateMachine machine) {
        this(machine, EMPTY);
    }

    public GTStateMachineStandardWorker(GTStateMachine machine, NBTTagCompound defaultTag) {
        this.machine = machine;
        this.defaultTag = defaultTag;
    }

    public void clear() {
        abortAsyncWalk();
        logicData = defaultTag.copy();
        logicDataSafe = defaultTag.copy();
        logicPosition = -1;
        logicPositionSafe = -1;
        logicTransientData.clear();
        pingChangeListeners();
    }

    public void walk(boolean stopOnAsync) {
        if (!hasAsyncWalkCompleted()) {
            GTLog.logger.warn("Attempted to walk a GTStateMachineStandardWorker " +
                    "before its dispatched async worker completed.");
            return;
        }
        handleCompletedWalk(
                machine.walk(logicPosition, logicData, logicTransientData, stopOnAsync, traceSinkOrNull()));
    }

    /**
     * As {@link #walk(boolean)}, but async-dispatchable operators encountered along the way are executed offthread
     * instead of stopping the walk. Note that the offthread portion of the walk is never traced (see
     * {@link GTStateMachine#dispatchAsync}), even if {@link #traceEnabled} is true.
     */
    public void dispatchAsyncWalk() {
        if (!hasAsyncWalkCompleted()) {
            GTLog.logger.warn("Attempted to dispatch a second async worker for a GTStateMachineStandardWorker " +
                    "before its dispatched async worker completed.");
            return;
        }
        trackedFuture = machine.dispatchAsync(logicPosition, logicData, logicTransientData);
    }

    public void abortAsyncWalk() {
        if (trackedFuture != null) {
            trackedFuture.cancel(true);
            trackedFuture = null;
        }
    }

    public boolean hasAsyncWalkCompleted() {
        if (trackedFuture == null) return true;
        if (trackedFuture.isDone()) {
            handleCompletedWalk(trackedFuture.join());
            trackedFuture = null;
            return true;
        }
        return false;
    }

    protected void handleCompletedWalk(GTSMWalkCompletionData completionData) {
        logicPosition = completionData.nextOpID();
        if (completionData.serializableTag() != null) {
            logicPositionSafe = completionData.nextOpAfterLastSerializable();
            logicDataSafe = completionData.serializableTag();
            pingChangeListeners();
        }
    }

    public void registerChangeListener(Consumer<GTStateMachineStandardWorker> listener) {
        changeListeners.add(listener);
    }

    protected void pingChangeListeners() {
        changeListeners.forEach(l -> l.accept(this));
    }

    public boolean isLogicEnabled() {
        return logicEnabled;
    }

    public void setLogicEnabled(boolean logicEnabled) {
        if (logicEnabled != this.logicEnabled) {
            this.logicEnabled = logicEnabled;
            pingChangeListeners();
        }
    }

    public NBTTagCompound logicData() {
        return logicDataSafe;
    }

    public int logicPosition() {
        return logicPositionSafe;
    }

    public void setPosition(int logicPosition) {
        if (!hasAsyncWalkCompleted()) {
            GTLog.logger.warn("Attempted to change the position of a GTStateMachineStandardWorker " +
                    "before its dispatched async worker completed.");
            return;
        }
        this.logicPosition = logicPosition;
        this.logicPositionSafe = logicPosition;
        this.logicDataSafe = this.logicData;
        this.logicTransientData.clear();
        pingChangeListeners();
    }

    public Map<String, Object> getLogicTransientData() {
        return logicTransientData;
    }

    /**
     * @return whether this worker's walks are currently being logged to {@link GTStateMachineTraceLog}.
     */
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    /**
     * Enables or disables execution tracing for this worker, without changing its label. Prefer
     * {@link #setTraceEnabled(boolean, String)} when enabling tracing so the resulting log lines can be attributed
     * to this worker's owner.
     */
    public void setTraceEnabled(boolean traceEnabled) {
        this.traceEnabled = traceEnabled;
    }

    /**
     * Enables or disables execution tracing for this worker, tagging any resulting log lines with {@code label}
     * (e.g. the owning machine's position and name).
     */
    public void setTraceEnabled(boolean traceEnabled, @Nullable String label) {
        this.traceEnabled = traceEnabled;
        this.traceLabel = label;
    }

    @Nullable
    private Consumer<String> traceSinkOrNull() {
        if (!traceEnabled) return null;
        String label = traceLabel != null ? traceLabel : "?";
        return debugName -> GTStateMachineTraceLog.logger.info("[{}] -> {}", label, debugName);
    }

    @Override
    public NBTTagCompound serializeNBT() {
        // check in on whether our async walker is done, and we can serialize its results too.
        hasAsyncWalkCompleted();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Enabled", logicEnabled);
        tag.setTag("Data", logicDataSafe);
        tag.setInteger("Position", logicPositionSafe);
        return tag;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        abortAsyncWalk();
        logicTransientData.clear();
        logicData = nbt.hasKey("Data") ? nbt.getCompoundTag("Data") : defaultTag.copy();
        logicPosition = nbt.hasKey("Position") ? nbt.getInteger("Position") : -1;
        logicEnabled = nbt.getBoolean("Enabled");
        logicDataSafe = logicData.copy();
        logicPositionSafe = logicPosition;
        pingChangeListeners();
    }
}
