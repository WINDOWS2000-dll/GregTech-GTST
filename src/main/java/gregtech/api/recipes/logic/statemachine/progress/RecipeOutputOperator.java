package gregtech.api.recipes.logic.statemachine.progress;

import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeFinalizer;
import gregtech.api.recipes.logic.statemachine.RecipeIOConfig;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.statemachine.GTStateMachineOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Delivers the currently-selected (just-completed) active recipe's outputs via {@link RecipeIOConfig#itemOutput}/
 * {@link RecipeIOConfig#fluidOutput}, first running {@link RecipeLogicConfig#hooks}'
 * {@link gregtech.api.recipes.logic.statemachine.RecipeLogicHooks#recipeFinalizer} if set.
 * <p>
 * A finalizer's result is written back onto the entry (via {@link ActiveRecipeList#setItemsOut}/
 * {@link ActiveRecipeList#setFluidsOut}) before delivery, not just used locally: {@code RecipeProgressTrackBuilder}
 * notifies {@code RecipeLogicCallbacks#onRecipeCompleted} with this same entry afterward, so writing back ensures
 * that callback also observes the finalized outputs rather than the pre-finalized ones.
 */
public final class RecipeOutputOperator implements GTStateMachineOperator {

    private final @NotNull RecipeLogicConfig config;

    public RecipeOutputOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    public void operate(NBTTagCompound data) {
        NBTTagCompound entry = ActiveRecipeList.selected(data);
        List<ItemStack> itemsOut = ActiveRecipeList.itemsOut(entry);
        List<FluidStack> fluidsOut = ActiveRecipeList.fluidsOut(entry);

        RecipeFinalizer finalizer = config.hooks.recipeFinalizer;
        if (finalizer != null) {
            RecipeFinalizer.Result result = finalizer.finalizeOutputs(entry, itemsOut, fluidsOut);
            itemsOut = result.itemsOut();
            fluidsOut = result.fluidsOut();
            ActiveRecipeList.setItemsOut(entry, itemsOut);
            ActiveRecipeList.setFluidsOut(entry, fluidsOut);
        }

        if (!itemsOut.isEmpty()) config.io.itemOutput.accept(itemsOut);
        if (!fluidsOut.isEmpty()) config.io.fluidOutput.accept(fluidsOut);
    }
}
