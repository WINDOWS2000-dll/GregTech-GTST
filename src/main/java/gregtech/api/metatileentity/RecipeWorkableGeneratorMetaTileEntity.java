package gregtech.api.metatileentity;

import gregtech.api.GTValues;
import gregtech.api.capability.IActiveOutputSide;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.mui.GTGuis;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeStallType;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.utils.PipelineUtil;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.SlotGroupWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * As {@code SimpleGeneratorMetaTileEntity} (legacy), but backed by {@link RecipeWorkableTieredMetaTileEntity}.
 * <p>
 * Replaces legacy's {@code FuelRecipeLogic} with plain
 * {@link RecipeLogicConfig} settings on the machine itself: no dedicated recipe-logic subclass is needed here,
 * just
 * <ul>
 * <li>{@code config.overclock.ocAmountCalculator} fixed at 0 (no overclocking at all &mdash; the standard
 * {@code RecipeOverclockOperator} already treats {@code ocAmount <= 0} as "run at the recipe's own EU/t, no OC",
 * so no dedicated no-overclocking operator class is needed),
 * <li>{@code config.power.downTransformForParallels = true},
 * <li>{@code config.parallel.parallelLimit} uncapped (limited only by the voltage/amperage budget itself, matching
 * legacy {@code FuelRecipeLogic#getParallelLimit}'s {@code Integer.MAX_VALUE}), and
 * <li>{@link RecipeStallType#PAUSE} (a stalled generator shouldn't lose progress already paid for in
 * fuel, unlike a consuming machine).
 * </ul>
 * The generating-direction energy hookup itself needs no override here:
 * {@link RecipeWorkableTieredMetaTileEntity#drainRecipeEnergy} already branches on
 * {@link gregtech.api.recipes.Recipe#isGenerating()} to add energy into the container instead of draining it.
 */
public class RecipeWorkableGeneratorMetaTileEntity extends RecipeWorkableTieredMetaTileEntity
                                                   implements IActiveOutputSide {

    public final boolean handlesRecipeOutputs;

    public RecipeWorkableGeneratorMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap,
                                                 ICubeRenderer renderer, int tier,
                                                 Function<Integer, Integer> tankScalingFunction) {
        this(metaTileEntityId, recipeMap, renderer, tier, tankScalingFunction, false);
    }

    public RecipeWorkableGeneratorMetaTileEntity(ResourceLocation metaTileEntityId, RecipeMap<?> recipeMap,
                                                 ICubeRenderer renderer, int tier,
                                                 Function<Integer, Integer> tankScalingFunction,
                                                 boolean handlesRecipeOutputs) {
        super(metaTileEntityId, recipeMap, renderer, tier, tankScalingFunction);
        this.handlesRecipeOutputs = handlesRecipeOutputs;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new RecipeWorkableGeneratorMetaTileEntity(metaTileEntityId, recipeMap, renderer, getTier(),
                getTankScalingFunction(), handlesRecipeOutputs);
    }

    @Override
    protected @NotNull RecipeLogicConfig createConfig(@NotNull RecipeMap<?> recipeMap) {
        RecipeLogicConfig config = super.createConfig(recipeMap);
        config.overclock.ocAmountCalculator = (recipeVoltage, maxVoltage) -> 0;
        config.power.downTransformForParallels = true;
        // Deliberately NOT overriding config.parallel.parallelLimit: leave it at super.createConfig()'s inherited
        // parallelLimit=1/consumedParallelSupplier=workable::getCommittedParallel, exactly like every consuming
        // single-block machine. An earlier version of this class set parallelLimit=Integer.MAX_VALUE, following
        // legacy FuelRecipeLogic#getParallelLimit()'s literal value ("parallel is limited by voltage") -- but at the
        // time, config.power.consumedPowerSupplier (which RecipeParallelOperator's amperage check needs to learn how
        // much amperage already-active entries have claimed) was not wired anywhere in this engine, so an uncapped
        // parallelLimit let the search admit one *additional* independent recipe instance every single tick
        // indefinitely (each one individually satisfying the same "1 amp available" budget, since the engine never
        // learned an amp was already spoken for by an earlier tick's still-active instance) -- observed in-game as a
        // single-block generator burning fuel at roughly (recipe duration in ticks) times its nominal rate, e.g. a
        // 40-tick Nitrobenzene recipe consuming ~40x too fast, with fuel still being visibly drained for up to a
        // whole recipe duration after the tank reads empty. consumedPowerSupplier is now wired by
        // RecipeWorkableTieredMetaTileEntity.createConfig() itself (found and fixed 2026-09-04 via the same class of
        // bug resurfacing on Processing Array, the first *consuming* parallelLimit > 1 machine), so that specific
        // failure mode no longer applies here -- but real single-block generators have never supported genuine
        // parallel execution (no multi-amp scaling like a Large Combustion Engine) regardless, so capping at 1
        // remains the historically-correct behavior, not just a workaround for the now-fixed accounting gap.
        config.hooks.stallType = RecipeStallType.PAUSE;
        return config;
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        if (handlesRecipeOutputs) return super.createExportFluidHandler();
        return new FluidTankList(false);
    }

    @Override
    protected void reinitializeEnergyContainer() {
        long tierVoltage = GTValues.V[getTier()];
        this.energyContainer = EnergyContainerHandler.emitterContainer(this, tierVoltage * 64L, tierVoltage,
                getMaxInputOutputAmperage());
        ((EnergyContainerHandler) this.energyContainer).setSideOutputCondition(side -> side == getFrontFacing());
    }

    @Override
    public boolean hasFrontFacing() {
        return true;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing side) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if (fluidInventory.getTankProperties().length > 0) {
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidInventory);
            }
            return null;
        } else if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (itemInventory.getSlots() > 0) {
                return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemInventory);
            }
            return null;
        }
        return super.getCapability(capability, side);
    }

    @Override
    public ModularPanel buildUI(PosGuiData guiData, PanelSyncManager panelSyncManager, UISettings settings) {
        int yOffset = getRecipeUIYOffset();

        ParentWidget<?> recipeWidgets;
        if (handlesRecipeOutputs) {
            recipeWidgets = recipeMap.getRecipeMapUI().buildUITemplate(() -> workable.getProgressPercent(0),
                    importItems, exportItems, importFluids, exportFluids);
        } else {
            recipeWidgets = recipeMap.getRecipeMapUI().buildUITemplateNoOutputs(() -> workable.getProgressPercent(0),
                    importItems, importFluids);
        }

        return GTGuis.createPanel(this, 176, 166 + yOffset)
                .child(IKey.lang(getMetaFullName()).asWidget().pos(6, 6))
                .child(recipeWidgets.pos(0, yOffset))
                .child(SlotGroupWidget.playerInventory(false).left(7).bottom(7));
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        Textures.ENERGY_OUT.renderSided(getFrontFacing(), renderState, translation,
                PipelineUtil.color(pipeline, GTValues.VC[getTier()]));
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.universal.tooltip.voltage_out", getEnergyContainer().getOutputVoltage(),
                GTValues.VNF[getTier()]));
        tooltip.add(I18n.format("gregtech.universal.tooltip.energy_storage_capacity",
                getEnergyContainer().getEnergyCapacity()));
        if (recipeMap.getMaxFluidInputs() > 0 || recipeMap.getMaxFluidOutputs() > 0) {
            tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity",
                    getTankScalingFunction().apply(getTier())));
        }
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        tooltip.add(I18n.format("gregtech.tool_action.soft_mallet.reset"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }

    @Override
    public boolean isAutoOutputItems() {
        return false;
    }

    @Override
    public boolean isAutoOutputFluids() {
        return false;
    }

    @Override
    public boolean isAllowInputFromOutputSideItems() {
        return false;
    }

    @Override
    public boolean isAllowInputFromOutputSideFluids() {
        return false;
    }

    @Override
    protected long getMaxInputOutputAmperage() {
        return 1L;
    }

    @Override
    protected boolean isEnergyEmitter() {
        return true;
    }

    @Override
    public boolean canVoidRecipeItemOutputs() {
        return !handlesRecipeOutputs;
    }

    @Override
    public boolean canVoidRecipeFluidOutputs() {
        return !handlesRecipeOutputs;
    }
}
