package gregtech.common.pipelike.itempipe.net;

import gregtech.api.util.FacingPos;

import net.minecraft.util.EnumFacing;
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
 * <p>
 * {@code NET_DATA} is keyed by {@link FacingPos} (pos+facing), not {@code BlockPos} alone -- see {@link
 * ItemPipeNet#NET_DATA}'s own note on why a single face's cache miss must not be satisfied by another face's
 * stale entry. This benchmark mirrors that key shape so it stays representative of the real map.
 */
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
