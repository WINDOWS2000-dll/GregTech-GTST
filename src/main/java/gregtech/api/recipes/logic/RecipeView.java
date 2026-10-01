package gregtech.api.recipes.logic;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A recipe matched against a specific set of inputs, at a specific parallel level.
 * <p>
 * {@link #getParallel()} is a genuine scaling factor: {@link Recipe} carries its own intrinsic amperage
 * ({@link Recipe#getAmperage()}), so parallel copies are represented as "this recipe, {@code n} times" rather than
 * GregTech's older approach of merging {@code n} copies into a single recipe with {@code n}&times; the ingredient/
 * output amounts beforehand. {@link #getActualAmperage()} and this interface's other methods apply that scaling
 * themselves; see {@code StandardRecipeView}'s JavaDoc for exactly where.
 * <p>
 * {@link #getConsumedItems()}/{@link #getConsumedFluids()} take no {@code rollBoost} parameter: GregTech has no concept
 * of chance-based/boostable recipe <i>inputs</i> (only outputs), so there is nothing for such a parameter to scale.
 */
public interface RecipeView {

    int getParallel();

    /**
     * Use sparingly. If this view has a dedicated method for what you need, use that whenever possible.
     */
    @NotNull
    Recipe getRecipe();

    default int getActualDuration() {
        return getRecipe().getDuration();
    }

    /** The recipe's raw per-unit voltage, before any overclocking. See {@link Recipe#getVoltage()}. */
    default long getActualEUt() {
        return getRecipe().getVoltage();
    }

    /**
     * @return the total amperage this view actually requires: {@link Recipe#getAmperage()} scaled by
     *         {@link #getParallel()}. Unlike {@link #getActualEUt()} (per-unit voltage, unaffected by parallel),
     *         amperage is where parallel copies add up.
     */
    default long getActualAmperage() {
        return getRecipe().getAmperage() * (long) getParallel();
    }

    /**
     * @return the items this view's recipe would actually consume from the inputs it was matched against (see
     *         implementations for how that match is captured). Amounts already account for non-consumable
     *         ingredients (excluded) and this view's {@link #getParallel()}.
     */
    @NotNull
    List<ItemStack> getConsumedItems();

    /** As {@link #getConsumedItems()}, but for fluids. */
    @NotNull
    List<FluidStack> getConsumedFluids();

    /**
     * Computes this recipe's item outputs for this run, rolling any chance-based ones (see
     * {@code gregtech.api.recipes.output.ItemOutputProvider}). Already scaled by {@link #getParallel()}.
     *
     * @param itemTrimLimit the most distinct item outputs to keep (see
     *                      {@code gregtech.api.recipes.output.ItemOutputProvider#trim}); guaranteed outputs are
     *                      kept first, with chanced ones only filling whatever slots (and chance rolls) are left
     *                      over. Pass {@link Integer#MAX_VALUE} for no trimming.
     */
    @NotNull
    List<ItemStack> rollItems(@NotNull RecipePropertySet properties, int recipeTier, int machineTier,
                              int itemTrimLimit);

    /** As {@link #rollItems}, but for fluids. */
    @NotNull
    List<FluidStack> rollFluids(@NotNull RecipePropertySet properties, int recipeTier, int machineTier,
                                int fluidTrimLimit);
}
