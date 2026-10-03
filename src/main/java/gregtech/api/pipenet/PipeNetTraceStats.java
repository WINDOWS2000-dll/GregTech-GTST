package gregtech.api.pipenet;

import org.jetbrains.annotations.NotNull;

public final class PipeNetTraceStats {

    private long walkerTraversalCount;
    private long walkerTraversalNanos;
    private long walkerNodesVisited;

    private long findConnectedCount;
    private long findConnectedNanos;
    private long findConnectedNodesVisited;

    private long mergeCount;
    private long mergeNanos;
    private long mergeNodesCopied;

    private long addNodeCount;
    private long addNodeNanos;

    private long removeNodeCount;
    private long removeNodeNanos;

    private long cacheHitCount;
    private long cacheMissCount;

    public void recordWalkerTraversal(long elapsedNanos, int nodesVisited) {
        walkerTraversalCount++;
        walkerTraversalNanos += elapsedNanos;
        walkerNodesVisited += nodesVisited;
    }

    public void recordFindConnected(long elapsedNanos, int nodesVisited) {
        findConnectedCount++;
        findConnectedNanos += elapsedNanos;
        findConnectedNodesVisited += nodesVisited;
    }

    long findConnectedNanosSoFar() {
        return findConnectedNanos;
    }

    public void recordMerge(long elapsedNanos, int nodesCopied) {
        mergeCount++;
        mergeNanos += elapsedNanos;
        mergeNodesCopied += nodesCopied;
    }

    public void recordAddNode(long elapsedNanos) {
        addNodeCount++;
        addNodeNanos += elapsedNanos;
    }

    public void recordRemoveNode(long elapsedNanos) {
        removeNodeCount++;
        removeNodeNanos += elapsedNanos;
    }

    public void recordCacheHit() {
        cacheHitCount++;
    }

    public void recordCacheMiss() {
        cacheMissCount++;
    }

    @NotNull
    public Snapshot snapshot() {
        return new Snapshot(walkerTraversalCount, walkerTraversalNanos, walkerNodesVisited,
                findConnectedCount, findConnectedNanos, findConnectedNodesVisited,
                mergeCount, mergeNanos, mergeNodesCopied,
                addNodeCount, addNodeNanos, removeNodeCount, removeNodeNanos,
                cacheHitCount, cacheMissCount);
    }

    public static final class Snapshot {

        public final long walkerTraversalCount;
        public final long walkerTraversalNanos;
        public final long walkerNodesVisited;
        public final long findConnectedCount;
        public final long findConnectedNanos;
        public final long findConnectedNodesVisited;
        public final long mergeCount;
        public final long mergeNanos;
        public final long mergeNodesCopied;
        public final long addNodeCount;
        public final long addNodeNanos;
        public final long removeNodeCount;
        public final long removeNodeNanos;
        public final long cacheHitCount;
        public final long cacheMissCount;

        private Snapshot(long walkerTraversalCount, long walkerTraversalNanos, long walkerNodesVisited,
                         long findConnectedCount, long findConnectedNanos, long findConnectedNodesVisited,
                         long mergeCount, long mergeNanos, long mergeNodesCopied,
                         long addNodeCount, long addNodeNanos, long removeNodeCount, long removeNodeNanos,
                         long cacheHitCount, long cacheMissCount) {
            this.walkerTraversalCount = walkerTraversalCount;
            this.walkerTraversalNanos = walkerTraversalNanos;
            this.walkerNodesVisited = walkerNodesVisited;
            this.findConnectedCount = findConnectedCount;
            this.findConnectedNanos = findConnectedNanos;
            this.findConnectedNodesVisited = findConnectedNodesVisited;
            this.mergeCount = mergeCount;
            this.mergeNanos = mergeNanos;
            this.mergeNodesCopied = mergeNodesCopied;
            this.addNodeCount = addNodeCount;
            this.addNodeNanos = addNodeNanos;
            this.removeNodeCount = removeNodeCount;
            this.removeNodeNanos = removeNodeNanos;
            this.cacheHitCount = cacheHitCount;
            this.cacheMissCount = cacheMissCount;
        }

        public static Snapshot zero() {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        @NotNull
        public Snapshot minus(@NotNull Snapshot earlier) {
            return new Snapshot(
                    walkerTraversalCount - earlier.walkerTraversalCount,
                    walkerTraversalNanos - earlier.walkerTraversalNanos,
                    walkerNodesVisited - earlier.walkerNodesVisited,
                    findConnectedCount - earlier.findConnectedCount,
                    findConnectedNanos - earlier.findConnectedNanos,
                    findConnectedNodesVisited - earlier.findConnectedNodesVisited,
                    mergeCount - earlier.mergeCount,
                    mergeNanos - earlier.mergeNanos,
                    mergeNodesCopied - earlier.mergeNodesCopied,
                    addNodeCount - earlier.addNodeCount,
                    addNodeNanos - earlier.addNodeNanos,
                    removeNodeCount - earlier.removeNodeCount,
                    removeNodeNanos - earlier.removeNodeNanos,
                    cacheHitCount - earlier.cacheHitCount,
                    cacheMissCount - earlier.cacheMissCount);
        }

        public long totalNanos() {
            return walkerTraversalNanos + findConnectedNanos + mergeNanos + addNodeNanos + removeNodeNanos;
        }

        @NotNull
        public String describe(long periodNanos) {
            double totalMs = totalNanos() / 1e6;
            double percentOfPeriod = periodNanos > 0 ? (totalNanos() * 100.0 / periodNanos) : 0.0;
            long cacheTotal = cacheHitCount + cacheMissCount;
            double cacheHitRate = cacheTotal > 0 ? (cacheHitCount * 100.0 / cacheTotal) : 100.0;
            return String.format(
                    "cpu=%.3fms (%.4f%% of interval) | walker: %d calls, %d nodes, %.3fms | findConnected: %d calls, " +
                            "%d nodes, %.3fms | merge: %d calls, %d nodes copied, %.3fms | addNode: %d (%.3fms) | " +
                            "removeNode: %d (%.3fms) | cache: %d hit / %d miss (%.1f%% hit rate)",
                    totalMs, percentOfPeriod,
                    walkerTraversalCount, walkerNodesVisited, walkerTraversalNanos / 1e6,
                    findConnectedCount, findConnectedNodesVisited, findConnectedNanos / 1e6,
                    mergeCount, mergeNodesCopied, mergeNanos / 1e6,
                    addNodeCount, addNodeNanos / 1e6,
                    removeNodeCount, removeNodeNanos / 1e6,
                    cacheHitCount, cacheMissCount, cacheHitRate);
        }
    }
}
