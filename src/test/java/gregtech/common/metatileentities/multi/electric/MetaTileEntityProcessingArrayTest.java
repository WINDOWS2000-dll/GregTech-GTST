package gregtech.common.metatileentities.multi.electric;

import gregtech.Bootstrap;
import gregtech.api.GTValues;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.EnergyContainerHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.RecipeWorkableTieredMetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaTileEntityProcessingArrayTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 20500;

    /** A minimal single-block machine, standing in for "whatever machine is inserted into the hatch". */
    private static class TestChildMachine extends RecipeWorkableTieredMetaTileEntity {

        TestChildMachine(ResourceLocation id, RecipeMap<?> recipeMap, int tier) {
            super(id, recipeMap, null, tier, GTUtility.defaultTankSizeFunction);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestChildMachine(metaTileEntityId, recipeMap, getTier());
        }
    }

    private static class TestProcessingArray extends MetaTileEntityProcessingArray {

        final int fixtureTier;
        boolean formed = true;
        ItemStack machineHatchStack = ItemStack.EMPTY;
        @Nullable
        MetaTileEntity insertedMachine;

        TestProcessingArray(ResourceLocation id, int tier) {
            super(id, tier);
            this.fixtureTier = tier;
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestProcessingArray(metaTileEntityId, fixtureTier);
        }

        @Override
        public boolean isStructureFormed() {
            return formed;
        }

        @Override
        protected @NotNull ItemStack getMachineHatchStack() {
            return machineHatchStack;
        }

        @Override
        protected @Nullable MetaTileEntity resolveMachineHatchMachine(@NotNull ItemStack machineStack) {
            return machineStack.isEmpty() ? null : insertedMachine;
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

        void insertMachine(MetaTileEntity machine, int count) {
            this.insertedMachine = machine;
            // A plain non-empty placeholder stack: only isEmpty()/getCount() matter here, since resolution is
            // faked above rather than actually round-tripping through this stack's item.
            this.machineHatchStack = new ItemStack(Blocks.IRON_BLOCK, count);
        }

        void setTestInventories(IItemHandlerModifiable input, IItemHandlerModifiable output,
                                IEnergyContainer energy) {
            this.inputInventory = input;
            this.outputInventory = output;
            this.energyContainer = energy;
        }

        void tick() {
            updateFormedValid();
        }
    }

    private static TestProcessingArray newHost(int tier) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestProcessingArray(GTUtility.gregtechId("processing_array_test_" + testId), tier));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (TestProcessingArray) holder;
    }

    /** Registers a fresh single-block "child" machine at {@code tier}, ready to insert via {@code insertMachine}. */
    private static MetaTileEntity newChildMachine(RecipeMap<?> map, int tier) {
        MetaTileEntity sample = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestChildMachine(GTUtility.gregtechId("processing_array_child_test_" + testId), map, tier));
        MetaTileEntityHolder holder = new MetaTileEntityHolder();
        MetaTileEntity mte = holder.setMetaTileEntity(sample);
        holder.setWorld(DummyWorld.INSTANCE);
        return mte;
    }

    @Test
    void searchesTheInsertedMachineSOwnRecipeMapAndRunsItsRecipes() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("processing_array_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(2).EUt(8).buildAndRegister();

        TestProcessingArray host = newHost(GTValues.LV);
        host.insertMachine(newChildMachine(map, GTValues.LV), 1);
        IItemHandlerModifiable input = new ItemStackHandler(1);
        IEnergyContainer energy = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, GTValues.V[GTValues.LV], 1, 0L,
                0L);
        host.setTestInventories(input, new ItemStackHandler(1), energy);
        energy.addEnergy(Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);

        assertThat("with no machine inserted yet, this array must not report the map's recipe",
                host.getRecipeMap(), is((RecipeMap<?>) null));

        host.tick();
        assertTrue(host.getWorkable().isActive(), "a recipe from the inserted machine's own map should be running");
        assertThat("getRecipeMap() should now reflect the inserted machine's map, not a fixed one",
                host.getRecipeMap(), is((RecipeMap<?>) map));
    }

    /**
     * Regression test for the reactor-tier-style voltage clamp (legacy {@code ProcessingArrayWorkable
     * #getOverclockForTier}/{@code getNumberOfOCs}): overclocking must never exceed the tier of the machine
     * actually inserted, even though this array's own energy supply is far higher voltage.
     */
    @Test
    void overclockIsClampedToTheInsertedMachineSOwnTierNotThisArraySOwnSupply() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("processing_array_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();

        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(128).EUt(GTValues.VA[GTValues.LV]).buildAndRegister();

        TestProcessingArray host = newHost(GTValues.HV);
        host.insertMachine(newChildMachine(map, GTValues.MV), 1);
        IItemHandlerModifiable input = new ItemStackHandler(1);
        IEnergyContainer energy = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, GTValues.V[GTValues.HV], 1, 0L,
                0L);
        host.setTestInventories(input, new ItemStackHandler(1), energy);
        energy.addEnergy(Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);

        host.tick();
        assertTrue(host.getWorkable().isActive());
        assertThat("overclocking must stop at the inserted machine's own tier (LV -> MV, one step), not this " +
                "array's own HV supply",
                host.getWorkable().getVoltage(0), is((long) GTValues.VA[GTValues.MV]));
    }

    /** Legacy {@code ProcessingArrayWorkable#getParallelLimit}, verbatim: as many as physically inserted, capped. */
    @Test
    void parallelLimitTracksHowManyMachinesArePhysicallyInserted() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("processing_array_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(128).EUt(8).buildAndRegister();

        TestProcessingArray host = newHost(GTValues.LV);
        host.insertMachine(newChildMachine(map, GTValues.LV), 5);
        IEnergyContainer energy = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, GTValues.V[GTValues.LV], 1, 0L,
                0L);
        host.setTestInventories(new ItemStackHandler(1), new ItemStackHandler(1), energy);

        // Forces the lazy machineChanged -> findMachineStack() recompute without needing a real recipe search.
        host.notifyMachineChanged();
        host.tick();

        assertThat("5 machines physically inserted should allow 5 parallel copies",
                host.getWorkable().getConfig().parallel.parallelLimit.getAsInt(), is(5));
    }


    @Test
    void committedParallelNeverExceedsThisArraysOwnRealEnergyContainerAmperage() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("processing_array_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(128).EUt(GTValues.V[GTValues.LV]).buildAndRegister();

        TestProcessingArray host = newHost(GTValues.LV);
        host.insertMachine(newChildMachine(map, GTValues.LV), 5);
        IItemHandlerModifiable input = new ItemStackHandler(1);
        IEnergyContainer energy = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, GTValues.V[GTValues.LV], 2, 0L,
                0L);
        host.setTestInventories(input, new ItemStackHandler(1), energy);
        energy.addEnergy(Long.MAX_VALUE / 2);
        // Plenty of ingredients so the ingredient ratio is never the limiting factor.
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 64), false);

        for (int i = 0; i < 10; i++) {
            host.tick();
            assertThat("tick " + i + ": committed parallel must never exceed this array's own real 2-amp supply",
                    host.getWorkable().getCommittedParallel() <= 2, is(true));
        }
        assertThat("with plentiful ingredients, committed parallel should reach (not merely stay under) the real " +
                "2-amp ceiling", host.getWorkable().getCommittedParallel(), is(2));
    }

    @Test
    void reachesTheFullParallelLimitInOneTickEvenWhenTheInsertedMachineSVoltageDiffersFromThisArraysOwnSupply() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("processing_array_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();

        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(128).EUt(GTValues.V[GTValues.ULV]).buildAndRegister();

        TestProcessingArray host = newHost(GTValues.MV);
        host.insertMachine(newChildMachine(map, GTValues.MV), 5);
        IItemHandlerModifiable input = new ItemStackHandler(1);
        IEnergyContainer energy = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, GTValues.V[GTValues.MV], 2, 0L,
                0L);
        host.setTestInventories(input, new ItemStackHandler(1), energy);
        energy.addEnergy(Long.MAX_VALUE / 2);
        input.insertItem(0, new ItemStack(Blocks.COBBLESTONE, 64), false);

        host.tick();

        assertThat("the full machine-count-based parallel limit should be reached in a single tick, not " +
                "fragmented across several ticks' worth of small batches",
                host.getWorkable().getCommittedParallel(), is(5));
    }

    @Test
    void reportsInactiveAndNoRecipeMapWhenTheHatchIsEmpty() {
        TestProcessingArray host = newHost(GTValues.LV);
        host.machineHatchStack = ItemStack.EMPTY;
        IEnergyContainer energy = new EnergyContainerHandler(host, Long.MAX_VALUE / 2, GTValues.V[GTValues.LV], 1, 0L,
                0L);
        host.setTestInventories(new ItemStackHandler(1), new ItemStackHandler(1), energy);
        energy.addEnergy(Long.MAX_VALUE / 2);

        host.tick();

        assertFalse(host.getWorkable().isActive());
        assertThat(host.getRecipeMap(), is((RecipeMap<?>) null));
    }
}
