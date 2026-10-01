package gregtech.common.metatileentities.multi.electric.generator;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IRotorHolder;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.metatileentity.ITieredMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.*;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.sync.FixedIntArraySyncValue;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.lookup.TurbineParallelOperator;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerCapacityProperty;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.TextFormattingUtil;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Migrated from {@code RecipeMapMultiblockController}/
 * {@code FuelMultiblockController} (legacy {@code MultiblockFuelRecipeLogic}/{@code LargeTurbineWorkableHandler})
 * to {@link RecipeWorkableMultiblockController} directly (as {@link MetaTileEntityLargeCombustionEngine}, no
 * shared base class -- see that class's own JavaDoc for why).
 * <p>
 * <b>Rotor-driven voltage targeting:</b> legacy computes a target voltage from the current rotor's
 * efficiency/power every time it (re-)admits a recipe, sizing that recipe's parallel count to hit the target as
 * closely as possible from above (ceiling division), banking the unavoidable rounding overshoot
 * ({@code excessVoltage}) so a future admission needs correspondingly less new fuel. Critically, legacy only ever
 * re-admits <i>once the previously-admitted recipe's own duration has fully elapsed</i>
 * ({@code AbstractRecipeLogic}'s {@code progressTime == 0} gate) -- the rebuilt recipe's duration is left unchanged
 * by {@code RecipeBuilder#append(recipe, parallel, false)}, so one admission's fuel funds {@code target} EU/t of
 * delivery for the <i>whole</i> of that fuel's own natural duration (10 ticks for Steam, anywhere from 3 to 320
 * ticks depending on which Gas/Plasma fuel was matched), not just one tick.
 * <p>
 * <b>Fuel consumption must match the legacy machine's cadence, or it comes out {@code duration}&times; too high:</b>
 * re-admitting (and re-consuming fuel) on essentially every tick once {@link #bankedEU} dips below target -- e.g.
 * from delivery
 * itself continuously draining {@link #bankedEU} every tick rather than the entry's own natural-duration window
 * funding it once -- would produce exactly that inflation. See {@link TurbineParallelOperator}'s own JavaDoc for
 * the two further risks (double-delivery
 * across simultaneously-admitted distinct fuels; fuel-starvation bank debt) this raises and how they're
 * closed. This migration reproduces legacy's actual cadence in two pieces:
 * <ul>
 * <li><b>Admission (on demand, at most once per {@code duration}-tick window):</b> {@code config.hooks
 * .shouldStartRecipeLookup} only allows a search when no entry is currently active ({@code workable
 * .getActiveRecipeCount() == 0}, mirroring legacy's {@code progressTime == 0}) <i>and</i> {@link #bankedEU} is
 * below the current target; {@link TurbineParallelOperator} (wired via {@code config.parallel
 * .parallelLimitFactory}) sizes the parallel count with the same ceiling-division formula as legacy, failing
 * outright (not clamping) if the tank can't cover the full amount, exactly like legacy's {@code prepareRecipe}.
 * {@code config.hooks.entryEnricher} freezes the target voltage this batch is worth onto the entry itself
 * ({@code ENTRY_DELIVERY_VOLTAGE_KEY}, mirroring legacy's own {@code recipeEUt} being frozen at {@code setupRecipe}
 * time) and records the net bank delta ({@code ENTRY_BANK_DELTA_KEY} = raw credit minus one target, matching
 * legacy's single one-time {@code excessVoltage += credit - target} update); the entry's own duration is left at
 * the matched fuel's natural value (no longer forced to 1 tick -- the "no active entry" admission gate above
 * already prevents the pile-up that fix used to guard against, more robustly, since piling up is now structurally
 * impossible rather than merely rare).</li>
 * <li><b>Delivery, split across the entry's active window vs. the gaps between windows:</b> while an entry is
 * active, {@code config.hooks.perTickRecipeCheck} delivers its frozen {@code ENTRY_DELIVERY_VOLTAGE_KEY} every
 * tick for the whole window, exactly like legacy's {@code updateRecipeProgress}/{@code drawEnergy(recipeEUt)} --
 * {@link #bankedEU} is untouched during this window (its one-time delta was already applied at admission).
 * Between windows (no entry active), {@code config.hooks.perTickWorkerCheck} runs {@link #runIdleBankDraw()},
 * which delivers a full target's worth straight from {@link #bankedEU} whenever it alone already covers it
 * (this is this migration's simplified stand-in for legacy's zero-parallel {@code excessVoltage >=
 * turbineMaxVoltage} branch -- see {@link TurbineParallelOperator}'s JavaDoc for why a literal, shared-pipeline
 * reproduction of that branch was rejected). {@link #lastDeliveredEUt} records whichever of the two actually fired
 * this tick (or {@code 0} if neither did, e.g. genuine fuel starvation) for {@link #configureDisplayText}'s
 * real-time production line and {@link #isGeneratingPower()}.</li>
 * </ul>
 * <p>
 * {@code config.power.properties} reports an effectively-unbounded {@link PowerCapacityProperty} (parallel sizing
 * is fully owned by {@link TurbineParallelOperator}, which never consults {@code config.power} at all): this only
 * needs to keep {@code RecipeOverclockOperator}'s own separate sanity ceiling check
 * ({@code magnitude > ceiling}) from spuriously rejecting a candidate {@link TurbineParallelOperator} already
 * approved (which, by design, can slightly exceed a naively-computed budget due to its own ceiling rounding).
 * <p>
 * <b>{@code boostProduction}'s rotor-speed ramp-up: found dead in legacy, revived as a live feature here</b>
 * (design investigation, 2026-09-05; user request, later that day): legacy's own
 * {@code LargeTurbineWorkableHandler#updateRecipeProgress} never actually calls it -- only
 * {@code MetaTileEntityLargeTurbine#getMaxVoltage()} does, in a comparison that is always true by construction
 * ({@code boostProduction(x) <= x} always), making that branch unreachable in legacy. The rotor speed value it
 * needs, though, was already being live-ramped the whole time by {@code MetaTileEntityRotorHolder#update()}
 * (1/tick towards max while {@link #isActive()}, e.g. 5000 ticks/250s to max on an HV Steam Turbine) with nothing
 * ever consuming it. {@link #getSpeedRampFactor()} now does, scoped to delivery only (see its own JavaDoc) --
 * this is a deliberate addition beyond legacy fidelity, not a bug reproduction.
 * <p>
 * <b>Frozen-target staleness is intentional, matching legacy exactly</b> (design discussion, 2026-09-05): if the
 * rotor is swapped, degrades, or is removed entirely while an entry's window is active, delivery still completes
 * that window using the target frozen at admission time -- legacy's {@code canProgressRecipe()} (which this
 * machine never overrides) only ever checks the cleanroom requirement, never the rotor, so this is not a
 * regression. For a very long-duration fuel (Americium Plasma: 320 ticks) this means a rotor change can take up to
 * 16 real-time seconds to visibly take effect, which is expected, not a bug.
 */
public class MetaTileEntityLargeTurbine extends RecipeWorkableMultiblockController
                                        implements ITieredMetaTileEntity, ProgressBarMultiblock {

    /**
     * Frozen at admission time (mirrors legacy's own {@code recipeEUt} being frozen at {@code setupRecipe}); the
     * EU/t this specific entry delivers every tick for the whole of its own natural-duration window.
     */
    private static final String ENTRY_DELIVERY_VOLTAGE_KEY = "TurbineDeliveryVoltage";
    /**
     * The net one-time change to apply to {@link #bankedEU} the instant this entry is admitted: the raw credit
     * this batch is worth, minus one target's worth (matching legacy's single {@code excessVoltage += credit -
     *  target} update, applied once per admission regardless of how many ticks the resulting window lasts).
     */
    private static final String ENTRY_BANK_DELTA_KEY = "TurbineBankDelta";
    private static final int MIN_DURABILITY_TO_WARN = 10;
    private static final String NBT_BANKED_EU = "BankedEU";

    public final int tier;
    private final long baseEUOutput;

    public final IBlockState casingState;
    public final IBlockState gearboxState;
    public final ICubeRenderer casingRenderer;
    public final boolean hasMufflerHatch;
    public final ICubeRenderer frontOverlay;

    public net.minecraftforge.fluids.capability.IFluidHandler exportFluidHandler;
    /**
     * Resolved once at {@link #formStructure}; the ability part itself doesn't change until the next re-formation
     * (its current rotor state is always queried fresh through it).
     */
    @Nullable
    private IRotorHolder cachedRotorHolder;
    /** Banked EU (legacy's {@code excessVoltage}): see this class's own JavaDoc for the admission/delivery split. */
    private long bankedEU;
    /**
     * What was actually delivered to {@link #getEnergyContainer()} this tick, after {@link #getSpeedRampFactor()}
     * scaling (server-authoritative; {@code 0} if neither an active entry's window nor {@link #runIdleBankDraw()}
     * delivered anything, e.g. genuine fuel starvation, or while still ramping up from a full stop) -- drives
     * {@link #configureDisplayText}'s real-time production line. Deliberately <i>not</i> what
     * {@link #isGeneratingPower()} uses -- see that method's own JavaDoc for why a ramp-caused {@code 0} here must
     * not look like "not generating".
     */
    private long lastDeliveredEUt = 0;
    /**
     * Whether a genuine fuel-funded delivery opportunity existed this tick, independent of how much
     * {@link #getSpeedRampFactor()} scaled the actual wattage down -- see {@link #isGeneratingPower()}.
     */
    private boolean generatingThisTick = false;
    /**
     * Server-side record of what {@link #isGeneratingPower()} was last pushed to the client as; see that
     * method's own JavaDoc for why a dedicated sync is needed here.
     */
    private boolean lastGeneratingPowerSynced = false;
    /**
     * Client-side cache of {@link #isGeneratingPower()}'s last value pushed from the server; see that method's
     * own JavaDoc.
     */
    private boolean clientGeneratingPower = false;

    public MetaTileEntityLargeTurbine(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, int tier,
                                      IBlockState casingState, IBlockState gearboxState, ICubeRenderer casingRenderer,
                                      boolean hasMufflerHatch, ICubeRenderer frontOverlay) {
        super(metaTileEntityId, recipeMap);
        this.casingState = casingState;
        this.gearboxState = gearboxState;
        this.casingRenderer = casingRenderer;
        this.hasMufflerHatch = hasMufflerHatch;
        this.frontOverlay = frontOverlay;
        this.tier = tier;
        this.baseEUOutput = GTValues.V[tier] * 2L;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityLargeTurbine(metaTileEntityId, recipeMap, tier, casingState, gearboxState,
                casingRenderer, hasMufflerHatch, frontOverlay);
    }

    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        config.overclock.ocAmountCalculator = (v, m) -> 0; // isAllowOverclocking() == false, legacy-equivalent
        config.parallel.parallelLimitFactory = downTransform -> new TurbineParallelOperator(
                this::getTargetVoltage, this::getCurrentEfficiency, () -> bankedEU);
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerCapacityProperty(Long.MAX_VALUE / 4, 1));
            properties.add(DimensionProperties.of(this));
            properties.add(CleanroomProperties.of(this));
            return properties;
        };
        config.hooks.shouldStartRecipeLookup = data -> {
            if (cachedRotorHolder == null || !cachedRotorHolder.hasRotor()) return false;
            // Mirrors legacy's progressTime==0 gate: never admit while a previous batch's window is still
            // delivering. This is what makes entry pile-up structurally impossible now, without needing to force
            // ENTRY_DURATION_KEY down to 1 tick the way an earlier version of this fix did.
            if (workable.getActiveRecipeCount() > 0) return false;
            long target = getTargetVoltage();
            return target > 0 && bankedEU < target;
        };
        config.hooks.entryEnricher = (recipe, entry) -> {
            long voltage = entry.getLong(ActiveRecipeList.ENTRY_VOLTAGE_KEY);
            long amperage = entry.getLong(ActiveRecipeList.ENTRY_AMPERAGE_KEY);
            long target = getTargetVoltage();
            // Frozen for this whole window -- see this class's own JavaDoc ("frozen-target staleness").
            entry.setLong(ENTRY_DELIVERY_VOLTAGE_KEY, target);
            entry.setLong(ENTRY_BANK_DELTA_KEY, (long) (voltage * amperage * getCurrentEfficiency()) - target);
            // Duration is deliberately left at the matched fuel's own natural value here (unlike an earlier version
            // of this fix, which forced it to 1 tick) -- see this class's own JavaDoc for why the admission gate
            // above makes that no longer necessary.
        };
        config.callbacks.onRecipeStarted = entry -> bankedEU += entry.getLong(ENTRY_BANK_DELTA_KEY);
        config.hooks.perTickRecipeCheck = recipeData -> {
            long fullTarget = recipeData.getLong(ENTRY_DELIVERY_VOLTAGE_KEY);
            long deliver = (long) (fullTarget * getSpeedRampFactor());
            getEnergyContainer().addEnergy(deliver);
            lastDeliveredEUt = deliver;
            // A genuine fuel-funded delivery opportunity exists this tick regardless of how much the speed ramp
            // scaled the actual wattage down -- see isGeneratingPower()'s own JavaDoc for why this must stay
            // decoupled from lastDeliveredEUt (a ramp-caused delivery of 0 W must not look like "not generating").
            generatingThisTick = true;
            return true;
        };
        config.hooks.perTickWorkerCheck = data -> {
            runIdleBankDraw();
            return true;
        };
        return config;
    }

    private long getTargetVoltage() {
        if (cachedRotorHolder == null || !cachedRotorHolder.hasRotor()) return 0;
        return baseEUOutput * cachedRotorHolder.getTotalPower() / 100;
    }

    private double getCurrentEfficiency() {
        if (cachedRotorHolder == null || !cachedRotorHolder.hasRotor()) return 0;
        return cachedRotorHolder.getTotalEfficiency() / 100.0;
    }

    /**
     * @return the fraction of a full-speed delivery this tick should actually push into the energy container,
     *         based on how close the rotor is to its maximum spin speed. Revives legacy's {@code boostProduction}
     *         formula (quadratic ramp, {@code (currentSpeed/maxSpeed)^2}) as a live feature (user request,
     *         2026-09-05) -- legacy itself never actually called it from the real delivery path (see this class's
     *         own JavaDoc), but the rotor speed value it needs was already being ramped up independently by
     *         {@code MetaTileEntityRotorHolder#update()} (1/tick towards max, driven by {@link #isActive()}, e.g.
     *         5000 ticks/250s to reach max on an HV Steam Turbine) the whole time, just never consumed by anything.
     *         Deliberately scoped to delivery only -- fuel consumption/parallel sizing/banking are all computed
     *         against the <i>unscaled</i> target, exactly mirroring how legacy's own dead formula only ever wrapped
     *         the value handed to {@code drawEnergy}, nothing upstream of it.
     */
    private double getSpeedRampFactor() {
        if (cachedRotorHolder == null || !cachedRotorHolder.hasRotor()) return 0;
        int maxSpeed = cachedRotorHolder.getMaxRotorHolderSpeed();
        if (maxSpeed <= 0) return 1;
        int currentSpeed = cachedRotorHolder.getRotorSpeed();
        if (currentSpeed >= maxSpeed) return 1;
        double ratio = (double) currentSpeed / maxSpeed;
        return ratio * ratio;
    }

    /**
     * The "gap between admission windows" half of delivery; see this class's own JavaDoc. Only ever does anything
     * while no entry is currently active -- while one is, that entry's own {@code perTickRecipeCheck} is already
     * delivering this tick (checked first specifically to avoid double-delivering; {@code perTickWorkerCheck} runs
     * <i>after</i> admission in {@code RecipeProgressTrackBuilder}'s own ordering, so an entry admitted this very
     * tick is already counted here). Cheap no-op when idle by design.
     */
    private void runIdleBankDraw() {
        if (workable.getActiveRecipeCount() > 0) return;
        if (!workable.isWorkingEnabled() || cachedRotorHolder == null || !cachedRotorHolder.hasRotor()) {
            lastDeliveredEUt = 0;
            generatingThisTick = false;
            return;
        }
        long target = getTargetVoltage();
        if (target > 0 && bankedEU >= target) {
            bankedEU -= target;
            long deliver = (long) (target * getSpeedRampFactor());
            getEnergyContainer().addEnergy(deliver);
            lastDeliveredEUt = deliver;
            generatingThisTick = true;
        } else {
            // Genuine fuel starvation (or zero target): nothing delivered this tick. The admission half will
            // catch up once fuel/rotor conditions allow shouldStartRecipeLookup to succeed again.
            lastDeliveredEUt = 0;
            generatingThisTick = false;
        }
    }

    /**
     * @return whether this turbine is genuinely producing power right now: {@link #generatingThisTick}, i.e. a
     *         fuel-funded delivery opportunity existed this tick, independent of {@code ActiveRecipeList}'s literal
     *         entry count. With entries collapsed to a 1-tick lifetime,
     *         {@code workable.isActive()} (a snapshot taken <i>after</i> the tick-walk finishes) would almost never
     *         observe an entry mid-flight even while delivery was continuously, correctly running --
     *         {@code workable.isActive()} answers "does ActiveRecipeList currently hold something", which is
     *         not a meaningful proxy for "is this turbine working" once admission becomes a momentary event.
     *         Used in place of {@code workable.isActive()} everywhere below -- the Tricorder's own raw
     *         {@code recipeWorkable.isActive()} debug line is deliberately left alone, since exposing the raw
     *         engine-internal value there is its whole purpose.
     *         <p>
     *         <b>Deliberately {@link #generatingThisTick}, not {@link #lastDeliveredEUt} {@code > 0}:</b>
     *         {@code lastDeliveredEUt > 0} is more accurate than a pure capacity check for fuel starvation, since it
     *         also
     *         correctly reads {@code false} when an admission-based signal would report {@code true} despite nothing
     *         actually being delivered. But {@link #getSpeedRampFactor()} legitimately delivers {@code 0} W for a long
     *         stretch right after a cold start (quadratic ramp from a full stop), and {@code MetaTileEntityRotorHolder
     * #update()} only ever increments the rotor speed that ramp reads while {@link #isActive()} is {@code true} --
     *         so {@code lastDeliveredEUt > 0} would create a startup deadlock (never generating because speed is 0,
     *         speed
     *         never rising because never generating). {@link #generatingThisTick} is set {@code true} by both delivery
     *         paths as soon as a genuine opportunity exists, strictly <i>before</i> the ramp factor is applied,
     *         breaking
     *         that cycle while still staying accurate for fuel starvation (both paths also set it {@code false} when
     *         nothing was actually deliverable).
     *         <p>
     *         <b>Client sync matters here too, or the working sound and front
     *         overlay never activate even though the GUI status line (which reads this same method) correctly
     *         shows "working".</b> {@code configureDisplayText} runs server-side (its output is pushed to the client as
     *         pre-built text), so it's unaffected; but {@code MetaTileEntity#updateSound()} and
     *         {@link RecipeWorkableMultiblockController#isOverlayActive()} (which this class overrides) are both called
     *         directly on the <i>client</i>, where this method's raw field reads ({@link #generatingThisTick}, etc.)
     *         are
     *         not guaranteed to reflect the server's authoritative state (the same class of bug
     *         {@code RecipeWorkable}'s
     *         own JavaDoc documents for {@code isActive()}/{@code isWorkingEnabled()}). This method branches on
     *         {@link World#isRemote}, returning
     *         {@link #clientGeneratingPower} (a cache kept current by an explicit
     *         {@link GregtechDataCodes#LARGE_TURBINE_GENERATING_POWER_CHANGED} push, including the
     *         {@code scheduleRenderUpdate()} call {@code RecipeWorkable}'s own sync needs for the same reason) on the
     *         client, and only computing the real condition on the server.
     */
    private boolean isGeneratingPower() {
        World world = getWorld();
        if (world != null && world.isRemote) return clientGeneratingPower;
        return generatingThisTick;
    }

    /**
     * Overridden so {@link MultiblockWithDisplayBase#update()}'s polymorphic {@code isActive()} call (which drives
     * the working sound and the multiblock's active/idle block-state swap) reflects {@link #isGeneratingPower()}
     * instead of {@code workable.isActive()} -- see that method's own JavaDoc for why. Does not affect
     * {@code workable}'s own {@code isActive()}/the Tricorder's raw debug reading of it.
     */
    @Override
    public boolean isActive() {
        return isGeneratingPower();
    }

    /** Pushes {@link #isGeneratingPower()} to the client whenever it changes; see that method's own JavaDoc. */
    @Override
    public void update() {
        super.update();
        World world = getWorld();
        if (world != null && !world.isRemote) {
            // Found while implementing the speed-ramp feature (2026-09-05): while working is disabled, the whole
            // recipe engine tick (RecipeWorkable#update()) never runs at all, so neither delivery path below ever
            // gets a chance to reset generatingThisTick/lastDeliveredEUt -- without this, a stale positive value
            // from right before disabling would survive indefinitely, keeping the sound/overlay/GUI production
            // line stuck "on".
            if (!workable.isWorkingEnabled()) {
                generatingThisTick = false;
                lastDeliveredEUt = 0;
            }
            boolean generating = isGeneratingPower();
            if (generating != lastGeneratingPowerSynced) {
                lastGeneratingPowerSynced = generating;
                writeCustomData(GregtechDataCodes.LARGE_TURBINE_GENERATING_POWER_CHANGED,
                        buf -> buf.writeBoolean(generating));
            }
        }
    }

    /** Covers a client that starts observing this machine already mid-operation (relogging, chunk reload). */
    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(isGeneratingPower());
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        clientGeneratingPower = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.LARGE_TURBINE_GENERATING_POWER_CHANGED) {
            clientGeneratingPower = buf.readBoolean();
            scheduleRenderUpdate();
        }
    }

    @Override
    protected void initializeAbilities() {
        super.initializeAbilities();
        List<IEnergyContainer> outputEnergy = new ArrayList<>(getAbilities(MultiblockAbility.OUTPUT_ENERGY));
        outputEnergy.addAll(getAbilities(MultiblockAbility.SUBSTATION_OUTPUT_ENERGY));
        outputEnergy.addAll(getAbilities(MultiblockAbility.OUTPUT_LASER));
        this.energyContainer = new EnergyContainerList(outputEnergy);
    }

    public IRotorHolder getRotorHolder() {
        return cachedRotorHolder;
    }

    /**
     * @return true if turbine is formed and it's face is free and contains
     *         only air blocks in front of rotor holder
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isRotorFaceFree() {
        return isStructureFormed() && cachedRotorHolder != null && cachedRotorHolder.isFrontFaceFree();
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        this.exportFluidHandler = new FluidTankList(true, getAbilities(MultiblockAbility.EXPORT_FLUIDS));
        List<IRotorHolder> abilities = getAbilities(MultiblockAbility.ROTOR_HOLDER);
        this.cachedRotorHolder = abilities.isEmpty() ? null : abilities.get(0);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.exportFluidHandler = null;
        this.cachedRotorHolder = null;
        this.bankedEU = 0;
        this.lastDeliveredEUt = 0;
        this.generatingThisTick = false;
    }

    /**
     * {@link #bankedEU} persistence: without this, a world
     * save/reload would silently wipe any carried-over credit -- now that fuel-starvation admissions fail outright
     * rather than partially succeeding (see {@link TurbineParallelOperator}'s JavaDoc) there is no debt to lose,
     * but a genuine banked surplus built up from ceiling-rounding overshoot would still vanish for free.
     */
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setLong(NBT_BANKED_EU, bankedEU);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        if (data.hasKey(NBT_BANKED_EU)) {
            this.bankedEU = data.getLong(NBT_BANKED_EU);
        }
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        long target = getTargetVoltage();
        builder.setWorkingStatus(workable.isWorkingEnabled(), isGeneratingPower())
                // MultiblockUIBuilder#addEnergyProductionLine's second parameter is gating-only (it never actually
                // renders it -- only ever shows "Max EU/t: <first argument>"), so this alone can never be the
                // real-time line the user asked for; feeding it target/target here for a normal always-true gate.
                .addEnergyProductionLine(target, target)
                // Real-time actual production: a genuinely separate line, since the one
                // above is max-only. current = what was actually delivered this tick (0 during fuel starvation).
                // Clamped to target per user decision: a stale frozen lastDeliveredEUt could otherwise transiently
                // show a number higher than the max line right after a rotor downgrade (see this class's own
                // JavaDoc on frozen-target staleness) -- reuses gregtech.multiblock.turbine.energy_per_tick, a
                // pre-existing lang key that was never wired up to anything until now.
                .addCustom((keyList, syncer) -> {
                    long current = syncer.syncLong(Math.min(lastDeliveredEUt, target));
                    String energyFormatted = TextFormattingUtil.formatNumbers(current);
                    var voltageName = KeyUtil.string(GTValues.VOCNF[GTUtility.getFloorTierByVoltage(current)]);
                    keyList.add(KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.turbine.energy_per_tick", energyFormatted, voltageName));
                })
                .addCustom((keyList, syncer) -> {
                    if (!isStructureFormed() || cachedRotorHolder == null) return;

                    int rotorEfficiency = syncer.syncInt(cachedRotorHolder.getRotorEfficiency());
                    int totalEfficiency = syncer.syncInt(cachedRotorHolder.getTotalEfficiency());

                    if (rotorEfficiency > 0) {
                        IKey efficiencyInfo = KeyUtil.number(TextFormatting.AQUA,
                                totalEfficiency, "%");
                        keyList.add(KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.turbine.efficiency",
                                efficiencyInfo));
                    }
                })
                .addWorkingStatusLine();
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addCustom((keyList, syncer) -> {
            if (!isStructureFormed() || cachedRotorHolder == null) return;

            int rotorEfficiency = syncer.syncInt(cachedRotorHolder.getRotorEfficiency());
            int rotorDurability = syncer.syncInt(cachedRotorHolder.getRotorDurabilityPercent());

            if (rotorEfficiency > 0 && rotorDurability <= MIN_DURABILITY_TO_WARN) {
                keyList.add(KeyUtil.lang(TextFormatting.YELLOW,
                        "gregtech.multiblock.turbine.rotor_durability_low"));
            }
        });
        super.configureWarningText(builder);
    }

    @Override
    protected void configureErrorText(MultiblockUIBuilder builder) {
        super.configureErrorText(builder);
        builder.addCustom((keyList, syncer) -> {
            if (!isStructureFormed() || cachedRotorHolder == null) return;

            if (syncer.syncBoolean(!isRotorFaceFree())) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.turbine.obstructed"));
                keyList.add(KeyUtil.lang(TextFormatting.GRAY,
                        "gregtech.multiblock.turbine.obstructed.desc"));
            }
            int rotorEfficiency = syncer.syncInt(cachedRotorHolder.getRotorEfficiency());

            if (rotorEfficiency <= 0) {
                keyList.add(KeyUtil.lang(TextFormatting.RED,
                        "gregtech.multiblock.turbine.no_rotor"));
            }
        });
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.base_production_eut", GTValues.V[tier] * 2));
        tooltip.add(I18n.format("gregtech.multiblock.turbine.efficiency_tooltip", GTValues.VNF[tier]));
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
                .aisle("CCCC", "CHHC", "CCCC")
                .aisle("CHHC", "RGGR", "CHHC")
                .aisle("CCCC", "CSHC", "CCCC")
                .where('S', selfPredicate())
                .where('G', states(getGearBoxState()))
                .where('C', states(getCasingState()))
                .where('R', metaTileEntities(MultiblockAbility.REGISTRY.get(MultiblockAbility.ROTOR_HOLDER).stream()
                        .filter(mte -> (mte instanceof ITieredMetaTileEntity) &&
                                (((ITieredMetaTileEntity) mte).getTier() >= tier))
                        .toArray(MetaTileEntity[]::new))
                                .addTooltips("gregtech.multiblock.pattern.clear_amount_3")
                                .addTooltip("gregtech.multiblock.pattern.error.limited.1", GTValues.VN[tier])
                                .setExactLimit(1)
                                .or(abilities(MultiblockAbility.OUTPUT_ENERGY)).setExactLimit(1))
                .where('H', states(getCasingState()).or(autoAbilities(false, true, false, false, true, true, true)))
                .build();
    }

    @Override
    public String[] getDescription() {
        return new String[] { I18n.format("gregtech.multiblock.large_turbine.description") };
    }

    public IBlockState getCasingState() {
        return casingState;
    }

    public IBlockState getGearBoxState() {
        return gearboxState;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return casingRenderer;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return frontOverlay;
    }

    /**
     * Overridden (unlike most {@link RecipeWorkableMultiblockController} subclasses) so the front overlay reflects
     * {@link #isGeneratingPower()} instead of the base class's default {@code workable.isActive()} -- see that
     * method's own JavaDoc for why {@code workable.isActive()} alone is no longer a meaningful "is this turbine
     * working" signal.
     * <p>
     * <b>Overriding {@link #renderMetaTileEntity} directly and drawing the front overlay a second time with the
     * corrected flag would be wrong:</b> the superclass's own
     * {@code renderMetaTileEntity()} already draws the front overlay once itself (using the base
     * {@code workable.isActive()}-based flag), so both draws would land on screen at once, visibly overlapping
     * while transitioning. Using this narrower override
     * point instead lets the superclass's single draw call use the right flag from the start.
     */
    @Override
    protected boolean isOverlayActive() {
        return isGeneratingPower();
    }

    @Override
    public boolean hasMufflerMechanics() {
        return hasMufflerHatch;
    }

    @Override
    public boolean isStructureObstructed() {
        return super.isStructureObstructed() || !isRotorFaceFree();
    }

    @Override
    public int getTier() {
        return tier;
    }

    @Override
    public boolean canVoidRecipeItemOutputs() {
        return true;
    }

    @Override
    public boolean canVoidRecipeFluidOutputs() {
        return true;
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    @Override
    public int getProgressBarCount() {
        return 3;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        FixedIntArraySyncValue fuelValue = new FixedIntArraySyncValue(this::getFuelAmount, null);
        StringSyncValue fuelNameValue = new StringSyncValue(() -> {
            FluidStack stack = firstNonEmptyFuelTank();
            if (stack == null) return null;
            Fluid fluid = stack.getFluid();
            return fluid == null ? null : fluid.getName();
        });
        syncManager.syncValue("fuel_amount", fuelValue);
        syncManager.syncValue("fuel_name", fuelNameValue);

        IntSyncValue rotorSpeedValue = new IntSyncValue(
                () -> cachedRotorHolder == null ? 0 : cachedRotorHolder.getRotorSpeed());
        IntSyncValue rotorMaxSpeedValue = new IntSyncValue(
                () -> cachedRotorHolder == null ? 0 : cachedRotorHolder.getMaxRotorHolderSpeed());

        syncManager.syncValue("rotor_speed", rotorSpeedValue);
        syncManager.syncValue("rotor_max_speed", rotorMaxSpeedValue);
        IntSyncValue durabilityValue = new IntSyncValue(
                () -> cachedRotorHolder == null ? 0 : cachedRotorHolder.getRotorDurabilityPercent());
        IntSyncValue efficiencyValue = new IntSyncValue(
                () -> cachedRotorHolder == null ? 0 : cachedRotorHolder.getRotorEfficiency());

        syncManager.syncValue("rotor_durability", durabilityValue);
        syncManager.syncValue("rotor_efficiency", efficiencyValue);

        bars.add(barTest -> barTest
                .progress(() -> fuelValue.getValue(1) == 0 ? 0 :
                        1.0 * fuelValue.getValue(0) / fuelValue.getValue(1))
                .texture(GTGuiTextures.PROGRESS_BAR_LCE_FUEL)
                .tooltipBuilder(t -> createFuelTooltip(t, fuelValue, fuelNameValue)));

        bars.add(barTest -> barTest
                .progress(() -> rotorMaxSpeedValue.getIntValue() == 0 ? 0 :
                        1.0 * rotorSpeedValue.getIntValue() / rotorMaxSpeedValue.getIntValue())
                .texture(GTGuiTextures.PROGRESS_BAR_TURBINE_ROTOR_SPEED)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        int speed = rotorSpeedValue.getIntValue();
                        int maxSpeed = rotorMaxSpeedValue.getIntValue();

                        t.addLine(KeyUtil.lang("gregtech.multiblock.turbine.rotor_speed",
                                getSpeedFormat(maxSpeed, speed), speed, maxSpeed));
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));

        bars.add(barTest -> barTest
                .progress(() -> durabilityValue.getIntValue() / 100.0)
                .texture(GTGuiTextures.PROGRESS_BAR_TURBINE_ROTOR_DURABILITY)
                .tooltipBuilder(t -> {
                    if (isStructureFormed()) {
                        if (efficiencyValue.getIntValue() <= 0) {
                            t.addLine(IKey.lang("gregtech.multiblock.turbine.no_rotor"));
                        } else {
                            int durability = durabilityValue.getIntValue();
                            if (durability > 40) {
                                t.addLine(IKey.lang("gregtech.multiblock.turbine.rotor_durability.high",
                                        durability));
                            } else if (durability > MIN_DURABILITY_TO_WARN) {
                                t.addLine(IKey.lang("gregtech.multiblock.turbine.rotor_durability.medium",
                                        durability));
                            } else {
                                t.addLine(IKey.lang("gregtech.multiblock.turbine.rotor_durability.low",
                                        durability));
                            }
                        }
                    } else {
                        t.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
                    }
                }));
    }

    private @NotNull TextFormatting getSpeedFormat(int maxSpeed, int speed) {
        float percent = maxSpeed == 0 ? 0 : 1.0f * speed / maxSpeed;

        if (percent < 0.4) {
            return TextFormatting.RED;
        } else if (percent < 0.8) {
            return TextFormatting.YELLOW;
        } else {
            return TextFormatting.GREEN;
        }
    }

    private void createFuelTooltip(com.cleanroommc.modularui.screen.RichTooltip tooltip,
                                   FixedIntArraySyncValue amounts, StringSyncValue fuelNameValue) {
        if (isStructureFormed()) {
            Fluid fluid = fuelNameValue.getStringValue() == null ? null :
                    net.minecraftforge.fluids.FluidRegistry.getFluid(fuelNameValue.getStringValue());
            if (fluid == null) {
                tooltip.addLine(IKey.lang("gregtech.multiblock.large_combustion_engine.fuel_none"));
            } else {
                tooltip.addLine(
                        IKey.lang("gregtech.multiblock.large_combustion_engine.fuel_amount", amounts.getValue(0),
                                amounts.getValue(1), fluid.getLocalizedName(new FluidStack(fluid, 1))));
            }
        } else {
            tooltip.addLine(IKey.lang("gregtech.multiblock.invalid_structure"));
        }
    }

    /**
     * @return whatever fluid currently sits in the input tanks -- mirrors legacy
     *         {@code LargeTurbineWorkableHandler#getInputFluidStack}'s fallback, without needing a
     *         {@code previousRecipe} concept this engine doesn't have (see
     *         {@code MetaTileEntityLargeCombustionEngine}'s identical simplification).
     */
    @Nullable
    private FluidStack firstNonEmptyFuelTank() {
        if (getInputFluidInventory() == null) return null;
        for (var tank : getInputFluidInventory()) {
            FluidStack contents = tank.getFluid();
            if (contents != null && contents.amount > 0) return contents;
        }
        return null;
    }

    /**
     * @return an array of [fuel stored, fuel capacity]
     */
    private int[] getFuelAmount() {
        if (getInputFluidInventory() != null) {
            FluidStack fuelStack = firstNonEmptyFuelTank();
            if (fuelStack != null) {
                FluidStack testStack = fuelStack.copy();
                testStack.amount = Integer.MAX_VALUE;
                return getTotalFluidAmount(testStack, getInputFluidInventory());
            }
        }
        return new int[2];
    }

    /** As {@code FuelMultiblockController#getTotalFluidAmount}, verbatim (that base class is no longer extended). */
    private int[] getTotalFluidAmount(FluidStack testStack, IMultipleTankHandler multiTank) {
        int fluidAmount = 0;
        int fluidCapacity = 0;
        for (var tank : multiTank) {
            if (tank != null) {
                FluidStack drainStack = tank.drain(testStack, false);
                if (drainStack != null && drainStack.amount > 0) {
                    fluidAmount += drainStack.amount;
                    fluidCapacity += tank.getCapacity();
                }
            }
        }
        return new int[] { fluidAmount, fluidCapacity };
    }
}
