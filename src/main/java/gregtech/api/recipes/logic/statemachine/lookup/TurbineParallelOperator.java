package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.Recipe;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

/**
 * {@link gregtech.api.recipes.logic.statemachine.ParallelLimitFactory} for Large Turbine's rotor-driven
 * voltage-targeting mechanic, replacing the standard {@link RecipeParallelOperator} entirely (via
 * {@code config.parallel.parallelLimitFactory}).
 * <p>
 * <b>Why this can't just be {@link RecipeParallelOperator} with different {@code config.power} numbers:</b> that
 * operator (and {@code RecipePowerConfig#getAvailableAmperage}) always <i>floors</i> the achievable count to stay
 * within a budget (never running more than the supply can afford). Large Turbine's legacy mechanic instead
 * <i>ceils</i>: it always burns at least enough fuel to reach (and typically slightly exceed) the rotor's current
 * target voltage, banking the unavoidable rounding overshoot ({@code bankedEUSupplier}) so the next admission needs
 * correspondingly less new fuel &mdash; the opposite rounding direction, not expressible via the existing
 * floor-based budget math no matter what numbers are fed into it.
 * <p>
 * <b>Deliberate simplification:</b> unlike legacy, this never admits a "zero-parallel,
 * pure bank withdrawal" run (legacy's {@code excessVoltage >= turbineMaxVoltage} branch) through this operator --
 * that would require every downstream shared operator in this graph (view/output-space/overclock/run-build, used by
 * every machine in the mod) to tolerate an achieved parallel of zero, an invasive and risky change for a single
 * machine's benefit. Instead, {@code MetaTileEntityLargeTurbine}'s {@code shouldStartRecipeLookup} only allows a
 * search when the bank is actually below target, and its own idle-bank-draw hook covers ticks where the bank alone
 * already suffices -- see that class's own JavaDoc for the full delivery-window design.
 * <p>
 * <b>A subtlety this design accounts for: fuel consumption must not scale with {@code duration}.</b> An earlier
 * version of this design got this wrong, by a factor of exactly {@code duration}. The root cause was in
 * {@code MetaTileEntityLargeTurbine}, not here
 * (see that class's JavaDoc for the fix -- admission now happens once per the admitted fuel's own recipe duration,
 * not once whenever the bank happens to dip below target), but fixing it exposed two latent risks in this operator
 * that a single-admission-per-duration-window model can no longer tolerate:
 * <ul>
 * <li><b>At most one candidate may be admitted per search pass.</b> Previously, a second distinct fuel type found in
 * the same pass (Gas/Plasma Turbine support many simultaneously-valid fuels) could also queue an admission once
 * {@link RecipeSearchOperator#EUT_CONSUMED_KEY} showed the first one didn't fully cover the deficit. Each admission
 * now creates an entry that independently delivers a full target-voltage window on its own -- two entries admitted
 * in the same pass would each deliver that full window <i>simultaneously</i>, doubling the electrical output for the
 * overlap. Reusing {@code EUT_CONSUMED_KEY} once more (as "has anything already been queued this pass") makes this
 * operator refuse every candidate after the first, mirroring legacy's fundamentally single-recipe-at-a-time model
 * (it never had a multi-fuel-per-tick concept to begin with).</li>
 * <li><b>All-or-nothing fuel sizing, matching legacy exactly.</b> Previously, {@link RecipeParallelOperator
 * #maxIngredientRatio} <i>clamped</i> the achieved parallel down to whatever fuel was actually available, admitting
 * a smaller-than-needed batch. Legacy's {@code prepareRecipe} never does this: if the tank can't cover the full
 * computed parallel, it returns {@code false} outright, touching {@code excessVoltage} not at all. Clamping instead
 * let a fuel-starved partial admission credit far less than one target's worth to the bank, driving it deeply
 * negative and forcing an eventual disproportionate "payback" burst once fuel became available again -- a debt
 * mechanic legacy simply doesn't have. This operator now fails outright (as legacy does) when the tank can't cover
 * the full computed parallel.</li>
 * </ul>
 * <p>
 * Reuses {@link RecipeParallelOperator#maxIngredientRatio}/{@code SUCCESS_KEY}/{@code CANDIDATE_RECIPE_KEY}/
 * {@code ACHIEVED_PARALLEL_KEY} so every downstream operator (which only knows about that class's public contract,
 * not this one) keeps working unmodified.
 */
public final class TurbineParallelOperator implements GTStateMachineTransientOperator {

    private final LongSupplier targetVoltageSupplier;
    private final DoubleSupplier efficiencySupplier;
    private final LongSupplier bankedEUSupplier;

    public TurbineParallelOperator(@NotNull LongSupplier targetVoltageSupplier,
                                   @NotNull DoubleSupplier efficiencySupplier,
                                   @NotNull LongSupplier bankedEUSupplier) {
        this.targetVoltageSupplier = targetVoltageSupplier;
        this.efficiencySupplier = efficiencySupplier;
        this.bankedEUSupplier = bankedEUSupplier;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        Recipe candidate = (Recipe) transientData.get(RecipeSelectionOperator.SELECTED_RECIPE_KEY);
        List<ItemStack> items = (List<ItemStack>) transientData.get(RecipeSearchOperator.ITEMS_SNAPSHOT_KEY);
        List<FluidStack> fluids = (List<FluidStack>) transientData.get(RecipeSearchOperator.FLUIDS_SNAPSHOT_KEY);
        if (candidate == null || items == null || fluids == null) {
            throw new IllegalStateException("TurbineParallelOperator ran without a selected candidate");
        }

        // At most one admission per search pass -- see this class's own JavaDoc for why a second, independently
        // duration-delivering entry admitted in the same pass would double-deliver during its overlap with the
        // first.
        long claimedThisPassRaw = (long) transientData.getOrDefault(RecipeSearchOperator.EUT_CONSUMED_KEY, 0L);
        if (claimedThisPassRaw > 0) {
            data.setBoolean(RecipeParallelOperator.SUCCESS_KEY, false);
            return;
        }

        double efficiency = efficiencySupplier.getAsDouble();
        long recipeEUt = candidate.getEUt();
        if (efficiency <= 0 || recipeEUt <= 0) {
            data.setBoolean(RecipeParallelOperator.SUCCESS_KEY, false);
            return;
        }

        long target = targetVoltageSupplier.getAsLong();
        long banked = bankedEUSupplier.getAsLong();
        double remainingDeficit = target - banked;

        if (remainingDeficit <= 0) {
            // No more fuel needed this pass -- see this class's own JavaDoc for why this is a normal end to the
            // candidate loop, not a failure.
            data.setBoolean(RecipeParallelOperator.SUCCESS_KEY, false);
            return;
        }

        int parallel = Math.max(1, (int) Math.ceil(remainingDeficit / (recipeEUt * efficiency)));
        int ingredientLimit = RecipeParallelOperator.maxIngredientRatio(candidate, items, fluids);
        if (ingredientLimit < parallel) {
            // All-or-nothing, matching legacy exactly -- see this class's own JavaDoc for why clamping down to a
            // partial batch instead would create a bank debt legacy never has.
            data.setBoolean(RecipeParallelOperator.SUCCESS_KEY, false);
            return;
        }

        transientData.put(RecipeParallelOperator.CANDIDATE_RECIPE_KEY, candidate);
        transientData.put(RecipeParallelOperator.ACHIEVED_PARALLEL_KEY, parallel);
        data.setBoolean(RecipeParallelOperator.SUCCESS_KEY, true);
    }
}
