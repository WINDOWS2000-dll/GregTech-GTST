package gregtech.api.recipes.logic.statemachine.property.impl;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty;

import org.jetbrains.annotations.Nullable;

import com.github.bsideup.jabel.Desugar;

/**
 * How much power a machine can currently draw from its input side: up to {@link #amperage()} amps at
 * {@link #voltage()} volts.
 * <p>
 * Only one {@code PowerSupplyProperty} can occupy a given {@link RecipePropertySet} at a time &mdash;
 * {@link #propertyEquals}/{@link #propertyHash()} key off the type alone, not the values &mdash; because a machine
 * only has one input power budget per search. A machine that can also generate uses {@link PowerCapacityProperty}
 * for its output side; the two coexist in the same set without conflict since they're different types.
 */
@Desugar
public record PowerSupplyProperty(long voltage, long amperage) implements RecipeSearchProperty {

    public static final PowerSupplyProperty EMPTY = new PowerSupplyProperty(0, 0);

    /** @return the total EU/t this supply can deliver, i.e. {@link #voltage()} {@code * }{@link #amperage()}. */
    public long getEUt() {
        return voltage * amperage;
    }

    @Override
    public int propertyHash() {
        return 128;
    }

    @Override
    public boolean propertyEquals(@Nullable RecipeSearchProperty other) {
        return other instanceof PowerSupplyProperty;
    }
}
