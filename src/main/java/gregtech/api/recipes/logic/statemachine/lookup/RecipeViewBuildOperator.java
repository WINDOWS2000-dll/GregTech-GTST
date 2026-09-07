package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.RecipeView;
import gregtech.api.recipes.logic.StandardRecipeView;
import gregtech.api.statemachine.GTStateMachineTransientOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.Map;

/**
 * Builds a {@link StandardRecipeView} from {@link RecipeParallelOperator}'s candidate recipe/achieved parallel and
 * {@link RecipeSearchOperator}'s input snapshot.
 */
public final class RecipeViewBuildOperator implements GTStateMachineTransientOperator {

    public static final String VIEW_KEY = "RecipeView";

    @Override
    @SuppressWarnings("unchecked")
    public void operate(NBTTagCompound data, Map<String, Object> transientData) {
        Recipe candidate = (Recipe) transientData.get(RecipeParallelOperator.CANDIDATE_RECIPE_KEY);
        List<ItemStack> items = (List<ItemStack>) transientData.get(RecipeSearchOperator.ITEMS_SNAPSHOT_KEY);
        List<FluidStack> fluids = (List<FluidStack>) transientData.get(RecipeSearchOperator.FLUIDS_SNAPSHOT_KEY);
        int parallel = (int) transientData.get(RecipeParallelOperator.ACHIEVED_PARALLEL_KEY);
        if (candidate == null || items == null || fluids == null) {
            throw new IllegalStateException("RecipeViewBuildOperator ran without a candidate recipe");
        }

        RecipeView view = new StandardRecipeView(candidate, items, fluids, parallel);
        transientData.put(VIEW_KEY, view);
    }
}
