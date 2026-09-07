package gregtech.api.metatileentity;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IVentable;
import gregtech.api.capability.impl.CommonFluidFilters;
import gregtech.api.capability.impl.FilteredFluidHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.damagesources.DamageSources;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.mui.GTGuiTheme;
import gregtech.api.mui.GTGuis;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable;
import gregtech.api.util.FacingPos;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.GTUtility;
import gregtech.client.particle.VanillaParticleEffects;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleSidedCubeRenderer;
import gregtech.client.utils.RenderUtil;
import gregtech.common.ConfigHolder;
import gregtech.core.advancement.AdvancementTriggers;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.ColourMultiplier;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public abstract class SteamMetaTileEntity extends MetaTileEntity implements IControllable, IVentable {

    protected static final int STEAM_CAPACITY = 16000;

    protected final boolean isHighPressure;
    protected final ICubeRenderer renderer;
    protected final @NotNull RecipeMap<?> recipeMap;
    protected final @NotNull RecipeWorkable workable;
    protected FluidTank steamFluidTank;

    private boolean lastDrainFailed = false;

    private boolean needsVenting;
    private boolean ventingStuck;
    private @NotNull EnumFacing ventingSide = EnumFacing.SOUTH;
    private boolean hasVentingSideBeenSet = false;

    public SteamMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap, ICubeRenderer renderer,
                               boolean isHighPressure) {
        super(metaTileEntityId);
        this.recipeMap = recipeMap;
        this.isHighPressure = isHighPressure;
        this.renderer = renderer;
        this.workable = new RecipeWorkable(this, createConfig(recipeMap), recipeMap);
    }

    protected @NotNull RecipeLogicConfig createConfig(@NotNull RecipeMap<?> recipeMap) {
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
        // No real overclocking (legacy isAllowOverclocking() == false); the pressure-based discount below is a
        // flat, one-time multiplier, not a real overclock ladder -- see this class's own JavaDoc.
        config.overclock.ocAmountCalculator = (v, m) -> 0;
        if (isHighPressure) {
            config.overclock.voltageDiscount = () -> 2.0;
        } else {
            config.overclock.durationDiscount = () -> 2.0;
        }
        config.hooks.perTickRecipeCheck = this::drainSteam;
        // One recipe at a time, matching every other migrated single-block machine's established default -- see
        // RecipeWorkableTieredMetaTileEntity#createConfig's identical field for the full real-machine-bug rationale.
        config.parallel.parallelLimit = () -> 1;
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
        int steamNeeded = GTUtility.safeCastLongToInt(eut);
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

    protected boolean insufficientSteam() {
        return isActive() && lastDrainFailed;
    }

    @Override
    public boolean isActive() {
        return workable.isActive() && workable.isWorkingEnabled();
    }

    @Override
    public boolean isWorkingEnabled() {
        return workable.isWorkingEnabled();
    }

    /** @return the currently-active recipe's progress fraction (0.0-1.0), for JEI/GUI progress widgets. */
    public double getProgressPercent() {
        return workable.getProgressPercent(0);
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        workable.setWorkingEnabled(isWorkingAllowed);
    }

    // --- Venting (IVentable) ---

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

    /**
     * Defaults the venting side to the opposite of the front face the very first time it's set. Overrides
     * {@link MetaTileEntity#setFrontFacing} directly (not {@code MTETrait#onFrontFacingSet}, legacy
     * {@code RecipeLogicSteam}'s own hook point) -- this class is an MTE, not a trait, and the two are unrelated
     * despite the similar name.
     */
    @Override
    public void setFrontFacing(EnumFacing frontFacing) {
        // Guarded the same way MTETrait#onFrontFacingSet's own call site is (see super's own body): only a
        // server has a World this early and a real reason to pick a default.
        boolean firstSet = !hasVentingSideBeenSet && getWorld() != null && !getWorld().isRemote;
        super.setFrontFacing(frontFacing);
        if (firstSet) {
            setVentingSide(frontFacing.getOpposite());
        }
    }

    public @NotNull EnumFacing getVentingSide() {
        return ventingSide;
    }

    public void setVentingSide(EnumFacing ventingSide) {
        this.hasVentingSideBeenSet = true;
        if (this.ventingSide != ventingSide) {
            this.ventingSide = ventingSide;
            if (!getWorld().isRemote) {
                markDirty();
                writeCustomData(GregtechDataCodes.VENTING_SIDE, buf -> buf.writeByte(ventingSide.getIndex()));
            } else {
                scheduleRenderUpdate();
            }
        }
    }

    /**
     * @return the single candidate vent position this machine's own wrench-selected {@link #ventingSide} offers.
     *         Single-block machines never have more than one -- a multi-facing abstraction for venting isn't
     *         needed at this level.
     */
    protected @NotNull FacingPos getVentingBlockFacing() {
        return new FacingPos(getPos(), getVentingSide());
    }

    protected float getVentingDamage() {
        return isHighPressure ? 12.0f : 6.0f;
    }

    @Override
    public void tryDoVenting() {
        FacingPos facingPos = getVentingBlockFacing();
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
        EnumFacing side = getVentingSide();
        getWorld().getEntitiesWithinAABB(EntityLivingBase.class, new AxisAlignedBB(ventingBlockPos),
                EntitySelectors.CAN_AI_TARGET).forEach(entity -> {
                    entity.attackEntityFrom(DamageSources.getHeatDamage(), getVentingDamage());
                    if (entity instanceof EntityPlayerMP) {
                        AdvancementTriggers.STEAM_VENT_DEATH.trigger((EntityPlayerMP) entity);
                    }
                });
        WorldServer world = (WorldServer) getWorld();
        double posX = getPos().getX() + 0.5 + side.getXOffset() * 0.6;
        double posY = getPos().getY() + 0.5 + side.getYOffset() * 0.6;
        double posZ = getPos().getZ() + 0.5 + side.getZOffset() * 0.6;

        world.spawnParticle(EnumParticleTypes.CLOUD, posX, posY, posZ,
                7 + world.rand.nextInt(3),
                side.getXOffset() / 2.0, side.getYOffset() / 2.0, side.getZOffset() / 2.0, 0.1);
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
        } else if (dataId == GregtechDataCodes.VENTING_SIDE) {
            this.ventingSide = EnumFacing.VALUES[buf.readByte()];
            scheduleRenderUpdate();
        } else if (dataId == GregtechDataCodes.VENTING_STUCK) {
            this.ventingStuck = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.LAST_STEAM_DRAIN_FAILED) {
            this.lastDrainFailed = buf.readBoolean();
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeByte(getVentingSide().getIndex());
        buf.writeBoolean(needsVenting);
        buf.writeBoolean(ventingStuck);
        buf.writeBoolean(lastDrainFailed);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.ventingSide = EnumFacing.VALUES[buf.readByte()];
        this.needsVenting = buf.readBoolean();
        this.ventingStuck = buf.readBoolean();
        this.lastDrainFailed = buf.readBoolean();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("VentingSide", getVentingSide().getIndex());
        data.setBoolean("NeedsVenting", needsVenting);
        data.setBoolean("VentingStuck", ventingStuck);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.ventingSide = EnumFacing.VALUES[data.getInteger("VentingSide")];
        this.hasVentingSideBeenSet = true;
        this.needsVenting = data.getBoolean("NeedsVenting");
        this.ventingStuck = data.getBoolean("VentingStuck");
    }

    // --- Rendering/GUI (unchanged from the legacy implementation) ---

    @SideOnly(Side.CLIENT)
    protected SimpleSidedCubeRenderer getBaseRenderer() {
        if (isHighPressure) {
            if (isBrickedCasing()) {
                return Textures.STEAM_BRICKED_CASING_STEEL;
            } else {
                return Textures.STEAM_CASING_STEEL;
            }
        } else {
            if (isBrickedCasing()) {
                return Textures.STEAM_BRICKED_CASING_BRONZE;
            } else {
                return Textures.STEAM_CASING_BRONZE;
            }
        }
    }

    @Override
    public int getDefaultPaintingColor() {
        return 0xFFFFFF;
    }

    @Override
    public boolean onWrenchClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                 CuboidRayTraceResult hitResult) {
        if (!playerIn.isSneaking()) {
            EnumFacing currentVentingSide = getVentingSide();
            if (currentVentingSide == facing ||
                    getFrontFacing() == facing)
                return false;
            setVentingSide(facing);
            return true;
        }
        return super.onWrenchClick(playerIn, hand, facing, hitResult);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public Pair<TextureAtlasSprite, Integer> getParticleTexture() {
        return Pair.of(getBaseRenderer().getParticleSprite(), getPaintingColorForRendering());
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        IVertexOperation[] colouredPipeline = ArrayUtils.add(pipeline,
                new ColourMultiplier(GTUtility.convertRGBtoOpaqueRGBA_CL(getPaintingColorForRendering())));
        getBaseRenderer().render(renderState, translation, colouredPipeline);
        renderer.renderOrientedState(renderState, translation, pipeline, getFrontFacing(), workable.isActive(),
                workable.isWorkingEnabled());
        Textures.STEAM_VENT_OVERLAY.renderSided(getVentingSide(), renderState,
                RenderUtil.adjustTrans(translation, getVentingSide(), 2), pipeline);
    }

    protected boolean isBrickedCasing() {
        return false;
    }

    @Override
    public FluidTankList createImportFluidHandler() {
        this.steamFluidTank = new FilteredFluidHandler(STEAM_CAPACITY).setFilter(CommonFluidFilters.STEAM);
        return new FluidTankList(false, steamFluidTank);
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new NotifiableItemStackHandler(this, recipeMap.getMaxInputs(), this, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new NotifiableItemStackHandler(this, recipeMap.getMaxOutputs(), this, true);
    }

    @Override
    public GTGuiTheme getUITheme() {
        return isHighPressure ? GTGuiTheme.STEEL : GTGuiTheme.BRONZE;
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        return buildUITemplate(guiData, panelSyncManager);
    }

    /**
     * Builds the base panel shared by all steam machines (background, label, energy indicator, player inventory).
     * Subclasses add their own item/fluid slots and progress bar widgets on top of the returned panel.
     */
    protected ModularPanel buildUITemplate(PosGuiData guiData, PanelSyncManager panelSyncManager) {
        BooleanSyncValue hasNotEnoughSteamValue = new BooleanSyncValue(this::insufficientSteam);

        return GTGuis.createPanel(this, 176, 166)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(6, 6))
                .child(new Widget<>()
                        .pos(79, 42)
                        .size(18, 18)
                        .overlay(new DynamicDrawable(() -> hasNotEnoughSteamValue.getBoolValue() ?
                                GTGuiTextures.INDICATOR_NO_STEAM.get(isHighPressure) : IDrawable.NONE)))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));
    }

    @Override
    public SoundEvent getSound() {
        return Objects.requireNonNull(recipeMap).getSound();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick() {
        if (this.isActive()) {
            EnumParticleTypes smokeParticle = isHighPressure ? EnumParticleTypes.SMOKE_LARGE :
                    EnumParticleTypes.SMOKE_NORMAL;
            VanillaParticleEffects.defaultFrontEffect(this, smokeParticle, EnumParticleTypes.FLAME);

            if (ConfigHolder.machines.machineSounds && GTValues.RNG.nextDouble() < 0.1) {
                BlockPos pos = getPos();
                getWorld().playSound(pos.getX(), pos.getY(), pos.getZ(),
                        SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 1.0F, 1.0F, false);
            }
        }
    }

    @Override
    public boolean needsSneakToRotate() {
        return true;
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        tooltip.add(I18n.format("gregtech.tool_action.soft_mallet.reset"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}
