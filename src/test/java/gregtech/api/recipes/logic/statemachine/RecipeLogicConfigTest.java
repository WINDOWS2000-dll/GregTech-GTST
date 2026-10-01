package gregtech.api.recipes.logic.statemachine;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.OverclockingLogic;

import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RecipeLogicConfigTest {

    private static RecipeLogicConfig newConfig() {
        return new RecipeLogicConfig(() -> (maxVoltage, items, fluids) -> Collections.<Recipe>emptyList().iterator());
    }

    @Test
    void defaultsMatchPr2755EquivalentBehaviorOfNoOpAndUnlimited() {
        RecipeLogicConfig config = newConfig();

        assertThat(config.io.itemInput, is(nullValue()));
        assertThat(config.io.fluidInput, is(nullValue()));
        assertThat(config.io.itemTrim.getAsInt(), is(Integer.MAX_VALUE));
        assertThat(config.io.fluidTrim.getAsInt(), is(Integer.MAX_VALUE));
        assertThat(config.io.itemOutAmountLimit, is(nullValue()));
        assertThat(config.io.notifiedItemInputs.get().isEmpty(), is(true));

        assertThat(config.parallel.parallelLimit, is(nullValue()));
        assertThat(config.power.downTransformForParallels, is(false));

        assertThat(config.overclock.costFactor, is(OverclockingLogic.STD_VOLTAGE_FACTOR));
        assertThat(config.overclock.speedFactor, is(OverclockingLogic.STD_DURATION_FACTOR_INV));
        assertThat(config.overclock.upTransformForOverclocks, is(false));

        assertThat(config.hooks.stallType, is(RecipeStallType.DEGRESS));
        assertThat(config.hooks.shouldStartRecipeLookup.test(new net.minecraft.nbt.NBTTagCompound()), is(true));
        assertThat(config.hooks.asyncSearchAndSetup, is(false));

        assertThat(config.callbacks.onRecipeStarted, is(nullValue()));
        assertThat(config.callbacks.onInputsUpdate.isEmpty(), is(true));
    }

    @Test
    void itemInputViewDefaultDelegatesToTheConfiguredItemInputSupplier() {
        RecipeLogicConfig config = newConfig();
        var handler = new net.minecraftforge.items.ItemStackHandler(1);
        config.io.itemInput = () -> handler;

        // default view is a thin GTUtility wrapper around whatever itemInput currently supplies
        assertThat(config.io.itemInputView.get().size(), is(1));
    }

    @Test
    void eachConfigInstanceOwnsIndependentNestedGroupInstances() {
        RecipeLogicConfig a = newConfig();
        RecipeLogicConfig b = newConfig();

        assertNotSame(a.io, b.io);
        assertNotSame(a.hooks, b.hooks);
        assertNotSame(a.hooks.additionalSearchSetupOperators, b.hooks.additionalSearchSetupOperators);

        a.overclock.costFactor = 99.0;
        assertThat(b.overclock.costFactor, is(OverclockingLogic.STD_VOLTAGE_FACTOR));
    }

    @Test
    void lookupIsExposedExactlyAsProvided() {
        RecipeLookup marker = (maxVoltage, items, fluids) -> Collections.<Recipe>emptyList().iterator();
        RecipeLogicConfig config = new RecipeLogicConfig(() -> marker);

        assertThat(config.lookup.get(), sameInstance(marker));
    }

    @Test
    void invalidateDiscardsEveryQueuedAndActiveRecipe() {
        RecipeLogicConfig config = newConfig();
        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data, new NBTTagCompound());
        ActiveRecipeList.append(data, new NBTTagCompound());
        data.setInteger(ActiveRecipeList.INDEX_KEY, 0);
        data.setTag(ActiveRecipeList.SELECTED_KEY, new NBTTagCompound());
        data.setDouble(ActiveRecipeList.BONUS_PROGRESS_KEY, 5.0);

        config.invalidate(data);

        assertThat(PreparedRecipeQueue.isEmpty(data), is(true));
        assertThat(ActiveRecipeList.count(data), is(0));
        assertThat(data.hasKey(ActiveRecipeList.INDEX_KEY), is(false));
        assertThat(data.hasKey(ActiveRecipeList.SELECTED_KEY), is(false));
        assertThat(data.hasKey(ActiveRecipeList.BONUS_PROGRESS_KEY), is(false));
    }

    @Test
    void invalidateRunsAdditionalCleanupAfterTheStandardDiscard() {
        RecipeLogicConfig config = newConfig();
        NBTTagCompound data = new NBTTagCompound();
        PreparedRecipeQueue.append(data, new NBTTagCompound());
        boolean[] queueAlreadyClearedWhenCalled = { false };
        config.hooks.additionalCleanup = d -> queueAlreadyClearedWhenCalled[0] = PreparedRecipeQueue.isEmpty(d);

        config.invalidate(data);

        assertThat(queueAlreadyClearedWhenCalled[0], is(true));
    }
}
