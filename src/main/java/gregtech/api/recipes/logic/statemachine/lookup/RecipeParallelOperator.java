package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.ingredients.GTRecipeInput;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Determines how many copies of {@link RecipeSelectionOperator}'s selected candidate can actually run given the
 * remaining parallel budget, available ingredients, and available amperage, and passes that count (and the
 * candidate itself, unchanged) forward for {@link RecipeViewBuildOperator} to build a view from.
 * <p>
 * <b>Does not merge {@code candidate} into a single N-wide {@link Recipe}:</b> {@link Recipe} carries its
 * own intrinsic {@link Recipe#getAmperage()}, so "N copies of this recipe" is represented as the recipe unchanged
 * plus a parallel count, mirroring how amperage itself scales with parallel count rather than voltage. Merging into
 * a synthetic N&times;-EUt recipe would incorrectly inflate the recipe's own per-unit voltage along with everything
 * else, corrupting the tier calculation {@link RecipeOverclockOperator} does next.
 * <p>
 * Operates on the plain item/fluid list snapshot {@link RecipeSearchOperator} already captured, not on live
 * {@code IItemHandler}s &mdash; consistent with this whole search pipeline being snapshot-based rather than tied
 * to live inventories until admission actually consumes anything (see {@code RecipeLookupTrackBuilder}'s JavaDoc).
 */
public final class RecipeParallelOperator implements GTStateMachineTransientOperator {

    public static final String CANDIDATE_RECIPE_KEY = "CandidateRecipe";
    public static final String ACHIEVED_PARALLEL_KEY = "AchievedParallel";

    /** On {@code data}: whether any parallel budget/ingredients/amperage remained to run at least one copy. */
    public static final String SUCCESS_KEY = "ParallelSuccess";

    public static final Predicate<NBTTagCompound> SUCCESS_PREDICATE = d -> d.getBoolean(SUCCESS_KEY);

    private final @NotNull RecipeLogicConfig config;

    public RecipeParallelOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        Recipe candidate = (Recipe) transientData.get(RecipeSelectionOperator.SELECTED_RECIPE_KEY);
        List<ItemStack> items = (List<ItemStack>) transientData.get(RecipeSearchOperator.ITEMS_SNAPSHOT_KEY);
        List<FluidStack> fluids = (List<FluidStack>) transientData.get(RecipeSearchOperator.FLUIDS_SNAPSHOT_KEY);
        if (candidate == null || items == null || fluids == null) {
            throw new IllegalStateException("RecipeParallelOperator ran without a selected candidate");
        }

        int budget = remainingParallelBudget(transientData);
        int achievable = budget <= 0 ? 0 : Math.min(budget, maxIngredientRatio(candidate, items, fluids));
        if (achievable > 0) achievable = (int) Math.min(achievable, maxAmperageRatio(candidate, transientData));

        if (achievable <= 0) {
            data.setBoolean(SUCCESS_KEY, false);
            return;
        }

        transientData.put(CANDIDATE_RECIPE_KEY, candidate);
        transientData.put(ACHIEVED_PARALLEL_KEY, achievable);
        data.setBoolean(SUCCESS_KEY, true);
    }

    private int remainingParallelBudget(@NotNull Map<String, Object> transientData) {
        if (config.parallel.parallelLimit == null) {
            // No parallel processing configured: cap at exactly one copy of *this* candidate, regardless of how
            // many ingredients happen to be available. This does not limit how many *different* candidates this
            // search pass may still queue afterward (see RecipeLookupTrackBuilder's JavaDoc) -- only whether a
            // single candidate gets multiplied.
            return 1;
        }
        int consumedElsewhere = config.parallel.consumedParallelSupplier == null ? 0 :
                config.parallel.consumedParallelSupplier.getAsInt();
        int consumedThisPass = (int) transientData.getOrDefault(RecipeSearchOperator.PARALLEL_CONSUMED_KEY, 0);
        return config.parallel.parallelLimit.getAsInt() - consumedElsewhere - consumedThisPass;
    }

    /** @return how many copies of {@code candidate} this logic's remaining power budget still allows. */
    private long maxAmperageRatio(@NotNull Recipe candidate, @NotNull Map<String, Object> transientData) {
        if (candidate.getAmperage() <= 0) return Long.MAX_VALUE;
        long consumedThisPassEUt = (long) transientData.getOrDefault(RecipeSearchOperator.EUT_CONSUMED_KEY, 0L);
        return config.power.getAvailableAmperage(candidate, consumedThisPassEUt) / candidate.getAmperage();
    }

    /**
     * Package-private (not {@code private}), so a custom {@link gregtech.api.recipes.logic.statemachine.ParallelLimitFactory}
     * operator in this same package (e.g. Large Turbine's {@code TurbineParallelOperator}) can reuse this
     * ingredient-availability check instead of duplicating it.
     */
    static int maxIngredientRatio(@NotNull Recipe recipe, @NotNull List<ItemStack> items,
                                          @NotNull List<FluidStack> fluids) {
        int ratio = Integer.MAX_VALUE;
        for (GTRecipeInput input : recipe.getInputs()) {
            if (input.isNonConsumable() || input.getAmount() <= 0) continue;
            int available = 0;
            for (ItemStack stack : items) {
                if (!stack.isEmpty() && input.acceptsStack(stack)) available += stack.getCount();
            }
            ratio = Math.min(ratio, available / input.getAmount());
            if (ratio <= 0) return 0;
        }
        for (GTRecipeInput input : recipe.getFluidInputs()) {
            if (input.isNonConsumable() || input.getAmount() <= 0) continue;
            int available = 0;
            for (FluidStack stack : fluids) {
                if (stack != null && input.acceptsFluid(stack)) available += stack.amount;
            }
            ratio = Math.min(ratio, available / input.getAmount());
            if (ratio <= 0) return 0;
        }
        return ratio == Integer.MAX_VALUE ? 1 : ratio;
    }
}
