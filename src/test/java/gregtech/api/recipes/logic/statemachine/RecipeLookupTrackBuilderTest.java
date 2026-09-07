package gregtech.api.recipes.logic.statemachine;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.lookup.OverclockOutcome;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeOverclockOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeParallelOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeSelectionOperator;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.statemachine.GTStateMachine;
import gregtech.api.statemachine.GTStateMachineBuilder;
import gregtech.api.statemachine.GTStateMachineOperator;
import gregtech.api.util.GTUtility;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemStackHandler;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeLookupTrackBuilderTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeMap<SimpleRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("lookup_track_test_" + System.nanoTime(), new SimpleRecipeBuilder())
                .itemInputs(3).itemOutputs(3).build();
    }

    /** As {@link #newConfig(RecipeMap, ItemStackHandler, long, long)}, with a generous default amperage. */
    private static RecipeLogicConfig newConfig(RecipeMap<SimpleRecipeBuilder> map, ItemStackHandler input,
                                               long voltage) {
        return newConfig(map, input, voltage, 64);
    }

    private static RecipeLogicConfig newConfig(RecipeMap<SimpleRecipeBuilder> map, ItemStackHandler input,
                                               long voltage, long amperage) {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.io.itemInput = () -> input;
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(voltage, amperage));
            return properties;
        };
        return config;
    }

    private static GTStateMachine buildMachine(RecipeLogicConfig config) {
        GTStateMachineBuilder builder = new GTStateMachineBuilder();
        builder.newOperator(GTStateMachineOperator.emptyOp(), false, "root");
        RecipeLookupTrackBuilder.build(builder, 0, config);
        return builder.getConstructing();
    }

    private static void tick(GTStateMachine machine, NBTTagCompound data) {
        machine.walk(0, data, new Object2ObjectOpenHashMap<>(), false);
    }

    @Test
    void aMatchingCandidateIsQueuedWithCorrectOutputsAndConsumption() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        RecipeLogicConfig config = newConfig(map, input, 30);

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(1));
        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        assertThat(ActiveRecipeList.itemsOut(entry).get(0).getItem(), is(Items.GOLD_INGOT));
        assertThat(PreparedRecipeQueue.itemsConsumed(entry).get(0).getCount(), is(4));
    }

    @Test
    void entryEnricherWritesACustomKeyIntoTheQueuedEntry() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        RecipeLogicConfig config = newConfig(map, input, 30);
        config.hooks.entryEnricher = (recipe, entry) -> entry.setInteger("CustomCWUt", recipe.getDuration());

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        assertThat(entry.getInteger("CustomCWUt"), is(100));
    }

    @Test
    void shouldStartRecipeLookupFalseSkipsSearchEntirelyEvenWithAMatchingCandidate() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 10));
        RecipeLogicConfig config = newConfig(map, input, 30);
        config.hooks.shouldStartRecipeLookup = workerData -> false;

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.isEmpty(data), is(true));
    }

    @Test
    void noMatchLeavesTheQueueEmpty() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 1)); // not enough
        RecipeLogicConfig config = newConfig(map, input, 30);

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.isEmpty(data), is(true));
    }

    @Test
    void severalDifferentRecipesCanBeQueuedFromOneSearchPass() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();
        map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT, 1)).outputs(new ItemStack(Items.DIAMOND))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(2);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        input.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 1));
        RecipeLogicConfig config = newConfig(map, input, 30);

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(2));
    }

    @Test
    void parallelMultipliesInputsOutputsBasedOnAvailableIngredients() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        // as of voltage/amperage separation, the recipe's own per-unit voltage (30) is unaffected by parallel --
        // it's amperage, not voltage, that must cover the achieved parallel count (5).
        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 20)); // enough for 5x
        RecipeLogicConfig config = newConfig(map, input, 30, 10); // amperage(10) comfortably covers 5x
        config.parallel.parallelLimit = () -> 10;

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(1));
        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        assertThat(PreparedRecipeQueue.itemsConsumed(entry).get(0).getCount(), is(20)); // 4 * 5
        assertThat(ActiveRecipeList.itemsOut(entry).get(0).getCount(), is(5)); // 1 * 5
    }

    @Test
    void parallelLimitCapsHowManyCopiesAreMerged() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 20)); // enough for 5x
        RecipeLogicConfig config = newConfig(map, input, 30); // default amperage(64) comfortably exceeds the limit
                                                              // below
        config.parallel.parallelLimit = () -> 2; // capped below ingredient availability

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        assertThat(PreparedRecipeQueue.itemsConsumed(entry).get(0).getCount(), is(8)); // 4 * 2
    }

    @Test
    void parallelIsCappedByAvailableAmperage() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 20)); // enough for 5x by ingredients alone
        RecipeLogicConfig config = newConfig(map, input, 30, 2); // amperage(2) caps parallel below ingredient
                                                                 // availability
        config.parallel.parallelLimit = () -> 10;

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        assertThat(PreparedRecipeQueue.itemsConsumed(entry).get(0).getCount(), is(8)); // 4 * 2, not 4 * 5
    }

    @Test
    void parallelDoesNotInflateTheRecipesOwnVoltageForOverclockPurposes() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 20)); // enough for 5x
        RecipeLogicConfig config = newConfig(map, input, 30, 10);
        config.parallel.parallelLimit = () -> 10;

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(1));
        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        // 5 copies at 30 EU/t each = 150 total, with zero overclocks (voltage exactly matches the recipe's own tier)
        assertThat(PreparedRecipeQueue.requiredEUt(entry), is(150L));
    }

    @Test
    void parallelIsNeverAppliedWithoutExplicitlyConfiguringAParallelLimit() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 40)); // enough for 10x, but no parallelLimit is set
        RecipeLogicConfig config = newConfig(map, input, 30);

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        assertThat(PreparedRecipeQueue.itemsConsumed(entry).get(0).getCount(), is(4));
        assertThat(ActiveRecipeList.itemsOut(entry).get(0).getCount(), is(1));
    }

    @Test
    void candidateIsSkippedWhenWorstCaseOutputCannotFit() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();
        map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT, 1)).outputs(new ItemStack(Items.DIAMOND))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(2);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        input.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 1));
        RecipeLogicConfig config = newConfig(map, input, 30);
        // reject any output batch that contains gold ingots specifically
        config.io.itemOutputSpace = items -> items.stream().noneMatch(s -> s.getItem() == Items.GOLD_INGOT);

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(1));
        assertThat(ActiveRecipeList.itemsOut(PreparedRecipeQueue.peekFirst(data)).get(0).getItem(), is(Items.DIAMOND));
    }

    @Test
    void candidateIsSkippedWhenVoltageIsInsufficient() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        // the map lookup itself already excludes recipes over maxVoltage, so this exercises that a queued run's
        // required voltage reflects what was actually available, not the recipe's raw EUt
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        RecipeLogicConfig config = newConfig(map, input, 10); // below the recipe's own 30 EU/t

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.isEmpty(data), is(true));
    }

    @Test
    void aSecondSearchDoesNotDoubleReserveItemsAlreadyQueued() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4)); // only enough for exactly 1 recipe
        RecipeLogicConfig config = newConfig(map, input, 30);
        GTStateMachine machine = buildMachine(config);

        NBTTagCompound data = new NBTTagCompound();
        tick(machine, data); // first tick queues the only possible candidate
        tick(machine, data); // second tick: the real inventory is unchanged (nothing admitted it yet)

        assertThat("a second search must not re-reserve the same 4 iron ingots for a second entry",
                PreparedRecipeQueue.count(data), is(1));
    }

    @Test
    void aCustomOverclockFactoryReplacesTheStandardOperator() {
        // regression test for the "paper validation" finding that config.overclock.overclockFactory was never
        // actually wired into the graph
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        RecipeLogicConfig config = newConfig(map, input, 30);
        config.overclock.overclockFactory = (costFactor, speedFactor, canUpTransform,
                                             durationDiscount) -> (recipeData, transientData) -> {
                                                 transientData.put(RecipeOverclockOperator.RESULT_KEY,
                                                         new OverclockOutcome(0, 999.0, 1, 1));
                                                 recipeData.setBoolean(RecipeOverclockOperator.SUCCESS_KEY, true);
                                             };

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        assertThat(entry.getDouble(PreparedRecipeQueue.ENTRY_DURATION_KEY), is(999.0));
    }

    @Test
    void aCustomParallelLimitFactoryReplacesTheStandardOperator() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        RecipeLogicConfig config = newConfig(map, input, 30);
        config.parallel.parallelLimitFactory = downTransform -> (recipeData, transientData) -> {
            Recipe candidate = (Recipe) transientData.get(RecipeSelectionOperator.SELECTED_RECIPE_KEY);
            transientData.put(RecipeParallelOperator.CANDIDATE_RECIPE_KEY, candidate);
            transientData.put(RecipeParallelOperator.ACHIEVED_PARALLEL_KEY, 7);
            recipeData.setBoolean(RecipeParallelOperator.SUCCESS_KEY, true);
        };

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        assertThat(entry.getLong(PreparedRecipeQueue.ENTRY_AMPERAGE_KEY), is(7L));
    }

    @Test
    void finalCheckRejectsAndTriesTheNextCandidate() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();
        map.recipeBuilder().inputs(new ItemStack(Items.GOLD_INGOT, 1)).outputs(new ItemStack(Items.DIAMOND))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(2);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        input.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 1));
        RecipeLogicConfig config = newConfig(map, input, 30);
        // reject any fully-resolved run that would output a gold ingot
        config.hooks.finalCheck = run -> run.getItemsOut().stream().noneMatch(s -> s.getItem() == Items.GOLD_INGOT);

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(1));
        assertThat(ActiveRecipeList.itemsOut(PreparedRecipeQueue.peekFirst(data)).get(0).getItem(), is(Items.DIAMOND));
    }

    @Test
    void durationBonusPreOverclockAppliesEvenWithoutAnyOverclockHeadroom() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        RecipeLogicConfig config = newConfig(map, input, 30); // supply voltage == recipe voltage: no OC headroom
        config.overclock.durationBonusPreOverclock = () -> 0.5; // e.g. an auto-maintenance hatch bonus

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        // 100 * 0.5, halved before overclocking is even calculated (there is no overclock headroom here to hide
        // behind, unlike durationDiscount which only ever applies after)
        assertThat(entry.getDouble(PreparedRecipeQueue.ENTRY_DURATION_KEY), is(50.0));
    }

    @Test
    void distinctGroupsAreEachSearchedIndependentlyInOneTick() {
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler bus0 = new ItemStackHandler(1);
        bus0.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        ItemStackHandler bus1 = new ItemStackHandler(1);
        bus1.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));

        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(30, 64));
            return properties;
        };
        config.parallel.parallelLimit = () -> 10;
        config.io.distinctInputGroups = () -> Arrays.asList(
                DistinctInputGroup.of(GTUtility.itemHandlerToList(bus0), Collections.emptyList()),
                DistinctInputGroup.of(GTUtility.itemHandlerToList(bus1), Collections.emptyList()));

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(2));
    }

    @Test
    void distinctGroupsShareTheSameParallelBudget() {
        // the parallel/power budget is a property of the whole machine, not of any one group -- a limit of 1 must
        // cap the *combined* total across both groups at 1, not allow 1 *per group*.
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler bus0 = new ItemStackHandler(1);
        bus0.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        ItemStackHandler bus1 = new ItemStackHandler(1);
        bus1.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));

        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(30, 64));
            return properties;
        };
        config.parallel.parallelLimit = () -> 1;
        config.io.distinctInputGroups = () -> Arrays.asList(
                DistinctInputGroup.of(GTUtility.itemHandlerToList(bus0), Collections.emptyList()),
                DistinctInputGroup.of(GTUtility.itemHandlerToList(bus1), Collections.emptyList()));

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(1));
    }

    @Test
    void upTransformForOverclocksDisabledRejectsARecipeExceedingSingleAmpVoltage() {
        // voltage(8) alone can't cover the recipe's own voltage(30), even though amperage(10) means the supply's
        // total EU/t (80) comfortably could -- with upTransformForOverclocks left at its default (false), only the
        // single-amp voltage counts, matching GTST's existing "never mixing voltage tiers" behavior.
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        RecipeLogicConfig config = newConfig(map, input, 8, 10);

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.isEmpty(data), is(true));
    }

    @Test
    void upTransformForOverclocksEnabledAdmitsARecipeExceedingSingleAmpVoltageWhenTotalEUtCoversIt() {
        // Same setup as the disabled case above, but with upTransformForOverclocks on: the supply's total EU/t
        // (voltage(8) * amperage(10) = 80) now covers the recipe's own voltage(30), so it must be admitted instead
        // of rejected -- PR #2755's up-transform, trading surplus amperage for voltage headroom.
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        RecipeLogicConfig config = newConfig(map, input, 8, 10);
        config.overclock.upTransformForOverclocks = true;

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(1));
    }

    @Test
    void upTransformForOverclocksWithCostFactorOneDoesNotOverclockInsteadOfDividingByZero() {
        // Same setup as the enabled case above (supply's total EU/t comfortably covers the recipe), but with
        // costFactor misconfigured to 1.0 (the default is 4.0) -- Math.log(1.0) == 0 would otherwise turn
        // RecipeOverclockOperator#upTransformOcAmount's division into +Infinity, producing a garbage ocAmount
        // instead of failing safely. This must still admit the recipe, just with no overclocking applied.
        RecipeMap<SimpleRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(100).EUt(30).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        RecipeLogicConfig config = newConfig(map, input, 8, 10);
        config.overclock.upTransformForOverclocks = true;
        config.overclock.costFactor = 1.0;

        NBTTagCompound data = new NBTTagCompound();
        tick(buildMachine(config), data);

        assertThat(PreparedRecipeQueue.count(data), is(1));
        NBTTagCompound entry = PreparedRecipeQueue.peekFirst(data);
        assertThat(PreparedRecipeQueue.requiredEUt(entry), is(30L));
    }
}
