package gregtech.api.recipes.logic.statemachine.property.impl;

import gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.Nullable;

/**
 * How much EU a machine currently has available to spend starting a fusion reaction.
 * Paired with {@code gregtech.api.recipes.properties.impl.FusionEUToStartProperty} (a
 * recipe's own minimum required starting EU) via
 * {@code gregtech.api.recipes.logic.statemachine.lookup.bitflag.FusionStartEnergyFilter}.
 */
@Desugar
public record FusionStartCapacityProperty(long euAvailable) implements RecipeSearchProperty {

    public static final FusionStartCapacityProperty EMPTY = new FusionStartCapacityProperty(0);

    @Override
    public int propertyHash() {
        return 132;
    }

    @Override
    public boolean propertyEquals(@Nullable RecipeSearchProperty other) {
        return other instanceof FusionStartCapacityProperty;
    }
}
