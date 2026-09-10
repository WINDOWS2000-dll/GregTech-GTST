package gregtech.api.metatileentity;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.metatileentity.multiblock.ICleanroomProvider;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeWorkableTieredMetaTileEntityTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 20100;

    /** The minimal concrete subclass: only {@link #createMetaTileEntity} is actually abstract above this class. */
    private static class TestMachine extends RecipeWorkableTieredMetaTileEntity {

        TestMachine(ResourceLocation id, RecipeMap<?> recipeMap) {
            super(id, recipeMap, null, 1, GTUtility.defaultTankSizeFunction);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestMachine(metaTileEntityId, recipeMap);
        }
    }

    private static RecipeWorkableTieredMetaTileEntity newHost(RecipeMap<?> map) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestMachine(GTUtility.gregtechId("recipe_workable_mte_test_" + testId), map));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (RecipeWorkableTieredMetaTileEntity) holder;
    }

    @Test
    void findsAndCompletesARecipeThroughTheMachineSInventoriesAndEnergyContainer() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(2).EUt(30).buildAndRegister();

        RecipeWorkableTieredMetaTileEntity host = newHost(map);
        host.getImportItems().insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);
        // fill the energy container directly, same as AbstractRecipeLogicTest's approach for a self-sufficient test
        host.getEnergyContainer().addEnergy(Long.MAX_VALUE / 2);
        long energyAfterCharge = host.getEnergyContainer().getEnergyStored();

        assertFalse(host.isActive());
        assertThat(host.getRecipeMap(), is(map)); // MetaTileEntity.getRecipeMap()'s generic IHasRecipeMap trait scan

        host.update(); // search finds+admits the candidate, progress -> 1, drains 1 tick's EUt (30)
        assertTrue(host.isActive());
        assertThat("the recipe's own EUt must actually be drained from the energy container each tick",
                host.getEnergyContainer().getEnergyStored(), is(energyAfterCharge - 30));

        host.update(); // progress -> 2 == duration -> completes and outputs this same tick, drains another 30

        assertThat(host.getEnergyContainer().getEnergyStored(), is(energyAfterCharge - 60));
        assertThat(host.getExportItems().getStackInSlot(0).getItem(), is(Item.getItemFromBlock(Blocks.STONE)));
        assertFalse(host.isActive());
    }

    @Test
    void stallsProgressAndReportsInsufficientEnergyWhenTheContainerRunsDry() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(5).EUt(30).buildAndRegister();

        RecipeWorkableTieredMetaTileEntity host = newHost(map);
        host.getImportItems().insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);
        // exactly one tick's worth: enough to admit and progress once, then run dry
        host.getEnergyContainer().addEnergy(30);

        assertFalse(host.insufficientEnergy()); // not active yet, so not reported as insufficient either

        host.update(); // admits, drains the only 30 EU available, progress -> 1
        assertTrue(host.isActive());
        assertThat(host.workable.getProgress(), is(1));
        assertThat(host.getEnergyContainer().getEnergyStored(), is(0L));
        // insufficientEnergy() tracks drainRecipeEnergy's own actual pass/fail result, not a fill-percentage
        // snapshot -- this tick's drain succeeded (exactly the 30 needed EU was available), so despite the
        // container now sitting at 0 energy, this must NOT be reported as insufficient (see insufficientEnergy's
        // own JavaDoc for why a fill-percentage heuristic would produce a false positive here).
        assertFalse(host.insufficientEnergy());

        host.update(); // per-tick check fails (0 EU available), progress degresses back to 0 rather than advancing
        assertTrue(host.isActive()); // still queued, just stalled -- not discarded
        assertThat(host.workable.getProgress(), is(0));
        assertThat(host.getEnergyContainer().getEnergyStored(), is(0L));
        // this tick's drain genuinely failed (0 of the 30 needed EU was available) -- now correctly reported.
        assertTrue(host.insufficientEnergy());
    }

    @Test
    void admitsOnlyOneRecipeAtATimeEvenWhenTheRecipeMapSynthesizesOneUnitAtATime() {
        RecipeWorkableTieredMetaTileEntity host = newHost(RecipeMaps.FURNACE_RECIPES);
        host.getImportItems().insertItem(0, new ItemStack(Blocks.IRON_ORE, 64), false);
        host.getEnergyContainer().addEnergy(Long.MAX_VALUE / 2);

        host.update();
        assertThat("only one of the 64 available units should be admitted per search pass",
                host.workable.getActiveRecipeCount(), is(1));

        host.update(); // still progressing; must not admit a second one while the first is still active
        assertThat(host.workable.getActiveRecipeCount(), is(1));
    }

    @Test
    void settingWorkingEnabledFalseIsReflectedByTheMachineSIControllable() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();

        RecipeWorkableTieredMetaTileEntity host = newHost(map);
        assertTrue(host.isWorkingEnabled());

        host.setWorkingEnabled(false);
        assertFalse(host.isWorkingEnabled());
    }

    @Test
    void shouldStartRecipeLookupIsFalseWhileARecipeIsAlreadyActive() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(5).EUt(30).buildAndRegister();

        RecipeWorkableTieredMetaTileEntity host = newHost(map);
        host.getImportItems().insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);
        host.getEnergyContainer().addEnergy(Long.MAX_VALUE / 2);

        NBTTagCompound dummy = new NBTTagCompound();
        assertTrue(host.workable.getConfig().hooks.shouldStartRecipeLookup.test(dummy),
                "idle (no active recipe) must allow a search to start");

        host.update(); // admits, progress -> 1
        assertTrue(host.isActive());
        assertFalse(host.workable.getConfig().hooks.shouldStartRecipeLookup.test(dummy),
                "already active with parallelLimit=1 must skip starting a new search");

        host.update();
        host.update();
        host.update();
        host.update(); // progress -> 5 == duration -> completes
        assertFalse(host.isActive());
        assertTrue(host.workable.getConfig().hooks.shouldStartRecipeLookup.test(dummy),
                "idle again after completion must allow searching once more");
    }

    private static class TestCleanroomProvider implements ICleanroomProvider {

        private final CleanroomType type;

        TestCleanroomProvider(CleanroomType type) {
            this.type = type;
        }

        @Override
        public boolean checkCleanroomType(@NotNull CleanroomType type) {
            return this.type == type;
        }

        @Override
        public void setCleanAmount(int amount) {}

        @Override
        public void adjustCleanAmount(int amount) {}

        @Override
        public boolean isClean() {
            return true;
        }

        @Override
        public boolean drainEnergy(boolean simulate) {
            return true;
        }

        @Override
        public long getEnergyInputPerSecond() {
            return 0;
        }

        @Override
        public int getEnergyTier() {
            return 0;
        }
    }

    @Test
    void cleanroomRequiredRecipeOnlySucceedsWithAMatchingCleanroom() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(2).EUt(30).cleanroom(CleanroomType.CLEANROOM).buildAndRegister();
        map.recipeBuilder().inputs(new ItemStack(Blocks.SAND)).outputs(new ItemStack(Blocks.SANDSTONE))
                .duration(2).EUt(30).buildAndRegister();

        RecipeWorkableTieredMetaTileEntity host = newHost(map);
        host.getImportItems().insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);
        host.getEnergyContainer().addEnergy(Long.MAX_VALUE / 2);

        host.update();
        assertFalse(host.isActive(), "no cleanroom set at all must reject the clean-room-gated recipe");

        host.setCleanroom(new TestCleanroomProvider(CleanroomType.STERILE_CLEANROOM));
        host.update();
        assertFalse(host.isActive(), "a cleanroom of the wrong type must still reject the recipe");

        host.setCleanroom(new TestCleanroomProvider(CleanroomType.CLEANROOM));
        host.update();
        assertTrue(host.isActive(), "a matching, clean cleanroom must allow the recipe to start");
    }
}
