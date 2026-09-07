package gregtech.api.metatileentity;

import gregtech.Bootstrap;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeWorkableSimpleMachineMetaTileEntityTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 20200;

    private static RecipeWorkableSimpleMachineMetaTileEntity newHost(RecipeMap<?> map) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new RecipeWorkableSimpleMachineMetaTileEntity(
                        GTUtility.gregtechId("recipe_workable_simple_mte_test_" + testId), map, null, 1, true));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (RecipeWorkableSimpleMachineMetaTileEntity) holder;
    }

    @Test
    void findsAndCompletesARecipeThroughTheMachineSInventoriesWithAGhostCircuitSlotPresent() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_simple_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(1).EUt(30).buildAndRegister();

        RecipeWorkableSimpleMachineMetaTileEntity host = newHost(map);
        host.getImportItems().insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);
        host.getEnergyContainer().addEnergy(Long.MAX_VALUE / 2);

        host.update(); // duration 1 -- completes and outputs this same tick
        assertThat(host.getExportItems().getStackInSlot(0).getItem(), is(Item.getItemFromBlock(Blocks.STONE)));
    }

    @Test
    void importItemsComposesTheRealImportHandlerWithTheGhostCircuitHandler() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_simple_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();

        RecipeWorkableSimpleMachineMetaTileEntity host = newHost(map);
        // the real import handler's own slot count, plus the ghost circuit's single virtual slot
        assertThat(host.getImportItems().getSlots(), is(map.getMaxInputs() + 1));
    }

    @Test
    void autoOutputTogglesRoundTrip() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>("recipe_workable_simple_mte_test_" + testId,
                new SimpleRecipeBuilder()).itemInputs(1).itemOutputs(1).build();
        RecipeWorkableSimpleMachineMetaTileEntity host = newHost(map);

        assertFalse(host.isAutoOutputItems());
        host.setAutoOutputItems(true);
        assertTrue(host.isAutoOutputItems());

        assertFalse(host.isAutoOutputFluids());
        host.setAutoOutputFluids(true);
        assertTrue(host.isAutoOutputFluids());
    }

}
