package gregtech.common.pipelike.cable.net;

import gregtech.common.pipelike.cable.tile.TileEntityCable;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class EnergyCacheBenchmark {

    @Test
    void cacheBehaviorUnchangedForUnrelatedUpdates() throws Exception {
        int sourceCount = 1_000;

        EnergyNet net = new EnergyNet(null);
        Field netDataField = EnergyNet.class.getDeclaredField("NET_DATA");
        netDataField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<BlockPos, List<EnergyRoutePath>> netData = (Map<BlockPos, List<EnergyRoutePath>>) netDataField.get(net);

        for (int i = 0; i < sourceCount; i++) {
            BlockPos sourcePos = new BlockPos(i, 0, 0);
            EnergyRoutePath route = new EnergyRoutePath(EnumFacing.UP, new TileEntityCable[] { null }, 1, 0L);
            netData.put(sourcePos, Collections.singletonList(route));
        }

        BlockPos changedPos = new BlockPos(sourceCount / 2, 0, 0);
        long begin = System.nanoTime();
        net.onPipeConnectionsUpdate(changedPos);
        long elapsedNanos = System.nanoTime() - begin;

        int survivors = netData.size();
        System.out.printf(
                "[EnergyCacheBenchmark] AFTER : sources=%d changed=1 survivors=%d/%d  invalidateCall=%.3fms%n",
                sourceCount, survivors, sourceCount, elapsedNanos / 1e6);
    }
}
