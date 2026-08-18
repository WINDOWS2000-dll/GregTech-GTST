package gregtech.core.network.packets;

import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.network.IClientExecutor;
import gregtech.api.network.IPacket;
import gregtech.common.items.behaviors.monitorplugin.FakeGuiPluginBehavior;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Tells one specific player the network ID of the {@link gregtech.common.mui.fakegui.FakeGuiServerSession}
 * that was just created for them, so they can register a matching
 * {@link gregtech.common.mui.fakegui.FakeGuiClientSession} to receive its sync packets.
 */
public class PacketFakeGuiSession implements IPacket, IClientExecutor {

    private BlockPos screenPos;
    private int networkId;

    @SuppressWarnings("unused")
    public PacketFakeGuiSession() {}

    public PacketFakeGuiSession(BlockPos screenPos, int networkId) {
        this.screenPos = screenPos;
        this.networkId = networkId;
    }

    @Override
    public void encode(PacketBuffer buf) {
        buf.writeBlockPos(screenPos);
        buf.writeVarInt(networkId);
    }

    @Override
    public void decode(PacketBuffer buf) {
        this.screenPos = buf.readBlockPos();
        this.networkId = buf.readVarInt();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void executeClient(NetHandlerPlayClient handler) {
        TileEntity te = Minecraft.getMinecraft().world.getTileEntity(screenPos);
        if (te instanceof IGregTechTileEntity &&
                ((IGregTechTileEntity) te).getMetaTileEntity() instanceof MetaTileEntityMonitorScreen screen) {
            MonitorPluginBaseBehavior plugin = screen.plugin;
            if (plugin instanceof FakeGuiPluginBehavior fakeGui) {
                fakeGui.onSessionGranted(networkId);
            }
        }
    }
}
