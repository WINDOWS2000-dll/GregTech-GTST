package gregtech.api.pipenet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipeNetTraceStatsTest {

    @Test
    void snapshotDiffOnlyReflectsActivitySinceTheEarlierSnapshot() {
        PipeNetTraceStats stats = new PipeNetTraceStats();
        stats.recordWalkerTraversal(1_000_000, 5);
        stats.recordAddNode(500_000);

        PipeNetTraceStats.Snapshot first = stats.snapshot();

        stats.recordWalkerTraversal(2_000_000, 7);
        stats.recordMerge(3_000_000, 10);
        stats.recordCacheHit();
        stats.recordCacheMiss();

        PipeNetTraceStats.Snapshot second = stats.snapshot();
        PipeNetTraceStats.Snapshot delta = second.minus(first);

        // only the activity recorded between the two snapshots
        assertEquals(1, delta.walkerTraversalCount);
        assertEquals(2_000_000, delta.walkerTraversalNanos);
        assertEquals(7, delta.walkerNodesVisited);
        assertEquals(1, delta.mergeCount);
        assertEquals(3_000_000, delta.mergeNanos);
        assertEquals(10, delta.mergeNodesCopied);
        assertEquals(1, delta.cacheHitCount);
        assertEquals(1, delta.cacheMissCount);
        // not carried over from before the first snapshot
        assertEquals(0, delta.addNodeCount);
        assertEquals(0, delta.addNodeNanos);

        // the lifetime total (undiffed) still reflects everything
        PipeNetTraceStats.Snapshot lifetime = stats.snapshot();
        assertEquals(2, lifetime.walkerTraversalCount);
        assertEquals(1, lifetime.addNodeCount);
        assertEquals(1, lifetime.mergeCount);
    }

    @Test
    void describeDoesNotThrowForAZeroPeriodOneShotSnapshot() {
        PipeNetTraceStats stats = new PipeNetTraceStats();
        stats.recordWalkerTraversal(1_000_000, 3);
        String description = stats.snapshot().describe(0);
        assertTrue(description.contains("walker: 1 calls"), description);
    }

    @Test
    void describeReportsPercentOfIntervalForANonZeroPeriod() {
        PipeNetTraceStats stats = new PipeNetTraceStats();
        stats.recordAddNode(1_000_000); // 1ms
        String description = stats.snapshot().describe(100_000_000L);
        assertTrue(description.contains("1.0000% of interval"), description);
    }

    @Test
    void removeNodeAccountingExcludesNestedFindConnectedTimeFromTheTotal() {
        PipeNetTraceStats stats = new PipeNetTraceStats();

        long findConnectedBefore = stats.findConnectedNanosSoFar();
        stats.recordFindConnected(1_000_000, 5); // the nested findAllConnectedBlocks call
        long nestedNanos = stats.findConnectedNanosSoFar() - findConnectedBefore;
        long wholeRemoveNodeCallNanos = 1_500_000;
        stats.recordRemoveNode(Math.max(0, wholeRemoveNodeCallNanos - nestedNanos));

        PipeNetTraceStats.Snapshot snapshot = stats.snapshot();
        assertEquals(500_000, snapshot.removeNodeNanos,
                "removeNode's own recorded time must exclude the nested findConnected call's time");
        assertEquals(1_000_000, snapshot.findConnectedNanos);
        assertEquals(wholeRemoveNodeCallNanos, snapshot.totalNanos(),
                "the total must equal the actual wall-clock cost, not double-count the nested call");
    }
}
