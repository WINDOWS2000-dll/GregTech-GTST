package gregtech.api.pipenet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for {@link PipeNetTraceStats}' snapshot-and-diff shape (see its own JavaDoc): confirms
 * that recording operations, taking a snapshot, recording more, then diffing against the first snapshot yields
 * exactly the second batch of activity -- not the cumulative lifetime total -- and that {@link
 * PipeNetTraceStats.Snapshot#describe} produces a readable summary without throwing, including in the
 * brand-new (all-zero, {@code periodNanos == 0}) case a one-shot dump (not a periodic summary) would use.
 */
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
        // a 100ms interval -> 1ms/100ms = 1%
        String description = stats.snapshot().describe(100_000_000L);
        assertTrue(description.contains("1.0000% of interval"), description);
    }

    /**
     * Regression coverage for the {@code findConnectedNanosSoFar()} accounting seam {@link PipeNet#removeNode}
     * uses to avoid double-counting: {@code rebuildNetworkOnNodeRemoval}'s own split-check can call {@link
     * PipeNet#findAllConnectedBlocks} (recorded under {@code findConnected}), *nested inside* the very call
     * {@code removeNode} is itself timing (recorded under {@code removeNode}). Without subtracting that nested
     * time back out before calling {@code #recordRemoveNode}, {@link
     * PipeNetTraceStats.Snapshot#totalNanos()} would count it twice -- once under each category -- overstating
     * the periodic summary's headline "cpu=X% of interval" figure. This test reproduces {@code removeNode}'s
     * exact bookkeeping sequence (snapshot {@code findConnectedNanosSoFar()} before and after the nested call,
     * subtract the delta) directly against {@link PipeNetTraceStats}, without relying on real wall-clock timing.
     */
    @Test
    void removeNodeAccountingExcludesNestedFindConnectedTimeFromTheTotal() {
        PipeNetTraceStats stats = new PipeNetTraceStats();

        // simulate removeNode's own bookkeeping around a call to rebuildNetworkOnNodeRemoval that happens to
        // trigger one nested findAllConnectedBlocks call (a split-check), out of a 1.5ms total wall-clock cost
        // for the whole removeNode() call, of which 1ms was spent inside that nested call.
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
