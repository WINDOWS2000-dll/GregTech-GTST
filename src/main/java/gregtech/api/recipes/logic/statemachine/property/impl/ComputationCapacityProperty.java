package gregtech.api.recipes.logic.statemachine.property.impl;

import gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.Nullable;

/**
 * How much computation, in CWU/t, a machine can currently supply.
 * Paired with {@code gregtech.api.recipes.properties.impl.ComputationProperty} (a recipe's own required CWU/t) via
 * {@code gregtech.api.recipes.logic.statemachine.lookup.bitflag.ComputationFilter}.
 */
@Desugar
public record ComputationCapacityProperty(int cwut) implements RecipeSearchProperty {

    public static final ComputationCapacityProperty EMPTY = new ComputationCapacityProperty(0);

    @Override
    public int propertyHash() {
        return 131;
    }

    @Override
    public boolean propertyEquals(@Nullable RecipeSearchProperty other) {
        return other instanceof ComputationCapacityProperty;
    }
}
