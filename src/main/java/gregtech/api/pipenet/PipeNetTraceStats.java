package gregtech.api.pipenet;

import org.jetbrains.annotations.NotNull;

/**
 * Per-{@link PipeNet} lifetime counters for the PipeNet execution-trace dev tool (see {@link PipeNet#setTraceEnabled}).
 * Every {@code record*} method here is only ever called from a call site already guarded by
 * {@link PipeNet#isTraceEnabled()} -- untraced nets never touch this class at all, beyond holding the one empty
 * instance (a handful of {@code long} fields; negligible even multiplied across every pipe in a world).
 * <p>
 * These are cumulative, monotonically-increasing lifetime totals, never reset. The periodic summary (see
 * {@code PipeNetTraceTickHandler}) takes a {@link #snapshot()} every reporting interval and diffs it against the
 * previous one via {@link Snapshot#minus} to get "how much happened in this interval" -- the same
 * snapshot-and-diff shape as reading a monotonic hardware counter, rather than maintaining separate
 * "since-last-flush" accumulators that would need resetting in lockstep with the reporting cadence.
 */
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

    /**
     * Records one {@code PipeNetWalker#traversePipeNet()} call (a route/inventory-list search walking the
     * pipe graph itself, e.g. {@code LaserNetWalker}/{@code ItemNetWalker}/{@code EnergyNetWalker}).
     */
    public void recordWalkerTraversal(long elapsedNanos, int nodesVisited) {
        walkerTraversalCount++;
        walkerTraversalNanos += elapsedNanos;
        walkerNodesVisited += nodesVisited;
    }

    /**
     * Records one {@link PipeNet#findAllConnectedBlocks} call (the {@code BreadthFirstIterator}-backed
     * split-detection check run after a blocked-connection/mark change or node removal).
     */
    public void recordFindConnected(long elapsedNanos, int nodesVisited) {
        findConnectedCount++;
        findConnectedNanos += elapsedNanos;
        findConnectedNodesVisited += nodesVisited;
    }

    /**
     * @return {@link #findConnectedNanos} as of right now. {@link PipeNet#removeNode} calls this before and
     *         after {@code rebuildNetworkOnNodeRemoval} to measure how much of that nested call's time was
     *         already recorded under {@code findConnected} (it can trigger {@link PipeNet#findAllConnectedBlocks}
     *         internally for its own split-check), then subtracts that back out of its own recorded duration --
     *         without this, that nested time would be counted twice in {@link Snapshot#totalNanos()}: once under
     *         {@code findConnected}, once again as part of {@code removeNode}'s wrapping duration.
     */
    long findConnectedNanosSoFar() {
        return findConnectedNanos;
    }

    /**
     * Records one {@link PipeNet#uniteNetworks} call (via {@link PipeNet#mergeWithSizeOrdering} or directly),
     * i.e. one net being fully absorbed into another.
     */
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

    /**
     * Records one {@code getNetData} call whose cached entry was reused as-is (no walker re-traversal needed) --
     * see {@code LaserPipeNet}/{@code OpticalPipeNet}/{@code ItemPipeNet}/{@code EnergyNet#getNetData}.
     */
    public void recordCacheHit() {
        cacheHitCount++;
    }

    /** Records one {@code getNetData} call that had to (re)compute its entry via a fresh walker traversal. */
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

    /** An immutable point-in-time copy of {@link PipeNetTraceStats}, for {@link #minus} interval diffing. */
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

        /** An all-zero snapshot, used as the "previous" snapshot the first time a net is summarized. */
        public static Snapshot zero() {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        /**
         * @return a new snapshot holding {@code this - earlier}, field by field -- "what happened between
         *         {@code earlier} and {@code this}".
         */
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

        /** Total CPU time this interval's recorded operations took, across every recorded category. */
        public long totalNanos() {
            return walkerTraversalNanos + findConnectedNanos + mergeNanos + addNodeNanos + removeNodeNanos;
        }

        /**
         * Formats this (already-diffed) interval snapshot into a one-line human-readable summary, expressing the
         * CPU time both in milliseconds and as a percentage of {@code periodNanos} (the wall-clock length of the
         * interval this snapshot covers) -- i.e. "what fraction of the server's tick budget this net's PipeNet
         * operations consumed", the closest single number to a "TPS load" figure this dev tool can honestly
         * report (see {@link PipeNet}'s memory-proxy note for why exact memory/TPS attribution isn't attempted).
         */
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
