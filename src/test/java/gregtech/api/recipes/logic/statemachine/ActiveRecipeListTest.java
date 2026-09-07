package gregtech.api.recipes.logic.statemachine;

import gregtech.Bootstrap;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

class ActiveRecipeListTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static NBTTagCompound stageSelected(NBTTagCompound data, int index) {
        NBTTagCompound entry = ActiveRecipeList.entryAt(index, data);
        data.setTag(ActiveRecipeList.SELECTED_KEY, entry);
        return entry;
    }

    @Test
    void selectedReturnsTheSameInstanceStoredInTheList() {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, 0);
        ActiveRecipeList.append(data, entry);

        NBTTagCompound staged = stageSelected(data, 0);
        assertSame(staged, ActiveRecipeList.selected(data));
    }

    @Test
    void mutatingSelectedMutatesTheListEntryInPlace() {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, 0);
        ActiveRecipeList.append(data, entry);
        stageSelected(data, 0);

        ActiveRecipeList.selected(data).setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, 42);

        // Read back through a completely separate accessor: if selected()/entryAt() ever started returning a
        // defensive copy, this would still read 0 and fail.
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(42));
    }

    @Test
    void selectedAliasingIsIndependentPerEntry() {
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound first = new NBTTagCompound();
        first.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, 0);
        NBTTagCompound second = new NBTTagCompound();
        second.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, 0);
        ActiveRecipeList.append(data, first);
        ActiveRecipeList.append(data, second);

        stageSelected(data, 1);
        ActiveRecipeList.selected(data).setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, 7);

        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(0));
        assertThat(ActiveRecipeList.entryAt(1, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(7));
    }
}
