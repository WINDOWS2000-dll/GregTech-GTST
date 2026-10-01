package gregtech.api.metatileentity.multiblock;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IDistinctBusController;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.IDataInfoProvider;
import gregtech.api.metatileentity.MTETrait;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.DistinctInputGroup;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeLookup;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.CleanroomFilter;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.DimensionFilter;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.EnergyContainerProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * As legacy {@code RecipeMapMultiblockController}, but backed by {@link RecipeWorkable} instead of
 * {@code MultiblockRecipeLogic}/{@code AbstractRecipeLogic}. A new, parallel class rather than a rewrite of that
 * legacy class itself, for the same reason
 * {@link gregtech.api.metatileentity.RecipeWorkableTieredMetaTileEntity} exists alongside
 * {@code WorkableTieredMetaTileEntity}: many registered multiblocks share that one class, and this migration is
 * opt-in, machine family by machine family.
 * <p>
 * <b>Structure-dependent inventories:</b> unlike a single-block machine, {@link #getInputInventory()}/
 * {@link #getOutputInventory()}/{@link #getInputFluidInventory()}/{@link #getOutputFluidInventory()}/
 * {@link #getEnergyContainer()} only reflect real ability parts once the structure is formed
 * ({@link #initializeAbilities()}, from {@link #formStructure}); {@link #resetTileAbilities()} keeps them as
 * harmless empty handlers otherwise (mirroring legacy {@code RecipeMapMultiblockController}'s identical pattern), so
 * {@link #createConfig()}'s IO suppliers can reference them unconditionally from construction onward.
 * <p>
 * <b>Ticking only while formed:</b> {@link MultiblockControllerBase#update()} runs {@code MetaTileEntity}'s generic
 * {@code MTETrait} tick loop (which would otherwise tick {@link #workable} every tick regardless of structure
 * state) before checking {@link #isStructureFormed()} for {@link #updateFormedValid()}. {@link #shouldUpdate} opts
 * {@link #workable} out of that automatic loop; {@link #updateFormedValid()} ticks it manually instead, exactly
 * mirroring {@code MultiblockRecipeLogic}'s own {@code update()}-is-a-no-op/{@code updateWorkable()}-from-
 * {@code updateFormedValid()} split.
 * <p>
 * <b>Structure loss:</b> {@link #invalidateStructure()} resets the abilities back to empty <i>and</i> discards any
 * queued/in-progress recipe state ({@link RecipeWorkable#invalidate()}), since a recipe admitted against one
 * structure's inventories/energy must not silently keep progressing against whatever the next formation happens to
 * expose.
 * <p>
 * <b>Recipe-output preview line intentionally omitted:</b> legacy {@code RecipeMapMultiblockController}'s
 * {@code addRecipeOutputLine} depends on {@code AbstractRecipeLogic#getPreviousRecipe()} (the last-run recipe kept
 * around specifically for idle-state display), a concept this trait doesn't have (an active recipe is simply gone
 * from {@link ActiveRecipeList} once it completes). This class keeps the progress
 * line (backed by {@link RecipeWorkable#getProgressPercent}) but omits the output
 * preview rather than inventing a new persistence mechanism for it.
 * <p>
 * <b>Distinct bus mode:</b> implements {@link IDistinctBusController}
 * directly (as legacy {@code RecipeMapMultiblockController} did), bridging it to {@code config.io.distinctInputGroups}
 * (the StateMachine engine's formalized successor to legacy's {@code MultiblockRecipeLogic#trySearchNewRecipeDistinct},
 * see {@link DistinctInputGroup}'s JavaDoc) via {@link #buildDistinctInputGroups()}. {@link #canBeDistinct()}
 * defaults to {@code false} (matching legacy {@code RecipeMapMultiblockController}'s own default); a subclass whose
 * recipe
 * map benefits from per-bus matching (Implosion Compressor, Pyrolyse Oven, ...) overrides it to {@code true}, which
 * is all that's needed to light up the GUI's distinct-bus toggle button ({@code MultiblockUIFactory} checks the
 * interface, not the concrete class) and the search-time behavior alike. Unlike legacy's {@code setDistinct}, this
 * class's override does not reset a "last recipe index" cache or dirty a notified-bus list on toggle: this trait's
 * search re-reads real bus contents from scratch on every idle tick regardless (see {@code createConfig()}'s
 * {@code shouldStartRecipeLookup}), so there is no stale cache to invalidate in the first place.
 * <p>
 * <b>Cleanroom/dimension enforcement:</b> {@code createConfig()}'s default {@code config.power
 * .properties} advertises
 * {@link gregtech.api.recipes.logic.statemachine.property.impl.CleanroomFulfillmentProperty}/
 * {@link gregtech.api.recipes.logic.statemachine.property.impl.CurrentDimensionProperty} via
 * {@link gregtech.api.recipes.logic.statemachine.property.CleanroomProperties}/
 * {@link gregtech.api.recipes.logic.statemachine.property.DimensionProperties}, and registers the matching
 * {@code CleanroomFilter}/{@code DimensionFilter} on {@link #recipeMap}'s {@code BitflagRecipeLookup}. A
 * subclass that replaces {@code config.power.properties} outright (Electric Blast Furnace, Fusion Reactor) must
 * call {@link gregtech.api.recipes.logic.statemachine.property.CleanroomProperties#of}/
 * {@link gregtech.api.recipes.logic.statemachine.property.DimensionProperties#of} itself to keep this coverage,
 * since this is a field replacement, not a chained addition.
 */
public abstract class RecipeWorkableMultiblockController extends MultiblockWithDisplayBase
                                                         implements IDataInfoProvider, ICleanroomReceiver,
                                                         IControllable, IDistinctBusController,
                                                         IMultiblockRecipeLogicInfoProvider {

    protected final @NotNull RecipeMap<?> recipeMap;
    protected final @NotNull RecipeWorkable workable;
    protected IItemHandlerModifiable inputInventory;
    protected IItemHandlerModifiable outputInventory;
    protected IMultipleTankHandler inputFluidInventory;
    protected IMultipleTankHandler outputFluidInventory;
    protected IEnergyContainer energyContainer;

    private boolean isDistinct = false;

    @Nullable
    private ICleanroomProvider cleanroom;

    /** See {@link #insufficientEnergy}'s JavaDoc for why this, not a tank/buffer fill percentage, drives it. */
    private boolean lastEnergyDrainFailed = false;

    public RecipeWorkableMultiblockController(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap) {
        super(metaTileEntityId);
        this.recipeMap = recipeMap;
        resetTileAbilities();
        this.workable = createWorkable(createConfig());
    }

    /**
     * Builds this machine's {@link RecipeWorkable} from its already-built {@link RecipeLogicConfig}. Called once,
     * from the constructor. Override only for the one reason {@link RecipeWorkable}'s own JavaDoc anticipates: a
     * machine whose {@link RecipeMap} can change at runtime (e.g. Processing Array) needs
     * {@link RecipeWorkable#getRecipeMap()} itself to reflect that, which the constructor-fixed {@link #recipeMap}
     * field passed to the default implementation cannot (Processing Array passes {@code null} there and reports the
     * currently-inserted child machine's map here instead).
     */
    protected @NotNull RecipeWorkable createWorkable(@NotNull RecipeLogicConfig config) {
        return new RecipeWorkable(this, config, recipeMap);
    }

    /**
     * Builds this class's default {@link RecipeLookup}, wrapped in a fresh {@code Supplier} and passed to
     * {@link RecipeLogicConfig}'s constructor by {@link #createConfig()} (which is otherwise the only place a
     * subclass could customize the search target, but {@link RecipeLogicConfig#lookup} itself is {@code final} --
     * assignable only at construction, unlike every other field {@link RecipeLogicConfig} exposes). A subclass whose
     * {@link RecipeMap} can change at runtime (e.g. Processing Array) overrides this instead of trying to
     * reassign {@code config.lookup} after the fact.
     */
    protected @NotNull RecipeLookup createDefaultLookup() {
        return new RecipeMapLookup(recipeMap);
    }

    /**
     * Builds this machine's {@link RecipeLogicConfig}. Called once, from the constructor, after
     * {@link #resetTileAbilities()} has already given the IO/energy fields harmless empty defaults. Override to
     * customize a subclass's search/overclock/parallel behavior.
     */
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = new RecipeLogicConfig(this::createDefaultLookup);
        config.io.itemInput = this::getInputInventory;
        config.io.fluidInput = this::getInputFluidInventory;
        // config.io.distinctInputGroups is read once, at graph-build time (RecipeLookupTrackBuilder.build,
        // called exactly once from this constructor), to decide whether the *graph itself* is structurally wrapped
        // in a per-group search loop -- unlike every other RecipeLogicConfig field, this one cannot be toggled at
        // runtime by returning null sometimes and a list other times. canBeDistinct() (a compile-time-fixed override,
        // safe to call this early since it never reads instance state) is the one-time decision: false (the default)
        // leaves this field null, exactly like every already-migrated non-distinct machine. true (Implosion
        // Compressor, Pyrolyse Oven, ...) permanently installs the loop, and the *supplier itself* -- re-evaluated
        // every search pass -- degrades to a single combined-inventory group whenever isDistinct() is currently
        // false, so runtime toggling still works: RecipeLookupTrackBuilder's loop-advance check
        // (index + 1 < distinctInputGroups.get().size()) naturally never advances past a 1-element list, reproducing
        // non-distinct behavior exactly.
        if (canBeDistinct()) {
            config.io.distinctInputGroups = () -> isDistinct() ? buildDistinctInputGroups() : combinedInputGroup();
        }
        config.io.itemOutput = outputs -> GTTransferUtils.addItemsToItemHandler(getOutputInventory(), false, outputs);
        config.io.fluidOutput = outputs -> GTTransferUtils.addFluidsToFluidHandler(getOutputFluidInventory(), false,
                outputs);
        config.io.itemOutputSpace = items -> canVoidRecipeItemOutputs() ||
                GTTransferUtils.addItemsToItemHandler(getOutputInventory(), true, items);
        config.io.fluidOutputSpace = fluids -> canVoidRecipeFluidOutputs() ||
                GTTransferUtils.addFluidsToFluidHandler(getOutputFluidInventory(), true, fluids);
        config.io.itemTrim = () -> getItemOutputLimit() < 0 ? Integer.MAX_VALUE : getItemOutputLimit();
        config.io.fluidTrim = () -> getFluidOutputLimit() < 0 ? Integer.MAX_VALUE : getFluidOutputLimit();
        config.power.properties = () -> {
            RecipePropertySet properties = EnergyContainerProperties.of(getEnergyContainer());
            properties.add(DimensionProperties.of(this));
            properties.add(CleanroomProperties.of(this));
            return properties;
        };
        // Idempotent (Set-keyed by filter identity) and cheap: only needs to happen once per RecipeMap, not once
        // per instance -- see RecipeWorkableTieredMetaTileEntity's identical wiring/comment. Null-guarded: Processing
        // Array is the one subclass with no fixed RecipeMap of its own (it passes null here and
        // registers on whichever child RecipeMap is currently inserted instead, see its own createConfig()).
        if (recipeMap != null) {
            recipeMap.getBitflagLookup().registerFilter(CleanroomFilter.INSTANCE);
            recipeMap.getBitflagLookup().registerFilter(DimensionFilter.INSTANCE);
        }
        config.hooks.perTickRecipeCheck = this::drainRecipeEnergy;
        // One recipe at a time -- see RecipeWorkableTieredMetaTileEntity.createConfig()'s identical wiring for why
        // this is necessary (RecipeLookupTrackBuilder's per-tick search loop otherwise admits as many distinct
        // candidates as ingredients allow, all at once, every tick).
        config.parallel.parallelLimit = () -> 1;
        // getCommittedParallel(), not getActiveRecipeCount() -- see RecipeWorkableTieredMetaTileEntity.createConfig()'s
        // identical wiring and RecipeWorkable#getCommittedParallel's JavaDoc for why. No-op for this
        // class's own parallelLimit = 1; matters once a subclass (e.g. Multi Smelter) raises it.
        config.parallel.consumedParallelSupplier = () -> workable.getCommittedParallel();
        // See RecipeWorkableTieredMetaTileEntity
        // .createConfig()'s identical wiring for the full explanation: without this, RecipeParallelOperator's
        // amperage budget check would never learn an already-active entry's amperage was spoken for, so it would
        // keep re-granting a fresh small batch (bounded by the machine's own real energy hatch amperage) every tick
        // until the unrelated
        // count-based parallelLimit finally caught up -- ending up with several simultaneously-active entries whose
        // combined EU/t draw exceeded what the hatch's amperage rating should allow. No-op for this class's own
        // parallelLimit = 1; matters once a subclass (e.g. Processing Array) raises it.
        config.power.consumedPowerSupplier = () -> workable.getTotalRequiredEUt();
        // See RecipeWorkableTieredMetaTileEntity.createConfig()'s
        // identical wiring for the full explanation: without this, every tick pays for a full O(recipe count)
        // RecipeLookup scan even while a recipe is already active and parallelLimit=1 guarantees nothing new could
        // be admitted anyway. Mirrors legacy AbstractRecipeLogic#shouldSearchForRecipes()'s idle-only gate. Reads
        // config.parallel.parallelLimit rather than hardcoding 1 so a future multi-recipe-capable subclass gets
        // the matching behavior for free. Also uses getCommittedParallel(), not getActiveRecipeCount(), for the
        // same reason noted above.
        config.hooks.shouldStartRecipeLookup = data -> workable.getCommittedParallel() < config.parallel.parallelLimit
                .getAsInt();
        return config;
    }

    protected boolean drainRecipeEnergy(@NotNull NBTTagCompound recipeData) {
        double progress = recipeData.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY);
        double maxProgress = recipeData.getDouble(ActiveRecipeList.ENTRY_DURATION_KEY);
        long voltage = recipeData.getLong(ActiveRecipeList.ENTRY_VOLTAGE_KEY);
        long amperage = recipeData.getLong(ActiveRecipeList.ENTRY_AMPERAGE_KEY);
        long eut = (long) (Math.min(1, maxProgress - progress) * voltage * amperage);
        if (recipeData.getBoolean(ActiveRecipeList.ENTRY_GENERATING_KEY)) {
            getEnergyContainer().addEnergy(eut);
            setLastEnergyDrainFailed(false);
            return true;
        }
        boolean success = Math.abs(getEnergyContainer().removeEnergy(eut)) >= eut;
        setLastEnergyDrainFailed(!success);
        return success;
    }

    private void setLastEnergyDrainFailed(boolean lastEnergyDrainFailed) {
        if (this.lastEnergyDrainFailed != lastEnergyDrainFailed) {
            this.lastEnergyDrainFailed = lastEnergyDrainFailed;
            if (!getWorld().isRemote) {
                writeCustomData(GregtechDataCodes.LAST_ENERGY_DRAIN_FAILED,
                        buf -> buf.writeBoolean(lastEnergyDrainFailed));
            }
        }
    }

    /**
     * Drives the GUI/TheOneProbe "not enough power" indicator. As
     * {@link gregtech.api.metatileentity.RecipeWorkableTieredMetaTileEntity#insufficientEnergy}: tracks
     * {@link #drainRecipeEnergy}'s own actual per-tick pass/fail result ({@link #lastEnergyDrainFailed}), not a
     * tank/buffer fill percentage -- see that method's JavaDoc for why the fill-percentage heuristic (this method's
     * original implementation) produces false positives during otherwise-normal operation.
     */
    public boolean insufficientEnergy() {
        return isActive() && lastEnergyDrainFailed;
    }

    public IEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    public IItemHandlerModifiable getInputInventory() {
        return inputInventory;
    }

    public IItemHandlerModifiable getOutputInventory() {
        return outputInventory;
    }

    public IMultipleTankHandler getInputFluidInventory() {
        return inputFluidInventory;
    }

    public IMultipleTankHandler getOutputFluidInventory() {
        return outputFluidInventory;
    }

    public @NotNull RecipeWorkable getWorkable() {
        return workable;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        initializeAbilities();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        resetTileAbilities();
        this.workable.invalidate();
    }

    @Override
    protected void updateFormedValid() {
        if (!hasMufflerMechanics() || isMufflerFaceFree()) {
            this.workable.update();
        }
    }

    @Override
    protected boolean shouldUpdate(MTETrait trait) {
        return trait != this.workable;
    }

    @Override
    public boolean isActive() {
        return isStructureFormed() && workable.isActive() && workable.isWorkingEnabled();
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(),
                isOverlayActive(), workable.isWorkingEnabled());
    }

    /**
     * @return the "active" flag passed to the front overlay's render call, overridable for a subclass whose real
     *         "is this machine working" signal isn't simply {@code workable.isActive()} (e.g.
     *         {@code MetaTileEntityLargeTurbine}'s {@code isGeneratingPower()} -- see that class's own JavaDoc).
     *         Deliberately a separate override point rather than having such a subclass override
     *         {@link #renderMetaTileEntity} itself and call {@code super.renderMetaTileEntity(...)}: that would
     *         re-invoke <i>this</i> method's own front-overlay draw first (with the wrong flag), then the
     *         subclass's own draw on top (e.g. on Large Turbine): both overlay
     *         textures would render simultaneously, visibly overlapping/ghosting during any activity transition.
     */
    protected boolean isOverlayActive() {
        return workable.isActive();
    }

    protected void initializeAbilities() {
        this.inputInventory = new ItemHandlerList(getAbilities(MultiblockAbility.IMPORT_ITEMS));
        this.inputFluidInventory = new FluidTankList(allowSameFluidFillForOutputs(),
                getAbilities(MultiblockAbility.IMPORT_FLUIDS));
        this.outputInventory = new ItemHandlerList(getAbilities(MultiblockAbility.EXPORT_ITEMS));
        this.outputFluidInventory = new FluidTankList(allowSameFluidFillForOutputs(),
                getAbilities(MultiblockAbility.EXPORT_FLUIDS));

        List<IEnergyContainer> inputEnergy = new ArrayList<>(getAbilities(MultiblockAbility.INPUT_ENERGY));
        inputEnergy.addAll(getAbilities(MultiblockAbility.SUBSTATION_INPUT_ENERGY));
        inputEnergy.addAll(getAbilities(MultiblockAbility.INPUT_LASER));
        this.energyContainer = new EnergyContainerList(inputEnergy);
    }

    /**
     * Builds one {@link DistinctInputGroup} per real import bus, mirroring
     * legacy {@code MultiblockRecipeLogic#trySearchNewRecipeDistinct}'s own per-bus lookup call
     * ({@code findRecipe(maxVoltage, bus, getInputTank(bus))}): fluids are <i>not</i> split per bus (legacy never
     * splits them either) &mdash; every group shares {@link #getInputFluidInventory()}'s tanks, plus whatever extra
     * tank {@code bus} itself happens to expose via {@link #groupFluidTanks}, for the "one auto-output-capable input
     * bus plus its adjacent input hatch" case {@link DistinctInputGroup}'s own JavaDoc calls out. Only called while
     * {@link #isDistinct()} is {@code true} (see {@code createConfig()}'s wiring); re-evaluated fresh on every
     * search pass, so a structure re-formation with a different bus count needs no special handling here.
     */
    protected @NotNull List<DistinctInputGroup> buildDistinctInputGroups() {
        List<IItemHandlerModifiable> buses = getAbilities(MultiblockAbility.IMPORT_ITEMS);
        List<DistinctInputGroup> groups = new ArrayList<>(buses.size());
        for (IItemHandlerModifiable bus : buses) {
            groups.add(DistinctInputGroup.of(GTUtility.itemHandlerToList(bus),
                    GTUtility.fluidHandlerToList(groupFluidTanks(bus))));
        }
        return groups;
    }

    /**
     * @return a single {@link DistinctInputGroup} covering this controller's whole combined inventory, exactly as
     *         a plain (non-distinct) {@code RecipeIOConfig.itemInputView}/{@code fluidInputView} would see it. Used
     *         by {@code createConfig()}'s {@code distinctInputGroups} wiring while {@link #isDistinct()} is
     *         {@code false}, so a {@link #canBeDistinct()} machine's search-time behavior matches every other
     *         (non-distinct-capable) machine's until the player actually toggles distinct mode on.
     */
    private @NotNull List<DistinctInputGroup> combinedInputGroup() {
        return Collections.singletonList(DistinctInputGroup.of(GTUtility.itemHandlerToList(getInputInventory()),
                GTUtility.fluidHandlerToList(getInputFluidInventory())));
    }

    /**
     * @return {@link #getInputFluidInventory()}'s shared tanks, plus {@code bus}'s own tank capability if it has one
     *         (a dual item+fluid hatch) &mdash; exactly {@code MultiblockRecipeLogic#getInputTank(IItemHandler)}'s
     *         legacy formula.
     */
    private @NotNull IMultipleTankHandler groupFluidTanks(@NotNull IItemHandlerModifiable bus) {
        if (!(bus instanceof IMultipleTankHandler busTanks)) return getInputFluidInventory();
        List<IMultipleTankHandler.ITankEntry> tanks = new ArrayList<>(getInputFluidInventory().getFluidTanks());
        tanks.addAll(busTanks.getFluidTanks());
        return new FluidTankList(allowSameFluidFillForOutputs(), tanks);
    }

    @Override
    public boolean canBeDistinct() {
        return false;
    }

    @Override
    public boolean isDistinct() {
        return isDistinct;
    }

    @Override
    public void setDistinct(boolean isDistinct) {
        this.isDistinct = isDistinct;
        getMultiblockParts().forEach(part -> part.onDistinctChange(isDistinct));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("isDistinct", isDistinct);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        isDistinct = data.getBoolean("isDistinct");
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(isDistinct);
        buf.writeBoolean(lastEnergyDrainFailed);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        isDistinct = buf.readBoolean();
        lastEnergyDrainFailed = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.LAST_ENERGY_DRAIN_FAILED) {
            this.lastEnergyDrainFailed = buf.readBoolean();
        }
    }

    private void resetTileAbilities() {
        this.inputInventory = new GTItemStackHandler(this, 0);
        this.inputFluidInventory = new FluidTankList(true);
        this.outputInventory = new GTItemStackHandler(this, 0);
        this.outputFluidInventory = new FluidTankList(true);
        this.energyContainer = new EnergyContainerList(Lists.newArrayList());
    }

    protected boolean allowSameFluidFillForOutputs() {
        return true;
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(workable.isWorkingEnabled(), workable.isActive())
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(getEnergyContainer().getInputVoltage()))
                .addWorkingStatusLine()
                .addProgressLine(workable.getProgress(), workable.getMaxProgress());
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addLowPowerLine(this::insufficientEnergy);
        super.configureWarningText(builder);
    }

    @Override
    public TraceabilityPredicate autoAbilities() {
        return autoAbilities(true, true, true, true, true, true, true);
    }

    public TraceabilityPredicate autoAbilities(boolean checkEnergyIn, boolean checkMaintenance, boolean checkItemIn,
                                               boolean checkItemOut, boolean checkFluidIn, boolean checkFluidOut,
                                               boolean checkMuffler) {
        TraceabilityPredicate predicate = super.autoAbilities(checkMaintenance, checkMuffler);

        if (checkEnergyIn) {
            predicate = predicate.or(abilities(MultiblockAbility.INPUT_ENERGY).setMinGlobalLimited(1)
                    .setMaxGlobalLimited(2)
                    .setPreviewCount(1));
        }
        if (checkItemIn && recipeMap.getMaxInputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.IMPORT_ITEMS).setPreviewCount(1));
        }
        if (checkItemOut && recipeMap.getMaxOutputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.EXPORT_ITEMS).setPreviewCount(1));
        }
        if (checkFluidIn && recipeMap.getMaxFluidInputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.IMPORT_FLUIDS).setPreviewCount(1));
        }
        if (checkFluidOut && recipeMap.getMaxFluidOutputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.EXPORT_FLUIDS).setPreviewCount(1));
        }
        return predicate;
    }

    @Override
    public SoundEvent getSound() {
        return recipeMap.getSound();
    }

    @Override
    public boolean isWorkingEnabled() {
        return workable.isWorkingEnabled();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        workable.setWorkingEnabled(isWorkingAllowed);
    }

    @Override
    public @NotNull List<ITextComponent> getDataInfo() {
        List<ITextComponent> list = new ArrayList<>();
        if (workable.getMaxProgress() > 0) {
            list.add(new TextComponentTranslation("behavior.tricorder.workable_progress",
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(workable.getProgress() / 20))
                            .setStyle(new Style().setColor(TextFormatting.GREEN)),
                    new TextComponentTranslation(TextFormattingUtil.formatNumbers(workable.getMaxProgress() / 20))
                            .setStyle(new Style().setColor(TextFormatting.YELLOW))));
        }

        list.add(new TextComponentTranslation("behavior.tricorder.energy_container_storage",
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(energyContainer.getEnergyStored()))
                        .setStyle(new Style().setColor(TextFormatting.GREEN)),
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(energyContainer.getEnergyCapacity()))
                        .setStyle(new Style().setColor(TextFormatting.YELLOW))));

        list.add(new TextComponentTranslation("behavior.tricorder.multiblock_energy_input",
                new TextComponentTranslation(TextFormattingUtil.formatNumbers(energyContainer.getInputVoltage()))
                        .setStyle(new Style().setColor(TextFormatting.YELLOW)),
                new TextComponentTranslation(GTValues.VN[GTUtility.getTierByVoltage(energyContainer.getInputVoltage())])
                        .setStyle(new Style().setColor(TextFormatting.YELLOW))));

        return list;
    }

    @Nullable
    @Override
    public ICleanroomProvider getCleanroom() {
        return this.cleanroom;
    }

    @Override
    public void setCleanroom(@NotNull ICleanroomProvider provider) {
        if (cleanroom == null || provider.getPriority() > cleanroom.getPriority()) {
            this.cleanroom = provider;
        }
    }

    @Override
    public void unsetCleanroom() {
        this.cleanroom = null;
    }
}
