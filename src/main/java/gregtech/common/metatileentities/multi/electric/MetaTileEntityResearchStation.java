package gregtech.common.metatileentities.multi.electric;

import gregtech.api.GTValues;
import gregtech.api.capability.IObjectHolder;
import gregtech.api.capability.IOpticalComputationHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalComputationReceiver;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.KeyManager;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.metatileentity.multiblock.ui.UISyncer;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.MultiblockShapeInfo;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeStallType;
import gregtech.api.recipes.logic.statemachine.computation.ComputationRecipeHooks;
import gregtech.api.recipes.logic.statemachine.computation.ComputationType;
import gregtech.api.recipes.logic.statemachine.property.ComputationProperties;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.ConfigHolder;
import gregtech.common.blocks.BlockComputerCasing;
import gregtech.common.blocks.MetaBlocks;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static gregtech.api.util.RelativeDirection.*;

/**
 * As before, but backed by {@link RecipeWorkableMultiblockController}
 * instead of {@code RecipeMapMultiblockController}/legacy {@code ComputationRecipeLogic}'s dedicated
 * recipe-logic subclass. Notable pitfalls this design avoids: the search-side
 * CWU capacity property never actually being exposed, CWU being drawn twice per tick, and energy no longer always
 * being drawn on a computation shortfall.
 * <p>
 * <b>Object Holder item replacement:</b> unlike a normal machine, this one has no separate output inventory --
 * the single Object Holder slot serves as both input and output, with the finished data item replacing the
 * research item in place. {@link #createConfig()} expresses this by overriding {@code config.io.itemOutput}/
 * {@code itemOutputSpace} directly (bypassing the standard "deliver to output inventory" hook entirely) rather than
 * needing any new engine machinery -- this is a purely machine-specific detail, not a generic concern.
 * <p>
 * <b>Locking</b> the holder for the duration of a run (legacy: on recipe setup) is expressed via
 * {@code config.hooks.finalCheck} -- the last point before a resolved run is queued, and (since this machine's
 * {@code parallelLimit} stays at the inherited default of 1) effectively equivalent to "recipe just started".
 */
public class MetaTileEntityResearchStation extends RecipeWorkableMultiblockController
                                           implements IOpticalComputationReceiver {

    private IOpticalComputationProvider computationProvider;
    private IObjectHolder objectHolder;

    public MetaTileEntityResearchStation(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.RESEARCH_STATION_RECIPES);
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityResearchStation(metaTileEntityId);
    }

    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        // This machine never overclocks (legacy ResearchStationRecipeLogic#isAllowOverclocking() == false).
        config.overclock.ocAmountCalculator = (recipeVoltage, maxVoltage) -> 0;
        // Legacy hardcodes a plain revert-by-one-tick on any per-tick check failure (both energy and, for STEADY,
        // computation shortfalls) -- RecipeStallType.DEGRESS is exactly that.
        config.hooks.stallType = RecipeStallType.DEGRESS;
        config.hooks.entryEnricher = ComputationRecipeHooks::enrichEntry;
        config.hooks.perTickRecipeCheck = ComputationRecipeHooks.perTickRecipeCheck(this::getComputationProvider,
                ComputationType.SPORADIC, this::drainRecipeEnergy);
        config.hooks.progressOperationOverride = ComputationRecipeHooks.progressOverride(this::getComputationProvider);
        // Advertises this array's currently available CWU/t to the search, adding the property to the *returned*
        // set here rather than replacing the base call's own set outright (which would drop cleanroom/dimension
        // advertising -- see this class's own JavaDoc for the class of bug that would cause).
        var baseProperties = config.power.properties;
        config.power.properties = () -> {
            var properties = baseProperties.get();
            properties.add(ComputationProperties.of(getComputationProvider()));
            return properties;
        };
        // No separate output inventory -- the finished data item replaces the research item in the Object Holder
        // slot directly (legacy ResearchStationRecipeLogic#outputRecipeOutputs, verbatim) rather than being
        // delivered anywhere conventional, so "is there space" is trivially always true.
        config.io.itemOutputSpace = items -> true;
        config.io.itemOutput = outputs -> {
            objectHolder.setHeldItem(ItemStack.EMPTY);
            ItemStack outputItem = outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0);
            objectHolder.setDataItem(outputItem);
            objectHolder.setLocked(false);
        };
        // Rejects a resolved run if the item it would consume isn't actually what's currently held.
        //
        // Must compare with isItemEqual (item + metadata only), not
        // areItemStacksEqual (which also compares NBT) -- research/data items are registered via
        // AssemblyLineManager#createDefaultResearchRecipe with inputNBT(..., NBTMatcher.ANY, ...) specifically
        // because each one carries its own unique in-progress research NBT tag that the recipe's own ingredient
        // template does not (and cannot) reproduce. Comparing NBT here made this check fail unconditionally,
        // silently blocking every Research Station recipe from ever starting.
        config.hooks.finalCheck = run -> !run.getItemsConsumed().isEmpty() &&
                run.getItemsConsumed().get(0).isItemEqual(objectHolder.getHeldItem(false));
        // Locks the holder once a recipe genuinely starts (legacy ResearchStationRecipeLogic#setupRecipe).
        //
        // Locking from within config.hooks.finalCheck instead would be wrong, even though finalCheck (the last
        // check before a candidate is queued) looks like it should double as legacy's "setup"
        // moment: finalCheck runs during *search*, one full step
        // before RecipeQueueAdmissionOperator actually extracts the item from the Object Holder's own handler.
        // MetaTileEntityObjectHolder#ObjectHolderHandler#extractItem unconditionally returns EMPTY while locked
        // (by design, to stop the item being pulled out mid-research) -- so locking this early made every
        // admission attempt's own extraction fail and discard the candidate as "stale", forever, before a recipe
        // could ever actually start. onRecipeStarted fires from admission itself, after real consumption already
        // succeeded, which is the only point this lock is actually safe to set.
        config.callbacks.onRecipeStarted = entry -> objectHolder.setLocked(true);
        return config;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        List<IOpticalComputationHatch> providers = getAbilities(MultiblockAbility.COMPUTATION_DATA_RECEPTION);
        if (providers != null && providers.size() >= 1) {
            computationProvider = providers.get(0);
        }
        List<IObjectHolder> holders = getAbilities(MultiblockAbility.OBJECT_HOLDER);
        if (holders != null && holders.size() >= 1) {
            objectHolder = holders.get(0);
            // cannot set in initializeAbilities since super() calls it before setting the objectHolder field here
            this.inputInventory = new ItemHandlerList(Collections.singletonList(objectHolder.getAsHandler()));
        }

        // should never happen, but would rather do this than have an obscure NPE
        if (computationProvider == null || objectHolder == null) {
            invalidateStructure();
        }
    }

    // force object holder to be facing the controller
    @Override
    public void checkStructurePattern() {
        super.checkStructurePattern();
        if (isStructureFormed() && objectHolder.getFrontFacing() != getFrontFacing().getOpposite()) {
            invalidateStructure();
        }
    }

    @Override
    public void invalidateStructure() {
        computationProvider = null;
        // recheck the ability to make sure it wasn't the one broken
        List<IObjectHolder> holders = getAbilities(MultiblockAbility.OBJECT_HOLDER);
        if (holders != null && holders.size() >= 1 && holders.get(0) == objectHolder) {
            objectHolder.setLocked(false);
        }
        objectHolder = null;
        super.invalidateStructure();
    }

    @Override
    public IOpticalComputationProvider getComputationProvider() {
        return computationProvider;
    }

    public IObjectHolder getObjectHolder() {
        return objectHolder;
    }

    @NotNull
    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
                .aisle("XXX", "VVV", "PPP", "PPP", "PPP", "VVV", "XXX")
                .aisle("XXX", "VAV", "AAA", "AAA", "AAA", "VAV", "XXX")
                .aisle("XXX", "VAV", "XAX", "XSX", "XAX", "VAV", "XXX")
                .aisle("XXX", "XAX", "---", "---", "---", "XAX", "XXX")
                .aisle(" X ", "XAX", "---", "---", "---", "XAX", " X ")
                .aisle(" X ", "XAX", "-A-", "-H-", "-A-", "XAX", " X ")
                .aisle("   ", "XXX", "---", "---", "---", "XXX", "   ")
                .where('S', selfPredicate())
                .where('X', states(getCasingState()))
                .where(' ', any())
                .where('-', air())
                .where('V', states(getVentState()))
                .where('A', states(getAdvancedState()))
                .where('P', states(getCasingState())
                        .or(abilities(MultiblockAbility.INPUT_ENERGY).setMinGlobalLimited(1))
                        .or(maintenancePredicate())
                        .or(abilities(MultiblockAbility.COMPUTATION_DATA_RECEPTION).setExactLimit(1)))
                .where('H', abilities(MultiblockAbility.OBJECT_HOLDER))
                .build();
    }

    @Override
    public List<MultiblockShapeInfo> getMatchingShapes() {
        return Collections.singletonList(MultiblockShapeInfo.builder(RIGHT, DOWN, FRONT)
                .aisle("XXX", "VVV", "POP", "PEP", "PMP", "VVV", "XXX")
                .aisle("XXX", "VAV", "AAA", "AAA", "AAA", "VAV", "XXX")
                .aisle("XXX", "VAV", "XAX", "XSX", "XAX", "VAV", "XXX")
                .aisle("XXX", "XAX", "---", "---", "---", "XAX", "XXX")
                .aisle("-X-", "XAX", "---", "---", "---", "XAX", "-X-")
                .aisle("-X-", "XAX", "-A-", "-H-", "-A-", "XAX", "-X-")
                .aisle("---", "XXX", "---", "---", "---", "XXX", "---")
                .where('S', MetaTileEntities.RESEARCH_STATION, EnumFacing.SOUTH)
                .where('X', getCasingState())
                .where('-', Blocks.AIR.getDefaultState())
                .where('V', getVentState())
                .where('A', getAdvancedState())
                .where('P', getCasingState())
                .where('O', MetaTileEntities.COMPUTATION_HATCH_RECEIVER, EnumFacing.NORTH)
                .where('E', MetaTileEntities.ENERGY_INPUT_HATCH[GTValues.LuV], EnumFacing.NORTH)
                .where('M',
                        () -> ConfigHolder.machines.enableMaintenance ? MetaTileEntities.MAINTENANCE_HATCH :
                                getCasingState(),
                        EnumFacing.NORTH)
                .where('H', MetaTileEntities.OBJECT_HOLDER, EnumFacing.NORTH)
                .build());
    }

    @NotNull
    private static IBlockState getVentState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.COMPUTER_HEAT_VENT);
    }

    @NotNull
    private static IBlockState getAdvancedState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.ADVANCED_COMPUTER_CASING);
    }

    @NotNull
    private static IBlockState getCasingState() {
        return MetaBlocks.COMPUTER_CASING.getState(BlockComputerCasing.CasingType.COMPUTER_CASING);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        if (sourcePart == null || sourcePart instanceof IObjectHolder) {
            return Textures.ADVANCED_COMPUTER_CASING;
        }
        return Textures.COMPUTER_CASING;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.RESEARCH_STATION_OVERLAY;
    }

    @Override
    public boolean shouldShowVoidingModeButton() {
        return false;
    }

    // let it think it can "void" since we replace an input item with the finished
    // item on completion, instead of outputting into a dedicated output slot.
    @Override
    public boolean canVoidRecipeItemOutputs() {
        return true;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, @NotNull List<String> tooltip,
                               boolean advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.research_station.tooltip.1"));
        tooltip.add(I18n.format("gregtech.machine.research_station.tooltip.2"));
        tooltip.add(I18n.format("gregtech.machine.research_station.tooltip.3"));
        tooltip.add(I18n.format("gregtech.machine.research_station.tooltip.4"));
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        // addRecipeOutputLine dropped: RecipeWorkable has no getPreviousRecipe() equivalent to back it -- see
        // RecipeWorkableMultiblockController's JavaDoc "Recipe-output preview line intentionally omitted". Legacy's
        // more detailed "researching: <sub-research item>" line (reading AssemblyLineManager research metadata off
        // the previous recipe) is dropped for the same reason, replaced by the plain CWU progress line below.
        builder.setWorkingStatus(workable.isWorkingEnabled(), workable.isActive())
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(getEnergyContainer().getInputVoltage()))
                .addComputationUsageExactLine(
                        workable.isActive() ?
                                workable.getActiveRecipeCustomInt(0, ComputationRecipeHooks.ENTRY_CWU_PER_TICK_KEY) :
                                0)
                .addWorkingStatusLine()
                .addCustom(this::addComputationProgress);
    }

    private void addComputationProgress(KeyManager manager, UISyncer syncer) {
        if (!workable.isActive()) return;
        int progress = syncer.syncInt(workable.getProgress(0));
        int maxProgress = syncer.syncInt(workable.getMaxProgress(0));
        manager.add(IKey.str("%s / %s CWU", progress, maxProgress).style(TextFormatting.GRAY));
    }

    @Override
    protected void configureWarningText(MultiblockUIBuilder builder) {
        builder.addLowPowerLine(insufficientEnergy())
                .addLowComputationLine(workable.isActive() &&
                        !workable.getActiveRecipeCustomBoolean(0,
                                ComputationRecipeHooks.ENTRY_HAS_ENOUGH_COMPUTATION_KEY));
        super.configureWarningText(builder);
    }
}
