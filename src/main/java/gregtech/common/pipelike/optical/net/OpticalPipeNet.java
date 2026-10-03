package gregtech.common.pipelike.optical.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.PipeNetTraceLog;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.common.pipelike.optical.OpticalPipeProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class OpticalPipeNet extends PipeNet<OpticalPipeProperties> {

    private final Long2ObjectMap<OpticalRoutePath> NET_DATA = new Long2ObjectOpenHashMap<>();

    public OpticalPipeNet(WorldPipeNet<OpticalPipeProperties, ? extends PipeNet<OpticalPipeProperties>> world) {
        super(world);
    }

    @Nullable
    public OpticalRoutePath getNetData(BlockPos pipePos, EnumFacing facing) {
        long encoded = pipePos.toLong();
        if (NET_DATA.containsKey(encoded)) {
            if (isTraceEnabled()) getTraceStats().recordCacheHit();
            return NET_DATA.get(encoded);
        }
        boolean traced = isTraceEnabled();
        if (traced) getTraceStats().recordCacheMiss();
        long start = traced ? System.nanoTime() : 0;
        OpticalRoutePath data = OpticalNetWalker.createNetData(getWorldData(), pipePos, facing);
        if (traced) {
            int nodesVisited = (data != null && data != OpticalNetWalker.FAILED_MARKER) ? data.getDistance() : 0;
            getTraceStats().recordWalkerTraversal(System.nanoTime() - start, nodesVisited);
            PipeNetTraceLog.log(getTraceLabel(),
                    "getNetData(" + pipePos + "," + facing + "): cache miss, walker traversal");
        }
        if (data == OpticalNetWalker.FAILED_MARKER) {
            // walker failed, don't cache, so it tries again on next insertion
            return null;
        }

        NET_DATA.put(encoded, data);
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        NET_DATA.clear();
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
        NET_DATA.values().removeIf(route -> route == null || route.passesThrough(pos));
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<OpticalPipeProperties>> transferredNodes,
                                    PipeNet<OpticalPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((OpticalPipeNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(OpticalPipeProperties nodeData, NBTTagCompound tagCompound) {}

    @Override
    protected OpticalPipeProperties readNodeData(NBTTagCompound tagCompound) {
        return OpticalPipeProperties.INSTANCE;
    }
}
