package gregtech.api.recipes.logic;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.List;

/**
 * A fully-overclocked, ready-to-execute recipe instance: everything a
 * {@code RecipeLookupTrackBuilder} needs to buffer and, once admitted, hand off to
 * {@code RecipeProgressTrackBuilder}'s {@code ActiveRecipeList} format.
 * <p>
 * GregTech has exactly one concrete implementation ({@link StandardRecipeRun}), regardless of whether the recipe costs
 * power: GregTech's overclock calculation already handles zero-EU/t recipes as a degenerate case rather than a
 * separate code path, so a second class would only duplicate the same handful of getters.
 * <p>
 * {@link #getRequiredVoltage()} is computed from GregTech's existing overclock calculation ({@code OverclockingLogic});
 * {@link #getRequiredAmperage()} is {@link RecipeView#getActualAmperage()} passed through unchanged, since
 * overclocking only ever affects voltage/duration, never amperage (see {@code RecipeOverclockOperator}'s JavaDoc).
 */
public interface RecipeRun {

    /**
     * Use sparingly. If this run has a dedicated method for what you need, use that whenever possible.
     */
    @NotNull
    RecipeView getRecipeView();

    @NotNull
    List<ItemStack> getItemsOut();

    @NotNull
    List<FluidStack> getFluidsOut();

    @NotNull
    List<ItemStack> getItemsConsumed();

    @NotNull
    List<FluidStack> getFluidsConsumed();

    @Range(from = 1, to = Integer.MAX_VALUE)
    int getParallel();

    @Range(from = 0, to = Integer.MAX_VALUE)
    int getOverclocks();

    double getDuration();

    @Range(from = 0, to = Long.MAX_VALUE)
    long getRequiredVoltage();

    @Range(from = 0, to = Long.MAX_VALUE)
    long getRequiredAmperage();

    @Range(from = 0, to = Long.MAX_VALUE)
    default long getRequiredEUt() {
        return getRequiredVoltage() * getRequiredAmperage();
    }

    boolean isGenerating();
}
