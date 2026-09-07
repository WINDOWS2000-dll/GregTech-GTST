package gregtech.api.recipes.logic;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.List;

/**
 * GregTech's sole concrete {@link RecipeRun} implementation. See that interface's JavaDoc for why there is only one.
 * <p>
 * Item/fluid outputs and consumption are computed eagerly at construction time (rolling chance exactly once per
 * run, as intended), not lazily.
 */
public final class StandardRecipeRun implements RecipeRun {

    private final @NotNull RecipeView view;
    private final @NotNull List<ItemStack> itemsOut;
    private final @NotNull List<FluidStack> fluidsOut;
    private final @NotNull List<ItemStack> itemsConsumed;
    private final @NotNull List<FluidStack> fluidsConsumed;

    private final int overclocks;
    private final double duration;
    private final long requiredVoltage;
    private final long requiredAmperage;
    private final boolean generating;

    /**
     * @param view            the matched recipe view this run was overclocked from.
     * @param properties      the power properties to roll chance-based outputs against.
     * @param recipeTier      the recipe's own tier, for chance/yield boosting on outputs.
     * @param machineTier     the tier the recipe is actually running at, for chance/yield boosting on outputs.
     * @param overclocks      the number of overclocks applied, for {@link #getOverclocks()}.
     * @param duration        the post-overclock duration, for {@link #getDuration()}.
     * @param requiredVoltage the post-overclock required voltage (per-unit; unaffected by parallel).
     * @param requiredAmperage the required amperage (already scaled by parallel; unaffected by overclocking). See
     *                        {@link RecipeView#getActualAmperage()}.
     * @param itemTrimLimit   the most distinct item outputs to keep; see {@link RecipeView#rollItems}.
     * @param fluidTrimLimit  the most distinct fluid outputs to keep; see {@link RecipeView#rollFluids}.
     */
    public StandardRecipeRun(@NotNull RecipeView view, @NotNull RecipePropertySet properties, int recipeTier,
                             int machineTier, @Range(from = 0, to = Integer.MAX_VALUE) int overclocks, double duration,
                             @Range(from = 0, to = Long.MAX_VALUE) long requiredVoltage,
                             @Range(from = 0, to = Long.MAX_VALUE) long requiredAmperage, int itemTrimLimit,
                             int fluidTrimLimit) {
        this.view = view;
        this.overclocks = overclocks;
        this.duration = duration;
        this.requiredVoltage = requiredVoltage;
        this.requiredAmperage = requiredAmperage;
        this.generating = view.getRecipe().isGenerating();
        this.itemsConsumed = view.getConsumedItems();
        this.fluidsConsumed = view.getConsumedFluids();
        this.itemsOut = view.rollItems(properties, recipeTier, machineTier, itemTrimLimit);
        this.fluidsOut = view.rollFluids(properties, recipeTier, machineTier, fluidTrimLimit);
    }

    @Override
    public @NotNull RecipeView getRecipeView() {
        return view;
    }

    @Override
    public @NotNull List<ItemStack> getItemsOut() {
        return itemsOut;
    }

    @Override
    public @NotNull List<FluidStack> getFluidsOut() {
        return fluidsOut;
    }

    @Override
    public @NotNull List<ItemStack> getItemsConsumed() {
        return itemsConsumed;
    }

    @Override
    public @NotNull List<FluidStack> getFluidsConsumed() {
        return fluidsConsumed;
    }

    @Override
    public int getParallel() {
        return view.getParallel();
    }

    @Override
    public int getOverclocks() {
        return overclocks;
    }

    @Override
    public double getDuration() {
        return duration;
    }

    @Override
    public long getRequiredVoltage() {
        return requiredVoltage;
    }

    @Override
    public long getRequiredAmperage() {
        return requiredAmperage;
    }

    @Override
    public boolean isGenerating() {
        return generating;
    }
}
