package gregtech.api.recipes.logic.statemachine.progress;

import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.statemachine.GTStateMachineOperator;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Advances the currently-selected active recipe's progress by one tick, consuming any banked
 * {@link ActiveRecipeList#BONUS_PROGRESS_KEY} carry-over from a previous recipe's overshoot (see that field's
 * JavaDoc) before it decays.
 * <p>
 * Mutates {@link ActiveRecipeList#selected} in place; see that method's JavaDoc for why no separate write-back
 * into the active-recipe list is needed.
 */
public final class RecipeProgressOperator implements GTStateMachineOperator {

    public static final RecipeProgressOperator INSTANCE = new RecipeProgressOperator();

    private RecipeProgressOperator() {}

    @Override
    public void operate(NBTTagCompound data) {
        NBTTagCompound entry = ActiveRecipeList.selected(data);
        double bonus = data.getDouble(ActiveRecipeList.BONUS_PROGRESS_KEY);
        int progress = 1;
        if (bonus > 1) {
            progress += (int) bonus;
            double remainder = bonus + 1 - progress;
            if (remainder == 0) {
                data.removeTag(ActiveRecipeList.BONUS_PROGRESS_KEY);
            } else {
                data.setDouble(ActiveRecipeList.BONUS_PROGRESS_KEY, remainder);
            }
        }
        entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY,
                entry.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY) + progress);
    }
}
