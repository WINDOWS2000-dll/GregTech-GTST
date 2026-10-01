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
 * Computes a recipe's fluid outputs. See {@link ItemOutputProvider} (its exact fluid counterpart) for the design
 * rationale.
 */
public interface FluidOutputProvider {

    /**
     * @see #computeOutputs(List, List, RecipePropertySet, int, int, int, int)
     */
    @NotNull
    default List<FluidStack> computeOutputs(@UnmodifiableView @NotNull List<ItemStack> inputItems,
                                            @UnmodifiableView @NotNull List<FluidStack> inputFluids,
                                            @NotNull RecipePropertySet properties, int recipeTier, int machineTier,
                                            int parallel) {
        return computeOutputs(inputItems, inputFluids, properties, recipeTier, machineTier, parallel,
                Integer.MAX_VALUE);
    }

    /**
     * As {@link #computeOutputsWithBoost}, deriving {@code boostStrength} from {@code recipeTier}/{@code
     * machineTier} the same way {@link ChanceBoostFunction#OVERCLOCK} always has (see {@link OverclockRollBoost}).
     * See {@link ItemOutputProvider}'s exact item counterpart for why this exists alongside
     * {@link #computeOutputsWithBoost}.
     *
     * @param inputItems  the items consumed by this recipe run.
     * @param inputFluids the fluids consumed by this recipe run.
     * @param properties  the power properties of this recipe run.
     * @param recipeTier  the recipe's own tier, for chance/yield boosting.
     * @param machineTier the tier the recipe is actually running at, for chance/yield boosting.
     * @param parallel    the parallel level of this recipe run.
     * @param trimLimit   the most distinct outputs allowed, before parallel is applied.
     * @return the fluid outputs for this recipe run.
     */
    @NotNull
    default List<FluidStack> computeOutputs(@UnmodifiableView @NotNull List<ItemStack> inputItems,
                                            @UnmodifiableView @NotNull List<FluidStack> inputFluids,
                                            @NotNull RecipePropertySet properties, int recipeTier, int machineTier,
                                            int parallel, int trimLimit) {
        return computeOutputsWithBoost(inputItems, inputFluids, properties,
                OverclockRollBoost.boostStrength(recipeTier, machineTier), parallel, trimLimit);
    }

    /**
     * As {@link #computeOutputs(List, List, RecipePropertySet, int, int, int, int)}, but taking the chance-boost
     * strength directly. See {@link ItemOutputProvider#computeOutputsWithBoost} (its exact item counterpart) for
     * the full contract; implement this one.
     */
    @NotNull
    List<FluidStack> computeOutputsWithBoost(@UnmodifiableView @NotNull List<ItemStack> inputItems,
                                             @UnmodifiableView @NotNull List<FluidStack> inputFluids,
                                             @NotNull RecipePropertySet properties, int boostStrength, int parallel,
                                             int trimLimit);

    /**
     * @param parallel  the parallel level to simulate.
     * @param trimLimit the most distinct outputs allowed, before parallel is applied.
     * @return the maximum possible outputs of this provider, as if every chance-based output succeeded.
     */
    @NotNull
    @UnmodifiableView
    List<FluidStack> getCompleteOutputs(int parallel, int trimLimit);

    /**
     * @return the most distinct {@link FluidStack}s {@link #computeOutputs} could ever return for the given
     *         {@code parallel}.
     */
    @Range(from = 0, to = Integer.MAX_VALUE)
    int getMaximumOutputs(@Range(from = 1, to = Integer.MAX_VALUE) int parallel);

    /**
     * @return whether this provider's configuration is internally valid (e.g. no output stack is empty).
     */
    boolean isValid();

    /**
     * @param limit the most distinct outputs to keep.
     * @return a provider reporting at most {@code limit} distinct outputs total. See
     *         {@link ItemOutputProvider#trim} (its exact item counterpart) for the full contract.
     */
    @NotNull
    FluidOutputProvider trim(@Range(from = 0, to = Integer.MAX_VALUE) int limit);
}
