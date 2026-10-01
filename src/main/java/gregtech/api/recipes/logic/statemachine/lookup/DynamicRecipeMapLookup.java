package gregtech.api.recipes.logic.statemachine.lookup;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.recipes.logic.statemachine.RecipeLookup;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

/**
 * A {@link RecipeLookup} whose target {@link RecipeMap} can change at runtime, e.g. Processing Array's "recipe map
 * of whichever machine is currently inserted into the machine hatch".
 * <p>
 * {@link RecipeLogicConfig#lookup} is itself a {@code Supplier<RecipeLookup>}, re-invoked on every search (see
 * {@code RecipeSearchOperator}), so this class is a convenience, not a structural necessity: a machine could
 * already achieve the same effect with {@code config.lookup = () -> new RecipeMapLookup(getCurrentRecipeMap())}.
 * This class exists so that pattern doesn't need to be rewritten (and a new {@link RecipeMapLookup} allocated) at
 * every call site that needs it.
 */
public final class DynamicRecipeMapLookup implements RecipeLookup {

    private final @NotNull Supplier<@Nullable RecipeMap<?>> recipeMapSupplier;

    public DynamicRecipeMapLookup(@NotNull Supplier<@Nullable RecipeMap<?>> recipeMapSupplier) {
        this.recipeMapSupplier = recipeMapSupplier;
    }

    @Override
    public @NotNull Iterator<Recipe> findRecipes(long maxVoltage, @NotNull List<ItemStack> items,
                                                 @NotNull List<FluidStack> fluids) {
        return findRecipes(maxVoltage, null, items, fluids);
    }

    // Overridden, not left to RecipeLookup's own default (which would silently drop properties and fall back to
    // the 3-arg overload above): without this override, every property-aware filter (Cleanroom/Dimension/Biome/
    // CoilTemperature/FusionStartEnergy/...) would be silently skipped for any machine using this lookup, even
    // though RecipeMapLookup itself (which this delegates to) fully supports them.
    @Override
    public @NotNull Iterator<Recipe> findRecipes(long maxVoltage, @Nullable RecipePropertySet properties,
                                                 @NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids) {
        RecipeMap<?> recipeMap = recipeMapSupplier.get();
        if (recipeMap == null) return Collections.emptyIterator();
        return new RecipeMapLookup(recipeMap).findRecipes(maxVoltage, properties, items, fluids);
    }

    @Override
    public @NotNull String diagnoseNoMatch(long maxVoltage, @Nullable RecipePropertySet properties,
                                           @NotNull List<ItemStack> items, @NotNull List<FluidStack> fluids) {
        RecipeMap<?> recipeMap = recipeMapSupplier.get();
        if (recipeMap == null) return "(no RecipeMap currently inserted)";
        return new RecipeMapLookup(recipeMap).diagnoseNoMatch(maxVoltage, properties, items, fluids);
    }
}
