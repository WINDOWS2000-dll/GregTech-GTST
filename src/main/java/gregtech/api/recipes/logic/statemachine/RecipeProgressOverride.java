package gregtech.api.recipes.logic.statemachine;

import gregtech.api.statemachine.GTStateMachineBuilder;

/**
 * Replaces {@code RecipeProgressTrackBuilder}'s standard per-tick progress/stall wiring for a single active recipe.
 * <p>
 * Contract: the override receives {@code builder} positioned at the per-tick check operator (the one that set the
 * {@code RecipeCheckSuccess}-equivalent flag {@code RecipeProgressTrackBuilder} computed for this active recipe),
 * and is responsible for registering whatever operators it needs and wiring them up from there. It must leave
 * {@code builder}'s pointer at whatever operator represents "this active recipe was just acted on for this tick" —
 * {@code RecipeProgressTrackBuilder} will attach the standard "loop to the next active recipe, or go to output if
 * this one just finished" links to *that* operator afterward, so the override does not need to (and should not)
 * wire those two outcomes itself.
 * <p>
 * {@code loopBackOp} is the ID of the operator that advances to the next active recipe index; if the override
 * wires any of its own branches (e.g. a custom stall path) to skip past the standard post-wiring entirely, it
 * should link back to this ID rather than dead-ending, so a stall on one active recipe doesn't halt processing of
 * the rest of this tick's active-recipe list.
 */
@FunctionalInterface
public interface RecipeProgressOverride {

    void wire(GTStateMachineBuilder builder, RecipeStallType stallType, int loopBackOp);
}
