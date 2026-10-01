package gregtech.api.recipes.logic.statemachine.lookup.bitflag;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.impl.CurrentBiomeProperty;
import gregtech.api.recipes.properties.RecipeProperty;
import gregtech.api.recipes.properties.impl.BiomeProperty;
import gregtech.api.recipes.properties.impl.BiomeProperty.BiomePropertyList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Bridges {@link BiomeProperty} (a recipe's own biome whitelist/blacklist) to a location-aware machine's
 * {@link BitflagRecipeLookup} via {@link CurrentBiomeProperty}. A recipe passes iff its own
 * {@link BiomePropertyList} accepts the machine's current biome.
 * <p>
 * Register with {@code lookup.registerFilter(BiomeFilter.INSTANCE)} on any {@link BitflagRecipeLookup} whose
 * {@code RecipeMap} has biome-gated recipes.
 */
public final class BiomeFilter implements RecipePredicateFilter<BiomePropertyList> {

    public static final BiomeFilter INSTANCE = new BiomeFilter();

    private BiomeFilter() {}

    @Override
    public @NotNull RecipeProperty<BiomePropertyList> recipeProperty() {
        return BiomeProperty.getInstance();
    }

    @Override
    public @Nullable Predicate<BiomePropertyList> extractQuery(@NotNull RecipePropertySet properties) {
        CurrentBiomeProperty current = properties.getNullable(CurrentBiomeProperty.EMPTY);
        if (current == null) return null;
        var biome = current.biome();
        return list -> list.checkBiome(biome);
    }
}
