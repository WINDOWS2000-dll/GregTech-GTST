package gregtech.api.recipes.logic.statemachine;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class DistinctInputGroupTest {

    @Test
    void ofExposesTheGivenItemsAndFluids() {
        DistinctInputGroup group = DistinctInputGroup.of(Collections.singletonList(new ItemStack(Items.IRON_INGOT)),
                Collections.singletonList(new FluidStack(FluidRegistry.WATER, 100)));

        assertThat(group.items().get(0).getItem(), is(Items.IRON_INGOT));
        assertThat(group.fluids().get(0).amount, is(100));
    }

    @Test
    void ofDefaultsNullListsToEmpty() {
        DistinctInputGroup group = DistinctInputGroup.of(null, null);

        assertThat(group.items().isEmpty(), is(true));
        assertThat(group.fluids().isEmpty(), is(true));
    }
}
