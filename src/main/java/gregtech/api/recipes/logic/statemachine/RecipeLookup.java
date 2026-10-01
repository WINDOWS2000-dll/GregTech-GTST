package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * Finds candidate recipes for a {@link RecipeLogicConfig}.
 * <p>
 * Returns an {@link Iterator} (not a single {@code Recipe}) because a single search pass may admit several
 * different matching recipes in one tick, each becoming its own independent entry in
 * {@code ActiveRecipeList} &mdash; see {@code RecipeLookupTrackBuilder}'s JavaDoc for why GregTech's recipe logic
 * supports multiple concurrently-progressing recipes.
 * <p>
 * Implementations are swappable independently of everything else built on top of this interface: the standard
 * {@code RecipeMap}-backed linear scan ({@code RecipeMapLookup}) and the performance-optimized bitflag-based
 * implementation ({@code lookup.bitflag.BitflagRecipeLookup}) both satisfy this same contract.
 */
public interface RecipeLookup {

    /**
     * @param maxVoltage the maximum voltage the searching machine can supply/accept; candidates requiring more
     *                   than this do not match (see {@code Recipe#getEUt()}).
     * @param items      the item inputs to match against. Implementations must not mutate this list or its
     *                   contents.
     * @param fluids     the fluid inputs to match against. Implementations must not mutate this list or its
     *                   contents.
     * @return an iterator over every recipe that currently matches. Lazy evaluation is encouraged (callers often
     *         stop partway through once search/parallel/power budget is exhausted), but not required.
     */
    @NotNull
    Iterator<Recipe> findRecipes(long maxVoltage, @NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids);

    /**
     * As {@link #findRecipes(long, List, List)}, but additionally given the full {@link RecipePropertySet} the
     * search is running under, for implementations that filter on more than just voltage (e.g.
     * {@code lookup.bitflag.BitflagRecipeLookup}'s pluggable property-range filters). {@code null} means this
     * logic doesn't track power/properties at all (see {@code RecipePowerConfig#properties}'s JavaDoc) &mdash;
     * distinct from a non-null but empty {@link RecipePropertySet}, which means "tracked, but currently reporting
     * nothing".
     * <p>
     * Default implementation ignores {@code properties} and delegates to {@link #findRecipes(long, List, List)},
     * so every existing implementation of this interface (which only ever needed voltage) keeps working unchanged;
     * only implementations that actually want the extra filtering power need to override this.
     */
    @NotNull
    default Iterator<Recipe> findRecipes(long maxVoltage, @Nullable RecipePropertySet properties,
                                         @NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids) {
        return findRecipes(maxVoltage, items, fluids);
    }

    /**
     * Debug-only: explains why the most recent {@link #findRecipes} call found nothing, broken down by which filter
     * stage excluded which candidates (voltage, then each registered property filter, then ingredient matching).
     * Never called by the standard search path &mdash; only wired to the execution-trace dev tool (see
     * {@code RecipeWorkable#setTraceEnabled}), and only once a search has already come back empty, so it costs
     * nothing for the overwhelmingly common case of an untraced machine.
     * <p>
     * The default implementation (for a {@link RecipeLookup} that hasn't overridden this) reports that diagnostics
     * aren't available, rather than an empty string, so a trace reader isn't left wondering whether the search was
     * even attempted.
     *
     * @return a short, human-readable summary.
     */
    @NotNull
    default String diagnoseNoMatch(long maxVoltage, @Nullable RecipePropertySet properties,
                                   @NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids) {
        return "(exclusion diagnostics not supported by " + getClass().getSimpleName() + ")";
    }
}
