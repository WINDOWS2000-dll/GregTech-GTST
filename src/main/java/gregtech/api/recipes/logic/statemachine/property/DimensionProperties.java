package gregtech.api.recipes.logic.statemachine.property;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.recipes.logic.statemachine.property.impl.CurrentDimensionProperty;

import org.jetbrains.annotations.NotNull;

/**
 * Builds {@link CurrentDimensionProperty} from a {@link MetaTileEntity}'s own current state. GregTech's
 * {@code RecipeLogicConfig} is a plain data holder rather than a virtual method tied to the class hierarchy, so
 * there is no single chaining point analogous to a machine base class's {@code super} call; this class exists so
 * every {@code createConfig()} site that needs this property (the two base classes' own defaults, plus any machine
 * that replaces {@code config.power.properties} outright rather than adding to it &mdash; see those classes'
 * JavaDoc) can call one shared, correct implementation instead of duplicating the dimension-requirement check
 * themselves. Applied at every {@code createConfig()} site that builds a search-time {@link RecipePropertySet}, so
 * every dimension-gated recipe (Gas Collector's Nether/End air recipes, ...) actually gets checked against it.
 *
 * @see gregtech.api.recipes.logic.statemachine.lookup.bitflag.DimensionFilter
 * @see CleanroomProperties
 */
public final class DimensionProperties {

    private DimensionProperties() {}

    /** @return {@code mte}'s current dimension, for {@link CurrentDimensionProperty}. */
    @NotNull
    public static CurrentDimensionProperty of(@NotNull MetaTileEntity mte) {
        return new CurrentDimensionProperty(mte.getWorld().provider.getDimension());
    }
}
