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

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeLogicEndToEndTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("end_to_end_test_" + System.nanoTime(), new SimpleRecipeBuilder())
                .itemInputs(2).itemOutputs(2).build();
    }

    private static void withPowerSupply(RecipeLogicConfig config, long voltage, long amperage) {
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(voltage, amperage));
            return properties;
        };
    }

    @Test
    void fullLifecycleFromSearchThroughAdmissionToCompletion() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(3).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.io.itemInput = () -> input;
        config.io.itemOutput = delivered::add;
        withPowerSupply(config, 30, 1);

        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data); // search finds+queues the candidate; admission consumes and starts
                                                     // it progressing
        assertThat("materials should already be consumed once admitted", input.getStackInSlot(0).isEmpty(), is(true));
        assertThat(ActiveRecipeList.count(data), is(1));
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(1));
        assertThat("no second candidate should be searched for while one is already consuming the only iron available",
                delivered.isEmpty(), is(true));

        RecipeLogicGraphBuilder.tick(machine, data); // progress -> 2
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(2));

        RecipeLogicGraphBuilder.tick(machine, data); // progress -> 3 == duration -> completes and outputs this same
                                                     // tick

        assertThat(delivered.size(), is(1));
        assertThat(delivered.get(0).get(0).getItem(), is(Items.GOLD_INGOT));
        assertThat(ActiveRecipeList.count(data), is(0));
    }

    @Test
    void aSecondRecipeStartsOnceInputsAreReplenishedAfterTheFirstCompletes() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(1).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.io.itemInput = () -> input;
        config.io.itemOutput = delivered::add;
        withPowerSupply(config, 30, 1);

        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data); // admitted, progresses to 1 == duration -> completes this same
                                                     // tick
        assertThat(delivered.size(), is(1));
        assertThat(ActiveRecipeList.count(data), is(0));

        // replenish inputs for a second run
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        RecipeLogicGraphBuilder.tick(machine, data);

        assertThat(delivered.size(), is(2));
        assertThat(input.getStackInSlot(0).isEmpty(), is(true));
    }

    @Test
    void bitflagRecipeLookupWorksThroughTheSamePipelineAsTheLinearScanLookup() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(1).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        RecipeLogicConfig config = new RecipeLogicConfig(map::getBitflagLookup);
        config.io.itemInput = () -> input;
        config.io.itemOutput = delivered::add;
        withPowerSupply(config, 30, 1);

        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data); // admitted, progresses to 1 == duration -> completes this same
                                                     // tick

        assertThat(delivered.size(), is(1));
        assertThat(delivered.get(0).get(0).getItem(), is(Items.GOLD_INGOT));
        assertThat(input.getStackInSlot(0).isEmpty(), is(true));
    }

    @Test
    void itemTrimSuppressesChancedByproductsThroughTheFullPipeline() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .chancedOutput(new ItemStack(Items.DIAMOND), 10_000, 0)
                .duration(1).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.io.itemInput = () -> input;
        config.io.itemOutput = delivered::add;
        config.io.itemTrim = () -> 1;
        withPowerSupply(config, 30, 1);

        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data); // admitted, progresses to 1 == duration -> completes this same
                                                     // tick

        assertThat(delivered.size(), is(1));
        assertThat(delivered.get(0).size(), is(1));
        assertThat(delivered.get(0).get(0).getItem(), is(Items.GOLD_INGOT));
    }
}
