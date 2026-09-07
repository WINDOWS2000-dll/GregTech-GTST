package gregtech.api.metatileentity.multiblock;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMapBuilder;
import gregtech.api.recipes.builders.SimpleRecipeBuilder;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.common.metatileentities.MetaTileEntities;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.items.CapabilityItemHandler;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeWorkablePrimitiveMultiblockControllerTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 20700;

    private static class TestPrimitiveMultiblock extends RecipeWorkablePrimitiveMultiblockController {

        boolean formed = true;

        TestPrimitiveMultiblock(ResourceLocation id, RecipeMap<?> recipeMap) {
            super(id, recipeMap);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestPrimitiveMultiblock(metaTileEntityId, recipeMap);
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

    private static TestPrimitiveMultiblock newHost(RecipeMap<?> map) {
        MetaTileEntity mte = MetaTileEntities.registerMetaTileEntity(testId++,
                new TestPrimitiveMultiblock(
                        GTUtility.gregtechId("recipe_workable_primitive_multiblock_test_" + testId), map));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        ((MetaTileEntityHolder) holder.getHolder()).setWorld(DummyWorld.INSTANCE);
        return (TestPrimitiveMultiblock) holder;
    }

    @Test
    void findsAndCompletesARecipeWithNoRealPowerSourceAtAll() {
        // Free power (legacy PrimitiveRecipeLogic spoofs infinite energy): no energy container/hatch is wired at
        // all here, unlike every other migrated multiblock family -- the recipe must still be found and completed
        // purely from the fixed placeholder PowerSupplyProperty createConfig() advertises.
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>(
                "recipe_workable_primitive_multiblock_test_" + testId, new SimpleRecipeBuilder())
                        .itemInputs(1).itemOutputs(1).build();
        map.recipeBuilder().inputs(new ItemStack(Blocks.COBBLESTONE)).outputs(new ItemStack(Blocks.STONE))
                .duration(2).EUt(1).buildAndRegister();

        TestPrimitiveMultiblock host = newHost(map);
        host.getImportItems().insertItem(0, new ItemStack(Blocks.COBBLESTONE), false);

        // MetaTileEntity's automatic MTETrait tick loop must not drive the recipe workable directly -- only
        // updateFormedValid() should (see shouldUpdate()'s override).
        host.update();
        assertFalse(host.getWorkable().isActive(),
                "the automatic MTETrait tick loop must not tick the recipe workable directly");

        host.updateFormedValid();
        assertTrue(host.getWorkable().isActive());

        host.updateFormedValid(); // progress -> 2 == duration -> completes and outputs this same tick
        assertThat(host.getExportItems().getStackInSlot(0).getItem(), is(Item.getItemFromBlock(Blocks.STONE)));
        assertFalse(host.getWorkable().isActive());
    }

    @Test
    void itemAndFluidHandlersAreSizedFromTheRecipeMapNotFixedSlotCounts() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>(
                "recipe_workable_primitive_multiblock_test_" + testId, new SimpleRecipeBuilder())
                        .itemInputs(2).itemOutputs(3).fluidInputs(1).fluidOutputs(1).build();

        TestPrimitiveMultiblock host = newHost(map);
        assertThat(host.getImportItems().getSlots(), is(2));
        assertThat(host.getExportItems().getSlots(), is(3));
        assertThat(host.getImportFluids().getTanks(), is(1));
        assertThat(host.getExportFluids().getTanks(), is(1));
    }

    @Test
    void blocksSideFacingItemCapabilityAccess() {
        RecipeMap<SimpleRecipeBuilder> map = new RecipeMapBuilder<>(
                "recipe_workable_primitive_multiblock_test_" + testId, new SimpleRecipeBuilder())
                        .itemInputs(1).itemOutputs(1).build();

        TestPrimitiveMultiblock host = newHost(map);
        assertNull(host.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH),
                "a side-facing query must be blocked, forcing GUI-only interaction");
    }
}
