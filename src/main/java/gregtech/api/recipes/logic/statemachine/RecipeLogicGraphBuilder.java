package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.logic.statemachine.lookup.RecipeQueueAdmissionOperator;
import gregtech.api.statemachine.GTStateMachine;
import gregtech.api.statemachine.GTStateMachineBuilder;
import gregtech.api.statemachine.GTStateMachineOperator;

import net.minecraft.nbt.NBTTagCompound;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Assembles a machine's complete recipe-driven {@link GTStateMachine} graph &mdash; {@link RecipeLookupTrackBuilder}'s
 * search half and {@link RecipeProgressTrackBuilder}'s progress half, wired into one machine &mdash; and drives it
 * every tick.
 * <p>
 * <b>Why two separate walks per tick instead of one combined walk:</b> the search track and the progress track are
 * deliberately independent walks (see {@link RecipeLookupTrackBuilder}'s JavaDoc on
 * {@code config.hooks.asyncSearchAndSetup}): the search track only ever reads a snapshot and stages candidates into
 * {@link PreparedRecipeQueue} (part of persistent {@code data}, not transient state), so it's safe to run it less
 * often, or off-thread, without the progress track (which must run every tick to advance already-active recipes)
 * ever needing to block on it. {@link #tick} runs both synchronously, one after the other, which is correct for
 * every machine today &mdash; nothing sets {@code asyncSearchAndSetup} yet. An async-driving variant that dispatches
 * the search walk via {@link GTStateMachine#dispatchAsync} is deferred until a machine actually needs it, to avoid
 * speculative complexity; {@link #SEARCH_ROOT}/{@link #PROGRESS_ROOT} are exposed precisely so a caller can drive
 * the two walks differently (e.g. asynchronously) without needing a different graph.
 * <p>
 * Each walk gets its own fresh transient-data map: the two tracks never share transient state with each other.
 */
public final class RecipeLogicGraphBuilder {

    /** The operator ID {@link RecipeLookupTrackBuilder}'s search track is rooted at in {@link #build}'s machine. */
    public static final int SEARCH_ROOT = 0;

    /** The operator ID {@link RecipeProgressTrackBuilder}'s progress track is rooted at in {@link #build}'s machine. */
    public static final int PROGRESS_ROOT = 1;

    private RecipeLogicGraphBuilder() {}

    /**
     * @see #build(RecipeLogicConfig, RecipeQueueAdmissionOperator)
     */
    @NotNull
    public static GTStateMachine build(@NotNull RecipeLogicConfig config) {
        return build(config, new RecipeQueueAdmissionOperator(config));
    }

    /**
     * @param admissionOperator satisfies {@link RecipeProgressTrackBuilder}'s admission contract; pass a custom one
     *                          only if the standard {@link RecipeQueueAdmissionOperator} doesn't fit the machine's
     *                          needs (see its JavaDoc).
     * @return a machine with the search track rooted at {@link #SEARCH_ROOT} and the progress track rooted at
     *         {@link #PROGRESS_ROOT}. Drive it with {@link #tick}, or walk the two roots directly for a custom
     *         driving scheme.
     */
    @NotNull
    public static GTStateMachine build(@NotNull RecipeLogicConfig config,
                                       @NotNull RecipeQueueAdmissionOperator admissionOperator) {
        GTStateMachineBuilder builder = new GTStateMachineBuilder();
        builder.newOperator(GTStateMachineOperator.emptyOp(), false, "searchRoot"); // SEARCH_ROOT
        builder.newOperator(GTStateMachineOperator.emptyOp(), false, "progressRoot"); // PROGRESS_ROOT
        RecipeLookupTrackBuilder.build(builder, SEARCH_ROOT, config);
        RecipeProgressTrackBuilder.build(builder, PROGRESS_ROOT, config, admissionOperator);
        return builder.getConstructing();
    }

    /**
     * @see #tick(GTStateMachine, NBTTagCompound, Consumer)
     */
    public static void tick(@NotNull GTStateMachine machine, @NotNull NBTTagCompound data) {
        tick(machine, data, null);
    }

    /**
     * As {@link #tick(GTStateMachine, NBTTagCompound, Map, Map, Consumer)}, allocating a fresh transient-data map
     * for each walk rather than reusing caller-owned ones. Prefer that overload for a caller (like
     * {@code RecipeWorkable}) that ticks the same machine every tick and can keep the two maps around instead.
     *
     * @param traceSink if non-null, receives every operator's debug name from both walks, in order (the "trace this
     *                  machine" dev tool's hook; see {@link GTStateMachine#walk}). Pass {@code null} in normal
     *                  operation to avoid the overhead entirely.
     */
    public static void tick(@NotNull GTStateMachine machine, @NotNull NBTTagCompound data,
                            @Nullable Consumer<String> traceSink) {
        tick(machine, data, new Object2ObjectOpenHashMap<>(), new Object2ObjectOpenHashMap<>(), traceSink);
    }

    /**
     * Runs one full tick of a machine built by {@link #build}: the search walk (finds and queues candidates), then
     * the progress walk (admits queued candidates and advances/completes already-active ones).
     * <p>
     * <b>{@code searchTransientData}/{@code progressTransientData} are cleared unconditionally on entry</b>, before
     * either walk runs -- a caller that reuses the same two maps across ticks (avoiding a fresh allocation every
     * tick, see {@code RecipeWorkable}'s own fields) does not need to clear them itself first, and a walk cut short
     * by an exception or {@code stepLimit} on one tick can never leak leftover keys into the next. The two maps
     * must be distinct instances: {@link RecipeLookupTrackBuilder}'s and {@link RecipeProgressTrackBuilder}'s
     * tracks are documented as never sharing transient state with each other, and passing the same map for both
     * would silently break that.
     *
     * @param traceSink if non-null, receives every operator's debug name from both walks, in order (the "trace this
     *                  machine" dev tool's hook; see {@link GTStateMachine#walk}). Pass {@code null} in normal
     *                  operation to avoid the overhead entirely.
     */
    public static void tick(@NotNull GTStateMachine machine, @NotNull NBTTagCompound data,
                            @NotNull Map<String, Object> searchTransientData,
                            @NotNull Map<String, Object> progressTransientData,
                            @Nullable Consumer<String> traceSink) {
        searchTransientData.clear();
        progressTransientData.clear();
        machine.walk(SEARCH_ROOT, data, searchTransientData, false, traceSink);
        machine.walk(PROGRESS_ROOT, data, progressTransientData, false, traceSink);
    }
}
