package gregtech.api.recipes.logic.statemachine.progress;

import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeStallType;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeStallOperatorTest {

    private static NBTTagCompound newDataWithSelectedProgress(int progress) {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, progress);
        data.setTag(ActiveRecipeList.SELECTED_KEY, entry);
        return data;
    }

    @Test
    void degressDecreasesProgressByOneButNotBelowZero() {
        NBTTagCompound data = newDataWithSelectedProgress(5);

        RecipeStallOperator.of(RecipeStallType.DEGRESS).operate(data);
        assertThat(ActiveRecipeList.selected(data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(4));

        NBTTagCompound atZero = newDataWithSelectedProgress(0);
        RecipeStallOperator.of(RecipeStallType.DEGRESS).operate(atZero);
        assertThat(ActiveRecipeList.selected(atZero).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(0));
    }

    @Test
    void resetDropsProgressStraightToZero() {
        NBTTagCompound data = newDataWithSelectedProgress(50);

        RecipeStallOperator.of(RecipeStallType.RESET).operate(data);

        assertThat(ActiveRecipeList.selected(data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(0));
    }

    @Test
    void pauseLeavesProgressExactlyWhereItWas() {
        NBTTagCompound data = newDataWithSelectedProgress(50);

        RecipeStallOperator.of(RecipeStallType.PAUSE).operate(data);

        assertThat(ActiveRecipeList.selected(data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(50));
    }
}
