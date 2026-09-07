package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.logic.statemachine.progress.RecipeOutputOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Transforms a just-completed active recipe's outputs immediately before delivery. Unlike
 * {@link RecipeLogicCallbacks#onRecipeCompleted} (a pure notification with no return value), this hook can
 * actually change what gets delivered &mdash; e.g. applying a global yield multiplier, or converting some item
 * output into an equivalent fluid.
 * <p>
 * Runs inside {@link RecipeOutputOperator}, which writes the returned {@link Result} back onto the active-recipe
 * entry (via {@code ActiveRecipeList#setItemsOut}/{@code setFluidsOut}) before delivering it and before
 * {@link RecipeLogicCallbacks#onRecipeCompleted} is notified &mdash; so both delivery and the completion callback
 * see the finalized outputs, not the pre-finalized ones.
 */
@FunctionalInterface
public interface RecipeFinalizer {

    /**
     * @param entry     the completed active recipe's full entry (see {@code ActiveRecipeList}), for context (e.g.
     *                  reading how many overclocks were applied). Must not be mutated directly; return a
     *                  {@link Result} instead.
     * @param itemsOut  the item outputs about to be delivered.
     * @param fluidsOut the fluid outputs about to be delivered.
     * @return the outputs to actually deliver in place of {@code itemsOut}/{@code fluidsOut}.
     */
    @NotNull
    Result finalizeOutputs(@NotNull NBTTagCompound entry, @NotNull List<ItemStack> itemsOut,
                           @NotNull List<FluidStack> fluidsOut);

    /** @see #finalizeOutputs */
    @Desugar
    record Result(@NotNull List<ItemStack> itemsOut, @NotNull List<FluidStack> fluidsOut) {}
}
