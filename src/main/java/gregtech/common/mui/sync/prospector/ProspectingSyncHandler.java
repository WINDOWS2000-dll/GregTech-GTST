package gregtech.common.mui.sync.prospector;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IElectricItem;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.ore.StoneType;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.api.util.GTUtility;
import gregtech.api.util.TextFormattingUtil;
import gregtech.api.worldgen.bedrockFluids.BedrockFluidVeinHandler;
import gregtech.common.mui.widget.prospector.ProspectingTexture;
import gregtech.common.mui.widget.prospector.ProspectorMode;
import gregtech.core.network.packets.PacketProspecting;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.value.sync.SyncHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scans the world around the holding player chunk by chunk (server side) and streams the results to the client,
 * where they are collected into a {@link ProspectingTexture} and a display-name lookup for the ore list.
 */
public class ProspectingSyncHandler extends SyncHandler {

    private static final int DATA_ID = 0;
    private static final long VOLTAGE_FACTOR = 16L;

    private final EnumHand hand;
    private final int tier;
    private final int chunkRadius;
    private final ProspectorMode mode;

    private int chunkIndex;

    private final Map<String, String> ores = new LinkedHashMap<>();
    private @Nullable ProspectingTexture texture;
    private String selected = ProspectingTexture.SELECTED_ALL;

    public ProspectingSyncHandler(EnumHand hand, int tier, int chunkRadius, @NotNull ProspectorMode mode) {
        this.hand = hand;
        this.tier = tier;
        this.chunkRadius = chunkRadius;
        this.mode = mode;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        EntityPlayer player = getSyncManager().getPlayer();
        if (!player.isCreative() && !tryDrainEnergy()) {
            player.closeScreen();
            return;
        }
        scanNextChunk(player);
    }

    private boolean tryDrainEnergy() {
        IElectricItem electricItem = getHeldStack().getCapability(GregtechCapabilities.CAPABILITY_ELECTRIC_ITEM, null);
        if (electricItem == null) return false;

        long amount = GTValues.V[this.tier] / VOLTAGE_FACTOR;
        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, true) >= amount &&
                electricItem.discharge(amount, Integer.MAX_VALUE, true, false, false) >= amount;
    }

    private ItemStack getHeldStack() {
        return getSyncManager().getPlayer().getHeldItem(this.hand);
    }

    private void scanNextChunk(EntityPlayer player) {
        int chunksPerSide = this.chunkRadius * 2 - 1;
        if (this.chunkIndex >= chunksPerSide * chunksPerSide) return;

        int playerChunkX = player.chunkCoordX;
        int playerChunkZ = player.chunkCoordZ;
        int ox = this.chunkIndex % chunksPerSide - this.chunkRadius + 1;
        int oz = this.chunkIndex / chunksPerSide - this.chunkRadius + 1;

        World world = player.world;
        Chunk chunk = world.getChunk(playerChunkX + ox, playerChunkZ + oz);
        PacketProspecting packet = new PacketProspecting(playerChunkX + ox, playerChunkZ + oz, playerChunkX,
                playerChunkZ, (int) player.posX, (int) player.posZ, this.mode);

        if (this.mode == ProspectorMode.ORE) {
            scanOres(chunk, packet);
        } else if (this.mode == ProspectorMode.FLUID) {
            scanFluidVein(world, chunk, packet);
        }

        syncToClient(DATA_ID, packet::writePacketData);
        this.chunkIndex++;
    }

    private static void scanOres(Chunk chunk, PacketProspecting packet) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int columnHeight = chunk.getHeightValue(x, z);
                for (int y = 1; y < columnHeight; y++) {
                    pos.setPos(x, y, z);
                    IBlockState state = chunk.getBlockState(pos);
                    ItemStack itemBlock = GTUtility.toItem(state);
                    if (GTUtility.isOre(itemBlock)) {
                        packet.addBlock(x, y, z, getOreDictKey(itemBlock));
                    }
                }
            }
        }
    }

    /**
     * @return the ore dictionary name best representing {@code itemBlock} for display purposes, preferring a raw
     *         ore's own name over the material it processes into.
     */
    private static String getOreDictKey(ItemStack itemBlock) {
        String oreDictName = OreDictUnifier.getOreDictionaryNames(itemBlock).stream().findFirst().orElse("");
        OrePrefix prefix = OreDictUnifier.getPrefix(itemBlock);
        if (prefix == null) return oreDictName;

        for (StoneType type : StoneType.STONE_TYPE_REGISTRY) {
            if (type.processingPrefix != prefix) continue;
            if (type.shouldBeDroppedAsItem) return oreDictName;

            MaterialStack materialStack = OreDictUnifier.getMaterial(itemBlock);
            if (materialStack != null) {
                return "ore" + oreDictName.replaceFirst(prefix.name(), "");
            }
        }
        return oreDictName;
    }

    private static void scanFluidVein(World world, Chunk chunk, PacketProspecting packet) {
        BedrockFluidVeinHandler.FluidVeinWorldEntry entry = BedrockFluidVeinHandler.getFluidVeinWorldEntry(world,
                chunk.x, chunk.z);
        if (entry == null || entry.getDefinition() == null) return;

        packet.addBlock(0, 3, 0, TextFormattingUtil.formatNumbers(100.0 *
                BedrockFluidVeinHandler.getOperationsRemaining(world, chunk.x, chunk.z) /
                BedrockFluidVeinHandler.MAXIMUM_VEIN_OPERATIONS));
        packet.addBlock(0, 2, 0, String.valueOf(BedrockFluidVeinHandler.getFluidYield(world, chunk.x, chunk.z)));

        Fluid fluid = BedrockFluidVeinHandler.getFluidInChunk(world, chunk.x, chunk.z);
        if (fluid != null) {
            packet.addBlock(0, 1, 0, fluid.getName());
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) throws IOException {
        if (id != DATA_ID) return;

        PacketProspecting packet = PacketProspecting.readPacketData(buf);
        if (packet == null) return;

        if (this.texture == null) {
            this.texture = new ProspectingTexture(packet.mode, this.chunkRadius);
        }
        this.texture.updateTexture(packet);
        for (String orePrefix : packet.ores) {
            this.ores.computeIfAbsent(orePrefix, this::displayNameOf);
        }
    }

    private @Nullable String displayNameOf(String orePrefix) {
        if (this.mode == ProspectorMode.FLUID) {
            FluidStack fluidStack = FluidRegistry.getFluidStack(orePrefix, 1);
            return fluidStack == null ? null : fluidStack.getLocalizedName();
        }
        ItemStack itemStack = OreDictUnifier.get(orePrefix);
        return itemStack.isEmpty() ? null : itemStack.getDisplayName();
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {}

    @Override
    public void dispose() {
        super.dispose();
        if (this.texture != null) {
            this.texture.deleteGlTexture();
            this.texture = null;
        }
    }

    /**
     * @param key an ore dictionary name, or {@link ProspectingTexture#SELECTED_ALL}
     */
    public void select(@NotNull String key) {
        this.selected = key;
        if (this.texture != null) {
            this.texture.loadTexture(null, key);
        }
    }

    public @NotNull String getSelected() {
        return this.selected;
    }

    public @Nullable ProspectingTexture getTexture() {
        return this.texture;
    }

    public @NotNull Map<String, String> getOres() {
        return this.ores;
    }

    public int getChunkRadius() {
        return this.chunkRadius;
    }

    public @NotNull ProspectorMode getMode() {
        return this.mode;
    }
}
