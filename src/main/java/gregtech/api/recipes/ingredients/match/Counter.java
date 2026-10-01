package gregtech.api.recipes.ingredients.match;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Reads/writes the "how much" of a matchable value: {@code ItemStack}'s
 * {@link net.minecraft.item.ItemStack#getCount()}/count, {@code FluidStack}'s amount, or anything else with a
 * quantity {@link IngredientMatchHelper} needs to reason about generically.
 */
public interface Counter<T> {

    /** @return how much of {@code value} there is. */
    long count(@NotNull T value);

    /** @return a copy of {@code value} with its quantity set to exactly {@code count}. */
    @Contract("_, _ -> new")
    T withCount(T value, long count);
}
