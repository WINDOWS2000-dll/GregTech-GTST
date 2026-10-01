package gregtech.api.metatileentity.multiblock;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.IMultipleTankHandler;

import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * The common surface OpenComputers
 * ({@code DriverRecipeMapMultiblockController}) actually needs from whichever recipe-driven multiblock controller
 * base a machine uses, so that integration doesn't have to hard-depend on any one specific base class. Implemented
 * by legacy {@code RecipeMapMultiblockController} and {@link RecipeWorkableMultiblockController}.
 * <p>
 * <b>Why this interface exists rather than a type check on a concrete base class:</b>
 * {@code DriverRecipeMapMultiblockController#createEnvironment} must recognize every recipe-driven multiblock
 * controller base that offers its {@code gt_multiblockRecipeLogic} OpenComputers component
 * (energy/inventory/tank/maintenance access), regardless of which engine backs it. Generalizing the driver's type
 * check to this interface (the same pattern {@code IRecipeLogicInfoProvider} uses for
 * HWYLA/TheOneProbe) covers every recipe-driven multiblock controller base at once.
 * <p>
 * {@link #getNumMaintenanceProblems()} is already common to both via {@link MultiblockWithDisplayBase} (their
 * shared superclass) -- restated here anyway so a caller holding only this interface doesn't need a second,
 * separate type check just for that one method.
 *
 * @see gregtech.api.capability.IRecipeLogicInfoProvider
 */
public interface IMultiblockRecipeLogicInfoProvider {

    IEnergyContainer getEnergyContainer();

    IItemHandlerModifiable getInputInventory();

    IItemHandlerModifiable getOutputInventory();

    IMultipleTankHandler getInputFluidInventory();

    IMultipleTankHandler getOutputFluidInventory();

    int getNumMaintenanceProblems();
}
