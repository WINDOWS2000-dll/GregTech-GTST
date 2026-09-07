package gregtech.api.metatileentity.multiblock;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IVentable;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.damagesources.DamageSources;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MTETrait;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.pattern.TraceabilityPredicate;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.CleanroomFilter;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.DimensionFilter;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable;
import gregtech.api.util.FacingPos;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.common.ConfigHolder;
import gregtech.core.advancement.AdvancementTriggers;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;

/**
 * As {@link RecipeWorkableMultiblockController}, but for steam multiblocks (Steam Grinder/Steam Oven): a new,
 * parallel class rather than a subclass of it, since {@link MultiblockAbility#STEAM}/{@code STEAM_IMPORT_ITEMS}/
 * {@code STEAM_EXPORT_ITEMS} are separate ability keys from the EU multiblock abilities that class is built around,
 * and there is no {@link gregtech.api.capability.IEnergyContainer} concept here at all.
 * <p>
 * <b>Fixed parallel budget, not real hatch amperage:</b> unlike
 * {@link RecipeWorkableMultiblockController}'s subclasses (whose {@code downTransformForParallels} budget comes
 * from real energy hatches), a steam ability part has no voltage/amperage of its own -- there's nothing to read a
 * real budget from. A fixed {@link PowerSupplyProperty}
 * ({@code V[LV]} &times; {@link #getBaseParallelLimit()}) drives both the admission voltage check and (via
 * {@code config.power.downTransformForParallels}) the parallel count ceiling, replacing legacy's
 * {@code SteamMultiWorkable}/{@code ParallelLogicType.APPEND_ITEMS} hand-rolled recipe-merging formula with the
 * standard engine's per-copy-amperage model (the same substitution used for Multi Smelter).
 * <p>
 * <b>Duration bonus: a flat {@code durationDiscount = 1.5}, not legacy's per-parallel-count formula:</b>
 * legacy's {@code SteamMultiWorkable#applyParallelBonus} computed
 * {@code EUt = min(32, ceil(EUt*1.33))}/{@code duration = (duration/parallelLimit)*1.5} by hand on a single merged
 * recipe; the standard engine has no such per-copy merge step to hook, so this is reduced to a flat
 * duration multiplier instead.
 * <p>
 * <b>Venting: front-facing only, zero damage</b> (verified against legacy {@code SteamMultiblockRecipeLogic
 * #ventSteam}): unlike a single-block steam machine ({@link gregtech.api.metatileentity.SteamMetaTileEntity},
 * wrench-selectable
 * side, real damage), a multiblock always vents at {@link #getFrontFacing()} and never damages nearby entities.
 * Uses the same hooks-only mechanism established for the
 * single-block class ({@code onRecipeCompleted}/{@code shouldStartRecipeLookup}/{@code perTickWorkerCheck}), not a
 * dedicated {@code RecipeWorkable} subclass -- no new engine extension point is
 * needed for either machine class.
 * <p>
 * <b>{@code insufficientSteam()} tracks actual per-tick drain failure, not tank fill percentage</b> (deliberate
 * deviation from legacy's {@code isActive() && fill <= 10%} heuristic, which is disconnected from whether steam
 * actually ran out on any given tick -- see the single-block {@code SteamMetaTileEntity}'s own JavaDoc). This class
 * reuses the same fix: {@link #drainSteam}'s own pass/fail result feeds {@link #lastDrainFailed} directly.
 */
public abstract class RecipeWorkableSteamMultiblockController extends MultiblockWithDisplayBase
                                                               implements IControllable, IVentable {

    protected static final double CONVERSION_RATE = ConfigHolder.machines.multiblockSteamToEU;

    protected final @NotNull RecipeMap<?> recipeMap;
    protected final @NotNull RecipeWorkable workable;
    protected IItemHandlerModifiable inputInventory;
    protected IItemHandlerModifiable outputInventory;
    protected IMultipleTankHandler steamFluidTank;

    private boolean needsVenting;
    private boolean ventingStuck;

    private boolean lastDrainFailed = false;

    public RecipeWorkableSteamMultiblockController(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap) {
        super(metaTileEntityId);
        this.recipeMap = recipeMap;
        resetTileAbilities();
        this.workable = new RecipeWorkable(this, createConfig(), recipeMap);
    }

    /**
     * @return the fixed amperage this machine's steam supply advertises -- both the parallel ceiling
     *         ({@code config.power.downTransformForParallels}) and the raw voltage-check amperage. {@code 1} by
     *         default (matching PR's own default); Steam Grinder/Steam Oven override to {@code 8}, matching their
     *         legacy {@code PARALLEL_LIMIT}/{@code MAX_PARALLELS}.
     */
    protected int getBaseParallelLimit() {
        return 1;
    }

    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(recipeMap));
        config.io.itemInput = this::getInputInventory;
        config.io.itemOutput = outputs -> GTTransferUtils.addItemsToItemHandler(getOutputInventory(), false, outputs);
        // Without this, byproducts (chance outputs) would be
        // granted in full despite getItemOutputLimit()==1 -- config.io.itemTrim is only consulted by
        // RecipeOutputSpaceCheckOperator's pre-admission worst-case check, never by the actual roll
        // (StandardRecipeView#rollItems/StandardRecipeRun) unless wired here too. This wiring is what actually
        // activates the trim for this machine family, matching legacy's (`Recipe#trimRecipeOutputs`) intent.
        config.io.itemTrim = this::getItemOutputLimit;
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(GTValues.V[GTValues.LV], getBaseParallelLimit()));
            properties.add(DimensionProperties.of(this));
            properties.add(CleanroomProperties.of(this));
            return properties;
        };
        if (recipeMap != null) {
            recipeMap.getBitflagLookup().registerFilter(CleanroomFilter.INSTANCE);
            recipeMap.getBitflagLookup().registerFilter(DimensionFilter.INSTANCE);
        }
        // No real overclocking (legacy isAllowOverclocking() == false); PR's flat duration discount replaces
        // legacy's per-parallel-count formula -- see this class's own JavaDoc.
        config.overclock.ocAmountCalculator = (v, m) -> 0;
        config.overclock.durationDiscount = () -> 1.5;
        config.power.downTransformForParallels = true;
        config.hooks.perTickRecipeCheck = this::drainSteam;
        config.parallel.parallelLimit = this::getBaseParallelLimit;
        config.parallel.consumedParallelSupplier = () -> workable.getCommittedParallel();
        config.hooks.shouldStartRecipeLookup = data -> !isNeedsVenting() &&
                workable.getCommittedParallel() < config.parallel.parallelLimit.getAsInt();
        config.callbacks.onRecipeCompleted = recipe -> {
            setLastDrainFailed(false);
            setNeedsVenting(true);
            tryDoVenting();
        };
        config.hooks.perTickWorkerCheck = data -> {
            if (isNeedsVenting() && getOffsetTimer() % 10 == 0) {
                tryDoVenting();
            }
            return true;
        };
        return config;
    }

    protected boolean drainSteam(@NotNull NBTTagCompound recipeData) {
        double progress = recipeData.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY);
        double maxProgress = recipeData.getDouble(ActiveRecipeList.ENTRY_DURATION_KEY);
        long voltage = recipeData.getLong(ActiveRecipeList.ENTRY_VOLTAGE_KEY);
        long amperage = recipeData.getLong(ActiveRecipeList.ENTRY_AMPERAGE_KEY);
        long eut = (long) (Math.min(1, maxProgress - progress) * voltage * amperage);
        int steamNeeded = GTUtility.safeCastLongToInt((long) Math.ceil(eut / CONVERSION_RATE));
        FluidStack drained = steamFluidTank.drain(steamNeeded, true);
        boolean success = drained != null && drained.amount >= steamNeeded;
        setLastDrainFailed(!success);
        return success;
    }

    private void setLastDrainFailed(boolean lastDrainFailed) {
        if (this.lastDrainFailed != lastDrainFailed) {
            this.lastDrainFailed = lastDrainFailed;
            if (!getWorld().isRemote) {
                writeCustomData(GregtechDataCodes.LAST_STEAM_DRAIN_FAILED, buf -> buf.writeBoolean(lastDrainFailed));
            }
        }
    }

    /** See this class's own JavaDoc for why this doesn't use the {@code fill <= 10%} heuristic. */
    protected boolean insufficientSteam() {
        return isActive() && lastDrainFailed;
    }

    public IItemHandlerModifiable getInputInventory() {
        return inputInventory;
    }

    public IItemHandlerModifiable getOutputInventory() {
        return outputInventory;
    }

    public IMultipleTankHandler getSteamFluidTank() {
        return steamFluidTank;
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
    public boolean isWorkingEnabled() {
        return workable.isWorkingEnabled();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        workable.setWorkingEnabled(isWorkingAllowed);
    }

    // --- Venting (IVentable): front-facing only, zero damage -- see this class's own JavaDoc ---

    @Override
    public boolean isVentingStuck() {
        return needsVenting && ventingStuck;
    }

    @Override
    public boolean isNeedsVenting() {
        return needsVenting;
    }

    @Override
    public void setNeedsVenting(boolean needsVenting) {
        this.needsVenting = needsVenting;
        if (!needsVenting && ventingStuck) setVentingStuck(false);
        if (!getWorld().isRemote) {
            markDirty();
            writeCustomData(GregtechDataCodes.NEEDS_VENTING, buf -> buf.writeBoolean(needsVenting));
        }
    }

    public void setVentingStuck(boolean ventingStuck) {
        this.ventingStuck = ventingStuck;
        if (!getWorld().isRemote) {
            markDirty();
            writeCustomData(GregtechDataCodes.VENTING_STUCK, buf -> buf.writeBoolean(ventingStuck));
        }
    }

    protected float getVentingDamage() {
        return 0;
    }

    @Override
    public void tryDoVenting() {
        FacingPos facingPos = new FacingPos(getPos(), getFrontFacing());
        BlockPos ventingBlockPos = facingPos.getPos().offset(facingPos.getFacing());
        IBlockState blockOnPos = getWorld().getBlockState(ventingBlockPos);
        if (blockOnPos.getCollisionBoundingBox(getWorld(), ventingBlockPos) == Block.NULL_AABB) {
            performVentingAnimation(ventingBlockPos);
        } else if (GTUtility.tryBreakSnow(getWorld(), ventingBlockPos, blockOnPos, false)) {
            performVentingAnimation(ventingBlockPos);
        } else if (!ventingStuck) {
            setVentingStuck(true);
        }
    }

    private void performVentingAnimation(BlockPos ventingBlockPos) {
        var frontFacing = getFrontFacing();
        float damage = getVentingDamage();
        if (damage > 0) {
            getWorld().getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(ventingBlockPos),
                    EntitySelectors.CAN_AI_TARGET).forEach(entity -> {
                        entity.attackEntityFrom(DamageSources.getHeatDamage(), damage);
                        if (entity instanceof EntityPlayerMP) {
                            AdvancementTriggers.STEAM_VENT_DEATH.trigger((EntityPlayerMP) entity);
                        }
                    });
        }
        WorldServer world = (WorldServer) getWorld();
        double posX = getPos().getX() + 0.5 + frontFacing.getXOffset() * 0.6;
        double posY = getPos().getY() + 0.5 + frontFacing.getYOffset() * 0.6;
        double posZ = getPos().getZ() + 0.5 + frontFacing.getZOffset() * 0.6;

        world.spawnParticle(EnumParticleTypes.CLOUD, posX, posY, posZ,
                7 + world.rand.nextInt(3),
                frontFacing.getXOffset() / 2.0, frontFacing.getYOffset() / 2.0, frontFacing.getZOffset() / 2.0, 0.1);
        if (ConfigHolder.machines.machineSounds && !isMuffled()) {
            world.playSound(null, posX, posY, posZ, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0f,
                    1.0f);
        }
        setNeedsVenting(false);
    }

    @Override
    public void receiveCustomData(int dataId, @NotNull PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.NEEDS_VENTING) {
            this.needsVenting = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.VENTING_STUCK) {
            this.ventingStuck = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.LAST_STEAM_DRAIN_FAILED) {
            this.lastDrainFailed = buf.readBoolean();
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(needsVenting);
        buf.writeBoolean(ventingStuck);
        buf.writeBoolean(lastDrainFailed);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.needsVenting = buf.readBoolean();
        this.ventingStuck = buf.readBoolean();
        this.lastDrainFailed = buf.readBoolean();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("NeedsVenting", needsVenting);
        data.setBoolean("VentingStuck", ventingStuck);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.needsVenting = data.getBoolean("NeedsVenting");
        this.ventingStuck = data.getBoolean("VentingStuck");
    }

    // --- Abilities ---

    protected void initializeAbilities() {
        this.inputInventory = new ItemHandlerList(getAbilities(MultiblockAbility.STEAM_IMPORT_ITEMS));
        this.outputInventory = new ItemHandlerList(getAbilities(MultiblockAbility.STEAM_EXPORT_ITEMS));
        this.steamFluidTank = new FluidTankList(true, getAbilities(MultiblockAbility.STEAM));
    }

    private void resetTileAbilities() {
        this.inputInventory = new GTItemStackHandler(this, 0);
        this.outputInventory = new GTItemStackHandler(this, 0);
        this.steamFluidTank = new FluidTankList(true);
    }

    @Override
    public TraceabilityPredicate autoAbilities() {
        return autoAbilities(true, true, true, true, true);
    }

    public TraceabilityPredicate autoAbilities(boolean checkSteam, boolean checkMaintainer, boolean checkItemIn,
                                               boolean checkItemOut, boolean checkMuffler) {
        TraceabilityPredicate predicate = super.autoAbilities(checkMaintainer, checkMuffler)
                .or(checkSteam ? abilities(MultiblockAbility.STEAM).setMinGlobalLimited(1).setPreviewCount(1) :
                        new TraceabilityPredicate());
        if (checkItemIn && recipeMap.getMaxInputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.STEAM_IMPORT_ITEMS).setPreviewCount(1));
        }
        if (checkItemOut && recipeMap.getMaxOutputs() > 0) {
            predicate = predicate.or(abilities(MultiblockAbility.STEAM_EXPORT_ITEMS).setPreviewCount(1));
        }
        return predicate;
    }

    // --- Display ---

    @Override
    public GTGuiTheme getUITheme() {
        return GTGuiTheme.BRONZE;
    }

    @Override
    public SoundEvent getSound() {
        return recipeMap.getSound();
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        this.getFrontOverlay().renderOrientedState(renderState, translation, pipeline, getFrontFacing(),
                workable.isActive(), workable.isWorkingEnabled());
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        builder.setWorkingStatus(workable.isWorkingEnabled(), workable.isActive())
                .addCustom((keyManager, syncer) -> {
                    // custom steam tank line, summed across every real STEAM ability part -- see this class's own
                    // JavaDoc for why this doesn't rebuild legacy's synthetic combined-tank object every tick.
                    int stored = 0;
                    int capacity = 0;
                    for (var tank : getSteamFluidTank().getFluidTanks()) {
                        stored += tank.getFluidAmount();
                        capacity += tank.getCapacity();
                    }
                    stored = syncer.syncInt(stored);
                    capacity = syncer.syncInt(capacity);
                    if (capacity > 0) {
                        IKey steamInfo = KeyUtil.string(TextFormatting.BLUE, "%s/%s L",
                                KeyUtil.number(stored),
                                KeyUtil.number(capacity));
                        IKey steamStored = KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.steam.steam_stored", steamInfo);
                        keyManager.add(steamStored);
                    }
                })
                .addParallelsLine(getBaseParallelLimit())
                .addWorkingStatusLine()
                .addProgressLine(workable.getProgress(), workable.getMaxProgress());
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addCustom((list, syncer) -> {
            boolean lowSteam = syncer.syncBoolean(this::insufficientSteam);
            if (isStructureFormed() && lowSteam) {
                list.add(KeyUtil.lang(TextFormatting.YELLOW, "gregtech.multiblock.steam.low_steam"));
            }
        });
        super.configureWarningText(builder);
    }
}
