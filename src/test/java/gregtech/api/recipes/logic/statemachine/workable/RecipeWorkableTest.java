package gregtech.api.recipes.logic.statemachine.workable;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.RecipeWorkableSimpleMachineMetaTileEntity;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.util.GTUtility;
import gregtech.api.util.ValidationResult;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemStackHandler;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeWorkableTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 20000;

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("recipe_workable_test_" + testId, new SimpleRecipeBuilder())
                .itemInputs(2).itemOutputs(2).build();
    }

    /** A minimal real {@link MetaTileEntity}, just to give {@link RecipeWorkable} a non-null, non-remote world. */
    private static MetaTileEntity newHost(RecipeMap<?> map) {
        return newHost(map, DummyWorld.INSTANCE);
    }

    /** As {@link #newHost(RecipeMap)}, but against an arbitrary world (e.g. {@link DummyWorld#REMOTE_INSTANCE}). */
    private static MetaTileEntity newHost(RecipeMap<?> map, World world) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new RecipeWorkableSimpleMachineMetaTileEntity(
                        GTUtility.gregtechId("recipe_workable_test_" + testId), map, null, 1, false));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(world);
        return holder;
    }

    private static RecipeLogicConfig newConfig(RecipeMap<?> map, ItemStackHandler input,
                                               List<List<ItemStack>> delivered, long voltage) {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.io.itemInput = () -> input;
        config.io.itemOutput = delivered::add;
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(voltage, 1));
            return properties;
        };
        return config;
    }

    @Test
    void findsAndCompletesARecipeThroughTheIWorkableSurface() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(3).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 1));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        MetaTileEntity host = newHost(map);
        RecipeWorkable workable = new RecipeWorkable(host, newConfig(map, input, delivered, 30), map);

        assertFalse(workable.isActive());
        assertThat(workable.getRecipeMap(), is(map));

        workable.update(); // search finds+admits the candidate, progress -> 1
        assertTrue(workable.isActive());
        assertThat(workable.getProgress(), is(1));
        assertThat(workable.getMaxProgress(), is(3));
        assertThat(workable.getActiveRecipeCount(), is(1));

        workable.update(); // progress -> 2
        assertThat(workable.getProgress(), is(2));

        workable.update(); // progress -> 3 == duration -> completes and outputs this same tick
        assertThat(delivered.size(), is(1));
        assertThat(delivered.get(0).get(0).getItem(), is(Item.getItemFromBlock(Blocks.STONE)));
        assertFalse(workable.isActive());
    }

    @Test
    void reportsVoltageAmperageAndTotalRequiredEUtForTheActiveRecipe() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(3).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 1));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        MetaTileEntity host = newHost(map);
        RecipeWorkable workable = new RecipeWorkable(host, newConfig(map, input, delivered, 30), map);

        assertThat("idle: no active recipe to report on", workable.getTotalRequiredEUt(), is(0L));

        workable.update(); // search finds+admits the candidate
        assertThat(workable.getActiveRecipeCount(), is(1));
        assertThat(workable.getVoltage(0), is(30L));
        assertThat(workable.getAmperage(0), is(1L)); // RecipeBuilder's own default, never overridden by this recipe
        assertFalse(workable.isGenerating(0));
        assertThat(workable.getRequiredEUt(0), is(30L));
        assertThat(workable.getTotalRequiredEUt(), is(30L));
    }

    @Test
    void reportsThroughTheIRecipeLogicInfoProviderSurface() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(3).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 1));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        MetaTileEntity host = newHost(map);
        RecipeWorkable workable = new RecipeWorkable(host, newConfig(map, input, delivered, 30), map);

        assertFalse(workable.isWorking(), "idle: nothing to report on yet");
        assertThat(workable.getInfoProviderEUt(), is(0L));

        workable.update(); // search finds+admits the candidate
        assertTrue(workable.isWorking());
        assertThat(workable.getInfoProviderEUt(), is(30L));
        assertTrue(workable.consumesEnergy(), "a plain (non-generating) recipe must report as a consumer");
    }

    @Test
    void debouncesTheActiveSyncAcrossBackToBackVeryShortRecipesToAvoidOverlayFlicker() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(2).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 64));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        RecipeLogicConfig config = newConfig(map, input, delivered, 30);
        RecipeWorkable workable = new RecipeWorkable(newHost(map), config, map);
        config.parallel.parallelLimit = () -> 1;
        config.parallel.consumedParallelSupplier = workable::getCommittedParallel;
        config.hooks.shouldStartRecipeLookup = data -> workable.getCommittedParallel() < 1;

        for (int i = 0; i < 20; i++) {
            workable.update();
            assertTrue(workable.reportedActive, "tick " + i + ": debounced active state must never blip false " +
                    "while a 2-tick recipe keeps immediately replacing itself");
        }
        assertTrue(delivered.size() > 1,
                "sanity check: multiple recipes must actually have completed during this test");
    }

    @Test
    void settingWorkingEnabledFalseStopsProgress() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(5).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 1));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        MetaTileEntity host = newHost(map);
        RecipeWorkable workable = new RecipeWorkable(host, newConfig(map, input, delivered, 30), map);

        assertTrue(workable.isWorkingEnabled());
        workable.update();
        assertThat(workable.getProgress(), is(1));

        workable.setWorkingEnabled(false);
        assertFalse(workable.isWorkingEnabled());
        workable.update();
        workable.update();
        assertThat("progress should not have advanced while working is disabled", workable.getProgress(), is(1));

        workable.setWorkingEnabled(true);
        workable.update();
        assertThat(workable.getProgress(), is(2));
    }

    @Test
    void nbtRoundTripsActiveRecipeStateAndWorkingEnabled() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(5).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 1));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        MetaTileEntity host = newHost(map);
        RecipeLogicConfig config = newConfig(map, input, delivered, 30);
        RecipeWorkable workable = new RecipeWorkable(host, config, map);
        workable.update();
        workable.setWorkingEnabled(false);

        RecipeWorkable reloaded = new RecipeWorkable(newHost(map), config, map);
        reloaded.deserializeNBT(workable.serializeNBT());

        assertThat(reloaded.isActive(), is(true));
        assertThat(reloaded.getProgress(), is(1));
        assertThat(reloaded.getMaxProgress(), is(5));
        assertFalse(reloaded.isWorkingEnabled());
    }

    @Test
    void remoteWorkableReportsActiveFromReceivedCustomDataNotLiveState() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        RecipeWorkable remote = new RecipeWorkable(newHost(map, DummyWorld.REMOTE_INSTANCE),
                newConfig(map, new ItemStackHandler(1), new ObjectArrayList<>(), 30), map);

        assertFalse(remote.isActive(), "a freshly-constructed remote workable defaults to inactive");

        PacketBuffer activate = new PacketBuffer(Unpooled.buffer());
        activate.writeBoolean(true);
        remote.receiveCustomData(RecipeWorkable.ACTIVE_CHANGED, activate);
        assertTrue(remote.isActive(),
                "receiveCustomData(ACTIVE_CHANGED, true) should flip isActive() on a remote workable");

        PacketBuffer deactivate = new PacketBuffer(Unpooled.buffer());
        deactivate.writeBoolean(false);
        remote.receiveCustomData(RecipeWorkable.ACTIVE_CHANGED, deactivate);
        assertFalse(remote.isActive());

        PacketBuffer disable = new PacketBuffer(Unpooled.buffer());
        disable.writeBoolean(false);
        remote.receiveCustomData(RecipeWorkable.WORKING_ENABLED_CHANGED, disable);
        assertFalse(remote.isWorkingEnabled(),
                "receiveCustomData(WORKING_ENABLED_CHANGED, false) should flip isWorkingEnabled() too");
    }

    /**
     * As above, but for the {@link RecipeWorkable#writeInitialSyncData}/{@link RecipeWorkable#receiveInitialSyncData}
     */
    @Test
    void initialSyncCarriesActiveAndWorkingEnabledToARemoteWorkable() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(5).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 1));

        RecipeWorkable server = new RecipeWorkable(newHost(map),
                newConfig(map, input, new ObjectArrayList<>(), 30), map);
        server.update(); // admits a recipe, now active
        server.setWorkingEnabled(false);
        assertTrue(server.isActive());

        RecipeWorkable remote = new RecipeWorkable(newHost(map, DummyWorld.REMOTE_INSTANCE),
                newConfig(map, new ItemStackHandler(1), new ObjectArrayList<>(), 30), map);
        assertFalse(remote.isActive(), "sanity: a fresh remote workable starts inactive/enabled");

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        server.writeInitialSyncData(buf);
        remote.receiveInitialSyncData(buf);

        assertTrue(remote.isActive(), "initial sync should carry the server's active state to a mid-recipe client");
        assertFalse(remote.isWorkingEnabled(), "initial sync should carry the server's working-enabled state too");
    }

    @Test
    void enablingTraceDoesNotAffectRecipeProgress() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(3).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 1));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();

        MetaTileEntity host = newHost(map);
        RecipeWorkable workable = new RecipeWorkable(host, newConfig(map, input, delivered, 30), map);

        assertFalse(workable.isTraceEnabled());
        workable.setTraceEnabled(true, "test");
        assertTrue(workable.isTraceEnabled());

        workable.update(); // search finds+admits the candidate, progress -> 1, while traced
        assertTrue(workable.isActive());
        assertThat(workable.getProgress(), is(1));

        workable.setTraceEnabled(false);
        assertFalse(workable.isTraceEnabled());

        workable.update(); // progress -> 2, no longer traced
        workable.update(); // progress -> 3 == duration -> completes
        assertThat(delivered.size(), is(1));
        assertFalse(workable.isActive());
    }

    @Test
    void getStateMachineReturnsTheSameNonNullGraphAcrossCalls() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        RecipeWorkable workable = new RecipeWorkable(newHost(map),
                newConfig(map, new ItemStackHandler(1), new ObjectArrayList<>(), 30), map);

        assertThat(workable.getStateMachine(), is(workable.getStateMachine()));
        assertTrue(workable.getStateMachine().operatorCount() > 0);
    }

    @Test
    void getPreviousRecipeReportsTheMostRecentlyAdmittedRecipe() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        ValidationResult<Recipe> validationResult = map.recipeBuilder()
                .inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(3).EUt(30).build();
        Recipe recipe = validationResult.getResult();
        map.addRecipe(validationResult);

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 1));

        RecipeWorkable workable = new RecipeWorkable(newHost(map),
                newConfig(map, input, new ObjectArrayList<>(), 30), map);

        assertThat("nothing admitted yet", workable.getPreviousRecipe(), is((Object) null));

        workable.update(); // search finds+admits the candidate
        assertThat(workable.getPreviousRecipe(), is(recipe));
    }

    @Test
    void constructorChainsOntoAnAlreadyConfiguredEntryEnricherRatherThanReplacingIt() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE, 1)).outputs(new ItemStack(Blocks.STONE))
                .duration(3).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Blocks.COBBLESTONE, 1));

        RecipeLogicConfig config = newConfig(map, input, new ObjectArrayList<>(), 30);
        List<String> enricherCalls = new ObjectArrayList<>();
        config.hooks.entryEnricher = (recipe, entry) -> enricherCalls.add("called");

        RecipeWorkable workable = new RecipeWorkable(newHost(map), config, map);
        workable.update(); // search finds+admits the candidate

        assertThat("the machine's own enricher must still run", enricherCalls.size(), is(1));
        assertThat("getPreviousRecipe must also be populated", workable.getPreviousRecipe(), is(notNullValue()));
    }
}
