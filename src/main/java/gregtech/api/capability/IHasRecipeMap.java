package gregtech.api.capability;

import gregtech.api.recipes.RecipeMap;

import org.jetbrains.annotations.Nullable;

/**
 * Implemented by an {@link gregtech.api.metatileentity.MTETrait} that has a {@link RecipeMap}.
 * {@link gregtech.api.metatileentity.MetaTileEntity#getRecipeMap()}
 * looks for any trait implementing this, rather than requiring one specific trait class the way
 * {@code gregtech.api.metatileentity.MetaTileEntity#getRecipeLogic()} does &mdash; letting a StateMachine-based
 * recipe trait report its {@link RecipeMap} without needing to also stand
 * in for a legacy recipe-logic trait's much larger API.
 */
public interface IHasRecipeMap {

    @Nullable
    RecipeMap<?> getRecipeMap();
}
