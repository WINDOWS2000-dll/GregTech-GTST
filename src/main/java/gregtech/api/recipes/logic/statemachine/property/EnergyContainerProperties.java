package gregtech.api.recipes.logic.statemachine.property;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerCapacityProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;

/**
 * Builds a {@link RecipePropertySet} describing a machine's current power situation from its
 * {@link IEnergyContainer}, so {@code RecipeLogicConfig.power.properties} suppliers don't each need to know how to
 * translate container state into {@link PowerSupplyProperty}/{@link PowerCapacityProperty} themselves.
 */
public final class EnergyContainerProperties {

    private EnergyContainerProperties() {}

    /**
     * @return a fresh {@link RecipePropertySet} holding both a {@link PowerSupplyProperty} (from the container's
     *         input side, for consuming recipes) and a {@link PowerCapacityProperty} (from its output side, for
     *         generating recipes) &mdash; a machine that only ever does one or the other simply reports zero on
     *         the unused side, which is harmless since a recipe search only ever consults the property matching
     *         its own {@link gregtech.api.recipes.Recipe#isGenerating()}.
     */
    public static RecipePropertySet of(IEnergyContainer container) {
        RecipePropertySet properties = RecipePropertySet.empty();
        properties.add(new PowerSupplyProperty(container.getInputVoltage(), container.getInputAmperage()));
        properties.add(new PowerCapacityProperty(container.getOutputVoltage(), container.getOutputAmperage()));
        return properties;
    }
}
