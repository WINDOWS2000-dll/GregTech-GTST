package gregtech.api.recipes.logic.statemachine.computation;

import gregtech.Bootstrap;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.ComputationRecipeBuilder;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeLogicGraphBuilder;
import gregtech.api.recipes.logic.statemachine.RecipeStallType;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeMapLookup;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.statemachine.GTStateMachine;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.ItemStackHandler;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ComputationRecipeHooksTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static final class FakeComputationProvider implements IOpticalComputationProvider {

        int available;
        final List<Integer> realDraws = new ObjectArrayList<>();

        @Override
        public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
            int given = Math.min(cwut, available);
            if (!simulate) realDraws.add(given);
            return given;
        }

        @Override
        public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
            return available;
        }

        @Override
        public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
            return false;
        }
    }

    private static RecipeMap<ComputationRecipeBuilder> newMap() {
        return new RecipeMapBuilder<>("computation_hooks_test_" + System.nanoTime(), new ComputationRecipeBuilder())
                .itemInputs(1).itemOutputs(1).build();
    }

    private static RecipeLogicConfig newConfig(RecipeMap<ComputationRecipeBuilder> map, ItemStackHandler input,
                                               List<List<ItemStack>> delivered, FakeComputationProvider provider,
                                               ComputationType type, boolean energyAvailable) {
        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.io.itemInput = () -> input;
        config.io.itemOutput = delivered::add;
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(30, 1));
            return properties;
        };
        config.overclock.ocAmountCalculator = (recipeVoltage, maxVoltage) -> 0; // matches legacy: no overclocking
        config.hooks.entryEnricher = ComputationRecipeHooks::enrichEntry;
        config.hooks.stallType = RecipeStallType.DEGRESS;
        config.hooks.perTickRecipeCheck = ComputationRecipeHooks.perTickRecipeCheck(() -> provider, type,
                recipeData -> energyAvailable);
        config.hooks.progressOperationOverride = ComputationRecipeHooks.progressOverride(() -> provider);
        return config;
    }

    @Test
    void perTickModeAdvancesByOneTickPerSuccessfulDrawOfExactlyTheRequiredAmount() {
        RecipeMap<ComputationRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(3).EUt(30).CWUt(10).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();
        FakeComputationProvider provider = new FakeComputationProvider();
        provider.available = 100; // comfortably more than the 10 CWU/t this recipe needs

        RecipeLogicConfig config = newConfig(map, input, delivered, provider, ComputationType.SPORADIC, true);
        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data); // search+admit; progress -> 1
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(1));
        assertThat("exactly one real draw of exactly the recipe's own CWU/t, not the whole 100 available",
                provider.realDraws, is(Arrays.asList(10)));

        RecipeLogicGraphBuilder.tick(machine, data); // progress -> 2
        RecipeLogicGraphBuilder.tick(machine, data); // progress -> 3 == duration -> completes

        assertThat(delivered.size(), is(1));
        assertThat(provider.realDraws, is(Arrays.asList(10, 10, 10)));
    }

    @Test
    void steadyRevertsProgressOnAComputationShortfall() {
        RecipeMap<ComputationRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(10).EUt(30).CWUt(10).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();
        FakeComputationProvider provider = new FakeComputationProvider();
        provider.available = 10;

        RecipeLogicConfig config = newConfig(map, input, delivered, provider, ComputationType.STEADY, true);
        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data); // enough CWU: progress -> 1
        RecipeLogicGraphBuilder.tick(machine, data); // enough CWU: progress -> 2
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(2));

        provider.available = 0; // computation network now starved
        RecipeLogicGraphBuilder.tick(machine, data);

        assertThat("STEADY must revert progress on a computation shortfall, exactly like an energy shortfall " +
                "(DEGRESS: decay by one tick's worth)",
                ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(1));
    }

    @Test
    void sporadicWithholdsProgressWithoutRevertingOnAComputationShortfall() {
        RecipeMap<ComputationRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(10).EUt(30).CWUt(10).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();
        FakeComputationProvider provider = new FakeComputationProvider();
        provider.available = 10;

        RecipeLogicConfig config = newConfig(map, input, delivered, provider, ComputationType.SPORADIC, true);
        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data); // enough CWU: progress -> 1
        RecipeLogicGraphBuilder.tick(machine, data); // enough CWU: progress -> 2
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(2));

        provider.available = 0; // computation network now starved
        int drawsBeforeShortfall = provider.realDraws.size();
        RecipeLogicGraphBuilder.tick(machine, data);

        assertThat("SPORADIC must withhold progress (not advance) on a computation shortfall, but must not " +
                "revert it either",
                ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(2));
        assertThat("no real CWU draw should happen on a shortfall tick (nothing was actually available to draw)",
                provider.realDraws.size(), is(drawsBeforeShortfall));
    }

    @Test
    void energyIsStillDrawnEvenDuringASporadicComputationShortfall() {
        RecipeMap<ComputationRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .duration(10).EUt(30).CWUt(10).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();
        FakeComputationProvider provider = new FakeComputationProvider();
        provider.available = 0; // starved from the very first tick

        int[] energyCheckCalls = { 0 };
        RecipeLogicConfig config = new RecipeLogicConfig(() -> new RecipeMapLookup(map));
        config.io.itemInput = () -> input;
        config.io.itemOutput = delivered::add;
        config.power.properties = () -> {
            RecipePropertySet properties = RecipePropertySet.empty();
            properties.add(new PowerSupplyProperty(30, 1));
            return properties;
        };
        config.overclock.ocAmountCalculator = (recipeVoltage, maxVoltage) -> 0;
        config.hooks.entryEnricher = ComputationRecipeHooks::enrichEntry;
        config.hooks.stallType = RecipeStallType.DEGRESS;
        config.hooks.perTickRecipeCheck = ComputationRecipeHooks.perTickRecipeCheck(() -> provider,
                ComputationType.SPORADIC, recipeData -> {
                    energyCheckCalls[0]++;
                    return true;
                });
        config.hooks.progressOperationOverride = ComputationRecipeHooks.progressOverride(() -> provider);

        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data);
        RecipeLogicGraphBuilder.tick(machine, data);

        assertThat("the energy-drawing check must run every tick regardless of the computation shortfall " +
                "(mirrors legacy SPORADIC's documented invariant, which PR #2755's port lost)",
                energyCheckCalls[0], is(2));
    }

    @Test
    void totalCwuModeAdvancesByExactlyWhatWasDrawnAndNeverMoreThanStillNeeded() {
        RecipeMap<ComputationRecipeBuilder> map = newMap();
        // CWUt() (the per-tick minimum-availability threshold) and totalCWU() are independent on this builder
        // (unlike legacy's StationRecipeBuilder, which derives one from the other) -- both must be set explicitly.
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .EUt(30).CWUt(1).totalCWU(50).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();
        FakeComputationProvider provider = new FakeComputationProvider();
        provider.available = 1000; // deliberately far more than the 50 total this recipe needs

        RecipeLogicConfig config = newConfig(map, input, delivered, provider, ComputationType.SPORADIC, true);
        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data);

        assertThat("must draw only what the recipe still needs (50), not PR #2755's bug of always requesting the " +
                "full total regardless of network surplus", provider.realDraws, is(Arrays.asList(50)));
        assertThat("progress must advance by exactly the drawn amount, completing in a single tick since that " +
                "alone reaches the total", delivered.size(), is(1));
    }

    @Test
    void totalCwuModeAccumulatesAcrossSeveralTicksWhenSupplyIsLimited() {
        RecipeMap<ComputationRecipeBuilder> map = newMap();
        map.recipeBuilder().inputs(new ItemStack(Items.IRON_INGOT, 4)).outputs(new ItemStack(Items.GOLD_INGOT))
                .EUt(30).CWUt(10).totalCWU(25).buildAndRegister();

        ItemStackHandler input = new ItemStackHandler(1);
        input.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        List<List<ItemStack>> delivered = new ObjectArrayList<>();
        FakeComputationProvider provider = new FakeComputationProvider();
        provider.available = 10; // needs 3 ticks: 10 + 10 + 5 (capped at what's still needed on the last tick)

        RecipeLogicConfig config = newConfig(map, input, delivered, provider, ComputationType.SPORADIC, true);
        GTStateMachine machine = RecipeLogicGraphBuilder.build(config);
        NBTTagCompound data = new NBTTagCompound();

        RecipeLogicGraphBuilder.tick(machine, data);
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(10));
        RecipeLogicGraphBuilder.tick(machine, data);
        assertThat(ActiveRecipeList.entryAt(0, data).getInteger(ActiveRecipeList.ENTRY_PROGRESS_KEY), is(20));
        RecipeLogicGraphBuilder.tick(machine, data);

        assertThat("the final tick must draw only the remaining 5, not the full 10 available",
                provider.realDraws, is(Arrays.asList(10, 10, 5)));
        assertThat(delivered.size(), is(1));
    }
}
