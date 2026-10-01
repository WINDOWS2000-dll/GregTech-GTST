package gregtech.api.metatileentity;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeWorkableGeneratorMetaTileEntityTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 20400;

    /** The minimal concrete subclass: only {@link #createMetaTileEntity} is actually abstract above this class. */
    private static class TestMachine extends RecipeWorkableGeneratorMetaTileEntity {

        TestMachine(ResourceLocation id, RecipeMap<?> recipeMap, int tier) {
            super(id, recipeMap, null, tier, GTUtility.defaultTankSizeFunction);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestMachine(metaTileEntityId, recipeMap, getTier());
        }
    }

    private static RecipeWorkableGeneratorMetaTileEntity newHost(RecipeMap<?> map, int tier) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestMachine(GTUtility.gregtechId("recipe_workable_generator_mte_test_" + testId), map, tier));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (RecipeWorkableGeneratorMetaTileEntity) holder;
    }

    @Test
    void findsAndCompletesAGeneratingRecipeAddingEnergyInsteadOfDrainingItAndNeverOverclocking() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_generator_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(0).allowEmptyOutputs().build();

        map.recipeBuilder().inputs(new ItemStack(Items.COAL)).duration(4).EUt(30).setGenerating()
                .buildAndRegister();

        RecipeWorkableGeneratorMetaTileEntity host = newHost(map, 3); // HV
        host.getImportItems().insertItem(0, new ItemStack(Items.COAL), false);
        long initialEnergy = host.getEnergyContainer().getEnergyStored();

        assertFalse(host.isActive());

        host.update(); // admits, progress -> 1, ADDS one tick's EUt (30) into the container
        assertTrue(host.isActive());
        assertThat("a generating recipe must add its EU/t into the container, not drain it",
                host.getEnergyContainer().getEnergyStored(), is(initialEnergy + 30));
        assertThat(host.workable.getProgress(), is(1));

        host.update(); // progress -> 2
        host.update(); // progress -> 3
        assertThat("no overclocking should ever be applied to a generator, despite ample voltage headroom",
                host.workable.getProgress(), is(3));
        assertTrue(host.isActive());

        host.update(); // progress -> 4 == duration -> completes, having taken exactly 4 ticks (no OC speedup)
        assertThat(host.getEnergyContainer().getEnergyStored(), is(initialEnergy + 120));
        assertFalse(host.isActive());
    }

    @Test
    void admitsOnlyOneRecipeAtATimeEvenWithAbundantFuelForManyMoreCopies() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_generator_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(0).allowEmptyOutputs().build();
        map.recipeBuilder().inputs(new ItemStack(Items.COAL, 1)).duration(40).EUt(30).setGenerating()
                .buildAndRegister();

        RecipeWorkableGeneratorMetaTileEntity host = newHost(map, 1); // LV, matching the recipe's own LV magnitude
        // abundant fuel: enough for far more than one copy if admission were left uncapped
        host.getImportItems().insertItem(0, new ItemStack(Items.COAL, 64), false);

        for (int tick = 0; tick < 10; tick++) {
            host.update();
            assertThat("only one active recipe should ever be admitted at a time, regardless of available fuel",
                    host.workable.getActiveRecipeCount(), is(1));
        }
    }
}
