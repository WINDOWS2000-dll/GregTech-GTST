package gregtech.api.recipes.logic.statemachine.property.impl;

import gregtech.api.metatileentity.multiblock.CleanroomType;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Whether a machine's current environment satisfies a given {@link CleanroomType} requirement. A predicate rather
 * than a single value since "does my current
 * cleanroom satisfy strictness X" isn't a simple threshold comparison &mdash; {@link CleanroomType} isn't linearly
 * ordered the way voltage or temperature are (see {@code gregtech.api.metatileentity.multiblock.CleanroomType} for
 * why: some types are unrelated to each other rather than strictly stronger/weaker).
 * <p>
 * Paired with {@code gregtech.api.recipes.properties.impl.CleanroomProperty} (a recipe's own required
 * {@link CleanroomType}) via {@code gregtech.api.recipes.logic.statemachine.lookup.bitflag.CleanroomFilter}.
 */
@Desugar
public record CleanroomFulfillmentProperty(Predicate<CleanroomType> fulfillment) implements RecipeSearchProperty {

    /**
     * A sentinel for {@link RecipePropertySet} type-lookups; {@link #fulfillment()} is never read off of this instance.
     */
    public static final CleanroomFulfillmentProperty EMPTY = new CleanroomFulfillmentProperty(type -> false);

    @Override
    public int propertyHash() {
        return 133;
    }

    @Override
    public boolean propertyEquals(@Nullable RecipeSearchProperty other) {
        return other instanceof CleanroomFulfillmentProperty;
    }
}
