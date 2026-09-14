package gregtech.common.pipelike.itempipe.net;

import gregtech.api.pipenet.Node;
import gregtech.api.pipenet.PipeNet;
import gregtech.api.pipenet.PipeNetTraceLog;
import gregtech.api.pipenet.WorldPipeNet;
import gregtech.api.unification.material.properties.ItemPipeProperties;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemPipeNet extends PipeNet<ItemPipeProperties> {

    private final Map<BlockPos, List<ItemRoutePath>> NET_DATA = new HashMap<>();

    public ItemPipeNet(WorldPipeNet<ItemPipeProperties, ? extends PipeNet<ItemPipeProperties>> world) {
        super(world);
    }

    public List<ItemRoutePath> getNetData(BlockPos pipePos, EnumFacing facing) {
        List<ItemRoutePath> data = NET_DATA.get(pipePos);
        if (data == null) {
            boolean traced = isTraceEnabled();
            if (traced) getTraceStats().recordCacheMiss();
            long start = traced ? System.nanoTime() : 0;
            data = ItemNetWalker.createNetData(getWorldData(), pipePos, facing);
            if (traced) {
                // unlike Laser/Optical, ItemNetWalker never stops early -- it always walks every reachable node
                // in the net to enumerate all destinations (see GTST-pipenet-optimization-design/README.md's
                // "今後の検討課題" section on why fine-grained cache invalidation doesn't help here), so
                // getAllNodes().size() (not the result list's size) is the actual work performed.
                getTraceStats().recordWalkerTraversal(System.nanoTime() - start, getAllNodes().size());
                PipeNetTraceLog.log(getTraceLabel(),
                        "getNetData(" + pipePos + "," + facing + "): cache miss, walker traversal");
            }
            if (data == null) {
                // walker failed, don't cache so it tries again on next insertion
                return Collections.emptyList();
            }
            data.sort(Comparator.comparingInt(inv -> inv.getProperties().getPriority()));
            NET_DATA.put(pipePos, data);
        } else if (isTraceEnabled()) {
            getTraceStats().recordCacheHit();
        }
        return data;
    }

    @Override
    public void onNeighbourUpdate(BlockPos fromPos) {
        NET_DATA.clear();
    }

    @Override
    public void onPipeConnectionsUpdate(BlockPos pos) {
        NET_DATA.clear();
    }

    @Override
    public void onChunkUnload(BlockPos pos) {
        NET_DATA.clear();
    }

    @Override
    protected void transferNodeData(Map<BlockPos, Node<ItemPipeProperties>> transferredNodes,
                                    PipeNet<ItemPipeProperties> parentNet) {
        super.transferNodeData(transferredNodes, parentNet);
        NET_DATA.clear();
        ((ItemPipeNet) parentNet).NET_DATA.clear();
    }

    @Override
    protected void writeNodeData(ItemPipeProperties nodeData, NBTTagCompound tagCompound) {
        tagCompound.setInteger("Resistance", nodeData.getPriority());
        tagCompound.setFloat("Rate", nodeData.getTransferRate());
    }

    @Override
    protected ItemPipeProperties readNodeData(NBTTagCompound tagCompound) {
        return new ItemPipeProperties(tagCompound.getInteger("Resistance"), tagCompound.getFloat("Rate"));
    }
}
