package gregtech.api.recipes.logic.statemachine.property.impl;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.Nullable;

/**
 * How much power a machine can currently accept on its output side: up to {@link #amperage()} amps at
 * {@link #voltage()} volts. Used when searching for
 * generating recipes, the mirror image of {@link PowerSupplyProperty} used for consuming recipes &mdash; see that
 * class's JavaDoc for why the two coexist independently in the same {@link RecipePropertySet}.
 */
@Desugar
public record PowerCapacityProperty(long voltage, long amperage) implements RecipeSearchProperty {

    public static final PowerCapacityProperty EMPTY = new PowerCapacityProperty(0, 0);

    /** @return the total EU/t this side can accept, i.e. {@link #voltage()} {@code * }{@link #amperage()}. */
    public long getEUt() {
        return voltage * amperage;
    }

    @Override
    public int propertyHash() {
        return 129;
    }

    @Override
    public boolean propertyEquals(@Nullable RecipeSearchProperty other) {
        return other instanceof PowerCapacityProperty;
    }
}
