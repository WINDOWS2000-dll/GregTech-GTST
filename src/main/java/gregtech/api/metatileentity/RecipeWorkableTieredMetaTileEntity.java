package gregtech.api.metatileentity;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.metatileentity.multiblock.ICleanroomReceiver;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.CleanroomFilter;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.DimensionFilter;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.EnergyContainerProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable;
import gregtech.api.util.GTTransferUtils;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * As legacy {@code WorkableTieredMetaTileEntity}, but backed by {@link RecipeWorkable}
 * instead of {@code AbstractRecipeLogic}. A new, parallel base class rather than a
 * rewrite of that legacy class itself: hundreds of existing machines extend that class, and this
 * migration is deliberately opt-in, machine family by machine family &mdash; nothing here changes how any existing
 * machine behaves.
 * <p>
 * Adapted to
 * GregTech's {@link MetaTileEntity#getRecipeMap()} being {@code final}: {@link #recipeMap} is still kept
 * as its own field here for this class's own use (inventory sizing, tooltips, sound), but {@link IHasRecipeMap}
 * itself is implemented by {@link #workable} (see {@link RecipeWorkable#getRecipeMap()}), which
 * {@link MetaTileEntity#getRecipeMap()}'s generic trait scan already finds &mdash; this class doesn't need to (and,
 * being {@code final}, couldn't) implement it again. {@link IControllable} is implemented here by delegating to
 * {@link #workable}'s concrete API, rather than introducing a bridging interface between this and
 * a legacy recipe-logic trait's much larger surface (see {@link IHasRecipeMap}'s JavaDoc for why only
 * {@code getRecipeMap()} was worth generalizing that way).
 * <p>
 * <b>{@link ICleanroomReceiver}:</b> legacy {@code WorkableTieredMetaTileEntity} implements this so
 * {@code AbstractRecipeLogic#checkCleanroomRequirement} has something to query; this class needs the same, since a
 * single-block machine's own recipes can be cleanroom-gated too (e.g. Circuit Assembler/Cutting Machine/Laser
 * Engraver). {@link #createConfig}'s default {@code config.power.properties}
 * advertises this via {@link CleanroomProperties#of}, mirroring
 * {@link gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController}'s own
 * implementation.
 */
public abstract class RecipeWorkableTieredMetaTileEntity extends TieredMetaTileEntity
                                                         implements IControllable, ICleanroomReceiver {

    protected final @NotNull RecipeMap<?> recipeMap;
    protected final @NotNull RecipeWorkable workable;
    protected final @NotNull ICubeRenderer renderer;

    private final @NotNull Function<Integer, Integer> tankScalingFunction;

    @Nullable
    private ICleanroomProvider cleanroom;

    /** See {@link #insufficientEnergy}'s JavaDoc for why this, not a tank/buffer fill percentage, drives it. */
    private boolean lastEnergyDrainFailed = false;

    public RecipeWorkableTieredMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap,
                                              ICubeRenderer renderer, int tier,
                                              Function<Integer, Integer> tankScalingFunction) {
        super(metaTileEntityId, tier);
        this.recipeMap = recipeMap;
        this.renderer = renderer;
        this.tankScalingFunction = tankScalingFunction;
        initializeInventory();
        reinitializeEnergyContainer();
        this.workable = new RecipeWorkable(this, createConfig(recipeMap), recipeMap);
    }

    /**
     * Builds this machine's {@link RecipeLogicConfig}. Called once, from the constructor, after inventories and
     * the energy container already exist (so their suppliers can be wired directly). Override to customize a
     * subclass's search/overclock/parallel behavior; the standard wiring here covers a single-recipe-at-a-time
     * electric machine with no special requirements, the same ground {@code RecipeLogicEnergy} covers for
     * {@code AbstractRecipeLogic}-based machines.
     */
    protected @NotNull RecipeLogicConfig createConfig(@NotNull RecipeMap<?> recipeMap) {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(recipeMap));
        config.io.itemInput = this::getImportItems;
        config.io.fluidInput = this::getImportFluids;
        config.io.itemOutput = outputs -> GTTransferUtils.addItemsToItemHandler(getExportItems(), false, outputs);
        config.io.fluidOutput = outputs -> GTTransferUtils.addFluidsToFluidHandler(getExportFluids(), false, outputs);
        config.io.itemOutputSpace = items -> canVoidRecipeItemOutputs() ||
                GTTransferUtils.addItemsToItemHandler(getExportItems(), true, items);
        config.io.fluidOutputSpace = fluids -> canVoidRecipeFluidOutputs() ||
                GTTransferUtils.addFluidsToFluidHandler(getExportFluids(), true, fluids);
        config.io.itemTrim = () -> getItemOutputLimit() < 0 ? Integer.MAX_VALUE : getItemOutputLimit();
        config.io.fluidTrim = () -> getFluidOutputLimit() < 0 ? Integer.MAX_VALUE : getFluidOutputLimit();
        config.power.properties = () -> {
            RecipePropertySet properties = EnergyContainerProperties.of(getEnergyContainer());
            properties.add(DimensionProperties.of(this));
            properties.add(CleanroomProperties.of(this));
            return properties;
        };
        // Idempotent (Set-keyed by filter identity) and cheap: only needs to happen once per RecipeMap, not once
        // per instance, but there is no dedicated one-time machine-type-registration hook to hang this on instead
        // (see MetaTileEntityFusionReactor's identical comment for the established precedent).
        recipeMap.getBitflagLookup().registerFilter(CleanroomFilter.INSTANCE);
        recipeMap.getBitflagLookup().registerFilter(DimensionFilter.INSTANCE);
        config.hooks.perTickRecipeCheck = this::drainRecipeEnergy;
        // One recipe at a time (matching AbstractRecipeLogic-based machines): without this, RecipeLookupTrackBuilder's
        // per-tick search loop (deliberately designed to admit several genuinely independent candidates per pass,
        // for future multi-recipe-capable machines) keeps admitting more as long as ingredients remain and nothing
        // caps the budget -- e.g. RecipeMapFurnace's one-unit-at-a-time vanilla-smelting fallback would get admitted
        // once per remaining unit within a single tick (all 64 of a Magnetite Dust stack at once, observed as a
        // real-machine regression), and even capped at 1 per tick, a fresh recipe would still get admitted every
        // subsequent tick while the previous one is still progressing. parallelLimit=1 caps a single search pass at
        // one admission; consumedParallelSupplier reporting the current active count blocks every following tick's
        // search for as long as that one is still active. A future multi-recipe-capable subclass can override
        // createConfig() to lift this.
        config.parallel.parallelLimit = () -> 1;
        // Deliberately a lambda, not a method reference: this method runs before this.workable is assigned (see
        // the constructor), so an eagerly-bound method reference would capture null. This defers the field read to
        // call time, by which point construction has completed.
        //
        // getCommittedParallel(), not getActiveRecipeCount() -- see RecipeWorkable#getCommittedParallel's JavaDoc
        // for why: a single active entry can already represent many parallel copies once RecipeParallelOperator
        // has multiplied a candidate up, so counting entries (as this used to) drastically undercounts how much of
        // the budget is spent for any machine raising parallelLimit above 1. For this class's own parallelLimit = 1
        // the two are numerically identical (at most one entry, whose own amperage is 1 for the vast majority of
        // recipes), so this fix is a no-op here -- it matters once a subclass overrides parallelLimit upward.
        config.parallel.consumedParallelSupplier = () -> workable.getCommittedParallel();
        // Without this, RecipeParallelOperator's own amperage budget check (RecipePowerConfig#getAvailableAmperage)
        // would only
        // ever see *this search pass's* own claims (RecipeSearchOperator.EUT_CONSUMED_KEY), never what
        // already-active entries from earlier ticks are still drawing. Every tick, the check would re-derive "how
        // many amps can this candidate draw" from the hatch's raw amperage alone, as if nothing were already
        // running -- e.g. a consuming multiblock with a real energy hatch amperage smaller than its
        // machine-count-based parallelLimit would admit a fresh small batch (limited by real hatch amperage,
        // e.g. 2A) every tick until the unrelated *count*-based parallelLimit finally caught up, ending up with
        // several simultaneously-active entries whose combined EU/t draw far exceeded what the hatch's amperage
        // rating should allow. getTotalRequiredEUt() (EU/t, not raw amperage -- comparable across differing entry
        // voltage tiers, see RecipePowerConfig#consumedPowerSupplier's JavaDoc) is this class's own already-existing
        // dev-tooling accessor for exactly this sum, mirroring consumedParallelSupplier above. For this class's own
        // parallelLimit = 1 this is a no-op (identical reasoning to consumedParallelSupplier's own comment above);
        // it matters once a subclass raises parallelLimit above 1.
        config.power.consumedPowerSupplier = () -> workable.getTotalRequiredEUt();
        // Without this, RecipeSearchOperator calls
        // config.lookup.get().findRecipes(...) -- an O(recipe count) scan through the *entire* RecipeMap, running
        // Recipe#matches (the ingredients-matching engine, itself non-trivial per candidate) against every one --
        // unconditionally, every single tick, even while a recipe is already active and parallelLimit=1 guarantees
        // no new one could be admitted regardless of what the search finds. For a RecipeMap with hundreds of
        // recipes (e.g. the Assembler's), this can cost tens of milliseconds of wasted work per tick per
        // machine, real enough to trip GregTech's own per-tile lag-source watchdog. Mirrors legacy
        // AbstractRecipeLogic#shouldSearchForRecipes()'s "idle-only" gate (a search only ever starts while nothing
        // is active), which every migrated machine should have from the start; a subclass that overrides
        // shouldStartRecipeLookup (e.g. Rock Breaker's fluids gate) must AND this condition in rather than replace
        // it, or it silently loses this optimization. Reads config.parallel.parallelLimit rather than hardcoding 1
        // so a future multi-recipe-capable subclass (which already has to raise parallelLimit to lift the cap
        // above) gets the matching "stop searching once full" behavior for free, without also having to remember
        // to update this hook. Also uses getCommittedParallel(), not getActiveRecipeCount(), for the same reason
        // as consumedParallelSupplier above -- entry count alone would never come close to parallelLimit on a
        // machine like Multi Smelter (parallelLimit=32), so this hook would otherwise keep authorizing a fresh
        // search every tick.
        config.hooks.shouldStartRecipeLookup = data -> workable.getCommittedParallel() < config.parallel.parallelLimit
                .getAsInt();
        return config;
    }

    /**
     * Draws (or, for a generating recipe, produces) the currently-progressing recipe's own EU/t from/into
     * {@link #getEnergyContainer()}. {@code recipeData} is the active
     * {@link ActiveRecipeList} entry currently selected by the progress track, passed by reference &mdash; this
     * only reads it.
     * <p>
     * The {@code Math.min(1, maxProgress - progress)} factor scales the draw down on a recipe's final, possibly
     * fractional tick (post-overclock durations can be fractional), so that tick doesn't draw a full tick's worth
     * of EU/t for less than a full tick's worth of progress.
     */
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

    protected IEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    @Nullable
    @Override
    public ICleanroomProvider getCleanroom() {
        return this.cleanroom;
    }

    @Override
    public void setCleanroom(@NotNull ICleanroomProvider provider) {
        this.cleanroom = provider;
    }

    @Override
    public void unsetCleanroom() {
        this.cleanroom = null;
    }

    /**
     * Drives the GUI "not enough power" indicator. Tracks {@link #drainRecipeEnergy}'s own actual per-tick
     * pass/fail result ({@link #lastEnergyDrainFailed}), not a tank/buffer fill percentage: an
     * {@code isActive() && getEnergyStored() <= getEnergyCapacity() * 0.1} heuristic (this method's original
     * implementation) is disconnected from whether energy actually ran short on any given tick -- e.g.
     * {@link #reinitializeEnergyContainer}'s own {@code getInputAmperage()} override deliberately only grants 2A
     * input once the buffer has drained below 50% capacity, so a machine drawing 2A steadily (fully supplied every
     * tick) can legitimately sit at or below the old 10% threshold as a side effect of that amperage-gating
     * hysteresis alone, well before supply is actually insufficient -- a false "not enough power" indicator despite
     * the machine never having failed to draw its full EU/t. Mirrors the identical fix already applied to
     * {@link gregtech.api.metatileentity.SteamMetaTileEntity#insufficientSteam()} for the same class of bug.
     */
    public boolean insufficientEnergy() {
        return isActive() && lastEnergyDrainFailed;
    }

    @Override
    protected void reinitializeEnergyContainer() {
        long tierVoltage = GTValues.V[getTier()];
        this.energyContainer = new EnergyContainerHandler(this, tierVoltage * 64L, tierVoltage, 2, 0L, 0L) {

            @Override
            public long getInputAmperage() {
                if (getEnergyCapacity() / 2 > getEnergyStored() && workable.isWorkingEnabled()) {
                    return 2;
                }
                return 1;
            }
        };
    }

    @Override
    protected long getMaxInputOutputAmperage() {
        return 2L;
    }

    @Override
    public boolean isWorkingEnabled() {
        return workable.isWorkingEnabled();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        workable.setWorkingEnabled(isWorkingAllowed);
    }

    public boolean isActive() {
        return workable.isActive() && workable.isWorkingEnabled();
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        renderOverlays(renderState, translation, pipeline);
    }

    /**
     * Renders this machine's front-facing overlay (on/off state). Split out from {@link #renderMetaTileEntity} as
     * its own overridable step so a subclass needing a different rendering shape (e.g.
     * {@code RecipeWorkableSingleTurbineMetaTileEntity} rendering on 4 sides when facing vertically, mirroring
     * legacy {@code MetaTileEntitySingleTurbine#renderOverlays}) can replace just this piece.
     */
    protected void renderOverlays(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        renderer.renderOrientedState(renderState, translation, pipeline, getFrontFacing(), workable.isActive(),
                workable.isWorkingEnabled());
    }

    // recipeMap/tankScalingFunction are still null the *first* time these are called: MetaTileEntity's own
    // constructor calls initializeInventory() before this class's constructor body has assigned them (see the
    // explicit re-call to initializeInventory() there, once they're actually set) - matching
    // WorkableTieredMetaTileEntity's identical null-guarded pattern.

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        if (recipeMap == null) return new GTItemStackHandler(this, 0);
        return new NotifiableItemStackHandler(this, recipeMap.getMaxInputs(), this, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        if (recipeMap == null) return new GTItemStackHandler(this, 0);
        return new NotifiableItemStackHandler(this, recipeMap.getMaxOutputs(), this, true);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        if (recipeMap == null) return new FluidTankList(false);
        NotifiableFluidTank[] fluidImports = new NotifiableFluidTank[recipeMap.getMaxFluidInputs()];
        for (int i = 0; i < fluidImports.length; i++) {
            fluidImports[i] = new NotifiableFluidTank(this.tankScalingFunction.apply(this.getTier()), this, false);
        }
        return new FluidTankList(false, fluidImports);
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        if (recipeMap == null) return new FluidTankList(false);
        FluidTank[] fluidExports = new FluidTank[recipeMap.getMaxFluidOutputs()];
        for (int i = 0; i < fluidExports.length; i++) {
            fluidExports[i] = new NotifiableFluidTank(this.tankScalingFunction.apply(this.getTier()), this, true);
        }
        return new FluidTankList(false, fluidExports);
    }

    @Override
    public SoundEvent getSound() {
        return recipeMap.getSound();
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_in", energyContainer.getInputVoltage(),
                GTValues.VNF[getTier()]));
        tooltip.add(I18n.format("gregtech.universal.tooltip.energy_storage_capacity",
                energyContainer.getEnergyCapacity()));
        if (recipeMap.getMaxFluidInputs() != 0) {
            tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity",
                    this.tankScalingFunction.apply(getTier())));
        }
    }

    public @NotNull Function<Integer, Integer> getTankScalingFunction() {
        return tankScalingFunction;
    }

    // Minecraft's FontRenderer FONT_HEIGHT value; matches legacy WorkableTieredMetaTileEntity's identically-named
    // constant.
    private static final int FONT_HEIGHT = 9;

    /**
     * @return the extra vertical offset the GUI should reserve below the recipe UI when {@link #recipeMap} has
     *         enough item/fluid slots that its {@link RecipeMap#getRecipeMapUI()} needs an extra row for them
     *         (verbatim port of legacy {@code WorkableTieredMetaTileEntity#getRecipeUIYOffset()}).
     */
    protected int getRecipeUIYOffset() {
        if (recipeMap.getMaxInputs() >= 6 || recipeMap.getMaxFluidInputs() >= 6 ||
                recipeMap.getMaxOutputs() >= 6 || recipeMap.getMaxFluidOutputs() >= 6) {
            return FONT_HEIGHT;
        }
        return 0;
    }

    @Override
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(lastEnergyDrainFailed);
    }

    @Override
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.lastEnergyDrainFailed = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.LAST_ENERGY_DRAIN_FAILED) {
            this.lastEnergyDrainFailed = buf.readBoolean();
        }
    }
}
