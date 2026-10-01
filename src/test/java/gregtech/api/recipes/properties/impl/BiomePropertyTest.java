package gregtech.api.recipes.properties.impl;

import gregtech.Bootstrap;
import gregtech.api.recipes.properties.impl.BiomeProperty.BiomePropertyList;

import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class BiomePropertyTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static Biome plains() {
        return ForgeRegistries.BIOMES.getValue(new ResourceLocation("plains"));
    }

    private static Biome desert() {
        return ForgeRegistries.BIOMES.getValue(new ResourceLocation("desert"));
    }

    @Test
    void aBiomeOnTheWhitelistPasses() {
        BiomePropertyList list = new BiomePropertyList();
        list.add(plains(), false);

        assertThat(list.checkBiome(plains()), is(true));
    }

    @Test
    void aBiomeNotOnTheWhitelistFails() {
        BiomePropertyList list = new BiomePropertyList();
        list.add(plains(), false);

        assertThat(list.checkBiome(desert()), is(false));
    }

    @Test
    void aBiomeOnTheBlacklistFailsEvenIfAlsoRequestedAsWhitelisted() {
        BiomePropertyList list = new BiomePropertyList();
        list.add(plains(), false);
        list.add(plains(), true);
        assertThat(list.checkBiome(plains()), is(false));
    }

    @Test
    void mergeCombinesBothListsWhitelistAndBlacklist() {
        BiomePropertyList a = new BiomePropertyList();
        a.add(plains(), false);
        BiomePropertyList b = new BiomePropertyList();
        b.add(desert(), true);

        a.merge(b);

        assertThat(a.whiteListBiomes.contains(plains()), is(true));
        assertThat(a.blackListBiomes.contains(desert()), is(true));
    }

    @Test
    void anEmptyListAcceptsNothing() {
        // mirrors DimensionPropertyList's own contract: a recipe declaring this property with no whitelist entries
        // is unusable everywhere, since "no restriction at all" is expressed by not declaring the property.
        BiomePropertyList list = new BiomePropertyList();

        assertThat(list.checkBiome(plains()), is(false));
    }
}
