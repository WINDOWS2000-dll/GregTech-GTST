package gregtech.common.pipelike.laser.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.PipeNetTraceLog;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.common.pipelike.laser.LaserPipeProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class LaserPipeNet extends PipeNet<LaserPipeProperties> {

    private final Long2ObjectMap<LaserRoutePath> netData = new Long2ObjectOpenHashMap<>();

    public LaserPipeNet(WorldPipeNet<LaserPipeProperties, ? extends PipeNet<LaserPipeProperties>> world) {
        super(world);
    }

    @Nullable
    public LaserRoutePath getNetData(BlockPos pipePos, EnumFacing facing) {
        long encoded = pipePos.toLong();
        if (netData.containsKey(encoded)) {
            if (isTraceEnabled()) getTraceStats().recordCacheHit();
            return netData.get(encoded);
        }
        boolean traced = isTraceEnabled();
        if (traced) getTraceStats().recordCacheMiss();
        long start = traced ? System.nanoTime() : 0;
        LaserRoutePath data = LaserNetWalker.createNetData(getWorldData(), pipePos, facing);
        if (traced) {
            int nodesVisited = (data != null && data != LaserNetWalker.FAILED_MARKER) ? data.getDistance() : 0;
            getTraceStats().recordWalkerTraversal(System.nanoTime() - start, nodesVisited);
            PipeNetTraceLog.log(getTraceLabel(),
                    "getNetData(" + pipePos + "," + facing + "): cache miss, walker traversal");
        }
        if (data == LaserNetWalker.FAILED_MARKER) {
            // walker failed, don't cache, so it tries again on next insertion
            return null;
        }
        netData.put(encoded, data);
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        netData.clear();
    }

    @Override
    public void onPipeConnectionsUpdate(BlockPos pos) {
        invalidateRoutesThrough(pos);
    }

    @Override
    public void onChunkUnload(BlockPos pos) {
        invalidateRoutesThrough(pos);
    }

    private void invalidateRoutesThrough(BlockPos pos) {
        netData.values().removeIf(route -> route == null || route.passesThrough(pos));
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<LaserPipeProperties>> transferredNodes,
                                    PipeNet<LaserPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        netData.clear();
        ((LaserPipeNet) parentNet).netData.clear();
    }

    @Override
    protected void writeNodeData(LaserPipeProperties nodeData, NBTTagCompound tagCompound) {}

    @Override
    protected LaserPipeProperties readNodeData(NBTTagCompound tagCompound) {
        return LaserPipeProperties.INSTANCE;
    }
}
