package gregtech.api.metatileentity.multiblock;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.capability.IHeatingCoil;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.BlastRecipeBuilder;
import gregtech.api.recipes.builders.FusionRecipeBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.recipes.logic.OverclockingLogic;
import gregtech.api.recipes.logic.RecipeRun;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeCoilOverclockOperator;
import gregtech.api.recipes.logic.statemachine.lookup.RecipeFusionOverclockOperator;
import gregtech.api.recipes.logic.statemachine.property.EnergyContainerProperties;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.FusionStartCapacityProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.TemperatureCapacityProperty;
import gregtech.api.recipes.properties.impl.FusionEUToStartProperty;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeWorkableMultiblockControllerTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 20300;

    /** The minimal concrete subclass: fakes structure state instead of real pattern matching. */
    private static class TestMultiblock extends RecipeWorkableMultiblockController {

        boolean formed = true;

        TestMultiblock(ResourceLocation id, RecipeMap<?> recipeMap) {
            super(id, recipeMap);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestMultiblock(metaTileEntityId, recipeMap);
        }

        @Override
        public boolean isStructureFormed() {
            return formed;
        }

        @Override
        protected BlockPattern createStructurePattern() {
            return null;
        }

        @Nullable
        @Override
        public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
            return null;
        }
    }

    private static TestMultiblock newHost(RecipeMap<?> map) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestMultiblock(GTUtility.gregtechId("recipe_workable_multiblock_test_" + testId), map));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (TestMultiblock) holder;
    }

    private static class TestParallelMultiblock extends TestMultiblock {

        TestParallelMultiblock(ResourceLocation id, RecipeMap<?> recipeMap) {
            super(id, recipeMap);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestParallelMultiblock(metaTileEntityId, recipeMap);
        }

        @Override
        protected @NotNull RecipeLogicConfig createConfig() {
            RecipeLogicConfig config = super.createConfig();
            config.parallel.parallelLimit = () -> 10;
            return config;
        }
    }

    private static TestParallelMultiblock newParallelHost(RecipeMap<?> map) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestParallelMultiblock(GTUtility.gregtechId("recipe_workable_multiblock_test_" + testId), map));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (TestParallelMultiblock) holder;
    }

    private static class TestDownTransformMultiblock extends TestMultiblock {

        TestDownTransformMultiblock(ResourceLocation id, RecipeMap<?> recipeMap) {
            super(id, recipeMap);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestDownTransformMultiblock(metaTileEntityId, recipeMap);
        }

        @Override
        protected @NotNull RecipeLogicConfig createConfig() {
            RecipeLogicConfig config = super.createConfig();
            config.parallel.parallelLimit = () -> 10;
            config.power.downTransformForParallels = true;
            return config;
        }
    }

    private static TestDownTransformMultiblock newDownTransformHost(RecipeMap<?> map) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestDownTransformMultiblock(GTUtility.gregtechId("recipe_workable_multiblock_test_" + testId),
                        map));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (TestDownTransformMultiblock) holder;
    }

    private static class TestDistinctMultiblock extends TestMultiblock {

        List<IItemHandlerModifiable> fakeImportBuses = Collections.emptyList();

        TestDistinctMultiblock(ResourceLocation id, RecipeMap<?> recipeMap) {
            super(id, recipeMap);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestDistinctMultiblock(metaTileEntityId, recipeMap);
        }

        @Override
        public boolean canBeDistinct() {
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> List<T> getAbilities(MultiblockAbility<T> ability) {
            return ability == MultiblockAbility.IMPORT_ITEMS ? (List<T>) fakeImportBuses : super.getAbilities(ability);
        }
    }

    private static TestDistinctMultiblock newDistinctHost(RecipeMap<?> map) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestDistinctMultiblock(GTUtility.gregtechId("recipe_workable_multiblock_test_" + testId), map));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (TestDistinctMultiblock) holder;
    }

    private static class TestCoilMultiblock extends TestMultiblock implements IHeatingCoil {

        int temperature = 0;

        TestCoilMultiblock(ResourceLocation id, RecipeMap<?> recipeMap) {
            super(id, recipeMap);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestCoilMultiblock(metaTileEntityId, recipeMap);
        }

        @Override
        public int getCurrentTemperature() {
            return temperature;
        }

        @Override
        protected @NotNull RecipeLogicConfig createConfig() {
            RecipeLogicConfig config = super.createConfig();
            config.power.properties = () -> {
                RecipePropertySet properties = EnergyContainerProperties.of(getEnergyContainer());
                properties.add(new TemperatureCapacityProperty(temperature));
                return properties;
            };
            config.overclock.overclockFactory = (costFactor, speedFactor, canUpTransform,
                                                 durationDiscount) -> new RecipeCoilOverclockOperator(config);
            return config;
        }
    }

    private static TestCoilMultiblock newCoilHost(RecipeMap<?> map) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestCoilMultiblock(GTUtility.gregtechId("recipe_workable_multiblock_test_" + testId), map));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (TestCoilMultiblock) holder;
    }

    @Test
    void rejectsARecipeRequiringMoreTemperatureThanCurrentlyProvided() {
        RecipeMap<BlastRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new BlastRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(100).EUt(30).blastFurnaceTemp(1800).buildAndRegister();

        TestCoilMultiblock host = newCoilHost(map);
        host.temperature = 900; // too cold for a 1800K recipe
        ItemStackHandler input = new ItemStackHandler(1);
        ItemStackHandler output = new ItemStackHandler(1);
        host.inputInventory = input;
        host.outputInventory = output;
        host.energyContainer = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, GTValues.V[1], 2, 0L, 0L);
        host.energyContainer.addEnergy(Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);

        host.updateFormedValid();
        assertFalse(host.getWorkable().isActive(),
                "an under-heated furnace must not run a recipe it can't reach the required temperature for");
    }

    @Test
    void appliesAnEUtDiscountForExcessCoilTemperature() {
        RecipeMap<BlastRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new BlastRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(100).EUt(8).blastFurnaceTemp(900).buildAndRegister();

        TestCoilMultiblock coldHost = newCoilHost(map);
        coldHost.temperature = 900; // exactly at requirement: zero excess, zero discount
        ItemStackHandler coldInput = new ItemStackHandler(1);
        coldHost.inputInventory = coldInput;
        coldHost.outputInventory = new ItemStackHandler(1);
        coldHost.energyContainer = new EnergyContainerHandler(coldHost, Long.MAX_VALUE / 2, GTValues.V[0], 1, 0L, 0L);
        coldHost.energyContainer.addEnergy(Long.MAX_VALUE / 2);
        coldInput.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);
        coldHost.updateFormedValid();
        assertTrue(coldHost.getWorkable().isActive());
        long undiscountedVoltage = coldHost.getWorkable().getVoltage(0);

        TestCoilMultiblock hotHost = newCoilHost(map);
        hotHost.temperature = 900 + 900 * 20; // 20 discount steps: 0.95^20 =~ 0.36
        ItemStackHandler hotInput = new ItemStackHandler(1);
        hotHost.inputInventory = hotInput;
        hotHost.outputInventory = new ItemStackHandler(1);
        hotHost.energyContainer = new EnergyContainerHandler(hotHost, Long.MAX_VALUE / 2, GTValues.V[0], 1, 0L, 0L);
        hotHost.energyContainer.addEnergy(Long.MAX_VALUE / 2);
        hotInput.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);
        hotHost.updateFormedValid();
        assertTrue(hotHost.getWorkable().isActive());
        long discountedVoltage = hotHost.getWorkable().getVoltage(0);

        assertTrue(discountedVoltage < undiscountedVoltage,
                "excess coil heat (" + hotHost.temperature + "K vs required 900K) should discount required EU/t (" +
                        discountedVoltage + " should be less than " + undiscountedVoltage + ")");
    }

    private static class TestFusionMultiblock extends TestMultiblock {

        final int tier;
        long heat = 0;

        TestFusionMultiblock(ResourceLocation id, RecipeMap<?> recipeMap, int tier) {
            super(id, recipeMap);
            this.tier = tier;
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestFusionMultiblock(metaTileEntityId, recipeMap, tier);
        }

        @Override
        protected @NotNull RecipeLogicConfig createConfig() {
            RecipeLogicConfig config = super.createConfig();
            config.power.properties = () -> {
                RecipePropertySet properties = RecipePropertySet.empty();
                properties.add(new PowerSupplyProperty(getEnergyContainer().getInputVoltage(), 1));
                properties.add(new FusionStartCapacityProperty(
                        Math.min(getEnergyContainer().getEnergyCapacity(),
                                getEnergyContainer().getEnergyStored() + heat)));
                return properties;
            };
            config.overclock.costFactor = OverclockingLogic.PERFECT_HALF_VOLTAGE_FACTOR;
            config.overclock.overclockFactory = (costFactor, speedFactor, canUpTransform,
                                                 durationDiscount) -> new RecipeFusionOverclockOperator(config,
                                                         () -> tier);
            config.hooks.finalCheck = this::chargeHeatForStart;
            return config;
        }

        // As MetaTileEntityFusionReactor#chargeHeatForStart, verbatim.
        private boolean chargeHeatForStart(RecipeRun run) {
            long euToStart = run.getRecipeView().getRecipe().getProperty(FusionEUToStartProperty.getInstance(), 0L);
            if (euToStart > getEnergyContainer().getEnergyCapacity()) return false;

            long heatDiff = euToStart - heat;
            if (heatDiff <= 0) return true;

            if (getEnergyContainer().getEnergyStored() < heatDiff) return false;

            getEnergyContainer().removeEnergy(heatDiff);
            heat += heatDiff;
            return true;
        }
    }

    private static TestFusionMultiblock newFusionHost(RecipeMap<?> map, int tier) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestFusionMultiblock(GTUtility.gregtechId("recipe_workable_multiblock_test_" + testId), map, tier));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (TestFusionMultiblock) holder;
    }

    @Test
    void fusionOverclockClampsToReactorTierDifference() {
        FusionEUToStartProperty.registerFusionTier(GTValues.LuV, "(MK1)");
        FusionEUToStartProperty.registerFusionTier(GTValues.UV, "(MK3)");

        RecipeMap<FusionRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new FusionRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        // EUToStart of 100,000,000 falls at/under the MK1 (LuV) threshold (160,000,000) registered above.
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(128).EUt(8).EUToStart(100_000_000).buildAndRegister();

        TestFusionMultiblock mk1Host = newFusionHost(map, GTValues.LuV); // same tier as the recipe: 0 extra overclocks
        ItemStackHandler mk1Input = new ItemStackHandler(1);
        mk1Host.inputInventory = mk1Input;
        mk1Host.outputInventory = new ItemStackHandler(1);

        mk1Host.energyContainer = new EnergyContainerHandler(mk1Host, Long.MAX_VALUE / 2, GTValues.V[GTValues.UV], 0,
                0L, 0L);
        mk1Host.energyContainer.addEnergy(Long.MAX_VALUE / 2);
        mk1Input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);
        mk1Host.updateFormedValid();
        assertTrue(mk1Host.getWorkable().isActive());
        long mk1Voltage = mk1Host.getWorkable().getVoltage(0);

        TestFusionMultiblock mk3Host = newFusionHost(map, GTValues.UV); // two MK tiers above the recipe: up to 2 more
        ItemStackHandler mk3Input = new ItemStackHandler(1);
        mk3Host.inputInventory = mk3Input;
        mk3Host.outputInventory = new ItemStackHandler(1);
        mk3Host.energyContainer = new EnergyContainerHandler(mk3Host, Long.MAX_VALUE / 2, GTValues.V[GTValues.UV], 0,
                0L, 0L);
        mk3Host.energyContainer.addEnergy(Long.MAX_VALUE / 2);
        mk3Input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);
        mk3Host.updateFormedValid();
        assertTrue(mk3Host.getWorkable().isActive());
        long mk3Voltage = mk3Host.getWorkable().getVoltage(0);

        assertThat("an MK1-tier recipe run through a same-tier reactor should get no reactor-MK overclock bonus",
                mk1Voltage, is(8L));
        assertThat(
                "the same recipe through a reactor two MK tiers higher should get exactly 2 extra overclocks " +
                        "(voltage *= " + OverclockingLogic.PERFECT_HALF_VOLTAGE_FACTOR + " each)",
                mk3Voltage, is((long) (8 * Math.pow(OverclockingLogic.PERFECT_HALF_VOLTAGE_FACTOR, 2))));
    }

    @Test
    void fusionChargesHeatBeforeStartingAndBanksIt() {
        RecipeMap<FusionRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new FusionRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(128).EUt(8).EUToStart(1000L).buildAndRegister();

        TestFusionMultiblock host = newFusionHost(map, GTValues.UV);
        ItemStackHandler input = new ItemStackHandler(1);
        host.inputInventory = input;
        host.outputInventory = new ItemStackHandler(1);
        // Just enough capacity to eventually cover EUToStart, but not enough stored energy yet.
        host.energyContainer = new EnergyContainerHandler(host, 1000L, GTValues.V[GTValues.UV], 0, 0L, 0L);
        host.energyContainer.addEnergy(500L); // half of what EUToStart requires
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);

        host.updateFormedValid();
        assertFalse(host.getWorkable().isActive(), "not enough stored energy to fully charge heat yet");
        assertThat("chargeHeatForStart must not partially charge on a failed attempt", host.heat, is(0L));

        host.energyContainer.addEnergy(500L); // now exactly enough
        host.updateFormedValid();
        assertTrue(host.getWorkable().isActive(), "heat should now be fully charged and the recipe should start");
        assertThat(host.heat, is(1000L));
        assertThat(host.energyContainer.getEnergyStored(), is(0L));
    }

    private static void wireAbilities(TestMultiblock host, ItemStackHandler input, ItemStackHandler output,
                                      long energyCapacity) {
        host.inputInventory = input;
        host.outputInventory = output;
        host.energyContainer = new EnergyContainerHandler(host, energyCapacity, GTValues.V[1], 2, 0L, 0L);
        host.energyContainer.addEnergy(energyCapacity);
    }

    @Test
    void findsAndCompletesARecipeOnlyWhenTickedThroughUpdateFormedValid() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(2).EUt(30).buildAndRegister();

        TestMultiblock host = newHost(map);
        ItemStackHandler input = new ItemStackHandler(1);
        ItemStackHandler output = new ItemStackHandler(1);
        wireAbilities(host, input, output, Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);

        // MetaTileEntity's automatic MTETrait tick loop must not drive the recipe workable directly -- only
        // updateFormedValid() should (see shouldUpdate()'s override).
        host.update();
        assertFalse(host.getWorkable().isActive(),
                "the automatic MTETrait tick loop must not tick the recipe workable directly");

        host.updateFormedValid();
        assertTrue(host.getWorkable().isActive());

        host.updateFormedValid(); // progress -> 2 == duration -> completes and outputs this same tick
        assertThat(output.getStackInSlot(0).getItem(), is(Item.getItemFromBlock(Blocks.STONE)));
        assertFalse(host.getWorkable().isActive());
    }

    @Test
    void admitsOnlyOneRecipeAtATimePerSearchPass() {
        // Same parallel-budget regression the single-block RecipeWorkableTieredMetaTileEntityTest covers, since
        // this class wires the identical parallelLimit/consumedParallelSupplier fix.
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(100).EUt(30).buildAndRegister();

        TestMultiblock host = newHost(map);
        ItemStackHandler input = new ItemStackHandler(1);
        ItemStackHandler output = new ItemStackHandler(1);
        wireAbilities(host, input, output, Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 64), false);

        host.updateFormedValid();
        assertThat(host.getWorkable().getActiveRecipeCount(), is(1));

        host.updateFormedValid();
        assertThat(host.getWorkable().getActiveRecipeCount(), is(1));
    }

    @Test
    void doesNotKeepAdmittingFreshEntriesOnceAnAlreadyActiveEntryHasConsumedTheParallelBudget() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(100).EUt(30).buildAndRegister();

        TestParallelMultiblock host = newParallelHost(map);
        ItemStackHandler input = new ItemStackHandler(1);
        ItemStackHandler output = new ItemStackHandler(1);
        host.inputInventory = input;
        host.outputInventory = output;
        // 20A, comfortably above parallelLimit=10: isolates the entry-count-vs-committed-parallel bug from
        // downTransformForParallels/voltage-vs-amperage concerns (covered separately for Multi Smelter itself).
        host.energyContainer = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, GTValues.V[1], 20, 0L, 0L);
        host.energyContainer.addEnergy(Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 64), false); // far more than parallelLimit=10 needs

        host.updateFormedValid(); // one search pass should admit ONE entry, already at the full parallel budget
        assertThat(host.getWorkable().getActiveRecipeCount(), is(1));
        assertThat(host.getWorkable().getCommittedParallel(), is(10));

        // Several more ticks: with the budget already fully committed, no further entry should ever be admitted.
        for (int i = 0; i < 5; i++) host.updateFormedValid();
        assertThat(host.getWorkable().getActiveRecipeCount(), is(1));
        assertThat(host.getWorkable().getCommittedParallel(), is(10));
    }

    @Test
    void overclockCeilingAccountsForAmperageAlreadyCommittedByDownTransformedParallel() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        // Tiny voltage, mirroring RecipeMapFurnace's dynamically-synthesized recipes (Multi Smelter's own RecipeMap).
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(128).EUt(4).buildAndRegister();

        TestDownTransformMultiblock host = newDownTransformHost(map);
        ItemStackHandler input = new ItemStackHandler(1);
        ItemStackHandler output = new ItemStackHandler(1);
        host.inputInventory = input;
        host.outputInventory = output;
        long supplyVoltage = GTValues.V[GTValues.ZPM];
        long supplyAmperage = 1;
        host.energyContainer = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, supplyVoltage, supplyAmperage,
                0L, 0L);
        host.energyContainer.addEnergy(Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 64), false); // enough for the full parallelLimit=10

        host.updateFormedValid();
        assertThat(host.getWorkable().getActiveRecipeCount(), is(1));

        long totalRequired = host.getWorkable().getVoltage(0) * host.getWorkable().getAmperage(0);
        long totalSupplied = supplyVoltage * supplyAmperage;
        assertTrue(totalRequired <= totalSupplied,
                "total required EU/t (" + totalRequired + ") must never exceed the supply's real total (" +
                        totalSupplied + ")");
    }

    @Test
    void distinctCapableMultiblockBehavesLikeCombinedModeWhenNotToggled() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(50).EUt(30).buildAndRegister();

        TestDistinctMultiblock host = newDistinctHost(map);
        ItemStackHandler input = new ItemStackHandler(1);
        ItemStackHandler output = new ItemStackHandler(1);
        wireAbilities(host, input, output, Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);

        assertFalse(host.isDistinct());
        host.updateFormedValid();
        assertTrue(host.getWorkable().isActive());
    }

    @Test
    void distinctModeSearchesEachImportBusIndependently() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(50).EUt(30).buildAndRegister();

        TestDistinctMultiblock host = newDistinctHost(map);
        ItemStackHandler bus1 = new ItemStackHandler(1); // empty: group 0 has nothing to offer
        ItemStackHandler bus2 = new ItemStackHandler(1);
        bus2.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 64), false); // group 1 has everything needed
        host.fakeImportBuses = Arrays.asList(bus1, bus2);
        host.inputInventory = new ItemHandlerList(host.fakeImportBuses);
        host.outputInventory = new ItemStackHandler(1);
        host.energyContainer = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, GTValues.V[1], 2, 0L, 0L);
        host.energyContainer.addEnergy(Long.MAX_VALUE / 2);
        host.setDistinct(true);

        host.updateFormedValid();
        assertTrue(host.getWorkable().isActive(),
                "the second bus's items should be found even though the first (searched first) is empty");
    }

    @Test
    void invalidateStructureResetsAbilitiesAndDiscardsInProgressRecipes() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_multiblock_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(100).EUt(30).buildAndRegister();

        TestMultiblock host = newHost(map);
        ItemStackHandler input = new ItemStackHandler(1);
        ItemStackHandler output = new ItemStackHandler(1);
        wireAbilities(host, input, output, Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);

        host.updateFormedValid();
        assertTrue(host.getWorkable().isActive());

        host.invalidateStructure();
        assertFalse(host.getWorkable().isActive(), "a recipe in progress must not survive structure loss");
        assertThat(host.getInputInventory().getSlots(), is(0)); // back to the empty placeholder handler
    }
}
