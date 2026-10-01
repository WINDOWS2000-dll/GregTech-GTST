package gregtech.api.metatileentity;

import net.minecraft.util.EnumFacing;

/**
 * The common surface OpenComputers ({@code DriverSimpleMachine})
 * actually needs from whichever single-block machine base a machine uses, so that integration doesn't have to
 * hard-depend on any one specific base class. Implemented by legacy
 * {@code SimpleMachineMetaTileEntity} and {@code RecipeWorkableSimpleMachineMetaTileEntity}.
 * <p>
 * <b>Why this interface exists rather than a type check on a concrete base class:</b> {@code DriverSimpleMachine
 * #worksWith}/{@code #createEnvironment} must recognize every single-block machine base that offers its
 * {@code gt_machine} OpenComputers component
 * (auto-output/input-from-output-side/output-facing configuration), regardless of which engine backs it.
 * Generalizing the driver's type check to this interface (the same pattern
 * {@link gregtech.api.capability.IRecipeLogicInfoProvider} uses for HWYLA/TheOneProbe and
 * {@link gregtech.api.metatileentity.multiblock.IMultiblockRecipeLogicInfoProvider} uses for its own OpenComputers
 * component) covers every single-block machine base at once.
 * <p>
 * {@link #getTier()} is inherited from {@link ITieredMetaTileEntity} (already common to both via
 * {@link TieredMetaTileEntity}, their shared superclass) rather than restated here.
 */
public interface ISimpleMachineIO extends ITieredMetaTileEntity {

    boolean isAutoOutputItems();

    void setAutoOutputItems(boolean autoOutputItems);

    boolean isAutoOutputFluids();

    void setAutoOutputFluids(boolean autoOutputFluids);

    EnumFacing getOutputFacing();

    void setOutputFacing(EnumFacing outputFacing);

    boolean isAllowInputFromOutputSideItems();

    void setAllowInputFromOutputSideItems(boolean allowInputFromOutputSide);

    boolean isAllowInputFromOutputSideFluids();

    void setAllowInputFromOutputSideFluids(boolean allowInputFromOutputSide);
}
