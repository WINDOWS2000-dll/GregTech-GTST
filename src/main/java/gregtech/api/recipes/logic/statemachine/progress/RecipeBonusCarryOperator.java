package gregtech.api.recipes.logic.statemachine.progress;

import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.statemachine.GTStateMachineOperator;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Runs once a completed active recipe has had its outputs delivered, before it is removed from the active list:
 * banks any progress accrued past the recipe's required duration as {@link ActiveRecipeList#BONUS_PROGRESS_KEY}, so
 * a fractional speed bonus that overshoots one recipe's completion carries into the next recipe's first tick
 * rather than being discarded.
 * <p>
 * Registered as a <b>transient</b> operator when {@code RecipeLogicCallbacks#onRecipeCompleted} is set (see
 * {@code RecipeProgressTrackBuilder}): the bonus-carry mutation this operator makes is only guaranteed to be
 * captured in the persisted snapshot once a later non-transient operator runs. If the completion callback throws
 * before that happens, the bonus is not double-banked on a retry after reload &mdash; the mutation only "commits"
 * once the walk successfully proceeds past the callback.
 */
public final class RecipeBonusCarryOperator implements GTStateMachineOperator {

    public static final RecipeBonusCarryOperator INSTANCE = new RecipeBonusCarryOperator();

    private RecipeBonusCarryOperator() {}

    @Override
    public void operate(NBTTagCompound data) {
        NBTTagCompound entry = ActiveRecipeList.selected(data);
        int progress = entry.getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY);
        double duration = entry.getDouble(ActiveRecipeList.ENTRY_DURATION_KEY);
        if (duration > progress) {
            throw new IllegalStateException(
                    "RecipeBonusCarryOperator ran on an active recipe that has not actually completed " +
                            "(progress " + progress + " < duration " + duration + ")");
        }
        double bonus = progress - duration;
        if (bonus > 0) {
            data.setDouble(ActiveRecipeList.BONUS_PROGRESS_KEY,
                    data.getDouble(ActiveRecipeList.BONUS_PROGRESS_KEY) + bonus);
        }
    }
}
