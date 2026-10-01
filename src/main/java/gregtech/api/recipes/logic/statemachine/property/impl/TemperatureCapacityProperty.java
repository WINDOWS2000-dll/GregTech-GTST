package gregtech.api.recipes.logic.statemachine.property.impl;

import gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.Nullable;

/**
 * How hot a machine's current heating coils can run, in Kelvin. Paired with
 * {@code gregtech.api.recipes.properties.impl.TemperatureProperty}
 * (a recipe's own minimum required temperature) via
 * {@code gregtech.api.recipes.logic.statemachine.lookup.bitflag.CoilTemperatureFilter}.
 */
@Desugar
public record TemperatureCapacityProperty(int temperature) implements RecipeSearchProperty {

    public static final TemperatureCapacityProperty EMPTY = new TemperatureCapacityProperty(0);

    @Override
    public int propertyHash() {
        return 130;
    }

    @Override
    public boolean propertyEquals(@Nullable RecipeSearchProperty other) {
        return other instanceof TemperatureCapacityProperty;
    }
}
