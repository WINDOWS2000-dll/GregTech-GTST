package gregtech.api.recipes.logic.statemachine;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.statemachine.GTStateMachine;
import gregtech.api.statemachine.GTStateMachineBuilder;
import gregtech.api.statemachine.GTStateMachineOperator;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeProgressTrackBuilderTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    /** A {@link RecipeLookup} that never finds anything, for tests that only need a config to construct with. */
    private static RecipeLookup noopLookup() {
        return (maxVoltage, items, fluids) -> Collections.<Recipe>emptyList().iterator();
    }

    private static NBTTagCompound newActiveEntry(double duration) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, 0);
        entry.setDouble(ActiveRecipeList.ENTRY_DURATION_KEY, duration);
        return entry;
    }

    private static GTStateMachine buildMachine(RecipeLogicConfig config, GTStateMachineOperator admissionOperator) {
        GTStateMachineBuilder builder = new GTStateMachineBuilder();
        builder.newOperator(GTStateMachineOperator.emptyOp(), false, "root");
        RecipeProgressTrackBuilder.build(builder, 0, config, admissionOperator);
        return builder.getConstructing();
    }

    private static void tick(GTStateMachine machine, NBTTagCompound data) {
        machine.walk(0, data, new Object2ObjectOpenHashMap<>(), false);
    }

    @Test
    void singleActiveRecipeProgressesAndCompletesAfterItsDurationInTicks() {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> noopLookup());
        List<List<net.minecraft.item.ItemStack>> outputs = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        config.io.itemOutput = outputs::add;
        GTStateMachine machine = buildMachine(config, GTStateMachineOperator.emptyOp());

        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = newActiveEntry(3.0);
        ActiveRecipeList.append(data, entry);

        tick(machine, data);
        assertThat(ActiveRecipeList.count(data), is(1));
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(1));

        tick(machine, data);
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(2));

        tick(machine, data);
        // completed on this tick: removed from the active list
        assertThat(ActiveRecipeList.count(data), is(0));
    }

    @Test
    void allActiveRecipesProgressWithinASingleTick() {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> noopLookup());
        GTStateMachine machine = buildMachine(config, GTStateMachineOperator.emptyOp());

        NBTTagCompound data = new NBTTagCompound();
        ActiveRecipeList.append(data, newActiveEntry(100));
        ActiveRecipeList.append(data, newActiveEntry(100));
        ActiveRecipeList.append(data, newActiveEntry(100));

        tick(machine, data);

        assertThat(ActiveRecipeList.count(data), is(3));
        for (int i = 0; i < 3; i++) {
            assertThat("entry " + i, ActiveRecipeList.entryAt(i, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY),
                    is(1));
        }
    }

    @Test
    void aStalledRecipeDoesNotPreventOtherActiveRecipesFromProgressingTheSameTick() {
        // regression test for the PR #2755 bug documented on RecipeProgressTrackBuilder: completing/stalling one
        // active recipe must not abandon the rest of this tick's active-recipe loop.
        RecipeLogicConfig config = new RecipeLogicConfig(() -> noopLookup());
        config.hooks.perTickRecipeCheck = recipeData -> !recipeData.getBoolean("ShouldStall");
        GTStateMachine machine = buildMachine(config, GTStateMachineOperator.emptyOp());

        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound stalling = newActiveEntry(100);
        stalling.setBoolean("ShouldStall", true);
        ActiveRecipeList.append(data, stalling);
        ActiveRecipeList.append(data, newActiveEntry(100));

        tick(machine, data);

        assertThat("stalled entry should not have progressed",
                ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(0));
        assertThat("other entry should still have progressed this tick",
                ActiveRecipeList.entryAt(1, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(1));
    }

    @Test
    void completionDeliversOutputsNotifiesCallbackAndRemovesTheEntry() {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> noopLookup());
        List<NBTTagCompound> completedNotifications = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        List<List<net.minecraft.item.ItemStack>> outputs = new it.unimi.dsi.fastutil.objects.ObjectArrayList<>();
        config.io.itemOutput = outputs::add;
        config.callbacks.onRecipeCompleted = completedNotifications::add;
        GTStateMachine machine = buildMachine(config, GTStateMachineOperator.emptyOp());

        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = newActiveEntry(1.0);
        ActiveRecipeList.setItemsOut(entry,
                Collections.singletonList(new net.minecraft.item.ItemStack(net.minecraft.init.Items.APPLE)));
        ActiveRecipeList.append(data, entry);

        tick(machine, data);

        assertThat(outputs.size(), is(1));
        assertThat(completedNotifications.size(), is(1));
        assertThat(ActiveRecipeList.count(data), is(0));
    }

    @Test
    void admissionOperatorRunsEveryTick() {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> noopLookup());
        int[] admissionCalls = { 0 };
        GTStateMachine machine = buildMachine(config, d -> admissionCalls[0]++);

        NBTTagCompound data = new NBTTagCompound();
        tick(machine, data);
        tick(machine, data);

        assertThat(admissionCalls[0], is(2));
    }

    @Test
    void perTickWorkerCheckFailingStallsEveryActiveRecipe() {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> noopLookup());
        config.hooks.perTickWorkerCheck = workerData -> false;
        GTStateMachine machine = buildMachine(config, GTStateMachineOperator.emptyOp());

        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = newActiveEntry(5.0);
        entry.setInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY, 3);
        ActiveRecipeList.append(data, entry);

        tick(machine, data);

        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(2));
    }
}
