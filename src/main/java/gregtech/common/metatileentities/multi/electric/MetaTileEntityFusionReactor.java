package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.metatileentity.IFastRenderMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.ProgressBarMultiblock;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIFactory;
import gregtech.api.metatileentity.multiblock.ui.TemplateBarBuilder;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.recipes.logic.RecipeRun;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeLogicCallbacks;
import gregtech.api.recipes.logic.statemachine.RecipeLogicHooks;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeFusionOverclockOperator;
import gregtech.api.recipes.logic.statemachine.lookup.bitflag.FusionStartEnergyFilter;
import gregtech.api.recipes.logic.statemachine.property.CleanroomProperties;
import gregtech.api.recipes.logic.statemachine.property.DimensionProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.FusionStartCapacityProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.recipes.properties.impl.FusionEUToStartProperty;
import gregtech.api.util.KeyUtil;
import gregtech.api.util.RelativeDirection;
import gregtech.api.util.interpolate.Eases;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.IRenderSetup;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.shader.postprocessing.BloomEffect;
import gregtech.client.shader.postprocessing.BloomType;
import gregtech.client.utils.BloomEffectUtil;
import gregtech.client.utils.EffectRenderContext;
import gregtech.client.utils.IBloomEffect;
import gregtech.client.utils.RenderBufferHelper;
import gregtech.client.utils.RenderUtil;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockFusionCasing;
import gregtech.common.blocks.BlockGlassCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.layout.Column;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static gregtech.api.util.RelativeDirection.*;

/**
 * Migrated from {@code RecipeMapMultiblockController} to
 * {@link RecipeWorkableMultiblockController}. Replaces legacy's {@code FusionRecipeLogic} inner class
 * (~90 lines for {@code getOverclockingDurationFactor}/{@code getOverclockingVoltageFactor}/{@code getMaxVoltage}/
 * {@code checkRecipe}/{@code modifyOverclockPre}/NBT round-trip, the exact "inner class spam for a handful of small
 * hooks" pattern this migration exists to eliminate) with declarative {@code createConfig()} wiring plus one
 * dedicated operator, {@link RecipeFusionOverclockOperator} (see that class's JavaDoc for why the reactor-MK
 * overclock clamp needs a full {@code overclockFactory}, not the narrower {@code ocAmountCalculator} seam an
 * earlier paper assessment assumed would suffice).
 * <p>
 * <b>Heat charging (legacy {@code checkRecipe}'s side effect) is now {@link RecipeLogicHooks#finalCheck
 * config.hooks.finalCheck}</b> ({@link #chargeHeatForStart}): both legacy and this migration run it <i>before</i>
 * a candidate is guaranteed to actually start (legacy: before {@code prepareRecipe}'s own output-space check;
 * here: after {@code RecipeOutputSpaceCheckOperator}'s worst-case check but still before admission's authoritative
 * one) &mdash; a pre-existing legacy characteristic (heat can theoretically be "spent" on a candidate that later
 * turns out unable to fit its output), not a new risk this migration introduces.
 * <p>
 * <b>The fusion ring color</b> (legacy: polled every tick against {@code getPreviousRecipe()}, which the new engine
 * has no equivalent of) is now set directly from {@link RecipeLogicCallbacks#onRecipeCompleted
 * config.callbacks.onRecipeCompleted}, which hands back the exact completed entry's rolled fluid outputs -- more
 * precise than legacy's own
 * "poll every tick until the ring happens to be off" approach, and needs no new engine-level plumbing at all.
 * <p>
 * <b>Deliberately kept:</b> legacy's heat-drain guard in {@code updateFormedValid()} ("don't drain heat while a
 * recipe is genuinely in progress just because energy is briefly insufficient, to avoid a double penalty") is
 * preserved verbatim rather than simplified to an unconditional
 * {@code !isActive() || insufficientEnergy()}, which would drop the in-progress guard.
 */
public class MetaTileEntityFusionReactor extends RecipeWorkableMultiblockController
                                         implements IFastRenderMetaTileEntity, IBloomEffect, ProgressBarMultiblock {

    protected static final int NO_COLOR = 0;

    private final int tier;
    private EnergyContainerList inputEnergyContainers;
    private long heat = 0; // defined in TileEntityFusionReactor but serialized in FusionRecipeLogic
    private int fusionRingColor = NO_COLOR;

    /**
     * Whether {@link #onRecipeCompleted} fired during the search track's most recent
     * {@code updateFormedValid()} call. Needed, or the ring never lights up: {@code RecipeWorkableMultiblockController}'s {@code shouldStartRecipeLookup} gate checks
     * {@code getCommittedParallel()} <i>before</i> that tick's completion has run (search walks before progress,
     * see {@code RecipeLogicGraphBuilder#tick}), so a just-completed recipe always leaves exactly one tick where
     * {@link #workable}{@code .isActive()} reads {@code false} before the next candidate is admitted &mdash; unlike
     * legacy {@code AbstractRecipeLogic}, which re-searched and restarted within the very same tick a recipe
     * finished. Without this flag, the unconditional {@code isActive()}-based reset below fires in the very same
     * {@code updateFormedValid()} call that {@link #onRecipeCompleted} just lit the ring in, undoing it before the
     * client ever renders a frame with it on. Not persisted: only bridges within a single tick, reset unconditionally
     * at the end of every {@code updateFormedValid()} call.
     */
    private boolean recipeJustCompletedThisTick = false;

    @SideOnly(Side.CLIENT)
    private boolean registeredBloomRenderTicket;

    public MetaTileEntityFusionReactor(ResourceLocation metaTileEntityId, int tier) {
        super(metaTileEntityId, RecipeMaps.FUSION_RECIPES);
        this.tier = tier;
        this.energyContainer = new EnergyContainerHandler(this, 0, 0, 0, 0, 0) {

            @NotNull
            @Override
            public String getName() {
                return GregtechDataCodes.FUSION_REACTOR_ENERGY_CONTAINER_TRAIT;
            }
        };
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityFusionReactor(metaTileEntityId, tier);
    }

    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        config.power.properties = () -> {
            // Not EnergyContainerProperties.of(getEnergyContainer()): the reactor's own energy container is a plain
            // capacitor with maxInputAmperage=0 (see its construction) -- legacy's own getMaxVoltage() ignored
            // amperage entirely and worked fine, but this engine's search-time prefilter and parallel-budget
            // accounting are both EU/t-based (voltage * amperage), so a literal amperage=0 makes every recipe's
            // voltage/EUt requirement look unaffordable regardless of how much energy is actually stored (the
            // reactor would never start even with ample energy supplied). Fusion recipes
            // always have amperage 1, so report 1 here to match.
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(getEnergyContainer().getInputVoltage(), 1));
            properties.add(new FusionStartCapacityProperty(
                    Math.min(getEnergyContainer().getEnergyCapacity(), getEnergyContainer().getEnergyStored() + heat)));
            // This replaces (not adds to) RecipeWorkableMultiblockController's own default properties supplier,
            // so cleanroom/dimension advertising has to be repeated here explicitly -- see that class's JavaDoc.
            properties.add(DimensionProperties.of(this));
            properties.add(CleanroomProperties.of(this));
            return properties;
        };
        // The reactor's gentler voltage growth per overclock (legacy PERFECT_HALF_VOLTAGE_FACTOR); duration still
        // halves per overclock exactly like the standard default (speedFactor unchanged).
        config.overclock.costFactor = OverclockingLogic.PERFECT_HALF_VOLTAGE_FACTOR;
        // Ignores overclockFactory's own four scalar parameters entirely -- see RecipeFusionOverclockOperator's
        // JavaDoc for why this factory closure needs config/tier directly instead.
        config.overclock.overclockFactory = (costFactor, speedFactor, canUpTransform, durationDiscount) ->
                new RecipeFusionOverclockOperator(config, () -> tier);
        config.hooks.finalCheck = this::chargeHeatForStart;
        config.callbacks.onRecipeCompleted = this::onRecipeCompleted;
        // Idempotent: registerFilter adds to a Set keyed by filter identity, and RecipeMaps.FUSION_RECIPES's
        // BitflagRecipeLookup is shared by every Fusion Reactor instance (see RecipeMap#getBitflagLookup()).
        RecipeMaps.FUSION_RECIPES.getBitflagLookup().registerFilter(FusionStartEnergyFilter.INSTANCE);
        return config;
    }

    /**
     * Legacy {@code FusionRecipeLogic#checkRecipe}'s side effect, verbatim: tops up {@link #heat} from
     * {@link #getEnergyContainer()} up to the candidate's required starting energy, rejecting the candidate outright
     * if the reactor's total capacity could never hold enough, or if there isn't enough energy stored right now to
     * finish topping up. See this class's JavaDoc for why running this here (rather than at admission) matches
     * legacy's own timing characteristics.
     */
    private boolean chargeHeatForStart(RecipeRun run) {
        long euToStart = run.getRecipeView().getRecipe().getProperty(FusionEUToStartProperty.getInstance(), 0L);
        if (euToStart > getEnergyContainer().getEnergyCapacity()) return false;

        long heatDiff = euToStart - heat;
        if (heatDiff <= 0) return true;

        if (getEnergyContainer().getEnergyStored() < heatDiff) return false;

        getEnergyContainer().removeEnergy(heatDiff);
        heat += heatDiff;
        return true;
    }

    /** Lights up the fusion ring using the just-completed recipe's own rolled fluid output color. */
    private void onRecipeCompleted(@NotNull NBTTagCompound completedEntry) {
        recipeJustCompletedThisTick = true;
        List<FluidStack> fluidsOut = ActiveRecipeList.fluidsOut(completedEntry);
        if (fusionRingColor == NO_COLOR && !fluidsOut.isEmpty()) {
            setFusionRingColor(0xFF000000 | fluidsOut.get(0).getFluid().getColor());
        }
    }

    @NotNull
    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
                .aisle("###############", "######OGO######", "###############")
                .aisle("######ICI######", "####GGAAAGG####", "######ICI######")
                .aisle("####CC###CC####", "###EAAOGOAAE###", "####CC###CC####")
                .aisle("###C#######C###", "##EKEG###GEKE##", "###C#######C###")
                .aisle("##C#########C##", "#GAE#######EAG#", "##C#########C##")
                .aisle("##C#########C##", "#GAG#######GAG#", "##C#########C##")
                .aisle("#I###########I#", "OAO#########OAO", "#I###########I#")
                .aisle("#C###########C#", "GAG#########GAG", "#C###########C#")
                .aisle("#I###########I#", "OAO#########OAO", "#I###########I#")
                .aisle("##C#########C##", "#GAG#######GAG#", "##C#########C##")
                .aisle("##C#########C##", "#GAE#######EAG#", "##C#########C##")
                .aisle("###C#######C###", "##EKEG###GEKE##", "###C#######C###")
                .aisle("####CC###CC####", "###EAAOGOAAE###", "####CC###CC####")
                .aisle("######ICI######", "####GGAAAGG####", "######ICI######")
                .aisle("###############", "######OSO######", "###############")
                .where('S', selfPredicate())
                .where('G', states(getCasingState(), getGlassState()))
                .where('E',
                        states(getCasingState(), getGlassState()).or(metaTileEntities(Arrays
                                .stream(MetaTileEntities.ENERGY_INPUT_HATCH)
                                .filter(mte -> mte != null && tier <= mte.getTier() && mte.getTier() <= GTValues.UV)
                                .toArray(MetaTileEntity[]::new))
                                        .setMinGlobalLimited(1).setPreviewCount(16)))
                .where('C', states(getCasingState()))
                .where('K', states(getCoilState()))
                .where('O', states(getCasingState(), getGlassState()).or(abilities(MultiblockAbility.EXPORT_FLUIDS)))
                .where('A', air())
                .where('I',
                        states(getCasingState()).or(abilities(MultiblockAbility.IMPORT_FLUIDS).setMinGlobalLimited(2)))
                .where('#', any())
                .build();
    }

    @Override
    public List<MultiblockShapeInfo> getMatchingShapes() {
        List<MultiblockShapeInfo> shapeInfos = new ArrayList<>();

        MultiblockShapeInfo.Builder baseBuilder = MultiblockShapeInfo.builder(RIGHT, DOWN, FRONT)
                .aisle("###############", "######WGW######", "###############")
                .aisle("######DCD######", "####GG###GG####", "######UCU######")
                .aisle("####CC###CC####", "###w##EGE##s###", "####CC###CC####")
                .aisle("###C#######C###", "##nKeG###GeKn##", "###C#######C###")
                .aisle("##C#########C##", "#G#s#######w#G#", "##C#########C##")
                .aisle("##C#########C##", "#G#G#######G#G#", "##C#########C##")
                .aisle("#D###########D#", "N#S#########N#S", "#U###########U#")
                .aisle("#C###########C#", "G#G#########G#G", "#C###########C#")
                .aisle("#D###########D#", "N#S#########N#S", "#U###########U#")
                .aisle("##C#########C##", "#G#G#######G#G#", "##C#########C##")
                .aisle("##C#########C##", "#G#s#######w#G#", "##C#########C##")
                .aisle("###C#######C###", "##eKnG###GnKe##", "###C#######C###")
                .aisle("####CC###CC####", "###w##WGW##s###", "####CC###CC####")
                .aisle("######DCD######", "####GG###GG####", "######UCU######")
                .aisle("###############", "######EME######", "###############")
                .where('M', MetaTileEntities.FUSION_REACTOR[tier - GTValues.LuV], EnumFacing.SOUTH)
                .where('C', getCasingState())
                .where('G', MetaBlocks.TRANSPARENT_CASING.getState(
                        BlockGlassCasing.CasingType.FUSION_GLASS))
                .where('K', getCoilState())
                .where('W', MetaTileEntities.FLUID_EXPORT_HATCH[tier], EnumFacing.NORTH)
                .where('E', MetaTileEntities.FLUID_EXPORT_HATCH[tier], EnumFacing.SOUTH)
                .where('S', MetaTileEntities.FLUID_EXPORT_HATCH[tier], EnumFacing.EAST)
                .where('N', MetaTileEntities.FLUID_EXPORT_HATCH[tier], EnumFacing.WEST)
                .where('w', MetaTileEntities.ENERGY_INPUT_HATCH[tier], EnumFacing.WEST)
                .where('e', MetaTileEntities.ENERGY_INPUT_HATCH[tier], EnumFacing.SOUTH)
                .where('s', MetaTileEntities.ENERGY_INPUT_HATCH[tier], EnumFacing.EAST)
                .where('n', MetaTileEntities.ENERGY_INPUT_HATCH[tier], EnumFacing.NORTH)
                .where('U', MetaTileEntities.FLUID_IMPORT_HATCH[tier], EnumFacing.UP)
                .where('D', MetaTileEntities.FLUID_IMPORT_HATCH[tier], EnumFacing.DOWN)
                .where('#', Blocks.AIR.getDefaultState());

        shapeInfos.add(baseBuilder.shallowCopy()
                .where('G', getCasingState())
                .build());
        shapeInfos.add(baseBuilder.build());
        return shapeInfos;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        if (isActive()) {
            return Textures.ACTIVE_FUSION_TEXTURE;
        } else {
            return Textures.FUSION_TEXTURE;
        }
    }

    private IBlockState getGlassState() {
        return MetaBlocks.TRANSPARENT_CASING.getState(BlockGlassCasing.CasingType.FUSION_GLASS);
    }

    private IBlockState getCasingState() {
        if (tier == GTValues.LuV)
            return MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_CASING);
        if (tier == GTValues.ZPM)
            return MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_CASING_MK2);

        return MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_CASING_MK3);
    }

    private IBlockState getCoilState() {
        if (tier == GTValues.LuV)
            return MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.SUPERCONDUCTOR_COIL);

        return MetaBlocks.FUSION_CASING.getState(BlockFusionCasing.CasingType.FUSION_COIL);
    }

    protected int getFusionRingColor() {
        return this.fusionRingColor;
    }

    protected boolean hasFusionRingColor() {
        return this.fusionRingColor != NO_COLOR;
    }

    protected void setFusionRingColor(int fusionRingColor) {
        if (this.fusionRingColor != fusionRingColor) {
            this.fusionRingColor = fusionRingColor;
            writeCustomData(GregtechDataCodes.UPDATE_COLOR, buf -> buf.writeVarInt(fusionRingColor));
        }
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        long energyStored = this.energyContainer.getEnergyStored();
        super.formStructure(context);
        this.initializeAbilities();
        ((EnergyContainerHandler) this.energyContainer).setEnergyStored(energyStored);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.energyContainer = new EnergyContainerHandler(this, 0, 0, 0, 0, 0) {

            @NotNull
            @Override
            public String getName() {
                return GregtechDataCodes.FUSION_REACTOR_ENERGY_CONTAINER_TRAIT;
            }
        };
        this.inputEnergyContainers = new EnergyContainerList(Lists.newArrayList());
        this.heat = 0;
        this.setFusionRingColor(NO_COLOR);
    }

    @Override
    protected void initializeAbilities() {
        super.initializeAbilities();
        List<IEnergyContainer> energyInputs = getAbilities(MultiblockAbility.INPUT_ENERGY);
        this.inputEnergyContainers = new EnergyContainerList(energyInputs);
        long euCapacity = calculateEnergyStorageFactor(energyInputs.size());
        this.energyContainer = new EnergyContainerHandler(this, euCapacity, GTValues.V[tier], 0, 0, 0) {

            @NotNull
            @Override
            public String getName() {
                return GregtechDataCodes.FUSION_REACTOR_ENERGY_CONTAINER_TRAIT;
            }
        };
    }

    private long calculateEnergyStorageFactor(int energyInputAmount) {
        return energyInputAmount * (long) Math.pow(2, tier - 6) * 10000000L;
    }

    @Override
    protected void updateFormedValid() {
        if (this.inputEnergyContainers.getEnergyStored() > 0) {
            long energyAdded = this.energyContainer.addEnergy(this.inputEnergyContainers.getEnergyStored());
            if (energyAdded > 0) this.inputEnergyContainers.removeEnergy(energyAdded);
        }
        super.updateFormedValid();
        // Legacy FusionRecipeLogic#updateWorkable's heat-drain guard, kept verbatim (see this class's JavaDoc for
        // why): don't drain heat while a recipe is genuinely
        // in progress just because energy is briefly insufficient, to avoid a double penalty (would have to recover
        // both heat and recipe progress).
        if (heat > 0) {
            if (!workable.isActive() || !workable.isWorkingEnabled() ||
                    (insufficientEnergy() && workable.getProgress() == 0)) {
                heat = heat <= 10000 ? 0 : (heat - 10000);
            }
        }
        // Not reset on the exact tick a recipe just completed -- see recipeJustCompletedThisTick's JavaDoc: the
        // engine's own admission gate always leaves a one-tick gap here before the next candidate starts, which
        // must not read as "genuinely stopped", or this unconditional check would
        // undo onRecipeCompleted's own write within the same call, so the ring would never render at all. A true
        // stoppage (out of fuel, redstone off, structure broken) still clears the ring, just one tick later than
        // before -- imperceptible.
        if (!recipeJustCompletedThisTick && !(workable.isActive() && workable.isWorkingEnabled()) &&
                isStructureFormed()) {
            setFusionRingColor(NO_COLOR);
        }
        recipeJustCompletedThisTick = false;
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeVarInt(this.fusionRingColor);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.fusionRingColor = buf.readVarInt();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        if (dataId == GregtechDataCodes.UPDATE_COLOR) {
            this.fusionRingColor = buf.readVarInt();
        } else {
            super.receiveCustomData(dataId, buf);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(
                I18n.format("gregtech.machine.fusion_reactor.capacity", calculateEnergyStorageFactor(16) / 1000000L));
        tooltip.add(I18n.format("gregtech.machine.fusion_reactor.overclocking"));
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.FUSION_REACTOR_OVERLAY;
    }

    @Override
    public boolean hasMaintenanceMechanics() {
        return false;
    }

    public long getHeat() {
        return heat;
    }

    @Override
    protected MultiblockUIFactory createUIFactory() {
        IDrawable title;
        if (tier == GTValues.LuV) {
            // MK1
            title = GTGuiTextures.FUSION_REACTOR_MK1_TITLE;
        } else if (tier == GTValues.ZPM) {
            // MK2
            title = GTGuiTextures.FUSION_REACTOR_MK2_TITLE;
        } else {
            // MK3
            title = GTGuiTextures.FUSION_REACTOR_MK3_TITLE;
        }

        DoubleSyncValue progress = new DoubleSyncValue(() -> workable.getProgressPercent(0));
        return new MultiblockUIFactory(this)
                .setScreenHeight(138)
                .disableDisplayText()
                .addScreenChildren((parent, syncManager) -> {
                    var status = MultiblockUIFactory.builder("status", syncManager);
                    status.setAction(b -> b.structureFormed(true)
                            .setWorkingStatus(workable.isWorkingEnabled(), workable.isActive())
                            .addWorkingStatusLine());
                    parent.child(new Column()
                            .padding(4)
                            .expanded()
                            .child(title.asWidget()
                                    .marginBottom(8)
                                    .size(69, 12))
                            .child(new ProgressWidget()
                                    .size(77, 77)
                                    .tooltipAutoUpdate(true)
                                    .tooltipBuilder(status::build)
                                    .background(GTGuiTextures.FUSION_DIAGRAM.asIcon()
                                            .size(89, 101)
                                            .marginTop(11))
                                    .direction(ProgressWidget.Direction.CIRCULAR_CW)
                                    .value(progress)
                                    .texture(null, GTGuiTextures.FUSION_PROGRESS, 77))
                            .child(GTGuiTextures.FUSION_LEGEND.asWidget()
                                    .left(4)
                                    .bottom(4)
                                    .size(108, 41)));
                });
    }

    @Override
    public int getProgressBarCount() {
        return 2;
    }

    @Override
    public void registerBars(List<UnaryOperator<TemplateBarBuilder>> bars, PanelSyncManager syncManager) {
        LongSyncValue capacity = new LongSyncValue(energyContainer::getEnergyCapacity);
        syncManager.syncValue("capacity", capacity);
        LongSyncValue stored = new LongSyncValue(energyContainer::getEnergyStored);
        syncManager.syncValue("stored", stored);
        LongSyncValue heat = new LongSyncValue(this::getHeat);
        syncManager.syncValue("heat", heat);

        bars.add(barTest -> barTest
                .progress(() -> capacity.getLongValue() > 0 ?
                        1.0 * stored.getLongValue() / capacity.getLongValue() : 0)
                .texture(GTGuiTextures.PROGRESS_BAR_FUSION_ENERGY)
                .tooltipBuilder(tooltip -> tooltip
                        .add(KeyUtil.lang(TextFormatting.GRAY,
                                "gregtech.multiblock.energy_stored",
                                stored.getLongValue(), capacity.getLongValue()))));

        bars.add(barTest -> barTest
                .texture(GTGuiTextures.PROGRESS_BAR_FUSION_HEAT)
                .tooltipBuilder(tooltip -> {
                    IKey heatInfo = KeyUtil.string(TextFormatting.AQUA,
                            "%,d / %,d EU",
                            heat.getLongValue(), capacity.getLongValue());
                    tooltip.add(KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.fusion_reactor.heat",
                            heatInfo));
                })
                .progress(() -> capacity.getLongValue() > 0 ?
                        1.0 * heat.getLongValue() / capacity.getLongValue() : 0));
    }

    @NotNull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setLong("Heat", heat);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        heat = data.getLong("Heat");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderMetaTileEntity(double x, double y, double z, float partialTicks) {
        if (this.hasFusionRingColor() && !this.registeredBloomRenderTicket) {
            this.registeredBloomRenderTicket = true;
            BloomEffectUtil.registerBloomRender(FusionBloomSetup.INSTANCE, getBloomType(), this, this);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderBloomEffect(@NotNull BufferBuilder buffer, @NotNull EffectRenderContext context) {
        if (!this.hasFusionRingColor()) return;
        int color = RenderUtil.interpolateColor(this.getFusionRingColor(), -1, Eases.QUAD_IN.getInterpolation(
                Math.abs((Math.abs(getOffsetTimer() % 50) + context.partialTicks()) - 25) / 25));
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        EnumFacing relativeBack = RelativeDirection.BACK.getRelativeFacing(getFrontFacing(), getUpwardsFacing(),
                isFlipped());
        EnumFacing.Axis axis = RelativeDirection.UP.getRelativeFacing(getFrontFacing(), getUpwardsFacing(), isFlipped())
                .getAxis();

        buffer.begin(GL11.GL_QUAD_STRIP, DefaultVertexFormats.POSITION_COLOR);
        RenderBufferHelper.renderRing(buffer,
                getPos().getX() - context.cameraX() + relativeBack.getXOffset() * 7 + 0.5,
                getPos().getY() - context.cameraY() + relativeBack.getYOffset() * 7 + 0.5,
                getPos().getZ() - context.cameraZ() + relativeBack.getZOffset() * 7 + 0.5,
                6, 0.2, 10, 20,
                r, g, b, a, axis);
        Tessellator.getInstance().draw();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean shouldRenderBloomEffect(@NotNull EffectRenderContext context) {
        return this.hasFusionRingColor() && context.camera().isBoundingBoxInFrustum(getRenderBoundingBox());
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        EnumFacing relativeRight = RelativeDirection.RIGHT.getRelativeFacing(getFrontFacing(), getUpwardsFacing(),
                isFlipped());
        EnumFacing relativeBack = RelativeDirection.BACK.getRelativeFacing(getFrontFacing(), getUpwardsFacing(),
                isFlipped());

        return new AxisAlignedBB(
                this.getPos().offset(relativeBack).offset(relativeRight, 6),
                this.getPos().offset(relativeBack, 13).offset(relativeRight.getOpposite(), 6));
    }

    @Override
    public boolean shouldRenderInPass(int pass) {
        return pass == 0;
    }

    @Override
    public boolean isGlobalRenderer() {
        return true;
    }

    private static BloomType getBloomType() {
        ConfigHolder.FusionBloom fusionBloom = ConfigHolder.client.shader.fusionBloom;
        return BloomType.fromValue(fusionBloom.useShader ? fusionBloom.bloomStyle : -1);
    }

    @SideOnly(Side.CLIENT)
    private static final class FusionBloomSetup implements IRenderSetup {

        private static final FusionBloomSetup INSTANCE = new FusionBloomSetup();

        float lastBrightnessX;
        float lastBrightnessY;

        @Override
        public void preDraw(@NotNull BufferBuilder buffer) {
            BloomEffect.strength = (float) ConfigHolder.client.shader.fusionBloom.strength;
            BloomEffect.baseBrightness = (float) ConfigHolder.client.shader.fusionBloom.baseBrightness;
            BloomEffect.highBrightnessThreshold = (float) ConfigHolder.client.shader.fusionBloom.highBrightnessThreshold;
            BloomEffect.lowBrightnessThreshold = (float) ConfigHolder.client.shader.fusionBloom.lowBrightnessThreshold;
            BloomEffect.step = 1;

            lastBrightnessX = OpenGlHelper.lastBrightnessX;
            lastBrightnessY = OpenGlHelper.lastBrightnessY;
            GlStateManager.color(1, 1, 1, 1);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
            GlStateManager.disableTexture2D();
        }

        @Override
        public void postDraw(@NotNull BufferBuilder buffer) {
            GlStateManager.enableTexture2D();
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastBrightnessX, lastBrightnessY);
        }
    }
}
