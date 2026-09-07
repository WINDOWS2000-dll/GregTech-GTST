package gregtech.api.metatileentity;

import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.client.particle.IMachineParticleEffect;
import gregtech.client.renderer.ICubeRenderer;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * As {@code gregtech.common.metatileentities.electric.SimpleMachineMetaTileEntityResizable}, but backed by
 * {@link RecipeWorkableSimpleMachineMetaTileEntity}
 * instead of {@code SimpleMachineMetaTileEntity}: a machine whose item I/O slot count differs from
 * {@link RecipeMap#getMaxInputs()}/{@link RecipeMap#getMaxOutputs()} by tier (e.g. Macerator/Arc Furnace, which
 * gain extra output slots at higher tiers for chanced byproducts).
 * <p>
 * {@link #getItemOutputLimit()} is overridden verbatim from the legacy class for fidelity, even though the new
 * StateMachine engine's own output-space checking doesn't consult it (unlike {@code AbstractRecipeLogic}'s
 * {@code Recipe#trimRecipeOutputs}) &mdash; harmless to keep in case something else (e.g. a future JEI recipe-
 * output-preview line, see {@link RecipeWorkableTieredMetaTileEntity}'s JavaDoc for why that's currently omitted)
 * ends up needing it.
 */
public class RecipeWorkableSimpleMachineMetaTileEntityResizable extends RecipeWorkableSimpleMachineMetaTileEntity {

    private final int inputAmount;
    private final int outputAmount;

    /**
     * @param inputAmount  Number of Item Input Slots for this machine. Pass -1 to use the default value from the
     *                     RecipeMap.
     * @param outputAmount Number of Item Output Slots for this machine. Pass -1 to use the default value from the
     *                     RecipeMap.
     */
    public RecipeWorkableSimpleMachineMetaTileEntityResizable(ResourceLocation metaTileEntityId,
                                                              RecipeMap<?> recipeMap, int inputAmount,
                                                              int outputAmount, ICubeRenderer renderer, int tier) {
        super(metaTileEntityId, recipeMap, renderer, tier, true);
        this.inputAmount = inputAmount;
        this.outputAmount = outputAmount;
        initializeInventory();
    }

    /**
     * @param inputAmount  Number of Item Input Slots for this machine. Pass -1 to use the default value from the
     *                     RecipeMap.
     * @param outputAmount Number of Item Output Slots for this machine. Pass -1 to use the default value from the
     *                     RecipeMap.
     */
    public RecipeWorkableSimpleMachineMetaTileEntityResizable(ResourceLocation metaTileEntityId,
                                                              RecipeMap<?> recipeMap, int inputAmount,
                                                              int outputAmount, ICubeRenderer renderer, int tier,
                                                              boolean hasFrontFacing,
                                                              Function<Integer, Integer> tankScalingFunction) {
        super(metaTileEntityId, recipeMap, renderer, tier, hasFrontFacing, tankScalingFunction);
        this.inputAmount = inputAmount;
        this.outputAmount = outputAmount;
        initializeInventory();
    }

    public RecipeWorkableSimpleMachineMetaTileEntityResizable(ResourceLocation metaTileEntityId,
                                                              RecipeMap<?> recipeMap, int inputAmount,
                                                              int outputAmount, ICubeRenderer renderer, int tier,
                                                              boolean hasFrontFacing,
                                                              Function<Integer, Integer> tankScalingFunction,
                                                              @Nullable IMachineParticleEffect tickingParticle,
                                                              @Nullable IMachineParticleEffect randomParticle) {
        super(metaTileEntityId, recipeMap, renderer, tier, hasFrontFacing, tankScalingFunction, tickingParticle,
                randomParticle);
        this.inputAmount = inputAmount;
        this.outputAmount = outputAmount;
        initializeInventory();
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        if (inputAmount != -1) {
            return new NotifiableItemStackHandler(this, inputAmount, this, false);
        }
        return super.createImportItemHandler();
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        if (outputAmount != -1) {
            return new NotifiableItemStackHandler(this, outputAmount, this, true);
        }
        return super.createExportItemHandler();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new RecipeWorkableSimpleMachineMetaTileEntityResizable(metaTileEntityId, recipeMap, inputAmount,
                outputAmount, renderer, getTier(), hasFrontFacing(), getTankScalingFunction(), tickingParticle,
                randomParticle);
    }

    @Override
    public int getItemOutputLimit() {
        return outputAmount;
    }
}
