package gregtech.api.recipes.logic.statemachine;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collections;
import java.util.List;

/**
 * One independent item/fluid pool a {@link RecipeIOConfig#distinctInputGroups}-enabled logic searches separately
 * from every other group ("distinct bus" mode: a multiblock whose input buses each feed a separate recipe search,
 * rather than pooling all their contents together). Typically one instance per input bus, paired with whatever
 * fluid tank that bus shares a recipe with, e.g. one auto-output-capable input bus plus its adjacent input hatch.
 * <p>
 * Instances are read fresh on every search pass (see {@link RecipeIOConfig#distinctInputGroups}); there is no
 * requirement that the same instance be reused across ticks, only that {@code distinctInputGroups.get()} returns
 * groups in a <b>stable order</b> from one pass to the next (whatever a group's index in that list is, on one tick,
 * it must mean the same physical group on the next) &mdash; {@link PreparedRecipeQueue}'s reservation bookkeeping
 * identifies a group by that index, not by instance identity, since NBT can't persist Java object references
 * across ticks.
 */
public interface DistinctInputGroup {

    /** @return this group's item inputs to search recipes against. Empty (not {@code null}) if it has none. */
    @NotNull
    @UnmodifiableView
    List<ItemStack> items();

    /** @return this group's fluid inputs to search recipes against. Empty (not {@code null}) if it has none. */
    @NotNull
    @UnmodifiableView
    List<FluidStack> fluids();

    /**
     * Convenience factory for the common case of a group backed by plain, already-flattened snapshots (e.g. via
     * {@link gregtech.api.util.GTUtility#itemHandlerToList}).
     */
    static @NotNull DistinctInputGroup of(@Nullable List<ItemStack> items, @Nullable List<FluidStack> fluids) {
        List<ItemStack> itemsFinal = items == null ? Collections.emptyList() : items;
        List<FluidStack> fluidsFinal = fluids == null ? Collections.emptyList() : fluids;
        return new DistinctInputGroup() {

            @Override
            public @NotNull List<ItemStack> items() {
                return itemsFinal;
            }

            @Override
            public @NotNull List<FluidStack> fluids() {
                return fluidsFinal;
            }
        };
    }
}
