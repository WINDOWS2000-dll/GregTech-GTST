package gregtech.api.recipes.logic.statemachine;

import gregtech.Bootstrap;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.statemachine.GTStateMachine;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemStackHandler;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Covers the {@code searchTransientData}/{@code progressTransientData}-reusing overload of {@link
 * RecipeLogicGraphBuilder#tick}, added so a caller (namely {@code RecipeWorkable}) can reuse the same two maps
 * across ticks instead of allocating fresh ones every time. See that overload's own JavaDoc for the "cleared
 * unconditionally on entry" contract these tests exercise.
 */
class RecipeLogicGraphBuilderTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("graph_builder_test_" + System.nanoTime(), new SimpleRecipeBuilder())
                .itemInputs(2).itemOutputs(2).build();
    }

    private static RecipeLogicConfig newConfig(RecipeMap<SimpleRecipeBuilder> map, ItemStackHandler input,
                                               Consumer<List<ItemStack>> onOutput) {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.io.itemInput = () -> input;
        config.io.itemOutput = onOutput;
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(30, 1));
            return properties;
        };
        return config;
    }

    @Test
    void reusedTransientDataMapsProduceTheSameResultAsFreshOnesEveryTick() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(3).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();
        RecipeLogicConfig config = newConfig(map, input, delivered::add);

        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();
        Map<String, Object> searchTransientData = new Object2ObjectOpenHashMap<>();
        Map<String, Object> progressTransientData = new Object2ObjectOpenHashMap<>();

        // Same three ticks as RecipeLogicEndToEndTest's equivalent scenario, but reusing the same two maps
        // across all three calls instead of letting the two-argument overload allocate fresh ones every time.
        RecipeLogicGraphBuilder.tick(machine, data, searchTransientData, progressTransientData, null);
        assertThat(ActiveRecipeList.count(data), is(1));
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(1));

        RecipeLogicGraphBuilder.tick(machine, data, searchTransientData, progressTransientData, null);
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(2));

        RecipeLogicGraphBuilder.tick(machine, data, searchTransientData, progressTransientData, null);
        assertThat(delivered.size(), is(1));
        assertThat(delivered.get(0).get(0).getItem(), is(Items.GOLD_INGOT));
        assertThat(ActiveRecipeList.count(data), is(0));
    }

    @Test
    void tickClearsCallerSuppliedTransientDataMapsBeforeRunningEvenIfTheyAlreadyHoldUnrelatedEntries() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        RecipeLogicConfig config = newConfig(map, input, outputs -> {});

        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        // Simulates a previous tick having been cut short mid-walk (an exception, or the step limit) and leaving
        // stale keys behind -- the next tick must not see them.
        Map<String, Object> searchTransientData = new Object2ObjectOpenHashMap<>();
        searchTransientData.put("leftoverFromAPreviousTick", new Object());
        Map<String, Object> progressTransientData = new Object2ObjectOpenHashMap<>();
        progressTransientData.put("leftoverFromAPreviousTick", new Object());

        RecipeLogicGraphBuilder.tick(machine, data, searchTransientData, progressTransientData, null);

        assertThat(ActiveRecipeList.count(data), is(1));
        assertThat(searchTransientData.containsKey("leftoverFromAPreviousTick"), is(false));
        assertThat(progressTransientData.containsKey("leftoverFromAPreviousTick"), is(false));
    }

    @Test
    void twoIndependentReuseSitesDoNotInterfereWithEachOtherAcrossTicks() {
        // Guards against a reuse implementation accidentally sharing state across two different machine instances
        // (e.g. via a static field) -- each RecipeWorkable-equivalent here has its own pair of maps and must
        // progress its own recipe completely independently of the other's.
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 1)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(2).EUt(30).buildAndRegister();

        ItemStackHandler inputA = new ItemStackHandler(1);
        inputA.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        List<List<ItemStack>> deliveredA = new ObjectArrayList<>();
        GTStateMachine machineA = RecipeLogicGraphBuilder.build(newConfig(map, inputA, deliveredA::add));
        NBTTagCompound dataA = new NBTTagCompound();
        Map<String, Object> searchA = new Object2ObjectOpenHashMap<>();
        Map<String, Object> progressA = new Object2ObjectOpenHashMap<>();

        ItemStackHandler inputB = new ItemStackHandler(1);
        inputB.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1));
        List<List<ItemStack>> deliveredB = new ObjectArrayList<>();
        GTStateMachine machineB = RecipeLogicGraphBuilder.build(newConfig(map, inputB, deliveredB::add));
        NBTTagCompound dataB = new NBTTagCompound();
        Map<String, Object> searchB = new Object2ObjectOpenHashMap<>();
        Map<String, Object> progressB = new Object2ObjectOpenHashMap<>();

        // Tick A twice (completes: duration 2) before B has run at all.
        RecipeLogicGraphBuilder.tick(machineA, dataA, searchA, progressA, null);
        RecipeLogicGraphBuilder.tick(machineA, dataA, searchA, progressA, null);
        assertThat(deliveredA.size(), is(1));
        assertThat(deliveredB.size(), is(0));
        assertThat("B's input must be untouched by A's ticks", inputB.getStackInSlot(0).getCount(), is(1));

        // Now tick B: it should behave exactly as if it were the only machine ever ticked.
        RecipeLogicGraphBuilder.tick(machineB, dataB, searchB, progressB, null);
        RecipeLogicGraphBuilder.tick(machineB, dataB, searchB, progressB, null);
        assertThat(deliveredB.size(), is(1));
        assertThat("A must not have received a second delivery from B's ticks", deliveredA.size(), is(1));
    }
}
