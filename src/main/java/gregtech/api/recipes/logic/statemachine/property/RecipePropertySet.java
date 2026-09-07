package gregtech.api.recipes.logic.statemachine.property;

import gregtech.api.recipes.logic.statemachine.property.impl.PowerSupplyProperty;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;

/**
 * A bag of {@link RecipeSearchProperty} instances available to a {@code RecipeLookup} while searching.
 * Membership is keyed by property <i>type</i> (see
 * {@link RecipeSearchProperty}'s JavaDoc), so adding a property of a type already present replaces the old value
 * rather than growing the set &mdash; exactly the behaviour a plain {@code Set} gives for free once hashing/equality
 * are defined that way.
 * <p>
 * This is deliberately a generic, open-ended bag rather than a fixed handful of named fields: any future search
 * predicate (temperature, cleanroom, etc.) can add its own {@link RecipeSearchProperty} type without this class, or
 * any of its callers, needing to change.
 */
public final class RecipePropertySet extends ObjectOpenCustomHashSet<RecipeSearchProperty> {

    private static final Hash.Strategy<RecipeSearchProperty> TYPE_STRATEGY = new Hash.Strategy<RecipeSearchProperty>() {

        @Override
        public int hashCode(RecipeSearchProperty property) {
            return property == null ? 0 : property.propertyHash();
        }

        @Override
        public boolean equals(RecipeSearchProperty a, RecipeSearchProperty b) {
            if (a == b) return true;
            if (a == null) return b.propertyEquals(null);
            return a.propertyEquals(b);
        }
    };

    public RecipePropertySet() {
        super(TYPE_STRATEGY);
    }

    /** @return a fresh, empty property set. */
    public static RecipePropertySet empty() {
        return new RecipePropertySet();
    }

    /**
     * @return the member of this set that occupies the same slot as {@code key} (i.e. the same concrete property
     *         type), or {@code null} if this set has none.
     */
    @SuppressWarnings("unchecked")
    public <T extends RecipeSearchProperty> T getNullable(T key) {
        return (T) super.get(key);
    }

    /**
     * @return the member of this set that occupies the same slot as {@code key}, or {@code key} itself if this set
     *         has none &mdash; convenient when {@code key} is a sensible "absent" default (e.g.
     *         {@link PowerSupplyProperty#EMPTY}).
     */
    public <T extends RecipeSearchProperty> T getOrDefault(T key) {
        T found = getNullable(key);
        return found == null ? key : found;
    }
}
