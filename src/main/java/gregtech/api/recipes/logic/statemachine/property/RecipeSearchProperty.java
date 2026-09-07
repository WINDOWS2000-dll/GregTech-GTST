package gregtech.api.recipes.logic.statemachine.property;

import gregtech.api.recipes.logic.statemachine.property.impl.PowerCapacityProperty;
import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;

import org.jetbrains.annotations.Nullable;

/**
 * A single piece of context a {@code RecipeLookup} can use while searching: power availability
 * ({@link PowerSupplyProperty}/{@link PowerCapacityProperty}), temperature, cleanroom/dimension state, and so on
 * &mdash; this interface doesn't preclude future search predicates needing other kinds of context.
 * <p>
 * Implementations are held in a {@link RecipePropertySet}, which looks entries up by <i>type</i> rather than by
 * value equality: {@link #propertyHash()}/{@link #propertyEquals} should treat every instance of the same
 * concrete property type as occupying the same "slot" (see {@link PowerSupplyProperty}'s JavaDoc for why), letting
 * a set hold at most one instance of each type, replaceable via normal {@code Set} semantics.
 */
public interface RecipeSearchProperty {

    /**
     * @return a hash code shared by every instance of this property's concrete type, regardless of the values it
     *         carries (see this interface's JavaDoc on why {@link RecipePropertySet} looks properties up by type).
     */
    int propertyHash();

    /**
     * @return whether {@code other} is the same concrete property <i>type</i> as this one (not necessarily
     *         carrying the same values &mdash; see this interface's JavaDoc).
     */
    boolean propertyEquals(@Nullable RecipeSearchProperty other);
}
