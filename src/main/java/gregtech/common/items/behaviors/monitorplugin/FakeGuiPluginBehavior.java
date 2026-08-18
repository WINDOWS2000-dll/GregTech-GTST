package gregtech.common.items.behaviors.monitorplugin;

import gregtech.api.GregTechAPI;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.items.behavior.MonitorPluginBaseBehavior;
import gregtech.api.items.behavior.ProxyHolderPluginBehavior;
import gregtech.api.items.toolitem.ToolClasses;
import gregtech.api.items.toolitem.ToolHelper;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.mui.GTGuiTextures;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.util.GTLog;
import gregtech.common.metatileentities.multi.electric.centralmonitor.MetaTileEntityMonitorScreen;
import gregtech.common.mui.fakegui.FakeGuiClientSession;
import gregtech.common.mui.fakegui.FakeGuiServerSession;
import gregtech.core.network.packets.PacketFakeGuiSession;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ButtonWidget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mirrors another machine's real MUI2 GUI onto the monitor screen it's plugged into, live: sneak-clicking the
 * mirror relays the click into it, and a plain right-click opens the real machine's GUI directly.
 * <p>
 * Every viewing player gets their own {@link FakeGuiServerSession}/{@link FakeGuiClientSession} pair, built by
 * simply calling the target's own {@code buildUI(...)} and registering the resulting sync manager with MUI2's real
 * network layer - without ever making Minecraft think that player's GUI is actually open. See those classes for
 * how.
 */
public class FakeGuiPluginBehavior extends ProxyHolderPluginBehavior {

    private int partIndex;

    // run-time
    private BlockPos partPos;
    private final Map<UUID, FakeGuiServerSession> serverSessions = new HashMap<>();
    @SideOnly(Side.CLIENT)
    private FakeGuiClientSession clientSession;

    public void setConfig(int partIndex) {
        if (this.partIndex == partIndex || partIndex < 0) return;
        this.partIndex = partIndex;
        this.partPos = null;
        disposeServerSessions();
        writePluginData(GregtechDataCodes.UPDATE_PLUGIN_CONFIG, buffer -> buffer.writeVarInt(this.partIndex));
        markAsDirty();
    }

    public MetaTileEntity getRealMTE() {
        if (this.holder == null) return null; // no proxied target bound (e.g. mode isn't PROXY, or no cover picked)
        MetaTileEntity target = this.holder.getMetaTileEntity();
        if (target instanceof MultiblockControllerBase multi && partIndex > 0) {
            if (partPos != null) {
                TileEntity entity = this.screen.getWorld().getTileEntity(partPos);
                if (entity instanceof IGregTechTileEntity) {
                    return ((IGregTechTileEntity) entity).getMetaTileEntity();
                } else {
                    partPos = null;
                    return null;
                }
            }
            PatternMatchContext context = multi.structurePattern.checkPatternFastAt(
                    target.getWorld(), target.getPos(), target.getFrontFacing().getOpposite(), multi.getUpwardsFacing(),
                    multi.allowsFlip());
            if (context == null) {
                return null;
            }
            Set<IMultiblockPart> rawPartsSet = context.getOrCreate("MultiblockParts", HashSet::new);
            List<IMultiblockPart> parts = new ArrayList<>(rawPartsSet);
            parts.sort(Comparator.comparing((it) -> ((MetaTileEntity) it).getPos().hashCode()));
            if (parts.size() > partIndex - 1 && parts.get(partIndex - 1) instanceof MetaTileEntity) {
                target = (MetaTileEntity) parts.get(partIndex - 1);
                partPos = target.getPos();
            } else {
                return null;
            }
        }
        return target;
    }

    /** Called client-side once the server has granted us a session to mirror the current target. */
    @SideOnly(Side.CLIENT)
    public void onSessionGranted(int networkId) {
        MetaTileEntity target = getRealMTE();
        if (target == null) return; // stale reply, target already changed again
        disposeClientSession();
        try {
            clientSession = new FakeGuiClientSession(target, networkId);
        } catch (Exception e) {
            GTLog.logger.error("Could not build FakeGui mirror session for {}", target, e);
        }
    }

    private void disposeServerSessions() {
        if (!serverSessions.isEmpty()) {
            serverSessions.values().forEach(FakeGuiServerSession::dispose);
            serverSessions.clear();
        }
    }

    @SideOnly(Side.CLIENT)
    private void disposeClientSession() {
        if (clientSession != null) {
            clientSession.dispose();
            clientSession = null;
        }
    }

    @Override
    public void readPluginAction(EntityPlayerMP player, int id, PacketBuffer buf) {
        if (id == GregtechDataCodes.ACTION_PLUGIN_CONFIG) {
            FakeGuiServerSession old = serverSessions.remove(player.getUniqueID());
            if (old != null) old.dispose();
            MetaTileEntity target = getRealMTE();
            if (target != null) {
                try {
                    FakeGuiServerSession session = new FakeGuiServerSession(target, player);
                    serverSessions.put(player.getUniqueID(), session);
                    GregTechAPI.networkHandler.sendTo(new PacketFakeGuiSession(this.screen.getPos(),
                            session.getNetworkId()), player);
                } catch (Exception e) {
                    GTLog.logger.error("Could not build FakeGui mirror session for {}", target, e);
                }
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setInteger("part", partIndex);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        partIndex = data.hasKey("part") ? data.getInteger("part") : 0;
    }

    @Override
    public void onHolderChanged(IGregTechTileEntity lastHolder) {
        disposeServerSessions();
        if (this.screen.getWorld() != null && this.screen.getWorld().isRemote) {
            disposeClientSession();
        }
    }

    @Override
    public void onMonitorValid(MetaTileEntityMonitorScreen screen, boolean valid) {
        super.onMonitorValid(screen, valid);
        if (!valid) {
            disposeServerSessions();
            if (screen != null && screen.getWorld() != null && screen.getWorld().isRemote) {
                disposeClientSession();
            }
        }
    }

    @Override
    public void update() {
        super.update();
        MetaTileEntity target = getRealMTE();
        if (this.screen.getWorld().isRemote) {
            if (target == null) {
                disposeClientSession();
            } else if (clientSession == null && this.screen.getOffsetTimer() % 20 == 0) {
                writePluginAction(GregtechDataCodes.ACTION_PLUGIN_CONFIG, buffer -> {});
            }
        } else if (target == null) {
            disposeServerSessions();
        } else {
            serverSessions.entrySet().removeIf(entry -> {
                EntityPlayerMP viewer = entry.getValue().getViewer();
                if (!viewer.world.playerEntities.contains(viewer)) {
                    entry.getValue().dispose();
                    return true;
                }
                entry.getValue().tick();
                return false;
            });
        }
    }

    @Override
    public MonitorPluginBaseBehavior createPlugin() {
        return new FakeGuiPluginBehavior();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderPlugin(float partialTicks, RayTraceResult rayTraceResult) {
        if (clientSession == null) return;
        double[] result = this.screen.checkLookingAt(rayTraceResult);
        if (result == null) {
            clientSession.render(0, 0, partialTicks);
        } else {
            clientSession.render(result[0], result[1], partialTicks);
        }
    }

    @Override
    public boolean onClickLogic(EntityPlayer playerIn, EnumHand hand, EnumFacing facing, boolean isRight, double x,
                                double y) {
        MetaTileEntity mte = getRealMTE();
        if (mte == null || ToolHelper.isTool(playerIn.getHeldItemMainhand(), ToolClasses.SCREWDRIVER)) return false;
        if (!this.screen.getWorld().isRemote) {
            // Server: a plain (non-sneak) right-click is a shortcut that opens the real machine's own GUI directly.
            // Sneak-clicks are relayed by the clicking player's own client below and don't need anything here.
            if (!playerIn.isSneaking()) {
                return isRight && mte.onRightClick(playerIn, hand, facing, null);
            }
            return true;
        }
        if (playerIn.isSneaking() && clientSession != null) {
            clientSession.click(x, y, isRight ? 1 : 0);
        }
        return true;
    }

    @Override
    public void readPluginData(int id, PacketBuffer buf) {
        if (id == GregtechDataCodes.UPDATE_PLUGIN_CONFIG) {
            this.partIndex = buf.readVarInt();
            this.partPos = null;
            disposeClientSession();
        }
    }

    @Override
    public IWidget customUI(PanelSyncManager syncManager) {
        ParentWidget<?> panel = new ParentWidget<>();
        panel.child(IKey.str("Part:").asWidget().pos(20, 20));
        panel.child(new ButtonWidget<>().pos(55, 15).size(20, 20).overlay(IKey.str("-1"))
                .onMousePressed(mouseButton -> {
                    setConfig(this.partIndex - 1);
                    return true;
                }));
        panel.child(new ButtonWidget<>().pos(135, 15).size(20, 20).overlay(IKey.str("+1"))
                .onMousePressed(mouseButton -> {
                    setConfig(this.partIndex + 1);
                    return true;
                }));
        panel.child(IKey.dynamic(() -> Integer.toString(this.partIndex))
                .asWidget().pos(75, 15).size(60, 20).background(GTGuiTextures.DISPLAY));
        return panel;
    }
}
