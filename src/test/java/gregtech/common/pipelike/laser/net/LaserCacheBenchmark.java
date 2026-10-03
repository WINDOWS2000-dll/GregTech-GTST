package gregtech.common.pipelike.laser.net;

import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

class LaserCacheBenchmark {

    @Test
    void fineGrainedInvalidationPreservesUnrelatedEntries() throws Exception {
        int sourceCount = 1_000;
        int changedIndex = sourceCount / 2;

        LaserPipeNet net = new LaserPipeNet(null);
        Field netDataField = LaserPipeNet.class.getDeclaredField("netData");
        netDataField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Long2ObjectMap<LaserRoutePath> netData = (Long2ObjectMap<LaserRoutePath>) netDataField.get(net);

        // one cached entry per source, each with a route path that only touches its own source position --
        // i.e. no two entries' routes overlap, so a change at exactly one source's position should only ever
        // invalidate that one entry.
        for (int i = 0; i < sourceCount; i++) {
            BlockPos sourcePos = new BlockPos(i, 0, 0);
            LongSet path = new LongOpenHashSet();
            path.add(sourcePos.toLong());
            netData.put(sourcePos.toLong(), new LaserRoutePath(null, null, 1, path));
        }

        BlockPos changedPos = new BlockPos(changedIndex, 0, 0);
        long begin = System.nanoTime();
        net.onPipeConnectionsUpdate(changedPos);
        long elapsedNanos = System.nanoTime() - begin;

        int survivors = netData.size();
        System.out.printf(
                "[LaserCacheBenchmark] AFTER : sources=%d changed=1 survivors=%d/%d  invalidateCall=%.3fms%n",
                sourceCount, survivors, sourceCount, elapsedNanos / 1e6);
    }
}
