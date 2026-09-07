package gregtech.api.recipes.logic.statemachine.progress;

import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeBonusCarryOperatorTest {

    private static NBTTagCompound newDataWithSelectedEntry(int progress, double duration) {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, progress);
        entry.setDouble(ActiveRecipeList.ENTRY_DURATION_KEY, duration);
        data.setTag(ActiveRecipeList.SELECTED_KEY, entry);
        return data;
    }

    @Test
    void banksOvershootAsBonusProgress() {
        NBTTagCompound data = newDataWithSelectedEntry(7, 5.0);

        RecipeBonusCarryOperator.INSTANCE.operate(data);

        assertThat(data.getDouble(ActiveRecipeList.BONUS_PROGRESS_KEY), is(2.0));
    }

    @Test
    void addsToAnyAlreadyBankedBonusRatherThanReplacingIt() {
        NBTTagCompound data = newDataWithSelectedEntry(6, 5.0);
        data.setDouble(ActiveRecipeList.BONUS_PROGRESS_KEY, 0.5);

        RecipeBonusCarryOperator.INSTANCE.operate(data);

        assertThat(data.getDouble(ActiveRecipeList.BONUS_PROGRESS_KEY), is(1.5));
    }

    @Test
    void doesNothingWhenThereWasNoOvershoot() {
        NBTTagCompound data = newDataWithSelectedEntry(5, 5.0);

        RecipeBonusCarryOperator.INSTANCE.operate(data);

        assertThat(data.hasKey(ActiveRecipeList.BONUS_PROGRESS_KEY), is(false));
    }

    @Test
    void rejectsBeingRunOnAnEntryThatHasNotActuallyCompleted() {
        NBTTagCompound data = newDataWithSelectedEntry(2, 5.0);

        assertThrows(IllegalStateException.class, () -> RecipeBonusCarryOperator.INSTANCE.operate(data));
    }
}
