package gregtech.common.pipelike.itempipe.net;

import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Confirmatory microbenchmark for {@link ItemPipeNet}'s cache invalidation. As {@code EnergyCacheBenchmark},
 * this is NOT expected to show any improvement -- {@code ItemNetWalker} explores the entire connected
 * component exhaustively with no early termination (needed to enumerate every reachable inventory with its
 * accumulated priority/filters), so a route's dependency set is always the whole net; fine-grained
 * invalidation would provide no benefit here even if implemented (see {@code
 * GTST-pipenet-optimization-design/README.md}'s "今後の検討課題" section). This benchmark exists to confirm
 * that expectation empirically.
 */
class ItemCacheBenchmark {

    @Test
    void cacheBehaviorUnchangedForUnrelatedUpdates() throws Exception {
        int sourceCount = 1_000;

        ItemPipeNet net = new ItemPipeNet(null);
        Field netDataField = ItemPipeNet.class.getDeclaredField("NET_DATA");
        netDataField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<BlockPos, List<ItemRoutePath>> netData = (Map<BlockPos, List<ItemRoutePath>>) netDataField.get(net);

        for (int i = 0; i < sourceCount; i++) {
            BlockPos sourcePos = new BlockPos(i, 0, 0);
            ItemRoutePath route = new ItemRoutePath(null, null, 1, null, Collections.emptyList());
            netData.put(sourcePos, Collections.singletonList(route));
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
