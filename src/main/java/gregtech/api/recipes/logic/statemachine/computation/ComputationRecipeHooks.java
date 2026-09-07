package gregtech.api.recipes.logic.statemachine.computation;

import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeProgressOverride;
import gregtech.api.recipes.logic.statemachine.RecipeProgressTrackBuilder;
import gregtech.api.recipes.logic.statemachine.progress.RecipeStallOperator;
import gregtech.api.recipes.properties.impl.ComputationProperty;
import gregtech.api.recipes.properties.impl.TotalComputationProperty;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Builds the {@code config.hooks.entryEnricher}/{@code perTickRecipeCheck}/{@code progressOperationOverride} triple
 * a computation-driven machine (e.g. Research Station) needs, parameterized by {@link ComputationType}.
 * Implemented as StateMachine hooks rather than a dedicated
 * recipe-logic subclass. Notable pitfalls this design avoids: the search-side capacity property never actually
 * being exposed, CWU being drawn twice per tick, and energy no longer always being drawn on a computation
 * shortfall.
 * <p>
 * <b>Why {@link #enrichEntry} exists instead of hardcoded {@link ActiveRecipeList} fields:</b> {@code
 * perTickRecipeCheck}/the progress override only ever see an active entry's {@link NBTTagCompound}, never the
 * {@link Recipe} that produced it (queue entries are persisted NBT that may still be sitting queued many ticks
 * after the {@link Recipe} reference was last available) &mdash; see {@code RecipeEntryEnricher}'s own JavaDoc for
 * the full rationale. A machine wires {@link #enrichEntry} as its {@code config.hooks.entryEnricher} to make
 * {@link #ENTRY_CWU_PER_TICK_KEY}/{@link #ENTRY_TOTAL_CWU_KEY} available to the other two methods here.
 */
public final class ComputationRecipeHooks {

    /** Key, on an entry, of the recipe's required CWU/t (absent or {@code <= 0} means "not a computation recipe"). */
    public static final String ENTRY_CWU_PER_TICK_KEY = "CWUt";

    /**
     * Key, on an entry, of the recipe's total CWU requirement (its {@link ActiveRecipeList#ENTRY_DURATION_KEY} is
     * also this same value, per {@code StationRecipeBuilder}'s convention of using total CWU as "duration"; kept as
     * its own key regardless so {@link #advanceProgress} doesn't have to assume that convention holds for every
     * possible computation recipe). Absent or {@code <= 0} means "per-tick mode": progress advances by one tick
     * per successful draw of exactly {@link #ENTRY_CWU_PER_TICK_KEY}, like a normal EU/t-gated recipe.
     */
    public static final String ENTRY_TOTAL_CWU_KEY = "TotalCWU";

    /**
     * Key, on an entry, of whether the last {@link #perTickRecipeCheck} found enough CWU/t available (only
     * meaningful for a computation recipe, i.e. when {@link #ENTRY_CWU_PER_TICK_KEY} is present and {@code > 0}).
     * Public so a machine's own UI can display a "not enough computation" warning, mirroring
     * {@code ComputationRecipeLogic#isHasNotEnoughComputation()}.
     */
    public static final String ENTRY_HAS_ENOUGH_COMPUTATION_KEY = "HasEnoughComputation";

    private ComputationRecipeHooks() {}

    /** {@code config.hooks.entryEnricher}: stashes a recipe's CWU/t and total-CWU requirements onto its entry. */
    public static void enrichEntry(@NotNull Recipe recipe, @NotNull NBTTagCompound entry) {
        int cwut = recipe.getProperty(ComputationProperty.getInstance(), 0);
        if (cwut > 0) entry.setInteger(ENTRY_CWU_PER_TICK_KEY, cwut);
        int total = recipe.getProperty(TotalComputationProperty.getInstance(), 0);
        if (total > 0) entry.setInteger(ENTRY_TOTAL_CWU_KEY, total);
    }

    /**
     * @param provider     the machine's own computation network access point.
     * @param type         see {@link ComputationType}.
     * @param drainEnergy  the machine's own energy-drawing per-tick check (e.g. {@code drainRecipeEnergy}), run
     *                     <i>first</i> and unconditionally &mdash; specifically so energy is always drawn whenever
     *                     it's available, independently of whatever this recipe's CWU outcome turns out to be (see
     *                     {@link ComputationType#SPORADIC}'s JavaDoc for why that order matters).
     * @return a {@code config.hooks.perTickRecipeCheck} that draws energy, then checks (but does not yet draw) this
     *         tick's available CWU/t, recording the outcome for {@link #progressOverride} to act on. A computation
     *         shortfall under {@link ComputationType#STEADY} returns {@code false} here (funneling into the
     *         machine's own configured {@code RecipeStallType}, matching legacy's hardcoded revert-on-shortfall for
     *         that type); under {@link ComputationType#SPORADIC} it returns {@code true} regardless (this tick
     *         "succeeds" from the graph's perspective, but {@link #progressOverride} withholds the actual advance).
     */
    @NotNull
    public static Predicate<NBTTagCompound> perTickRecipeCheck(@NotNull Supplier<IOpticalComputationProvider> provider,
                                                               @NotNull ComputationType type,
                                                               @NotNull Predicate<NBTTagCompound> drainEnergy) {
        return recipeData -> {
            if (!drainEnergy.test(recipeData)) return false;
            int cwut = recipeData.getInteger(ENTRY_CWU_PER_TICK_KEY);
            if (cwut <= 0) return true; // not a computation recipe at all
            int available = provider.get().requestCWUt(Integer.MAX_VALUE, true);
            boolean enough = available >= cwut;
            recipeData.setBoolean(ENTRY_HAS_ENOUGH_COMPUTATION_KEY, enough);
            return enough || type == ComputationType.SPORADIC;
        };
    }

    /**
     * @param provider the same computation network access point passed to {@link #perTickRecipeCheck}.
     * @return a {@code config.hooks.progressOperationOverride} that replaces the standard "+1 progress on success"
     *         operator with one that: applies the machine's configured {@code RecipeStallType} on outright failure
     *         (energy shortfall, a paused worker, or a {@link ComputationType#STEADY} computation shortfall, all of
     *         which {@link #perTickRecipeCheck} reports as {@code false}); otherwise draws this tick's real CWU and
     *         advances progress by one tick (per-tick mode) or by however much CWU was actually drawn (total-CWU
     *         mode, capped at what the recipe still needs -- rather than always requesting the
     *         recipe's <i>full</i> total every tick regardless of existing progress, which would over-draw a shared
     *         network well past what it still needs to finish); or, for a {@link ComputationType#SPORADIC} shortfall
     *         specifically, withholds the advance entirely without drawing anything or reverting existing progress.
     */
    @NotNull
    public static RecipeProgressOverride progressOverride(@NotNull Supplier<IOpticalComputationProvider> provider) {
        return (builder, stallType, loopBackOp) -> {
            int checkOp = builder.getPointer();

            builder.andThenIf(d -> !d.getBoolean(RecipeProgressTrackBuilder.RECIPE_CHECK_KEY),
                    "energy insufficient, worker paused, or STEADY computation shortfall",
                    RecipeStallOperator.of(stallType), false, "computationStall");
            builder.andThenToDefault(loopBackOp);

            builder.setPointer(checkOp).andThenIf(d -> d.getBoolean(RecipeProgressTrackBuilder.RECIPE_CHECK_KEY),
                    "per-tick checks passed", d -> advanceProgress(d, provider.get()), false, "progressComputation");
        };
    }

    private static void advanceProgress(@NotNull NBTTagCompound data, @NotNull IOpticalComputationProvider provider) {
        NBTTagCompound entry = ActiveRecipeList.selected(data);
        int cwut = entry.getInteger(ENTRY_CWU_PER_TICK_KEY);
        if (cwut <= 0) {
            // Not a computation recipe at all -- plain +1, exactly like the standard RecipeProgressOperator (this
            // override replaces that operator wholesale for every active recipe on this machine, computation or
            // not, so it must still handle the plain case correctly).
            entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY,
                    entry.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY) + 1);
            return;
        }
        if (!entry.getBoolean(ENTRY_HAS_ENOUGH_COMPUTATION_KEY)) {
            // Only reachable for SPORADIC (STEADY's shortfall already took the stall branch above): withhold
            // progress without reverting it. Energy was already drawn by perTickRecipeCheck regardless.
            return;
        }
        int total = entry.getInteger(ENTRY_TOTAL_CWU_KEY);
        if (total <= 0) {
            // Per-tick mode: draw exactly cwut, advance by one tick.
            provider.requestCWUt(cwut, false);
            entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY,
                    entry.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY) + 1);
        } else {
            // Total-CWU mode: draw as much as is both available and still needed, advance progress by that amount.
            int progress = entry.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY);
            int remaining = Math.max(0, total - progress);
            int drawn = provider.requestCWUt(remaining, false);
            entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, progress + drawn);
        }
    }
}
