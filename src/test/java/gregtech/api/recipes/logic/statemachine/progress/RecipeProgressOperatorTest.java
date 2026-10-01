package gregtech.api.recipes.logic.statemachine.progress;

import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeProgressOperatorTest {

    private static NBTTagCompound newDataWithSelectedEntry(int progress, double duration) {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, progress);
        entry.setDouble(ActiveRecipeList.ENTRY_DURATION_KEY, duration);
        data.setTag(ActiveRecipeList.SELECTED_KEY, entry);
        return data;
    }

    @Test
    void advancesProgressByOneWithNoBankedBonus() {
        NBTTagCompound data = newDataWithSelectedEntry(0, 100);

        RecipeProgressOperator.INSTANCE.operate(data);

        assertThat(ActiveRecipeList.selected(data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(1));
    }

    @Test
    void consumesWholeBankedBonusAndKeepsTheFractionalRemainder() {
        NBTTagCompound data = newDataWithSelectedEntry(0, 100);
        data.setDouble(ActiveRecipeList.BONUS_PROGRESS_KEY, 2.5);

        RecipeProgressOperator.INSTANCE.operate(data);

        // base 1 + floor(2.5) = 3 progress this tick; 0.5 remains banked
        assertThat(ActiveRecipeList.selected(data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(3));
        assertThat(data.getDouble(ActiveRecipeList.BONUS_PROGRESS_KEY), is(0.5));
    }

    @Test
    void removesTheBonusTagOnceFullyConsumed() {
        NBTTagCompound data = newDataWithSelectedEntry(0, 100);
        data.setDouble(ActiveRecipeList.BONUS_PROGRESS_KEY, 2.0);

        RecipeProgressOperator.INSTANCE.operate(data);

        assertThat(ActiveRecipeList.selected(data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(3));
        assertThat(data.hasKey(ActiveRecipeList.BONUS_PROGRESS_KEY), is(false));
    }
}
