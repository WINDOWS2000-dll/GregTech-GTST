package gregtech.api.recipes.logic.statemachine.progress;

import gregtech.Bootstrap;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.logic.statemachine.ActiveRecipeList;
import gregtech.api.recipes.logic.statemachine.RecipeFinalizer;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class RecipeOutputOperatorTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static RecipeLogicConfig newConfig() {
        return new RecipeLogicConfig(() -> (maxVoltage, items, fluids) -> Collections.<Recipe>emptyList().iterator());
    }

    @Test
    void deliversOnlyNonEmptyOutputLists() {
        RecipeLogicConfig config = newConfig();
        List<List<ItemStack>> itemCalls = new ObjectArrayList<>();
        List<List<FluidStack>> fluidCalls = new ObjectArrayList<>();
        config.io.itemOutput = itemCalls::add;
        config.io.fluidOutput = fluidCalls::add;

        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        ActiveRecipeList.setItemsOut(entry, Collections.singletonList(new ItemStack(Items.APPLE, 3)));
        // no fluids set - ENTRY_FLUIDS_OUT_KEY absent means an empty list, not a call
        data.setTag(ActiveRecipeList.SELECTED_KEY, entry);

        new RecipeOutputOperator(config).operate(data);

        assertThat(itemCalls.size(), is(1));
        assertThat(itemCalls.get(0).get(0).getItem(), is(Items.APPLE));
        assertThat(fluidCalls.size(), is(0));
    }

    @Test
    void deliversFluidOutputsWhenPresent() {
        RecipeLogicConfig config = newConfig();
        List<List<FluidStack>> fluidCalls = new ObjectArrayList<>();
        config.io.fluidOutput = fluidCalls::add;

        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        FluidStack water = new FluidStack(FluidRegistry.WATER, 1000);
        ActiveRecipeList.setFluidsOut(entry, Collections.singletonList(water));
        data.setTag(ActiveRecipeList.SELECTED_KEY, entry);

        new RecipeOutputOperator(config).operate(data);

        assertThat(fluidCalls.size(), is(1));
        assertThat(fluidCalls.get(0).get(0).amount, is(1000));
    }

    @Test
    void recipeFinalizerTransformsOutputsBeforeDeliveryAndWritesBackToTheEntry() {
        RecipeLogicConfig config = newConfig();
        List<List<ItemStack>> itemCalls = new ObjectArrayList<>();
        config.io.itemOutput = itemCalls::add;
        config.hooks.recipeFinalizer = (entry, itemsOut, fluidsOut) -> {
            ItemStack doubled = itemsOut.get(0).copy();
            doubled.setCount(doubled.getCount() * 2);
            return new RecipeFinalizer.Result(Collections.singletonList(doubled), fluidsOut);
        };

        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound entry = new NBTTagCompound();
        ActiveRecipeList.setItemsOut(entry, Collections.singletonList(new ItemStack(Items.APPLE, 3)));
        data.setTag(ActiveRecipeList.SELECTED_KEY, entry);

        new RecipeOutputOperator(config).operate(data);

        assertThat(itemCalls.get(0).get(0).getCount(), is(6));
        // the entry itself must reflect the finalized amount too, since onRecipeCompleted reads the same entry
        assertThat(ActiveRecipeList.itemsOut(entry).get(0).getCount(), is(6));
    }
}
