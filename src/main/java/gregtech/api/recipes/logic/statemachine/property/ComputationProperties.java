package gregtech.api.recipes.logic.statemachine.property;

import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.recipes.logic.statemachine.property.impl.ComputationCapacityProperty;

import org.jetbrains.annotations.NotNull;

/**
 * Builds a {@link ComputationCapacityProperty} from a {@link IOpticalComputationProvider}'s current state. The
 * property this produces must actually be added to the returned {@link RecipePropertySet} by the caller, not
 * silently discarded.
 * <p>
 * Queried once per tick (like {@link EnergyContainerProperties#of}), via {@code true} (simulate): a real draw only
 * ever happens later, inside the progress track's own per-tick check, once a candidate has actually been admitted
 * &mdash; see {@code gregtech.api.recipes.logic.statemachine.computation.ComputationRecipeHooks}'s JavaDoc for why
 * this two-phase simulate-then-consume split is safe even with multiple consumers sharing one computation network
 * (unlike an {@link gregtech.api.capability.IEnergyContainer}'s static amperage budget, {@code requestCWUt} is
 * itself the single authoritative, immediately-mutating source of truth, so there is no separate budget-accounting
 * step to get wrong).
 */
public final class ComputationProperties {

    private ComputationProperties() {}

    /** @return {@code provider}'s currently available CWU/t, for {@link ComputationCapacityProperty}. */
    @NotNull
    public static ComputationCapacityProperty of(@NotNull IOpticalComputationProvider provider) {
        return new ComputationCapacityProperty(provider.requestCWUt(Integer.MAX_VALUE, true));
    }
}
