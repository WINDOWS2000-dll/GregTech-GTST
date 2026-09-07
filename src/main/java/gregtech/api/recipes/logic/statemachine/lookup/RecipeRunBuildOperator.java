package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.logic.RecipeRun;
import gregtech.api.recipes.logic.RecipeView;
import gregtech.api.recipes.logic.StandardRecipeRun;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.statemachine.GTStateMachineTransientOperator;
import gregtech.api.util.GTUtility;

import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Builds the final {@link StandardRecipeRun} from {@link RecipeViewBuildOperator}'s view and
 * {@link RecipeOverclockOperator}'s outcome. This is where chance-based outputs are actually rolled (once, via
 * {@code StandardRecipeRun}'s constructor) &mdash; everything before this operator only ever dealt with worst-case/
 * guaranteed amounts.
 * <p>
 * The recipe tier (for chance/yield boosting) is derived from the candidate's own EU/t, and the machine tier from
 * {@code config.power.getMaxVoltage()} &mdash; both per-invocation, not baked in at graph-build time, since this
 * {@link gregtech.api.statemachine.GTStateMachine} graph is shared across every instance of a machine type (which
 * may have different tiers, e.g. via casing upgrades).
 */
public final class RecipeRunBuildOperator implements GTStateMachineTransientOperator {

    public static final String RUN_KEY = "RecipeRun";

    private final @NotNull RecipeLogicConfig config;

    public RecipeRunBuildOperator(@NotNull RecipeLogicConfig config) {
        this.config = config;
    }

    @Override
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        RecipeView view = (RecipeView) transientData.get(RecipeViewBuildOperator.VIEW_KEY);
        OverclockOutcome outcome = (OverclockOutcome) transientData.get(RecipeOverclockOperator.RESULT_KEY);
        if (view == null || outcome == null) {
            throw new IllegalStateException("RecipeRunBuildOperator ran without a view/overclock outcome");
        }

        long maxVoltage = config.power.getMaxVoltage();
        int recipeTier = GTUtility.getOCTierByVoltage(Math.abs(view.getActualEUt()));
        int machineTier = GTUtility.getOCTierByVoltage(maxVoltage);

        RecipePropertySet properties = config.power.properties == null ? RecipePropertySet.empty() :
                config.power.properties.get();

        RecipeRun run = new StandardRecipeRun(view, properties, recipeTier, machineTier, outcome.overclocks(),
                outcome.duration(), outcome.requiredVoltage(), outcome.requiredAmperage(),
                config.io.itemTrim.getAsInt(), config.io.fluidTrim.getAsInt());
        transientData.put(RUN_KEY, run);
    }
}
