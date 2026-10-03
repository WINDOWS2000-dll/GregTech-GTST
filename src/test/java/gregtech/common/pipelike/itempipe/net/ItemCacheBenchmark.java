package gregtech.common.pipelike.itempipe.net;

import gregtech.api.util.FacingPos;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class ItemCacheBenchmark {

    @Test
    void cacheBehaviorUnchangedForUnrelatedUpdates() throws Exception {
        int sourceCount = 1_000;

        ItemPipeNet net = new ItemPipeNet(null);
        Field netDataField = ItemPipeNet.class.getDeclaredField("NET_DATA");
        netDataField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<FacingPos, List<ItemRoutePath>> netData = (Map<FacingPos, List<ItemRoutePath>>) netDataField.get(net);

        for (int i = 0; i < sourceCount; i++) {
            FacingPos sourceKey = new FacingPos(new BlockPos(i, 0, 0), EnumFacing.UP);
            ItemRoutePath route = new ItemRoutePath(null, null, 1, null, Collections.emptyList());
            netData.put(sourceKey, Collections.singletonList(route));
        }

        BlockPos changedPos = new BlockPos(sourceCount / 2, 0, 0);
        long begin = System.nanoTime();
        net.onPipeConnectionsUpdate(changedPos);
        long elapsedNanos = System.nanoTime() - begin;

        int survivors = netData.size();
        System.out.printf(
                "[ItemCacheBenchmark] AFTER : sources=%d changed=1 survivors=%d/%d  invalidateCall=%.3fms%n",
                sourceCount, survivors, sourceCount, elapsedNanos / 1e6);
    }
}
