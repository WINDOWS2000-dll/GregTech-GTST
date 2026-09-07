package gregtech.api.recipes.logic;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.util.GTUtility;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The standard {@link RecipeView}: a recipe matched against a fixed snapshot of item/fluid inputs (captured at
 * construction time, e.g. by a search operator), with consumption computed by bridging to {@link Recipe#matches}
 * against a private copy of that snapshot, run in simulate-then-diff form instead of directly against the real
 * inventory.
 * <p>
 * <b>{@code parallel} genuinely scales this view's output, unlike GregTech's older merged-recipe model:</b>
 * {@code recipe} always represents exactly
 * <i>one</i> copy of itself &mdash; {@code RecipeParallelOperator} never merges {@code parallel} copies into a
 * single recipe with inflated ingredient/output amounts beforehand (doing so would incorrectly inflate the recipe's
 * own per-unit voltage along with it; see that operator's JavaDoc). Accordingly, both {@link #rollItems}/
 * {@link #rollFluids} and {@link #computeConsumption()} scale {@code recipe}'s one-copy amounts by {@code parallel}
 * themselves, exactly once each.
 */
public final class StandardRecipeView implements RecipeView {

    private final @NotNull Recipe recipe;
    private final int parallel;
    private final @NotNull List<ItemStack> matchedItemInputs;
    private final @NotNull List<@Nullable FluidStack> matchedFluidInputs;

    private @Nullable List<ItemStack> consumedItems;
    private @Nullable List<FluidStack> consumedFluids;

    /**
     * @param recipe             the recipe this view represents, always exactly one copy of itself (see this
     *                           class's JavaDoc).
     * @param matchedItemInputs  the item inputs this recipe was matched against. Not mutated by this view.
     * @param matchedFluidInputs the fluid inputs this recipe was matched against (elements may be {@code null} for
     *                           empty tanks, matching {@link Recipe#matches}'s convention). Not mutated by this
     *                           view.
     * @param parallel           the number of copies of {@code recipe} this view represents, scaling this view's
     *                           consumption/output amounts (see this class's JavaDoc).
     */
    public StandardRecipeView(@NotNull Recipe recipe, @NotNull List<ItemStack> matchedItemInputs,
                              @NotNull List<@Nullable FluidStack> matchedFluidInputs, int parallel) {
        this.recipe = recipe;
        this.matchedItemInputs = matchedItemInputs;
        this.matchedFluidInputs = matchedFluidInputs;
        this.parallel = parallel;
    }

    @Override
    public int getParallel() {
        return parallel;
    }

    @Override
    public @NotNull Recipe getRecipe() {
        return recipe;
    }

    @Override
    public @NotNull List<ItemStack> getConsumedItems() {
        if (consumedItems == null) computeConsumption();
        return consumedItems;
    }

    @Override
    public @NotNull List<FluidStack> getConsumedFluids() {
        if (consumedFluids == null) computeConsumption();
        return consumedFluids;
    }

    /**
     * Runs {@link Recipe#matches} in actual-consume mode against private copies of {@link #matchedItemInputs}/
     * {@link #matchedFluidInputs} (never the originals), then diffs before/after amounts to recover exactly what
     * was consumed per slot. This is the only way to get that breakdown out of {@link Recipe#matches}'s public
     * API, which otherwise only reports success/failure in simulate mode.
     * <p>
     * Matches exactly one copy of {@link #recipe} (not {@link #parallel} of them): {@code RecipeParallelOperator}
     * already verified upstream that {@link #parallel} copies' worth of ingredients are available, so the one-copy
     * diff computed here is simply scaled by {@link #parallel} afterward rather than matched {@link #parallel}
     * times over &mdash; consumption amounts are a pure linear function of copy count, so the two are equivalent.
     */
    private void computeConsumption() {
        List<ItemStack> itemsCopy = GTUtility.copyStackList(matchedItemInputs);
        int[] originalItemCounts = new int[itemsCopy.size()];
        for (int i = 0; i < itemsCopy.size(); i++) originalItemCounts[i] = itemsCopy.get(i).getCount();

        List<FluidStack> fluidsCopy = new ObjectArrayList<>(matchedFluidInputs.size());
        int[] originalFluidAmounts = new int[matchedFluidInputs.size()];
        for (int i = 0; i < matchedFluidInputs.size(); i++) {
            FluidStack stack = matchedFluidInputs.get(i);
            fluidsCopy.add(stack == null ? null : stack.copy());
            originalFluidAmounts[i] = stack == null ? 0 : stack.amount;
        }

        if (!recipe.matches(true, itemsCopy, fluidsCopy)) {
            throw new IllegalStateException(
                    "StandardRecipeView was constructed against inputs its own recipe does not actually match");
        }

        List<ItemStack> consumed = new ObjectArrayList<>();
        for (int i = 0; i < itemsCopy.size(); i++) {
            ItemStack after = itemsCopy.get(i);
            int remaining = after.isEmpty() ? 0 : after.getCount();
            int consumedAmount = originalItemCounts[i] - remaining;
            if (consumedAmount > 0) {
                ItemStack template = matchedItemInputs.get(i).copy();
                template.setCount((int) Math.min(Integer.MAX_VALUE, (long) consumedAmount * parallel));
                consumed.add(template);
            }
        }
        consumedItems = consumed;

        List<FluidStack> consumedFluidList = new ObjectArrayList<>();
        for (int i = 0; i < fluidsCopy.size(); i++) {
            FluidStack after = fluidsCopy.get(i);
            int remaining = after == null ? 0 : after.amount;
            int consumedAmount = originalFluidAmounts[i] - remaining;
            if (consumedAmount > 0) {
                FluidStack template = matchedFluidInputs.get(i).copy();
                template.amount = (int) Math.min(Integer.MAX_VALUE, (long) consumedAmount * parallel);
                consumedFluidList.add(template);
            }
        }
        consumedFluids = consumedFluidList;
    }

    @Override
    public @NotNull List<ItemStack> rollItems(@NotNull RecipePropertySet properties, int recipeTier, int machineTier,
                                              int itemTrimLimit) {
        return recipe.getItemOutputProvider().computeOutputs(matchedItemInputs, nonNullFluids(), properties,
                recipeTier, machineTier, parallel, itemTrimLimit);
    }

    @Override
    public @NotNull List<FluidStack> rollFluids(@NotNull RecipePropertySet properties, int recipeTier,
                                                int machineTier, int fluidTrimLimit) {
        return recipe.getFluidOutputProvider().computeOutputs(matchedItemInputs, nonNullFluids(), properties,
                recipeTier, machineTier, parallel, fluidTrimLimit);
    }

    private @NotNull List<FluidStack> nonNullFluids() {
        List<FluidStack> result = new ObjectArrayList<>(matchedFluidInputs.size());
        for (FluidStack stack : matchedFluidInputs) {
            if (stack != null) result.add(stack);
        }
        return result;
    }
}
