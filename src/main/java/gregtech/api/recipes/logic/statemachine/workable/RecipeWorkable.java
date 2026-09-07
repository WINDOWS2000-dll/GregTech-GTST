package gregtech.api.recipes.logic.statemachine.workable;

import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IHasRecipeMap;
import gregtech.api.capability.IRecipeLogicInfoProvider;
import gregtech.api.capability.IWorkable;
import gregtech.api.metatileentity.MTETrait;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeEntryEnricher;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeLogicGraphBuilder;
import gregtech.api.recipes.logic.statemachine.RecipeLookup;
import gregtech.api.statemachine.GTStateMachine;
import gregtech.api.statemachine.GTStateMachineTraceLog;
import gregtech.api.util.GTLog;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

/**
 * The recipe-driven {@link MTETrait} backed by {@link RecipeLogicGraphBuilder}/{@link RecipeLogicConfig}. Registered
 * under {@link GregtechDataCodes#RECIPE_WORKABLE_TRAIT}, a distinct key from legacy machines'
 * {@link GregtechDataCodes#ABSTRACT_WORKABLE_TRAIT}: {@code MetaTileEntity#getRecipeLogic()} (which requires
 * exactly one trait class under its own key) simply reports {@code null} for a machine using this trait instead
 * &mdash; see {@link IHasRecipeMap}'s JavaDoc for why only {@code getRecipeMap()} was worth generalizing across
 * both traits.
 * <p>
 * <b>Multiple simultaneous active recipes vs. the single-recipe {@link IWorkable} contract:</b> the underlying
 * engine can run several recipes at once per machine (see {@code RecipeLookupTrackBuilder}'s JavaDoc).
 * {@link IWorkable#getProgress()}/{@link IWorkable#getMaxProgress()} can only ever describe one, so they report
 * {@link ActiveRecipeList}'s first entry &mdash; correct as long as a machine's own {@link RecipeLogicConfig} never
 * actually admits more than one (true of every machine so far). {@link #getActiveRecipeCount()}/
 * {@link #getProgress(int)}/{@link #getMaxProgress(int)} expose every active recipe by index, so a future machine's
 * GUI can render one progress bar per active recipe without this trait's API needing to change again once that day
 * comes.
 * <p>
 * <b>Working-enabled gating:</b> {@link RecipeLogicGraphBuilder#tick} itself has no notion of being disabled (this
 * trait's {@link RecipeLogicGraphBuilder} deliberately stayed synchronous-only, see its JavaDoc); {@link #update()}
 * simply skips calling it while {@link #isWorkingEnabled()} is {@code false}.
 * <p>
 * <b>Client sync:</b> {@link #isActive()}/{@link #isWorkingEnabled()} are derived from server-authoritative state
 * ({@link #data}/{@link #workingEnabled}) with no automatic push to the client. Without an explicit push, a
 * client's copy only ever reflects whatever was last written via NBT (chunk load), so front-overlay rendering
 * (driven directly by these two accessors, without going through a GUI-open {@code PanelSyncManager} the way the
 * progress bar does) would never update in real time. Instead, {@link #clientActive}/{@link #clientWorkingEnabled}
 * cache the last-pushed value, consulted only when {@link net.minecraft.world.World#isRemote} is {@code true};
 * {@link #update()}/{@link #setWorkingEnabled} push a change via {@link #writeCustomData} exactly when the
 * respective value actually transitions, and {@link #writeInitialSyncData}/{@link #receiveInitialSyncData} cover a
 * client that starts observing this machine mid-recipe (e.g. relogging, chunk reload) rather than at a transition.
 */
public class RecipeWorkable extends MTETrait implements IWorkable, IControllable, IHasRecipeMap,
                            IRecipeLogicInfoProvider {

    // Package-private (not private) so RecipeWorkableTest, in the same package, can drive receiveCustomData directly.
    static final int ACTIVE_CHANGED = 0;
    static final int WORKING_ENABLED_CHANGED = 1;

    protected final @NotNull RecipeLogicConfig config;
    protected final @NotNull GTStateMachine machine;
    protected final @NotNull NBTTagCompound data = new NBTTagCompound();
    /**
     * Reported by {@link #getRecipeMap()}; stored directly rather than derived from {@link #config}'s
     * {@code lookup} (which may not even be backed by a single {@link RecipeMap}, e.g. a composite/dynamic lookup).
     * A future machine whose {@link RecipeMap} can change at runtime (e.g. a
     * Processing-Array-style child-machine swap) should override {@link #getRecipeMap()} rather than relying on
     * this fixed field.
     */
    protected final @NotNull RecipeMap<?> recipeMap;

    protected boolean workingEnabled = true;

    /** Client-side cache of {@link #isActive()}'s last value pushed from the server; see this class's JavaDoc. */
    protected boolean clientActive = false;
    /**
     * Client-side cache of {@link #isWorkingEnabled()}'s last value pushed from the server; see this class's JavaDoc.
     */
    protected boolean clientWorkingEnabled = true;

    /**
     * Server-side record of what {@link #isActive()} was last actually reported to the client as (distinct from
     * {@link #clientActive}, which is the client's own received copy and is never touched on the server). See
     * {@link #update()}'s JavaDoc for why this is debounced rather than a plain before/after comparison.
     * Package-private (not private), like {@link #ACTIVE_CHANGED}, so {@code RecipeWorkableTest} can assert on it
     * directly.
     */
    boolean reportedActive = false;
    /**
     * Consecutive ticks {@link #isActive()} has been {@code false} since it was last reported active; see
     * {@link #update()}.
     */
    private int inactiveStreak = 0;

    /**
     * How many consecutive idle ticks {@link #update()} tolerates before actually reporting {@link #isActive()}
     * going false to the client. See {@link #update()}'s JavaDoc for why this exists; 1 tick is enough to coalesce
     * the specific real-machine bug that motivated it without meaningfully delaying a genuinely-idle machine's
     * overlay (at 20 TPS, one extra tick is imperceptible).
     */
    private static final int INACTIVE_DEBOUNCE_TICKS = 1;

    /**
     * Whether this workable's ticks are logged to {@link GTStateMachineTraceLog} (this project's execution-trace
     * dev tool). Toggled server-side by the trace item; never synced to the client
     * (tracing only ever inspects server-authoritative state, and the log itself is a server-side file/console
     * concern). Off by default and costs nothing beyond a boolean check when disabled.
     */
    protected boolean traceEnabled = false;
    /** A human-readable identifier prefixed onto this workable's trace log lines; see {@link #setTraceEnabled}. */
    protected @Nullable String traceLabel;

    /**
     * The most recently admitted {@link Recipe}, for {@link #getPreviousRecipe()} (needed for OpenComputers'
     * {@code gt_recipeLogic} component -- see {@link IRecipeLogicInfoProvider#getPreviousRecipe()}'s own JavaDoc).
     * Deliberately a plain transient field, not
     * NBT under {@link #data}: {@link ActiveRecipeList}'s entries are kept intentionally lightweight (progress/EUt/
     * rolled outputs only, see its own JavaDoc), and everything a caller could want from the full {@link Recipe}
     * (inputs, chanced outputs, their fixed chance/boost values) is static recipe-definition data already reachable
     * through this one reference -- no need to duplicate any of it into persisted state. Consequently this resets to
     * {@code null} on world reload until a recipe is next admitted.
     */
    private @Nullable Recipe previousRecipe;

    public RecipeWorkable(@NotNull MetaTileEntity metaTileEntity, @NotNull RecipeLogicConfig config,
                          @NotNull RecipeMap<?> recipeMap) {
        super(metaTileEntity);
        this.config = config;
        this.recipeMap = recipeMap;
        // Chain onto whatever entryEnricher this machine's own createConfig() may already have set,
        // rather than claiming the single-slot hook outright -- see RecipeEntryEnricher's own JavaDoc for why it's
        // "not a registry", which is exactly the constraint this chaining respects.
        RecipeEntryEnricher existingEnricher = config.hooks.entryEnricher;
        config.hooks.entryEnricher = (recipe, entry) -> {
            this.previousRecipe = recipe;
            if (existingEnricher != null) existingEnricher.enrich(recipe, entry);
        };
        this.machine = RecipeLogicGraphBuilder.build(config);
    }

    /**
     * @return the most recently admitted {@link Recipe}, or {@code null} if none has been admitted yet. See
     *         {@link #previousRecipe}'s own field JavaDoc.
     */
    @Override
    public @Nullable Recipe getPreviousRecipe() {
        return previousRecipe;
    }

    /**
     * <b>Front-overlay flicker on very-short-duration recipes:</b> once an aggressively overclocked recipe's
     * post-overclock duration drops to just 2 ticks (e.g. on a Multi Smelter), the front overlay would flicker
     * on/off every tick without the debouncing below. {@link RecipeLogicGraphBuilder#tick} walks the search track
     * before the progress track (see
     * its own JavaDoc): {@link gregtech.api.recipes.logic.statemachine.RecipeLogicHooks#shouldStartRecipeLookup}
     * gates a new search on nothing currently being active, so it only sees "still active" while the previous
     * recipe hasn't finished <i>yet</i> (completion
     * happens moments later, in the same tick's progress walk) &mdash; a fresh candidate can only be found and
     * admitted starting the <i>next</i> tick. For any recipe lasting more than a couple of ticks this one-tick gap
     * between "just completed" and "next one admitted" is imperceptible; for a 2-tick recipe repeating back to
     * back, {@link #isActive()} genuinely toggles false for exactly one tick every single cycle, and syncing every
     * such transition to the client makes the overlay strobe at up to 10Hz. Restructuring the graph so completion
     * and the next admission always land in the same tick was considered and rejected as too invasive this late
     * (it would touch the shared search/progress track builders every migrated machine depends on) for what is
     * purely a client-visible rendering artifact &mdash; {@link #isActive()} itself stays perfectly accurate for
     * every other (server-side) consumer. Instead, only the client sync is debounced: a false reading isn't pushed
     * until it has held for more than {@link #INACTIVE_DEBOUNCE_TICKS} tick(s) in a row, which fully absorbs a
     * single-tick blip while adding at most one imperceptible tick of latency to a genuinely-idle machine's overlay.
     */
    @Override
    public void update() {
        var world = getMetaTileEntity().getWorld();
        if (world == null || world.isRemote || !workingEnabled) return;
        RecipeLogicGraphBuilder.tick(machine, data, traceEnabled ? this::logTrace : null);
        boolean nowActive = isActive();
        if (nowActive) {
            inactiveStreak = 0;
            if (!reportedActive) {
                reportedActive = true;
                getMetaTileEntity().markDirty();
                writeCustomData(ACTIVE_CHANGED, buf -> buf.writeBoolean(true));
            }
        } else if (reportedActive && ++inactiveStreak > INACTIVE_DEBOUNCE_TICKS) {
            reportedActive = false;
            getMetaTileEntity().markDirty();
            writeCustomData(ACTIVE_CHANGED, buf -> buf.writeBoolean(false));
        }
    }

    private void logTrace(@NotNull String debugName) {
        GTStateMachineTraceLog.log(traceLabel != null ? traceLabel : "?", debugName);
    }

    /**
     * Reports {@code config.hooks.onNoMatchFound}'s diagnostic breakdown (see {@link RecipeLookup#diagnoseNoMatch})
     * through the same trace log a traced walk's operator names go to, so "why did this machine's search come up
     * empty this tick" shows up right alongside the {@code selectCandidate} line it explains.
     */
    private void logNoMatch(@NotNull String breakdown) {
        GTStateMachineTraceLog.log(traceLabel != null ? traceLabel : "?", "  no match: " + breakdown);
    }

    /** @return whether this workable's ticks are currently being logged to {@link GTStateMachineTraceLog}. */
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    /** As {@link #setTraceEnabled(boolean, String)}, without changing the current label. */
    public void setTraceEnabled(boolean traceEnabled) {
        setTraceEnabled(traceEnabled, traceLabel);
    }

    /**
     * Enables or disables execution tracing for this workable, tagging any resulting log lines with {@code label}
     * (e.g. the owning machine's position and name). Intended to be called by the trace item's server-side handler
     * when it selects/deselects this specific machine instance.
     * <p>
     * Also wires/unwires {@code config.hooks.onNoMatchFound} to {@link #logNoMatch}: the "why did the search find
     * nothing" diagnostic breakdown is itself only computed while tracing is on (see that hook's own JavaDoc), so
     * toggling tracing is also what toggles whether that computation happens at all, not just whether it's logged.
     */
    public void setTraceEnabled(boolean traceEnabled, @Nullable String label) {
        this.traceEnabled = traceEnabled;
        this.traceLabel = label;
        config.hooks.onNoMatchFound = traceEnabled ? this::logNoMatch : null;
    }

    /**
     * Discards any queued/in-progress recipe state (via {@link RecipeLogicConfig#invalidate}), for a host that can
     * lose its inputs/energy/output space out from under a running recipe without warning &mdash; namely a
     * multiblock losing its structure. A single-block machine never needs this (it always keeps its own inventories
     * regardless of anything else happening to it).
     */
    public void invalidate() {
        config.invalidate(data);
    }

    /** @return the {@link RecipeLogicConfig} driving this trait, for a machine that needs to inspect/tweak it live. */
    public @NotNull RecipeLogicConfig getConfig() {
        return config;
    }

    /**
     * @return the {@link GTStateMachine} graph driving this trait's search/progress tracks (the graph-dump dev
     *         tool's target). Every {@link RecipeWorkable} instance for a
     *         given machine class builds an equivalent graph from that class's {@code createConfig()}, including the
     *         "prototype" instance sitting in the MTE registry -- so this is reachable (and dumpable) without ever
     *         placing a block.
     */
    public @NotNull GTStateMachine getStateMachine() {
        return machine;
    }

    @Override
    public @Nullable RecipeMap<?> getRecipeMap() {
        return recipeMap;
    }

    @Override
    public boolean isWorkingEnabled() {
        World world = getMetaTileEntity().getWorld();
        if (world != null && world.isRemote) return clientWorkingEnabled;
        return workingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (this.workingEnabled != isWorkingAllowed) {
            this.workingEnabled = isWorkingAllowed;
            getMetaTileEntity().markDirty();
            writeCustomData(WORKING_ENABLED_CHANGED, buf -> buf.writeBoolean(isWorkingAllowed));
        }
    }

    @Override
    public boolean isActive() {
        World world = getMetaTileEntity().getWorld();
        if (world != null && world.isRemote) return clientActive;
        return getActiveRecipeCount() > 0;
    }

    /** @return how many recipes are simultaneously active right now; see this class's JavaDoc. */
    @Range(from = 0, to = Integer.MAX_VALUE)
    public int getActiveRecipeCount() {
        return ActiveRecipeList.count(data);
    }

    /**
     * @return the server's own record of what {@link #isActive()} was last actually reported to the client as
     *         (see {@link #reportedActive}'s JavaDoc). Dev-tooling accessor: comparing this against the live
     *         {@link #isActive()} value (e.g. via {@code TricorderBehavior}) tells apart "the debounce is holding
     *         a stale true on the server itself" from "the server correctly flipped to false but the client never
     *         received/applied it".
     */
    public boolean isReportedActive() {
        return reportedActive;
    }

    /**
     * @return the total parallel budget every active recipe has already committed &mdash; the sum of each entry's
     *         own achieved parallel count ({@link #getAmperage(int)}, per
     *         {@link gregtech.api.recipes.logic.RecipeView#getActualAmperage()}'s JavaDoc on why amperage is where
     *         parallel copies "add up"), <b>not</b> {@link #getActiveRecipeCount()} (a mere entry count). This is
     *         what {@link gregtech.api.recipes.logic.statemachine.RecipeParallelConfig#consumedParallelSupplier}/
     *         {@link gregtech.api.recipes.logic.statemachine.RecipeLogicHooks#shouldStartRecipeLookup} must read
     *         for any machine whose {@code parallelLimit} can exceed 1: a single entry can already represent many
     *         parallel copies once {@code RecipeParallelOperator} has multiplied a candidate up, so counting
     *         entries drastically undercounts how much of the budget is actually spent.
     *         <p>
     *         <b>Important for any {@code parallelLimit > 1} machine:</b> {@code consumedParallelSupplier}/
     *         {@code shouldStartRecipeLookup} must be wired to this method, not {@link #getActiveRecipeCount()}. For a
     *         {@code parallelLimit = 1} machine the two coincidentally agree (at most one entry ever
     *         exists, so "entry count" and "parallel committed" are the same number), which can mask the mistake until
     *         a
     *         higher-parallel-limit machine is wired the same way. For {@code parallelLimit = 32} it does not: with 2
     *         entries
     *         already active (say, 30 and 29 parallel copies each), entry count (2) is nowhere near the limit (32), so
     *         {@code shouldStartRecipeLookup} would keep authorizing a brand new search every subsequent tick, and each
     *         such
     *         search's own {@code RecipeParallelOperator} budget check (also reading entry count) would keep seeing
     *         "only 2
     *         used out of 32" and admit yet another near-maximum-parallel entry &mdash; dozens of
     *         simultaneously active entries and a demanded EU/t climbing into the millions, never actually progressing
     *         because the energy supply could never keep up.
     */
    public int getCommittedParallel() {
        int total = 0;
        for (int i = 0; i < getActiveRecipeCount(); i++) total += (int) getAmperage(i);
        return total;
    }

    @Override
    public int getProgress() {
        return getActiveRecipeCount() == 0 ? 0 : getProgress(0);
    }

    /** @return {@code index}'s active recipe's current progress, in ticks; see this class's JavaDoc. */
    public int getProgress(@Range(from = 0, to = Integer.MAX_VALUE) int index) {
        return ActiveRecipeList.entryAt(index, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY);
    }

    @Override
    public int getMaxProgress() {
        return getActiveRecipeCount() == 0 ? 0 : getMaxProgress(0);
    }

    /** @return {@code index}'s active recipe's total duration, in ticks; see this class's JavaDoc. */
    public int getMaxProgress(@Range(from = 0, to = Integer.MAX_VALUE) int index) {
        return ActiveRecipeList.entryAt(index, data).getInteger(ActiveRecipeList.ENTRY_DURATION_KEY);
    }

    /** @return {@code index}'s active recipe's progress as a 0-1 fraction, or 0 if it has zero duration. */
    public double getProgressPercent(@Range(from = 0, to = Integer.MAX_VALUE) int index) {
        int max = getMaxProgress(index);
        return max == 0 ? 0 : (double) getProgress(index) / max;
    }

    /**
     * @return {@code index}'s active recipe's required/produced voltage (per amp; see
     *         {@link ActiveRecipeList#ENTRY_VOLTAGE_KEY}'s JavaDoc for why voltage and amperage are stored
     *         separately rather than pre-multiplied).
     */
    public long getVoltage(@Range(from = 0, to = Integer.MAX_VALUE) int index) {
        return ActiveRecipeList.entryAt(index, data).getLong(ActiveRecipeList.ENTRY_VOLTAGE_KEY);
    }

    /** @return {@code index}'s active recipe's required/produced amperage at {@link #getVoltage(int)}. */
    public long getAmperage(@Range(from = 0, to = Integer.MAX_VALUE) int index) {
        return ActiveRecipeList.entryAt(index, data).getLong(ActiveRecipeList.ENTRY_AMPERAGE_KEY);
    }

    /** @return whether {@code index}'s active recipe generates power rather than consuming it. */
    public boolean isGenerating(@Range(from = 0, to = Integer.MAX_VALUE) int index) {
        return ActiveRecipeList.entryAt(index, data).getBoolean(ActiveRecipeList.ENTRY_GENERATING_KEY);
    }

    /**
     * @return {@code index}'s active recipe's value at {@code key}, or {@code 0} if absent. Generic counterpart to
     *         {@link #getVoltage(int)}/{@link #getAmperage(int)} for a machine-specific key a
     *         {@link gregtech.api.recipes.logic.statemachine.RecipeEntryEnricher} wrote (e.g. Research
     *         Station's CWU/t bookkeeping) &mdash; lets a machine read its own custom entry data back for UI
     *         display without this class needing to know that key exists.
     */
    public int getActiveRecipeCustomInt(@Range(from = 0, to = Integer.MAX_VALUE) int index, @NotNull String key) {
        return ActiveRecipeList.entryAt(index, data).getInteger(key);
    }

    /** As {@link #getActiveRecipeCustomInt(int, String)}, for a boolean-valued custom key. */
    public boolean getActiveRecipeCustomBoolean(@Range(from = 0, to = Integer.MAX_VALUE) int index,
                                                @NotNull String key) {
        return ActiveRecipeList.entryAt(index, data).getBoolean(key);
    }

    /**
     * @return {@code index}'s active recipe's total required/produced power, in EU/t ({@link #getVoltage(int)}
     *         &times; {@link #getAmperage(int)}). A dev-tooling accessor (useful for investigating the
     *         {@code downTransformForParallels}-driven parallel count's sensitivity to supply voltage) as much as a
     *         general-purpose one: unlike
     *         {@link #getProgress(int)}/{@link #getMaxProgress(int)}, nothing in this engine reads this back, only
     *         a human inspecting a machine (see {@code TricorderBehavior}) does.
     */
    public long getRequiredEUt(@Range(from = 0, to = Integer.MAX_VALUE) int index) {
        return getVoltage(index) * getAmperage(index);
    }

    /**
     * @return the sum of {@link #getRequiredEUt(int)} across every currently active recipe (0 if idle). A machine
     *         admitting several genuinely independent candidates in the same tick (see this class's JavaDoc) has
     *         more than one active recipe at once, each contributing its own share.
     */
    public long getTotalRequiredEUt() {
        long total = 0;
        for (int i = 0; i < getActiveRecipeCount(); i++) total += getRequiredEUt(i);
        return total;
    }

    @Override
    public @NotNull String getName() {
        return GregtechDataCodes.RECIPE_WORKABLE_TRAIT;
    }

    @Override
    public <T> T getCapability(Capability<T> capability) {
        if (capability == GregtechTileCapabilities.CAPABILITY_WORKABLE) {
            return GregtechTileCapabilities.CAPABILITY_WORKABLE.cast(this);
        } else if (capability == GregtechTileCapabilities.CAPABILITY_CONTROLLABLE) {
            return GregtechTileCapabilities.CAPABILITY_CONTROLLABLE.cast(this);
        } else if (capability == GregtechTileCapabilities.CAPABILITY_RECIPE_LOGIC) {
            return GregtechTileCapabilities.CAPABILITY_RECIPE_LOGIC.cast(this);
        }
        return null;
    }

    /**
     * See {@link IRecipeLogicInfoProvider}'s own JavaDoc for why this exists (HWYLA/TheOneProbe hover info would
     * otherwise be missing for a machine using this trait).
     */
    @Override
    public boolean isWorking() {
        return isActive() && isWorkingEnabled();
    }

    /**
     * As {@link IRecipeLogicInfoProvider#getInfoProviderEUt()}; describes only the first active recipe, like
     * {@link #getProgress()}/{@link #getMaxProgress()} -- see this class's own JavaDoc for why.
     */
    @Override
    public long getInfoProviderEUt() {
        return getActiveRecipeCount() == 0 ? 0 : getRequiredEUt(0);
    }

    @Override
    public boolean consumesEnergy() {
        return getActiveRecipeCount() == 0 || !isGenerating(0);
    }

    /** Covers a client that starts observing this machine already mid-recipe (relogging, chunk reload). */
    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        buf.writeBoolean(isActive());
        buf.writeBoolean(isWorkingEnabled());
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        clientActive = buf.readBoolean();
        clientWorkingEnabled = buf.readBoolean();
    }

    /**
     * <b>Front overlay can get permanently stuck showing "active" after a machine genuinely finishes processing,
     * even though {@link #clientActive} is already correctly updated in memory, unless the render update below is
     * triggered explicitly.</b> {@link MetaTileEntity}'s block rendering goes through Forge's fast-TESR path
     * ({@code IFastRenderMetaTileEntity}/{@code MetaTileEntityTESR}), which bakes its output into the chunk's
     * render buffer rather than redrawing dynamically every frame &mdash; exactly like
     * {@code MetaTileEntity#receiveCustomData}'s own {@code UPDATE_FRONT_FACING}/{@code UPDATE_PAINTING_COLOR}
     * cases, this needs an explicit {@link MetaTileEntity#scheduleRenderUpdate()} call
     * ({@code world.markBlockRangeForRenderUpdate}) to actually force a re-render; {@code SYNC_MTE_TRAITS} (the
     * generic dispatch this trait's sync rides on) never called it. Without that trigger, the stale "active" bake
     * only ever got corrected by some unrelated coincidental re-render (a neighbor block update, a lighting
     * refresh, etc.) &mdash; frequent enough in normal play to make this look "mostly working", but not guaranteed,
     * which is exactly the intermittent symptom reported.
     */
    @Override
    public void receiveCustomData(int discriminator, @NotNull PacketBuffer buf) {
        if (discriminator == ACTIVE_CHANGED) {
            clientActive = buf.readBoolean();
            getMetaTileEntity().scheduleRenderUpdate();
            // Dev tooling (kept permanently, low-cost given the debounce above already keeps this infrequent):
            // a client-side record of every applied ACTIVE_CHANGED transition, for diagnosing any front-
            // overlay desync -- compare against RecipeWorkable#isReportedActive()
            // (the server's own record of what it last sent) to tell a send-side bug apart from a receive/render-
            // side one.
            GTLog.logger.info("[RecipeWorkable] {} received ACTIVE_CHANGED -> {}", getMetaTileEntity().getPos(),
                    clientActive);
        } else if (discriminator == WORKING_ENABLED_CHANGED) {
            clientWorkingEnabled = buf.readBoolean();
            getMetaTileEntity().scheduleRenderUpdate();
        }
    }

    @Override
    public @NotNull NBTTagCompound serializeNBT() {
        NBTTagCompound tag = super.serializeNBT();
        tag.setTag("Data", data);
        tag.setBoolean("WorkingEnabled", workingEnabled);
        return tag;
    }

    @Override
    public void deserializeNBT(@NotNull NBTTagCompound compound) {
        super.deserializeNBT(compound);
        data.merge(compound.getCompoundTag("Data"));
        workingEnabled = !compound.hasKey("WorkingEnabled") || compound.getBoolean("WorkingEnabled");
    }
}
