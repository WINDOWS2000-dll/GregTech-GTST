package gregtech.common.metatileentities.multi.electric;

import gregtech.api.capability.IDistillationTower;
import gregtech.api.capability.impl.DistillationTowerLogicHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.util.GTTransferUtils;
import gregtech.api.util.RelativeDirection;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

import static gregtech.api.util.RelativeDirection.*;

/**
 * Migrated from {@code RecipeMapMultiblockController} to
 * {@link RecipeWorkableMultiblockController}. No intermediate {@code fluidOutputBuffer} array is needed: this trait's
 * {@link RecipeLogicConfig#io}'s {@code fluidOutputSpace} (a pre-check, simulated) and {@code fluidOutput} (the
 * actual delivery, called exactly once per completed recipe from {@code RecipeOutputOperator}) are already two
 * cleanly separate hooks -- {@link DistillationTowerLogicHandler#applyFluidToOutputs} can be called directly from
 * each, with {@code doFill} toggled accordingly, exactly as legacy's own {@code DistillationTowerRecipeLogic} did
 * ({@code checkOutputSpaceFluids}/{@code outputRecipeOutputs}). No intermediate state to get wrong.
 * <p>
 * <b>{@link #handler} vs. {@code createConfig()}'s construction-order trap:</b> {@link #handler} is assigned in this
 * constructor's body, which only runs <i>after</i> {@code super(...)} returns -- and {@code createConfig()} is
 * itself called from inside that {@code super(...)} call, so {@link #handler} is still {@code null} at the moment
 * {@code createConfig()} executes, regardless of {@code useAdvHatchLogic}. This is exactly the same pitfall
 * {@code RecipeWorkableTieredMetaTileEntity}'s {@code initializeInventory()} JavaDoc documents for subclass fields
 * in general. Multi Smelter's {@code createConfig()} sidesteps the identical problem for its own subclass fields
 * (`heatingCoilLevel}/{@code heatingCoilDiscount}) by only ever reading them from inside a lazily-invoked lambda
 * (a {@code Supplier}/{@code IntSupplier}), never eagerly at {@code createConfig()} call time -- this class follows
 * the same pattern: {@code config.io.fluidOutputSpace}/{@code fluidOutput}/{@code fluidTrim} are all lambdas that
 * read {@link #handler} only when actually invoked (search/output time, long after construction), never during
 * {@code createConfig()} itself.
 */
public class MetaTileEntityDistillationTower extends RecipeWorkableMultiblockController implements IDistillationTower {

    protected DistillationTowerLogicHandler handler;

    @SuppressWarnings("unused") // backwards compatibility
    public MetaTileEntityDistillationTower(ResourceLocation metaTileEntityId) {
        this(metaTileEntityId, false);
    }

    public MetaTileEntityDistillationTower(ResourceLocation metaTileEntityId, boolean useAdvHatchLogic) {
        super(metaTileEntityId, RecipeMaps.DISTILLATION_RECIPES);
        this.handler = useAdvHatchLogic ? new DistillationTowerLogicHandler(this) : null;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityDistillationTower(metaTileEntityId, this.handler != null);
    }

    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        // See this class's JavaDoc: handler is read lazily inside these lambdas, never here, so it's safe that
        // it's still null (regardless of useAdvHatchLogic) at the moment this method itself runs.
        config.io.fluidOutputSpace = fluids -> handler == null || canVoidRecipeFluidOutputs() ||
                handler.applyFluidToOutputs(fluids, false);
        config.io.fluidOutput = fluids -> {
            if (handler != null) handler.applyFluidToOutputs(fluids, true);
            // else: no handler (legacy useAdvHatchLogic=false path) -- fall back to the same combined-inventory
            // delivery RecipeWorkableMultiblockController#createConfig() wires by default.
            else GTTransferUtils.addFluidsToFluidHandler(getOutputFluidInventory(), false, fluids);
        };
        config.io.fluidTrim = () -> handler == null ? Integer.MAX_VALUE : handler.getLayerCount();
        return config;
    }

    /**
     * Used if MultiblockPart Abilities need to be sorted a certain way, like
     * Distillation Tower and Assembly Line. <br>
     * <br>
     * There will be <i>consequences</i> if this is changed. Make sure to set the logic handler to one with
     * a properly overriden {@link DistillationTowerLogicHandler#determineOrderedFluidOutputs()}
     */
    @Override
    protected Function<BlockPos, Integer> multiblockPartSorter() {
        return RelativeDirection.UP.getSorter(getFrontFacing(), getUpwardsFacing(), isFlipped());
    }

    /**
     * Whether this multi can be rotated or face upwards. <br>
     * <br>
     * There will be <i>consequences</i> if this returns true. Make sure to set the logic handler to one with
     * a properly overriden {@link DistillationTowerLogicHandler#determineOrderedFluidOutputs()}
     */
    @Override
    public boolean allowsExtendedFacing() {
        return false;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        if (this.handler == null || this.structurePattern == null) return;
        handler.determineLayerCount(this.structurePattern);
        handler.determineOrderedFluidOutputs();
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        if (this.handler != null) handler.invalidate();
    }

    @NotNull
    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start(RIGHT, FRONT, UP)
                .aisle("YSY", "YYY", "YYY")
                .aisle("XXX", "X#X", "XXX").setRepeatable(1, 11)
                .aisle("XXX", "XXX", "XXX")
                .where('S', selfPredicate())
                .where('Y', states(getCasingState())
                        .or(abilities(MultiblockAbility.EXPORT_ITEMS).setMaxGlobalLimited(1))
                        .or(abilities(MultiblockAbility.INPUT_ENERGY).setMinGlobalLimited(1).setMaxGlobalLimited(3))
                        .or(abilities(MultiblockAbility.IMPORT_FLUIDS).setExactLimit(1)))
                .where('X', states(getCasingState())
                        .or(abilities(MultiblockAbility.EXPORT_FLUIDS).setMaxLayerLimited(1, 1))
                        .or(autoAbilities(true, false)))
                .where('#', air())
                .build();
    }

    @Override
    public boolean allowSameFluidFillForOutputs() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.CLEAN_STAINLESS_STEEL_CASING;
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(MetalCasingType.STAINLESS_CLEAN);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.DISTILLATION_TOWER_OVERLAY;
    }

    /**
     * Kept verbatim from the legacy class for fidelity, even though the new StateMachine engine's own output-space
     * checking doesn't consult it (it uses {@code config.io.fluidTrim}, wired above, instead) -- see
     * {@code RecipeWorkableSimpleMachineMetaTileEntityResizable#getItemOutputLimit()}'s identical precedent.
     */
    @Override
    public int getFluidOutputLimit() {
        if (this.handler != null) return this.handler.getLayerCount();
        else return super.getFluidOutputLimit();
    }
}
