package gregtech.api.metatileentity.multiblock;

import gregtech.api.GTValues;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.impl.FluidHandlerProxy;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.ItemHandlerProxy;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.MTETrait;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.CleanroomFilter;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.DimensionFilter;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable;
import gregtech.api.util.GTTransferUtils;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * As {@link RecipeWorkableMultiblockController}, but for "free power" primitive multiblocks (Primitive Blast
 * Furnace; Coke Oven is deferred -- see design doc for why the two were split): a new, parallel class
 * rather than a subclass of it, mirroring the reasoning that produced
 * {@link RecipeWorkableSteamMultiblockController} -- legacy's {@code PrimitiveRecipeLogic} spoofs
 * infinite/free energy entirely (unlike a real {@link gregtech.api.capability.IEnergyContainer}), and this class's
 * I/O isn't gathered from {@link MultiblockAbility} hatches at all (see {@link #initializeAbilities()}), so neither
 * of {@link RecipeWorkableMultiblockController}/{@link RecipeWorkableSteamMultiblockController} fit.
 * <p>
 * <b>Self-owned, GUI-only I/O (legacy {@code RecipeMapPrimitiveMultiblockController}'s own design, kept exactly):</b>
 * unlike every ability-hatch-based multiblock, this class builds {@code importItems}/{@code exportItems}/
 * {@code importFluids}/{@code exportFluids} (base {@link gregtech.api.metatileentity.MetaTileEntity} fields) directly
 * from {@link RecipeMap#getMaxInputs()}/etc., sized once at construction (not per structure formation -- there are no
 * external ability parts to rescan), and blocks side-facing capability access entirely ({@link #getCapability}) so
 * automation can only interact through this controller's own GUI slots. A subclass may still offer external pipe
 * access by placing its own capability-exposing {@link IMultiblockPart} in the structure (e.g. Coke Oven's own
 * hatch) -- that bypasses this block at the hatch's own tile entity, not by changing this method.
 * <p>
 * <b>Free power: fixed placeholder {@link PowerSupplyProperty}, no real energy at all:</b>
 * legacy's {@code PrimitiveRecipeLogic} spoofs {@code drawEnergy}/{@code hasEnoughPower} as always
 * succeeding and reports {@code Integer.MAX_VALUE} stored/capacity -- there is nothing to actually check or drain.
 * {@link gregtech.api.recipes.logic.statemachine.RecipeLogicHooks#perTickRecipeCheck}'s own default (unconditionally
 * {@code true}) already matches this
 * exactly, so no override is needed at all. The fixed supply here exists purely so the search's voltage filter has
 * something to compare the recipe's own (deliberately tiny, see {@code PrimitiveRecipeBuilder}) advertised voltage
 * against; it is never backed by a real container.
 * <p>
 * <b>No overclocking</b> ({@code ocAmountCalculator = (v, m) -> 0}): matches legacy exactly, though not for the
 * reason one might expect -- {@code PrimitiveRecipeLogic} never actually disables overclocking
 * ({@code isAllowOverclocking()} stays at {@code AbstractRecipeLogic}'s own default {@code true}), but
 * {@code getMaximumOverclockVoltage()} is fixed at {@code V[LV]}, and {@code AbstractRecipeLogic#getNumberOfOCs}'s
 * own {@code if (maximumTier <= GTValues.LV) return 0;} guard (GT's standard "LV and below never overclocks" rule)
 * independently zeroes it out regardless. Confirmed by reading the real registered recipes
 * ({@code MachineRecipeLoader#registerPrimitiveBlastFurnaceRecipes}): durations run up to 16200 ticks (13.5 minutes,
 * block-tier smelts) and are applied as-is, unshortened.
 * <p>
 * <b>{@code config.io.itemTrim}/{@code fluidTrim} are deliberately left at their engine default
 * ({@link Integer#MAX_VALUE}), not wired to {@link gregtech.api.metatileentity.IVoidable#getItemOutputLimit()}:</b>
 * neither Primitive Blast Furnace nor Coke Oven override {@code getItemOutputLimit()}, so it returns
 * {@code IVoidable}'s own default of {@code -1} (a "no limit" sentinel, <i>not</i> {@code Integer.MAX_VALUE}).
 * Passing {@code -1} straight through to {@code RecipeIOConfig.itemTrim} (an {@code IntSupplier} the engine expects
 * to return a real, non-negative limit) would reach
 * {@code RollableOutputList#comprehensiveRoll}'s {@code new long[trimLimit]} as {@code new long[-1]} and throw
 * {@link NegativeArraySizeException} the moment any recipe here ever completed. Steam Grinder/Oven's own
 * {@code config.io.itemTrim = this::getItemOutputLimit} wiring is safe only because both concretely
 * override the method to return {@code 1}; that pattern must never be copied onto a machine that doesn't.
 */
public abstract class RecipeWorkablePrimitiveMultiblockController extends MultiblockWithDisplayBase
                                                                  implements IControllable {

    protected final @NotNull RecipeMap<?> recipeMap;
    protected final @NotNull RecipeWorkable workable;

    public RecipeWorkablePrimitiveMultiblockController(ResourceLocation metaTileEntityId,
                                                       @NotNull RecipeMap<?> recipeMap) {
        super(metaTileEntityId);
        this.recipeMap = recipeMap;
        initializeAbilities();
        this.workable = new RecipeWorkable(this, createConfig(), recipeMap);
    }

    // just initialize inventories based on RecipeMap values by default, once -- there are no ability hatches to
    // rescan on (re)formation, see this class's own JavaDoc.
    protected void initializeAbilities() {
        this.importItems = new NotifiableItemStackHandler(this, recipeMap.getMaxInputs(), this, false);
        this.importFluids = new FluidTankList(true, makeFluidTanks(recipeMap.getMaxFluidInputs(), false));
        this.exportItems = new NotifiableItemStackHandler(this, recipeMap.getMaxOutputs(), this, true);
        this.exportFluids = new FluidTankList(false, makeFluidTanks(recipeMap.getMaxFluidOutputs(), true));

        this.itemInventory = new ItemHandlerProxy(this.importItems, this.exportItems);
        this.fluidInventory = new FluidHandlerProxy(this.importFluids, this.exportFluids);
    }

    private List<FluidTank> makeFluidTanks(int length, boolean isExport) {
        List<FluidTank> fluidTankList = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            fluidTankList.add(new NotifiableFluidTank(32000, this, isExport));
        }
        return fluidTankList;
    }

    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(recipeMap));
        config.io.itemInput = this::getImportItems;
        config.io.fluidInput = this::getImportFluids;
        config.io.itemOutput = outputs -> GTTransferUtils.addItemsToItemHandler(getExportItems(), false, outputs);
        config.io.fluidOutput = outputs -> GTTransferUtils.addFluidsToFluidHandler(getExportFluids(), false, outputs);
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(GTValues.V[GTValues.LV], 1));
            properties.add(DimensionProperties.of(this));
            properties.add(CleanroomProperties.of(this));
            return properties;
        };
        if (recipeMap != null) {
            recipeMap.getBitflagLookup().registerFilter(CleanroomFilter.INSTANCE);
            recipeMap.getBitflagLookup().registerFilter(DimensionFilter.INSTANCE);
        }
        // No real overclocking -- see this class's own JavaDoc for why this matches legacy exactly despite
        // isAllowOverclocking() never actually being set to false.
        config.overclock.ocAmountCalculator = (v, m) -> 0;
        // One recipe at a time, matching every other migrated single-recipe-at-once machine's established default.
        config.parallel.parallelLimit = () -> 1;
        config.parallel.consumedParallelSupplier = () -> workable.getCommittedParallel();
        config.hooks.shouldStartRecipeLookup = data -> workable.getCommittedParallel() < config.parallel.parallelLimit
                .getAsInt();
        return config;
    }

    public @NotNull RecipeWorkable getWorkable() {
        return workable;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if ((capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY ||
                capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) && side != null) {
            return null;
        }
        return super.getCapability(capability, side);
    }

    @Override
    protected void updateFormedValid() {
        this.workable.update();
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
    public void invalidateStructure() {
        super.invalidateStructure();
        this.workable.invalidate();
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
    public SoundEvent getSound() {
        return recipeMap.getSound();
    }

    @Override
    protected boolean openGUIOnRightClick() {
        return isStructureFormed();
    }

    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }
}
