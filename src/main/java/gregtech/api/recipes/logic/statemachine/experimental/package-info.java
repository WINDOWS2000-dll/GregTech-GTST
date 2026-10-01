/**
 * An intentionally unstable extension point letting an addon mod hook into an <i>already-compiled, unmodified</i>
 * GTST machine's recipe logic ({@link gregtech.api.recipes.logic.statemachine.RecipeLogicConfig}) without GTST
 * itself needing to change &mdash; something the stable API cannot support, since
 * {@link gregtech.api.recipes.logic.statemachine.RecipeLogicConfig} is a {@code final} class with a fixed set of
 * sub-configs, and Java has no way to add a new field to an existing compiled class from outside its own source.
 * <p>
 * <b>No compatibility guarantee whatsoever</b>: every class in this package may change shape, be renamed, or be
 * removed outright in any future GTST version, without notice and without regard for semantic versioning. This is
 * not a caveat added out of caution; it is the whole reason this machinery lives in its own package separate from
 * the rest of {@code gregtech.api} instead of being folded into {@link
 * gregtech.api.recipes.logic.statemachine.RecipeLogicConfig} itself. Depend on it only if you have specifically
 * decided the capability is worth that risk.
 * <p>
 * See {@link gregtech.api.recipes.logic.statemachine.experimental.ExperimentalRecipeLogicRegistry} for the actual
 * entry point.
 */
package gregtech.api.recipes.logic.statemachine.experimental;
