package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerCapacityProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Power-budget bookkeeping for a {@link RecipeLogicConfig}: where the machine's available power comes from, and how
 * much of it has already been claimed by other in-flight recipe runs.
 * <p>
 * Voltage and amperage are tracked separately via {@link #properties}: a machine's supply is a full
 * {@link RecipePropertySet}, so both its voltage <i>and</i> its amperage are available wherever needed, instead of
 * only a flat voltage cap. {@link #getMaxVoltage()}/{@link #getAvailableAmperage} are derived convenience accessors
 * built on top of it,
 * so operators that only care about one or the other (e.g. {@code RecipeSearchOperator} only ever needs voltage)
 * don't need to unpack a {@link RecipePropertySet} themselves.
 */
public final class RecipePowerConfig {

    /**
     * Supplies this logic's current power properties, or {@code null} if this logic does not consume/produce power
     * at all (as opposed to a non-null supplier that happens to currently report zero &mdash; see
     * {@link #getMaxVoltage()}/{@link #getAvailableAmperage} for why that distinction matters).
     */
    public @Nullable Supplier<RecipePropertySet> properties;

    /**
     * If non-null, reports how much power (in EU/t, i.e. voltage &times; amperage) has already been claimed by
     * other recipe runs sharing this logic's power budget (e.g. concurrently-running parallels), so a newly-
     * considered run's budget check accounts for it. EU/t, rather than raw amperage, is the unit here specifically
     * because it stays comparable even when the claiming runs are at a different voltage tier than the candidate
     * currently being checked &mdash; see {@link #getAvailableAmperage} for where that matters.
     */
    public @Nullable LongSupplier consumedPowerSupplier;

    /**
     * Whether a candidate whose voltage differs from this logic's supply/capacity voltage may still draw amperage
     * from it by "down-transforming" the supply's total power: e.g. a single high-voltage/low-amperage supply
     * powering several lower-voltage/higher-amperage recipes. When
     * {@code false} (the default, matching GregTech's current behavior of never mixing voltage tiers), a candidate whose
     * voltage doesn't match this logic's supply/capacity voltage simply cannot draw any amperage from it at all.
     */
    public boolean downTransformForParallels = false;

    /**
     * @return the highest voltage a recipe could possibly be searched for or run at right now: the higher of this
     *         logic's current supply and capacity voltage, or {@link Long#MAX_VALUE} if {@link #properties} is
     *         {@code null} (this logic doesn't track power at all, so nothing should be filtered by voltage).
     */
    public long getMaxVoltage() {
        if (properties == null) return Long.MAX_VALUE;
        RecipePropertySet set = properties.get();
        return Math.max(set.getOrDefault(PowerSupplyProperty.EMPTY).voltage(),
                set.getOrDefault(PowerCapacityProperty.EMPTY).voltage());
    }

    /**
     * @return the highest voltage a recipe could <i>possibly</i> be searched for at all, unlike {@link #getMaxVoltage()}
     *         accounting for amperage (the higher of this logic's supply/capacity total EU/t, i.e. voltage &times;
     *         amperage), or {@link Long#MAX_VALUE} if {@link #properties} is {@code null}. Used only by
     *         {@code RecipeSearchOperator} as a coarse search-stage prefilter, deliberately more permissive than
     *         {@link #getMaxVoltage()} so a candidate {@link RecipeOverclockConfig#upTransformForOverclocks} could
     *         otherwise rescue (by trading surplus amperage for extra overclock headroom beyond a single amp's own
     *         voltage) is never excluded before {@code RecipeOverclockOperator} gets a chance to evaluate it
     *         properly. A logic with {@code upTransformForOverclocks} left disabled sees no change in final
     *         outcome: such a candidate is simply rejected one stage later ({@code RecipeOverclockOperator} still
     *         gates on {@link #getMaxVoltage()} in that case) instead of here.
     */
    public long getMaxSearchVoltage() {
        if (properties == null) return Long.MAX_VALUE;
        RecipePropertySet set = properties.get();
        return Math.max(set.getOrDefault(PowerSupplyProperty.EMPTY).getEUt(),
                set.getOrDefault(PowerCapacityProperty.EMPTY).getEUt());
    }

    /**
     * @param candidate           the recipe being considered; its {@link Recipe#isGenerating()} selects whether this
     *                            logic's {@link PowerSupplyProperty} (consuming) or {@link PowerCapacityProperty}
     *                            (generating) is the relevant side.
     * @param consumedThisPassEUt power (in EU/t) already committed to other candidates earlier in the same search
     *                            pass, on top of {@link #consumedPowerSupplier}.
     * @return how many amps of {@code candidate} could still be run given this logic's remaining power budget, or
     *         {@link Long#MAX_VALUE} if {@code candidate} needs no power or this logic doesn't track power at all.
     *         When {@link #downTransformForParallels} is {@code false}, a voltage mismatch is simply ignored rather
     *         than zeroing the budget out &mdash; the supply's own raw amperage is the limit regardless of
     *         {@code candidate}'s (already tier-filtered) voltage. Only when {@code true} does the mismatch
     *         actually convert into extra amperage via down-transforming the supply's total EU/t at
     *         {@code candidate}'s lower voltage.
     */
    public long getAvailableAmperage(@NotNull Recipe candidate, long consumedThisPassEUt) {
        if (properties == null || candidate.getVoltage() <= 0) return Long.MAX_VALUE;

        RecipePropertySet set = properties.get();
        long supplyVoltage;
        long supplyAmperage;
        if (candidate.isGenerating()) {
            PowerCapacityProperty capacity = set.getOrDefault(PowerCapacityProperty.EMPTY);
            supplyVoltage = capacity.voltage();
            supplyAmperage = capacity.amperage();
        } else {
            PowerSupplyProperty supply = set.getOrDefault(PowerSupplyProperty.EMPTY);
            supplyVoltage = supply.voltage();
            supplyAmperage = supply.amperage();
        }

        long consumedElsewhere = consumedPowerSupplier == null ? 0 : consumedPowerSupplier.getAsLong();
        long totalConsumedEUt = consumedElsewhere + consumedThisPassEUt;

        if (downTransformForParallels) {
            long availableEUt = supplyVoltage * supplyAmperage - totalConsumedEUt;
            return Math.max(0, Math.floorDiv(availableEUt, candidate.getVoltage()));
        }
        long consumedAmps = supplyVoltage <= 0 ? 0 : Math.floorDiv(totalConsumedEUt, supplyVoltage);
        return Math.max(0, supplyAmperage - consumedAmps);
    }
}
