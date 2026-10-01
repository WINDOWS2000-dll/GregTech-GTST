package gregtech.api.capability;

import gregtech.api.recipes.Recipe;

import org.jetbrains.annotations.Nullable;

/**
 * The common surface HWYLA ({@code RecipeLogicDataProvider})/
 * TheOneProbe ({@code RecipeLogicInfoProvider}) hover info actually need from whichever recipe-running trait a
 * machine uses, so those integrations don't have to hard-depend on any one specific trait class.
 * <p>
 * <b>Why this interface exists rather than exposing the capability off a concrete trait type directly:</b>
 * {@link GregtechTileCapabilities#CAPABILITY_RECIPE_LOGIC} needs a capability type
 * every recipe-running trait can expose, regardless of which engine backs it. Generalizing the capability's type
 * to this interface (implemented by both
 * legacy's {@code AbstractRecipeLogic} and {@link gregtech.api.recipes.logic.statemachine.workable.RecipeWorkable})
 * lets HWYLA/TheOneProbe hover info work for every migrated machine without either integration needing
 * per-engine special-casing.
 */
public interface IRecipeLogicInfoProvider {

    /** @return whether this machine is actively progressing a recipe right now. */
    boolean isWorking();

    /**
     * @return the currently-active recipe's EU/t (or steam mB/t, for a steam machine -- callers distinguish this
     *         via the {@code MetaTileEntity}'s own type, not this value), always non-negative. 0 if idle.
     */
    long getInfoProviderEUt();

    /** @return {@code true} if the active recipe consumes energy, {@code false} if it generates energy instead. */
    boolean consumesEnergy();

    /**
     * @return the most recently admitted {@link Recipe} this machine ran (or is running), or {@code null} if none
     *         has been admitted yet (e.g. a freshly placed machine). Needed by OpenComputers'
     *         {@code gt_recipeLogic} component ({@code DriverAbstractRecipeLogic}) -- see
     *         {@code RecipeWorkable}'s own field JavaDoc for how it captures this without touching
     *         {@code ActiveRecipeList}'s deliberately lightweight NBT schema.
     */
    @Nullable
    Recipe getPreviousRecipe();
}
