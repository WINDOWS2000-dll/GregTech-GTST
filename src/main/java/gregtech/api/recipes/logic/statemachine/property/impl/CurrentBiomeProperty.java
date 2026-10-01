package gregtech.api.recipes.logic.statemachine.property.impl;

import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.logic.statemachine.property.RecipeSearchProperty;

import net.minecraft.world.biome.Biome;

import com.github.bsideup.jabel.Desugar;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link Biome} a machine is currently in. Paired with
 * {@code gregtech.api.recipes.properties.impl.BiomeProperty} (a recipe's own biome whitelist/blacklist) via
 * {@code gregtech.api.recipes.logic.statemachine.lookup.bitflag.BiomeFilter}.
 */
@Desugar
public record CurrentBiomeProperty(Biome biome) implements RecipeSearchProperty {

    /** A sentinel for {@link RecipePropertySet} type-lookups; {@link #biome()} is never read off of this instance. */
    public static final CurrentBiomeProperty EMPTY = new CurrentBiomeProperty(null);

    @Override
    public int propertyHash() {
        return 135;
    }

    @Override
    public boolean propertyEquals(@Nullable RecipeSearchProperty other) {
        return other instanceof CurrentBiomeProperty;
    }
}
