package gregtech.api.recipes.properties.impl;

import gregtech.api.GregTechAPI;
import gregtech.api.recipes.properties.RecipeProperty;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * A recipe's biome whitelist/blacklist requirement. Mirrors {@link DimensionProperty}'s whitelist/blacklist shape,
 * since the two are conceptually the same kind of "is my current location acceptable" check.
 * <p>
 * Paired with {@code gregtech.api.recipes.logic.statemachine.property.impl.CurrentBiomeProperty} (a machine's current
 * biome) via {@code gregtech.api.recipes.logic.statemachine.lookup.bitflag.BiomeFilter}.
 */
public final class BiomeProperty extends RecipeProperty<BiomeProperty.BiomePropertyList> {

    public static final String KEY = "biome";

    private static BiomeProperty INSTANCE;

    private BiomeProperty() {
        super(KEY, BiomePropertyList.class);
    }

    public static BiomeProperty getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BiomeProperty();
            GregTechAPI.RECIPE_PROPERTIES.register(KEY, INSTANCE);
        }
        return INSTANCE;
    }

    @Override
    public @NotNull NBTBase serialize(@NotNull Object value) {
        BiomePropertyList list = castValue(value);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("whiteListBiomes", String.join(";", list.registryNames(list.whiteListBiomes)));
        tag.setString("blackListBiomes", String.join(";", list.registryNames(list.blackListBiomes)));
        return tag;
    }

    @Override
    public @NotNull Object deserialize(@NotNull NBTBase nbt) {
        NBTTagCompound tag = (NBTTagCompound) nbt;
        BiomePropertyList list = new BiomePropertyList();
        for (String name : split(tag.getString("whiteListBiomes"))) {
            Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(name));
            if (biome != null) list.add(biome, false);
        }
        for (String name : split(tag.getString("blackListBiomes"))) {
            Biome biome = ForgeRegistries.BIOMES.getValue(new ResourceLocation(name));
            if (biome != null) list.add(biome, true);
        }
        return list;
    }

    private static String @NotNull [] split(@NotNull String joined) {
        return joined.isEmpty() ? new String[0] : joined.split(";");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void drawInfo(@NotNull Minecraft minecraft, int x, int y, int color, Object value) {
        BiomePropertyList list = castValue(value);

        if (!list.whiteListBiomes.isEmpty()) {
            minecraft.fontRenderer.drawString(
                    I18n.format("gregtech.recipe.biomes", getBiomeNames(list.whiteListBiomes)), x, y, color);
        }
        if (!list.blackListBiomes.isEmpty()) {
            minecraft.fontRenderer.drawString(
                    I18n.format("gregtech.recipe.biomes_blocked", getBiomeNames(list.blackListBiomes)), x, y, color);
        }
    }

    @SideOnly(Side.CLIENT)
    private static String getBiomeNames(@NotNull List<Biome> biomes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < biomes.size(); i++) {
            builder.append(biomes.get(i).getBiomeName());
            if (i != biomes.size() - 1) builder.append(", ");
        }
        String str = builder.toString();
        return str.length() >= 13 ? str.substring(0, 10) + ".." : str;
    }

    /** As {@link DimensionProperty.DimensionPropertyList}, but for {@link Biome}s rather than dimension IDs. */
    public static class BiomePropertyList {

        public static final BiomePropertyList EMPTY_LIST = new BiomePropertyList();

        public final List<Biome> whiteListBiomes = new ObjectArrayList<>();
        public final List<Biome> blackListBiomes = new ObjectArrayList<>();

        public void add(@NotNull Biome biome, boolean toBlacklist) {
            if (toBlacklist) {
                blackListBiomes.add(biome);
                whiteListBiomes.remove(biome);
            } else {
                whiteListBiomes.add(biome);
                blackListBiomes.remove(biome);
            }
        }

        public void merge(@NotNull BiomePropertyList list) {
            this.whiteListBiomes.addAll(list.whiteListBiomes);
            this.blackListBiomes.addAll(list.blackListBiomes);
        }

        public boolean checkBiome(@NotNull Biome biome) {
            return !blackListBiomes.contains(biome) && whiteListBiomes.contains(biome);
        }

        private List<String> registryNames(@NotNull List<Biome> biomes) {
            Set<String> names = new ObjectOpenHashSet<>();
            for (Biome biome : biomes) {
                ResourceLocation key = ForgeRegistries.BIOMES.getKey(biome);
                if (key != null) names.add(key.toString());
            }
            return new ObjectArrayList<>(names);
        }
    }
}
