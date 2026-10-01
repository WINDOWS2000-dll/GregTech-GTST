package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.ingredients.GTRecipeInput;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class IngredientBitflagIndex {

    private final @NotNull List<Recipe> recipes;
    private final @NotNull BitSet fallback;
    private final long @NotNull [] requiredMask;
    private final @NotNull Map<Item, List<SlotApplicator>> itemBuckets;
    private final @NotNull Map<Fluid, List<SlotApplicator>> fluidBuckets;

    private IngredientBitflagIndex(@NotNull List<Recipe> recipes, @NotNull BitSet fallback,
                                   long @NotNull [] requiredMask,
                                   @NotNull Map<Item, List<SlotApplicator>> itemBuckets,
                                   @NotNull Map<Fluid, List<SlotApplicator>> fluidBuckets) {
        this.recipes = recipes;
        this.fallback = fallback;
        this.requiredMask = requiredMask;
        this.itemBuckets = itemBuckets;
        this.fluidBuckets = fluidBuckets;
    }

    public int recipeCount() {
        return recipes.size();
    }

    @NotNull
    public Recipe recipeAt(int index) {
        return recipes.get(index);
    }

    @NotNull
    public static IngredientBitflagIndex build(@NotNull Collection<Recipe> source) {
        List<Recipe> recipes = new ObjectArrayList<>(source);
        BitSet fallback = new BitSet(recipes.size());
        long[] requiredMask = new long[recipes.size()];
        Map<Item, List<SlotApplicator>> itemBuckets = new Object2ObjectOpenHashMap<>();
        Map<Fluid, List<SlotApplicator>> fluidBuckets = new Object2ObjectOpenHashMap<>();

        for (int i = 0; i < recipes.size(); i++) {
            indexRecipe(i, recipes.get(i), fallback, requiredMask, itemBuckets, fluidBuckets);
        }

        return new IngredientBitflagIndex(recipes, fallback, requiredMask, itemBuckets, fluidBuckets);
    }

    private static void indexRecipe(int recipeIndex, @NotNull Recipe recipe, @NotNull BitSet fallback,
                                    long @NotNull [] requiredMask,
                                    @NotNull Map<Item, List<SlotApplicator>> itemBuckets,
                                    @NotNull Map<Fluid, List<SlotApplicator>> fluidBuckets) {
        List<GTRecipeInput> itemInputs = recipe.getInputs();
        List<GTRecipeInput> fluidInputs = recipe.getFluidInputs();
        int totalSlots = itemInputs.size() + fluidInputs.size();

        if (totalSlots > 64) {
            fallback.set(recipeIndex);
            return;
        }

        // staged into local lists first so a mid-recipe indexing failure (see the null-stacks guard below) can
        // fall back this *whole* recipe without leaving partial bucket entries from earlier slots behind.
        List<Runnable> pendingRegistrations = new ObjectArrayList<>();
        long mask = 0;

        for (int slot = 0; slot < itemInputs.size(); slot++) {
            GTRecipeInput input = itemInputs.get(slot);
            ItemStack[] representativeStacks = input.getInputStacks();
            if (representativeStacks == null || representativeStacks.length == 0) {
                fallback.set(recipeIndex);
                return;
            }
            long bit = 1L << slot;
            mask |= bit;
            SlotApplicator applicator = new SlotApplicator(recipeIndex, bit, input);
            for (ItemStack stack : representativeStacks) {
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                pendingRegistrations.add(
                        () -> itemBuckets.computeIfAbsent(item, k -> new ObjectArrayList<>()).add(applicator));
            }
        }

        for (int slot = 0; slot < fluidInputs.size(); slot++) {
            GTRecipeInput input = fluidInputs.get(slot);
            FluidStack representativeFluid = input.getInputFluidStack();
            if (representativeFluid == null) {
                fallback.set(recipeIndex);
                return;
            }
            long bit = 1L << (itemInputs.size() + slot);
            mask |= bit;
            SlotApplicator applicator = new SlotApplicator(recipeIndex, bit, input);
            Fluid fluid = representativeFluid.getFluid();
            pendingRegistrations.add(
                    () -> fluidBuckets.computeIfAbsent(fluid, k -> new ObjectArrayList<>()).add(applicator));
        }

        requiredMask[recipeIndex] = mask;
        for (Runnable registration : pendingRegistrations) registration.run();
    }

    /**
     * @param excluded recipe indices to skip outright (e.g. from a property-based prefilter such as
     *                 {@code BitflagRecipeLookup}'s voltage check); not mutated.
     * @return every recipe whose ingredient types are all satisfiable given {@code items}/{@code fluids}, in index
     *         order. See this class's JavaDoc for why quantity sufficiency is deliberately not checked here.
     */
    @NotNull
    public List<Recipe> match(@NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids,
                              @NotNull BitSet excluded) {
        long[] accumulated = new long[recipes.size()];

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            List<SlotApplicator> applicators = itemBuckets.get(stack.getItem());
            if (applicators == null) continue;
            for (SlotApplicator applicator : applicators) {
                if (excluded.get(applicator.recipeIndex)) continue;
                if (applicator.input.acceptsStack(stack)) accumulated[applicator.recipeIndex] |= applicator.bit;
            }
        }

        for (FluidStack stack : fluids) {
            if (stack == null || stack.amount <= 0) continue;
            List<SlotApplicator> applicators = fluidBuckets.get(stack.getFluid());
            if (applicators == null) continue;
            for (SlotApplicator applicator : applicators) {
                if (excluded.get(applicator.recipeIndex)) continue;
                if (applicator.input.acceptsFluid(stack)) accumulated[applicator.recipeIndex] |= applicator.bit;
            }
        }

        List<Recipe> matches = new ObjectArrayList<>();
        for (int i = 0; i < recipes.size(); i++) {
            if (excluded.get(i)) continue;
            if (fallback.get(i)) {
                if (recipes.get(i).matches(false, items, fluids)) matches.add(recipes.get(i));
            } else if (accumulated[i] == requiredMask[i]) {
                matches.add(recipes.get(i));
            }
        }
        return matches;
    }

    @Desugar
    private record SlotApplicator(int recipeIndex, long bit, @NotNull GTRecipeInput input) {}
}
