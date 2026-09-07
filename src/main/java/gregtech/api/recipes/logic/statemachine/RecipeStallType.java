package gregtech.api.recipes.logic.statemachine;

/**
 * What happens to a recipe's progress when a per-tick check (see {@link RecipeLogicHooks#perTickWorkerCheck}/
 * {@link RecipeLogicHooks#perTickRecipeCheck}) fails, e.g. due to insufficient power or a maintenance problem.
 */
public enum RecipeStallType {

    /** Progress decays gradually rather than halting outright. */
    DEGRESS,

    /** Progress is reset to zero immediately. */
    RESET,

    /**
     * Progress is left exactly where it is, neither advancing nor decaying, until the check passes again. PR
     * #2755 uses this for generators ({@code SimpleGeneratorMetaTileEntity}): a fuel-burning recipe that can't
     * currently deliver its EU/t (e.g. output capped) shouldn't lose the fuel already spent reaching its current
     * progress, unlike a consuming machine where {@link #DEGRESS}/{@link #RESET} discourage leaving a recipe
     * stalled indefinitely.
     */
    PAUSE
}
