package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.logic.RecipeRun;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Applies {@code RecipeLogicConfig.hooks.finalCheck} (if set) to {@link RecipeRunBuildOperator}'s finished
 * {@link RecipeRun}, as the last gate before it's queued. See {@code RecipeLogicHooks#finalCheck}'s JavaDoc for how
 * this differs from {@code RecipeLogicHooks#recipeSearchPredicate}.
 */
public final class RecipeFinalCheckOperator implements GTStateMachineTransientOperator {

    /** On {@code data}: whether the run passed {@code finalCheck} (or {@code finalCheck} isn't set). */
    public static final String SUCCESS_KEY = "FinalCheckSuccess";

    public static final Predicate<NBTTagCompound> SUCCESS_PREDICATE = d -> d.getBoolean(SUCCESS_KEY);

    private final @NotNull RecipeLogicConfig config;

    public RecipeFinalCheckOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        RecipeRun run = (RecipeRun) transientData.get(RecipeRunBuildOperator.RUN_KEY);
        if (run == null) throw new IllegalStateException("RecipeFinalCheckOperator ran without a finished run");

        boolean passed = config.hooks.finalCheck == null || config.hooks.finalCheck.test(run);
        data.setBoolean(SUCCESS_KEY, passed);
    }
}
