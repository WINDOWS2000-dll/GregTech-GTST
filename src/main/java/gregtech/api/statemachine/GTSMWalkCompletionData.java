package gregtech.api.statemachine;

import net.minecraft.nbt.NBTTagCompound;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The result of a {@link GTStateMachine#walk} (or {@link GTStateMachine#dispatchAsync}) call.
 *
 * @param nextOpID                    the operator ID the walk stopped at (may be a non-transient operator awaiting
 *                                    its turn, an async-compatible operator if the walk stopped for that reason, or
 *                                    -1/-2 if the graph ran out of known links).
 * @param data                        the (possibly mutated) serializable data at the point the walk stopped.
 * @param transientData               the (possibly mutated) transient data at the point the walk stopped.
 * @param nextOpAfterLastSerializable the operator ID to resume at using only the data captured in
 *                                    {@code serializableTag}, ignoring any transient-only progress made since. -2
 *                                    if no non-transient operator was ever completed during this walk.
 * @param serializableTag             a snapshot of {@code data} taken right after the last non-transient operator
 *                                    completed during this walk, or {@code null} if none completed (in which case
 *                                    {@code nextOpAfterLastSerializable} is always -2). Callers resuming from a
 *                                    persisted state (e.g. after a crash mid-walk) should use this snapshot rather
 *                                    than {@code data}, since {@code data} may reflect transient-only progress that
 *                                    was never meant to be saved.
 */
@Desugar
public record GTSMWalkCompletionData(int nextOpID, NBTTagCompound data, Map<String, Object> transientData,
                                     int nextOpAfterLastSerializable, @Nullable NBTTagCompound serializableTag) {}
