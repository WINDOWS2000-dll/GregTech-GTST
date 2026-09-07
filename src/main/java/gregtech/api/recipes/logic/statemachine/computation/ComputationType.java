package gregtech.api.recipes.logic.statemachine.computation;

import gregtech.api.recipes.properties.impl.ComputationProperty;

/**
 * How a computation-driven recipe behaves when its {@link ComputationProperty} requirement isn't currently met by
 * its {@link gregtech.api.capability.IOpticalComputationProvider}.
 */
public enum ComputationType {

    /**
     * Insufficient CWU/t is treated exactly like insufficient EU/t: the machine's configured
     * {@link gregtech.api.recipes.logic.statemachine.RecipeStallType} applies (see
     * {@link gregtech.api.recipes.logic.statemachine.RecipeStallType#DEGRESS}).
     */
    STEADY,

    /**
     * Insufficient CWU/t simply withholds this tick's progress without stalling/reverting it, independently of
     * the machine's configured stall type &mdash; EU/t is still drawn regardless. Energy must always be drawn on
     * a computation shortfall; a per-tick check that returns before reaching the energy-draw code would violate
     * that invariant.
     */
    SPORADIC
}
