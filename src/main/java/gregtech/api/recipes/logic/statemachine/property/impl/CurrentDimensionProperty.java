package gregtech.api.recipes.logic.statemachine.property.impl;

import gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty;

import org.jetbrains.annotations.Nullable;

import com.github.bsideup.jabel.Desugar;

/**
 * The dimension ID a machine is currently in. Paired
 * with {@code gregtech.api.recipes.properties.impl.DimensionProperty} (a recipe's own dimension whitelist/blacklist)
 * via {@code gregtech.api.recipes.logic.statemachine.lookup.bitflag.DimensionFilter}.
 */
@Desugar
public record CurrentDimensionProperty(int dimension) implements RecipeSearchProperty {

    public static final CurrentDimensionProperty EMPTY = new CurrentDimensionProperty(0);

    @Override
    public int propertyHash() {
        return 134;
    }

    @Override
    public boolean propertyEquals(@Nullable RecipeSearchProperty other) {
        return other instanceof CurrentDimensionProperty;
    }
}
