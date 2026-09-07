package gregtech.api.recipes.output;

import gregtech.api.recipes.chance.boost.ChanceBoostFunction;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.roll.OverclockRollBoost;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;

/**
 * Computes a recipe's item outputs.
 * <p>
 * This interface does not extend a JEI display-control interface: JEI/tooltip presentation is
 * a separate, deferred concern (see {@code RollInterpreter}'s JavaDoc for the same scoping decision), kept out of
 * the core computation contract.
 * <p>
 * {@code properties} carries the power-related context a recipe's output computation may need; see
 * {@link RecipePropertySet}.
 */
public interface ItemOutputProvider {

    /**
     * @see #computeOutputs(List, List, RecipePropertySet, int, int, int, int)
     */
    @NotNull
    default List<ItemStack> computeOutputs(@UnmodifiableView @NotNull List<ItemStack> inputItems,
                                           @UnmodifiableView @NotNull List<FluidStack> inputFluids,
                                           @NotNull RecipePropertySet properties, int recipeTier, int machineTier,
                                           int parallel) {
        return computeOutputs(inputItems, inputFluids, properties, recipeTier, machineTier, parallel,
                Integer.MAX_VALUE);
    }

    /**
     * As {@link #computeOutputsWithBoost}, deriving {@code boostStrength} from {@code recipeTier}/{@code
     * machineTier} the same way {@link ChanceBoostFunction#OVERCLOCK} always has (see {@link OverclockRollBoost}).
     * This covers every caller that doesn't need a different {@link ChanceBoostFunction}; use
     * {@link #computeOutputsWithBoost} directly for one that does (e.g. {@code Recipe}'s bridge onto
     * {@code RecipeMap#getChanceFunction()}, which may be {@link ChanceBoostFunction#NONE}).
     *
     * @param inputItems  the items consumed by this recipe run.
     * @param inputFluids the fluids consumed by this recipe run.
     * @param properties  the power properties of this recipe run.
     * @param recipeTier  the recipe's own tier, for chance/yield boosting.
     * @param machineTier the tier the recipe is actually running at, for chance/yield boosting.
     * @param parallel    the parallel level of this recipe run.
     * @param trimLimit   the most distinct outputs allowed, before parallel is applied.
     * @return the item outputs for this recipe run.
     */
    @NotNull
    default List<ItemStack> computeOutputs(@UnmodifiableView @NotNull List<ItemStack> inputItems,
                                           @UnmodifiableView @NotNull List<FluidStack> inputFluids,
                                           @NotNull RecipePropertySet properties, int recipeTier, int machineTier,
                                           int parallel, int trimLimit) {
        return computeOutputsWithBoost(inputItems, inputFluids, properties,
                OverclockRollBoost.boostStrength(recipeTier, machineTier), parallel, trimLimit);
    }

    /**
     * As {@link #computeOutputs(List, List, RecipePropertySet, int, int, int, int)}, but taking the chance-boost
     * strength directly instead of deriving it from {@code recipeTier}/{@code machineTier} via the standard
     * {@link ChanceBoostFunction#OVERCLOCK} formula. This is the actual computation every {@code computeOutputs}
     * overload bottoms out in; implement this one.
     *
     * @param boostStrength how strongly to boost each chanced output's yield; {@code 0} means no boosting at all
     *                      (matching {@link ChanceBoostFunction#NONE}), matching {@link OverclockRollBoost}'s
     *                      result means matching {@link ChanceBoostFunction#OVERCLOCK} (the common case, and what
     *                      the {@code recipeTier}/{@code machineTier}-based overloads always pass).
     */
    @NotNull
    List<ItemStack> computeOutputsWithBoost(@UnmodifiableView @NotNull List<ItemStack> inputItems,
                                            @UnmodifiableView @NotNull List<FluidStack> inputFluids,
                                            @NotNull RecipePropertySet properties, int boostStrength, int parallel,
                                            int trimLimit);

    /**
     * @param parallel  the parallel level to simulate.
     * @param trimLimit the most distinct outputs allowed, before parallel is applied.
     * @return the maximum possible outputs of this provider, as if every chance-based output succeeded. Used for
     *         JEI display and output-space-fit calculations, not for actually rolling a recipe run.
     */
    @NotNull
    @UnmodifiableView
    List<ItemStack> getCompleteOutputs(int parallel, int trimLimit);

    /**
     * @return the most distinct {@link ItemStack}s {@link #computeOutputs} could ever return for the given
     *         {@code parallel}.
     */
    @Range(from = 0, to = Integer.MAX_VALUE)
    int getMaximumOutputs(@Range(from = 1, to = Integer.MAX_VALUE) int parallel);

    /**
     * @return whether this provider's configuration is internally valid (e.g. no output stack is empty/air).
     */
    boolean isValid();

    /**
     * @param limit the most distinct outputs to keep.
     * @return a provider reporting at most {@code limit} distinct outputs total: guaranteed outputs are kept
     *         first, with chanced ones only filling whatever slots are left over (successor to legacy
     *         {@code Recipe#getItemAndChanceOutputs}'s trimming semantics, e.g. for a macerator's limited output
     *         slots). Returns {@code this} unchanged if {@code limit} doesn't actually cut anything.
     */
    @NotNull
    ItemOutputProvider trim(@Range(from = 0, to = Integer.MAX_VALUE) int limit);
}
