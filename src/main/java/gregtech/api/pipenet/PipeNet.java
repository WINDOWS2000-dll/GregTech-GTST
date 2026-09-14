package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.INBTSerializable;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public abstract class PipeNet<NodeDataType> implements INBTSerializable<NBTTagCompound> {

    protected final WorldPipeNet<NodeDataType, PipeNet<NodeDataType>> worldData;
    private final Map<BlockPos, Node<NodeDataType>> nodeByBlockPos = new HashMap<>();
    private final Map<BlockPos, Node<NodeDataType>> unmodifiableNodeByBlockPos = Collections
            .unmodifiableMap(nodeByBlockPos);
    private final Map<ChunkPos, Integer> ownedChunks = new HashMap<>();
    private long lastUpdate;
    boolean isValid = false;

    /**
     * Whether this net's operations are logged to {@link PipeNetTraceLog} (the PipeNet execution-trace dev
     * tool; see {@code PipeNetTraceBehavior}, the right-click-a-pipe dev item that toggles this). Off by
     * default and costs nothing beyond a boolean check when disabled -- mirrors {@code
     * RecipeWorkable#traceEnabled}'s established pattern exactly.
     */
    private boolean traceEnabled = false;
    /** A human-readable identifier prefixed onto this net's trace log lines; see {@link #setTraceEnabled}. */
    private String traceLabel;
    /** Lifetime operation counters, only ever touched while {@link #traceEnabled}; see its own JavaDoc. */
    private final PipeNetTraceStats traceStats = new PipeNetTraceStats();

    public PipeNet(WorldPipeNet<NodeDataType, ? extends PipeNet<NodeDataType>> world) {
        // noinspection unchecked
        this.worldData = (WorldPipeNet<NodeDataType, PipeNet<NodeDataType>>) world;
    }

    /** @return whether this net's operations are currently being logged to {@link PipeNetTraceLog}. */
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    /** @return the label this net's trace log lines are currently prefixed with, or {@code null} if untraced. */
    public String getTraceLabel() {
        return traceLabel;
    }

    /** @return this net's lifetime trace counters (all zero if it has never been traced). */
    public PipeNetTraceStats getTraceStats() {
        return traceStats;
    }

    /** As {@link #setTraceEnabled(boolean, String)}, without changing the current label. */
    public void setTraceEnabled(boolean traceEnabled) {
        setTraceEnabled(traceEnabled, traceLabel);
    }

    /**
     * Enables or disables execution tracing for this net, tagging any resulting log lines with {@code label}
     * (e.g. a traced pipe's position). Intended to be called by the trace item's server-side handler when it
     * selects/deselects the net a right-clicked pipe currently belongs to.
     * <p>
     * Tracing follows the <em>network</em>, not the specific Java object that happens to represent it at any
     * given moment: {@link #uniteNetworks} propagates an absorbed net's trace state onto whichever net
     * survives the merge (see its own note), so a traced net staying traced doesn't depend on it never being
     * on the losing side of a future merge.
     */
    public void setTraceEnabled(boolean traceEnabled, String label) {
        boolean wasEnabled = this.traceEnabled;
        this.traceEnabled = traceEnabled;
        this.traceLabel = label;
        if (traceEnabled && !wasEnabled) {
            PipeNetTraceTickHandler.register(this);
        } else if (!traceEnabled && wasEnabled) {
            PipeNetTraceTickHandler.unregister(this);
        }
    }

    /**
     * A memory-load proxy for this net, expressed as structural counts (node/edge/owned-chunk/persistent-data
     * counts) plus a very rough estimated byte footprint. This is NOT measured via {@code
     * java.lang.instrument.Instrumentation} -- an exact-bytes approach would need a Java agent, disproportionate
     * for a dev tool -- it is simply {@code nodeCount * ~150 bytes + edgeCount * ~80 bytes}, ballpark figures for
     * a {@code HashMap<BlockPos, Node>} entry plus one counted edge on a typical 64-bit JVM with compressed
     * oops. Treat the byte figure as an order-of-magnitude indicator, not a measurement; the structural counts
     * alongside it are the reliable part.
     */
    public String describeMemoryProxy() {
        int nodeCount = getAllNodes().size();
        int edgeCount = countEdges();
        int chunkCount = ownedChunks.size();
        long estimatedBytes = nodeCount * 150L + edgeCount * 80L;
        return String.format("nodes=%d, edges=%d, ownedChunks=%d, estMemory=~%s",
                nodeCount, edgeCount, chunkCount, formatBytes(estimatedBytes));
    }

    /**
     * Counts this net's edges (pairs of adjacent nodes that currently satisfy {@link #canNodesConnect}) by
     * scanning every node's 6 neighbours -- O(node count), computed on demand rather than incrementally
     * maintained. {@link #describeMemoryProxy} is this method's only caller, and that in turn is only invoked
     * by the rarely-run PipeNet execution-trace dev tool (the periodic summary and the {@code /gt dumppipenet}
     * command) -- paying an O(node count) scan there is far cheaper overall than maintaining a persistent edge
     * count (or a full graph structure) on every add/remove/connection-change call PipeNet makes on behalf of
     * every pipe in the world, which is what an earlier version of this class did (see {@link
     * #findAllConnectedBlocks}'s own note).
     */
    private int countEdges() {
        int doubleCounted = 0;
        for (Entry<BlockPos, Node<NodeDataType>> entry : nodeByBlockPos.entrySet()) {
            BlockPos pos = entry.getKey();
            Node<NodeDataType> node = entry.getValue();
            for (EnumFacing facing : EnumFacing.VALUES) {
                Node<NodeDataType> neighbour = nodeByBlockPos.get(pos.offset(facing));
                if (neighbour != null && canNodesConnect(node, facing, neighbour, this)) {
                    doubleCounted++;
                }
            }
        }
        // every edge was seen once from each endpoint
        return doubleCounted / 2;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024));
    }

    public Set<ChunkPos> getContainedChunks() {
        return Collections.unmodifiableSet(ownedChunks.keySet());
    }

    public World getWorldData() {
        return worldData.getWorld();
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public boolean isValid() {
        return isValid;
    }

    /**
     * Is only called when connections changed of nodes. Nodes can ONLY connect to other nodes.
     */
    protected void onNodeConnectionsUpdate() {
        this.lastUpdate = System.currentTimeMillis();
    }

    /**
     * Is called when any connection of any pipe in the net changes
     *
     * @param pos the position of the pipe whose connections changed
     */
    public void onPipeConnectionsUpdate(BlockPos pos) {}

    public void onNeighbourUpdate(BlockPos fromPos) {}

    /**
     * Is called when any Pipe TE in the PipeNet is unloaded
     *
     * @param pos the position of the pipe that unloaded
     */
    public void onChunkUnload(BlockPos pos) {}

    public Map<BlockPos, Node<NodeDataType>> getAllNodes() {
        return unmodifiableNodeByBlockPos;
    }

    public Node<NodeDataType> getNodeAt(BlockPos blockPos) {
        return nodeByBlockPos.get(blockPos);
    }

    public boolean containsNode(BlockPos blockPos) {
        return nodeByBlockPos.containsKey(blockPos);
    }

    /**
     * Registers {@code nodePos} in {@link #nodeByBlockPos} and this net's owned-chunk bookkeeping. Neighbour
     * connectivity itself is never precomputed or cached here -- see {@link #findAllConnectedBlocks}'s own
     * note on why PipeNet doesn't maintain a persistent adjacency structure at all; every consumer that needs
     * to know which neighbours a node actually connects to (a split/merge check, a route walk) computes it
     * fresh via {@link #canNodesConnect} against whatever is in {@link #nodeByBlockPos} at that moment.
     */
    protected void addNodeSilently(BlockPos nodePos, Node<NodeDataType> node) {
        this.nodeByBlockPos.put(nodePos, node);
        checkAddedInChunk(nodePos);
    }

    protected void addNode(BlockPos nodePos, Node<NodeDataType> node) {
        long start = traceEnabled ? System.nanoTime() : 0;
        addNodeSilently(nodePos, node);
        if (traceEnabled) {
            traceStats.recordAddNode(System.nanoTime() - start);
            PipeNetTraceLog.log(traceLabel, "addNode(" + nodePos + ") -> " + getAllNodes().size() + " node(s) total");
        }
        onNodeConnectionsUpdate();
        worldData.markDirty();
    }

    protected Node<NodeDataType> removeNodeWithoutRebuilding(BlockPos nodePos) {
        Node<NodeDataType> removedNode = this.nodeByBlockPos.remove(nodePos);
        ensureRemovedFromChunk(nodePos);
        worldData.markDirty();
        return removedNode;
    }

    protected void removeNode(BlockPos nodePos) {
        if (nodeByBlockPos.containsKey(nodePos)) {
            boolean traced = traceEnabled;
            long start = traced ? System.nanoTime() : 0;
            // rebuildNetworkOnNodeRemoval's own split-check can call findAllConnectedBlocks, which records
            // its own findConnected stats -- snapshot that counter here so its nested time can be subtracted
            // back out below, instead of being counted twice (once under findConnected, once again as part
            // of this method's own wrapping duration) in Snapshot#totalNanos().
            long findConnectedNanosBefore = traced ? traceStats.findConnectedNanosSoFar() : 0;
            Node<NodeDataType> selfNode = removeNodeWithoutRebuilding(nodePos);
            rebuildNetworkOnNodeRemoval(nodePos, selfNode);
            if (traced) {
                // note: traceLabel is read again (not a captured pre-call label) since rebuildNetworkOnNodeRemoval
                // can remove this net entirely (worldData.removePipeNet) if it ends up empty -- that path also
                // disables tracing itself (see the isEmpty() check below), so by the time we get here traceLabel
                // may already be null; that's fine, it just means this line logs under a null label.
                long elapsedNanos = System.nanoTime() - start;
                long nestedFindConnectedNanos = traceStats.findConnectedNanosSoFar() - findConnectedNanosBefore;
                traceStats.recordRemoveNode(Math.max(0, elapsedNanos - nestedFindConnectedNanos));
                PipeNetTraceLog.log(traceLabel, "removeNode(" + nodePos + ")");
            }
        }
    }

    protected void checkAddedInChunk(BlockPos nodePos) {
        ChunkPos chunkPos = new ChunkPos(nodePos);
        int newValue = this.ownedChunks.compute(chunkPos, (pos, old) -> (old == null ? 0 : old) + 1);
        if (newValue == 1 && isValid()) {
            this.worldData.addPipeNetToChunk(chunkPos, this);
        }
    }

    protected void ensureRemovedFromChunk(BlockPos nodePos) {
        ChunkPos chunkPos = new ChunkPos(nodePos);
        int newValue = this.ownedChunks.compute(chunkPos, (pos, old) -> old == null ? 0 : old - 1);
        if (newValue == 0) {
            this.ownedChunks.remove(chunkPos);
            if (isValid()) {
                this.worldData.removePipeNetFromChunk(chunkPos, this);
            }
        }
    }

    protected void updateBlockedConnections(BlockPos nodePos, EnumFacing facing, boolean isBlocked) {
        if (!containsNode(nodePos)) {
            return;
        }
        Node<NodeDataType> selfNode = getNodeAt(nodePos);
        if (selfNode.isBlocked(facing) == isBlocked)
            return;

        setBlocked(selfNode, facing, isBlocked);
        BlockPos offsetPos = nodePos.offset(facing);
        PipeNet<NodeDataType> pipeNetAtOffset = worldData.getNetFromPos(offsetPos);
        if (pipeNetAtOffset == null) {
            // if there is no any pipe net at this side,
            // updating blocked status of it won't change anything in any net
            return;
        }
        // if we are on that side of node too
        // and it is blocked now
        if (pipeNetAtOffset == this) {
            Node<NodeDataType> neighbourNode = getNodeAt(offsetPos);
            // if side was unblocked, well, there is really nothing changed in this e-net
            // if it is blocked now, but was able to connect with neighbour node before, try split networks
            if (isBlocked) {
                // need to unblock node before doing canNodesConnectCheck
                setBlocked(selfNode, facing, false);
                if (canNodesConnect(selfNode, facing, neighbourNode, this)) {
                    // now block again to call findAllConnectedBlocks -- findAllConnectedBlocks itself always
                    // re-checks canNodesConnect fresh against this now-reblocked state, so there is no separate
                    // adjacency structure that needs to be kept in sync here (see that method's own note)
                    setBlocked(selfNode, facing, true);
                    HashMap<BlockPos, Node<NodeDataType>> thisENet = findAllConnectedBlocks(nodePos);
                    if (!getAllNodes().equals(thisENet)) {
                        // node visibility has changed, split network into 2
                        // node that code below is similar to removeNodeInternal, but only for 2 networks, and without
                        // node removal
                        PipeNet<NodeDataType> newPipeNet = worldData.createNetInstance();
                        thisENet.keySet().forEach(this::removeNodeWithoutRebuilding);
                        newPipeNet.transferNodeData(thisENet, this);
                        worldData.addPipeNet(newPipeNet);
                    }
                }
            }
            // (unblocking within the same net doesn't restructure anything -- they were already the same
            // component via some other path -- and there is no separate adjacency structure to keep in sync)
            // there is another network on that side
            // if this is an unblock, and we can connect with their node, merge them

        }
        // The net that currently owns nodePos -- reassigned below if a size-ordered merge (see
        // mergeWithSizeOrdering) causes *this* object itself to be the one absorbed, so that the trailing
        // onNodeConnectionsUpdate() below always fires on whichever object actually survived.
        PipeNet<NodeDataType> selfNet = this;
        if (pipeNetAtOffset != this && !isBlocked) {
            Node<NodeDataType> neighbourNode = pipeNetAtOffset.getNodeAt(offsetPos);
            // check connection availability from both networks
            if (canNodesConnect(selfNode, facing, neighbourNode, pipeNetAtOffset) &&
                    pipeNetAtOffset.canNodesConnect(neighbourNode, facing.getOpposite(), selfNode, this)) {
                // so, side is unblocked now, and nodes can connect, merge two networks
                selfNet = mergeWithSizeOrdering(pipeNetAtOffset);
            }
        }
        selfNet.onNodeConnectionsUpdate();
        worldData.markDirty();
    }

    protected void updateMark(BlockPos nodePos, int newMark) {
        if (!containsNode(nodePos)) {
            return;
        }
        HashMap<BlockPos, Node<NodeDataType>> selfConnectedBlocks = null;
        Node<NodeDataType> selfNode = getNodeAt(nodePos);
        int oldMark = selfNode.mark;
        selfNode.mark = newMark;
        // The net that currently owns nodePos. A single call can merge across multiple facings (each qualifying
        // neighbour is its own potential merge), and mergeWithSizeOrdering may pick *either* side as the
        // survivor -- so this is reassigned after every merge below, and used everywhere `this` was previously
        // used for identity comparisons or net-level operations, instead of `this` directly. See
        // mergeWithSizeOrdering's own doc for why relying on `this` staying valid is unsafe once size-ordering
        // can flip which object survives.
        PipeNet<NodeDataType> selfNet = this;
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos offsetPos = nodePos.offset(facing);
            PipeNet<NodeDataType> otherPipeNet = worldData.getNetFromPos(offsetPos);
            Node<NodeDataType> secondNode = otherPipeNet == null ? null : otherPipeNet.getNodeAt(offsetPos);
            if (secondNode == null)
                continue; // there is noting here
            if (!areNodeBlockedConnectionsCompatible(selfNode, facing, secondNode) ||
                    !areNodesCustomContactable(selfNode.data, secondNode.data, otherPipeNet))
                continue; // if connections aren't compatible, skip them
            if (areMarksCompatible(oldMark, secondNode.mark) == areMarksCompatible(newMark, secondNode.mark))
                continue; // if compatibility didn't change, skip it
            if (areMarksCompatible(newMark, secondNode.mark)) {
                // if marks are compatible now, and offset network is different network, merge them
                // if it is same network, nothing needs to change (there is no separate adjacency structure
                // to keep in sync -- findAllConnectedBlocks always re-checks canNodesConnect fresh)
                if (otherPipeNet != selfNet) {
                    selfNet = selfNet.mergeWithSizeOrdering(otherPipeNet);
                    // a merge changes selfNet's node membership, so any previously-memoized reachable-set
                    // snapshot (computed before the merge) is stale and must be recomputed if needed again below
                    selfConnectedBlocks = null;
                }
                // marks are incompatible now, and this net is connected with it
            } else if (otherPipeNet == selfNet) {
                // search connected nodes from newly marked node (selfNode.mark is already updated above, so
                // this naturally no longer traverses the nodePos<->offsetPos edge -- there is no separate
                // adjacency structure that needs an explicit edge removal here)
                // populate self connected blocks lazily only once
                if (selfConnectedBlocks == null) {
                    selfConnectedBlocks = selfNet.findAllConnectedBlocks(nodePos);
                }
                if (selfNet.getAllNodes().equals(selfConnectedBlocks)) {
                    continue; // if this node is still connected to this network, just continue
                }
                // otherwise, it is not connected
                HashMap<BlockPos, Node<NodeDataType>> offsetConnectedBlocks = selfNet.findAllConnectedBlocks(offsetPos);
                // if in the result of remarking offset node has separated from main network,
                // and it is also separated from current cable too, form new network for it
                if (!offsetConnectedBlocks.equals(selfConnectedBlocks)) {
                    offsetConnectedBlocks.keySet().forEach(selfNet::removeNodeWithoutRebuilding);
                    PipeNet<NodeDataType> offsetPipeNet = worldData.createNetInstance();
                    offsetPipeNet.transferNodeData(offsetConnectedBlocks, selfNet);
                    worldData.addPipeNet(offsetPipeNet);
                }
            }
        }
        selfNet.onNodeConnectionsUpdate();
        worldData.markDirty();
    }

    private void setBlocked(Node<NodeDataType> selfNode, EnumFacing facing, boolean isBlocked) {
        if (!isBlocked) {
            selfNode.openConnections |= 1 << facing.getIndex();
        } else {
            selfNode.openConnections &= ~(1 << facing.getIndex());
        }
    }

    public boolean markNodeAsActive(BlockPos nodePos, boolean isActive) {
        if (containsNode(nodePos) && getNodeAt(nodePos).isActive != isActive) {
            getNodeAt(nodePos).isActive = isActive;
            worldData.markDirty();
            onNodeConnectionsUpdate();
            return true;
        }
        return false;
    }

    protected final void uniteNetworks(PipeNet<NodeDataType> unitedPipeNet) {
        boolean survivorAlreadyTraced = traceEnabled;
        boolean absorbedWasTraced = unitedPipeNet.traceEnabled;
        boolean shouldRecord = survivorAlreadyTraced || absorbedWasTraced;
        long start = shouldRecord ? System.nanoTime() : 0;
        int copiedCount = unitedPipeNet.getAllNodes().size();

        Map<BlockPos, Node<NodeDataType>> allNodes = new HashMap<>(unitedPipeNet.getAllNodes());
        worldData.removePipeNet(unitedPipeNet);
        allNodes.keySet().forEach(unitedPipeNet::removeNodeWithoutRebuilding);
        transferNodeData(allNodes, unitedPipeNet);

        if (absorbedWasTraced) {
            // Tracing follows the network's identity (whichever object currently holds its nodes), not the
            // specific Java object that happened to survive this particular merge -- see #setTraceEnabled's
            // own note on why. The absorbed object is being discarded regardless, so its own trace state is
            // cleared either way.
            if (!survivorAlreadyTraced) setTraceEnabled(true, unitedPipeNet.traceLabel);
            unitedPipeNet.setTraceEnabled(false, null);
        }
        if (shouldRecord) {
            traceStats.recordMerge(System.nanoTime() - start, copiedCount);
            PipeNetTraceLog.log(traceLabel, "uniteNetworks: absorbed " + copiedCount + " node(s) from another net" +
                    (absorbedWasTraced ? " (that net was itself traced; tracing now follows this surviving net)" :
                            ""));
        }
    }

    /**
     * Merges {@code this} and {@code other}, choosing the merge direction so that the <em>smaller</em> net's
     * nodes are the ones copied -- the same union-by-size idea classic Union-Find uses to bound the amortized
     * cost of a long sequence of merges. Without this, a small net repeatedly bridged onto an ever-growing
     * large one (a common real layout: a small connector segment whose cover/mark gets toggled to link it into
     * a big trunk line, over and over) would copy the *entire* large net on every single merge, which is the
     * O(n^2)-worst-case pattern union-by-size specifically exists to avoid.
     * <p>
     * Unlike {@link #uniteNetworks}, which always keeps {@code this} as the survivor, this method may return
     * {@code other} instead. Callers MUST use the returned value in place of any reference to either net they
     * held before calling this -- see the callers in {@link #updateBlockedConnections} and {@link #updateMark}
     * for the pattern (a local "current net" variable, reassigned from this method's return value, used for
     * everything from this point on instead of {@code this}). This is necessary because {@code this} is a
     * plain object reference, not an indirection layer: once it stops being the survivor, calling any
     * instance method on it (or comparing it for identity against a freshly-looked-up net) silently operates
     * on a net that's no longer registered in {@link #worldData} at all.
     *
     * @return the surviving net (either {@code this} or {@code other})
     */
    protected final PipeNet<NodeDataType> mergeWithSizeOrdering(PipeNet<NodeDataType> other) {
        if (this.getAllNodes().size() >= other.getAllNodes().size()) {
            this.uniteNetworks(other);
            return this;
        } else {
            other.uniteNetworks(this);
            return other;
        }
    }

    private boolean areNodeBlockedConnectionsCompatible(Node<NodeDataType> first, EnumFacing firstFacing,
                                                        Node<NodeDataType> second) {
        return !first.isBlocked(firstFacing) && !second.isBlocked(firstFacing.getOpposite());
    }

    private static boolean areMarksCompatible(int mark1, int mark2) {
        return mark1 == mark2 || mark1 == Node.DEFAULT_MARK || mark2 == Node.DEFAULT_MARK;
    }

    /**
     * Checks if given nodes can connect
     * Note that this logic should equal with block connection logic
     * for proper work of network
     */
    protected final boolean canNodesConnect(Node<NodeDataType> first, EnumFacing firstFacing, Node<NodeDataType> second,
                                            PipeNet<NodeDataType> secondPipeNet) {
        return areNodeBlockedConnectionsCompatible(first, firstFacing, second) &&
                areMarksCompatible(first.mark, second.mark) &&
                areNodesCustomContactable(first.data, second.data, secondPipeNet);
    }

    /**
     * Returns every node reachable from {@code startPos} within this net -- i.e. {@code startPos}'s connected
     * component, computed by a plain iterative breadth-first search directly over {@link #nodeByBlockPos} and
     * {@link #canNodesConnect} (a small explicit queue plus the {@code observedSet} map doubling as the
     * visited set -- the same shape {@link PipeNetWalker} already uses to walk the real world). {@code
     * startPos} must already be a member of this net (matching the previous implementation's own implicit
     * precondition, which would likewise fail if given a non-member position).
     * <p>
     * This net does NOT maintain any persistent graph/adjacency structure for this query to consult. An
     * earlier version of this class did (first a hand-rolled depth-first search directly on {@code
     * nodeByBlockPos} like this one, then later a JGraphT {@code Graph<Long, DefaultEdge>} kept in sync on
     * every {@link #addNodeSilently}/{@link #removeNodeWithoutRebuilding}/{@link
     * #updateBlockedConnections}/{@link #updateMark} call) -- but this method was, and remains, the *only*
     * caller that ever read that graph (besides {@link #describeMemoryProxy}'s edge count, itself computed
     * on demand now too). Maintaining a persistent structure across PipeNet's hottest paths, purely to speed
     * up one comparatively infrequent, already-cheap-on-its-own query, was backwards: this BFS costs exactly
     * what a graph-library traversal would (both are O(component size)), just without paying any per-hop
     * maintenance cost on every add/remove/connection-change PipeNet makes for every pipe in the world, and
     * without a boxed-{@code Long}-keyed graph library's own bookkeeping overhead on top.
     * <p>
     * (A JGraphT-backed version briefly existed between these two states, first via {@code
     * ConnectivityInspector} -- discovered via a real playtest with the PipeNet execution-trace dev tool, see
     * {@link #setTraceEnabled}, to have been misused: it partitions and caches the *entire* graph on first
     * query, so a fresh instance per call re-paid that whole-net cost every time -- then via {@link
     * org.jgrapht.traverse.BreadthFirstIterator}, which fixed that specific bug but not the underlying
     * per-hot-path maintenance cost the graph itself added; this version removes the graph entirely instead.)
     */
    protected HashMap<BlockPos, Node<NodeDataType>> findAllConnectedBlocks(BlockPos startPos) {
        long start = traceEnabled ? System.nanoTime() : 0;
        HashMap<BlockPos, Node<NodeDataType>> observedSet = new HashMap<>();
        observedSet.put(startPos, getNodeAt(startPos));
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(startPos);
        while (!frontier.isEmpty()) {
            BlockPos pos = frontier.poll();
            Node<NodeDataType> node = observedSet.get(pos);
            for (EnumFacing facing : EnumFacing.VALUES) {
                BlockPos offsetPos = pos.offset(facing);
                if (observedSet.containsKey(offsetPos)) continue;
                Node<NodeDataType> neighbour = getNodeAt(offsetPos);
                if (neighbour != null && canNodesConnect(node, facing, neighbour, this)) {
                    observedSet.put(offsetPos, neighbour);
                    frontier.add(offsetPos);
                }
            }
        }
        if (traceEnabled) {
            traceStats.recordFindConnected(System.nanoTime() - start, observedSet.size());
            PipeNetTraceLog.log(traceLabel,
                    "findAllConnectedBlocks(" + startPos + ") -> " + observedSet.size() + " node(s)");
        }
        return observedSet;
    }

    // called when node is removed to rebuild network
    protected void rebuildNetworkOnNodeRemoval(BlockPos nodePos, Node<NodeDataType> selfNode) {
        // The removed node's entry is already gone from nodeByBlockPos by this point (see
        // removeNodeWithoutRebuilding), so its former degree is recomputed here from scratch via
        // canNodesConnect against whichever neighbours are still present. This is the *true* degree (an
        // actual edge existed), not just "a node happens to occupy this
        // neighbouring position" (which the original implementation counted, and which could overcount when
        // an adjacent same-net member wasn't actually directly connected via canNodesConnect, e.g. blocked or
        // mark-incompatible but still reachable some other way) -- only ever making this fast path skip the
        // full check *more* often, which is safe: a vertex of true degree <=1 can never be a cut vertex.
        int trueDegree = 0;
        for (EnumFacing facing : EnumFacing.VALUES) {
            BlockPos offsetPos = nodePos.offset(facing);
            Node<NodeDataType> neighbour = getNodeAt(offsetPos);
            if (neighbour != null && canNodesConnect(selfNode, facing, neighbour, this)) {
                trueDegree++;
            }
        }
        // if we are connected only on one side or not connected at all, we don't need to find connected blocks
        // because they are only on on side or doesn't exist at all
        // this saves a lot of performance in big networks, which are quite big to depth-first them fastly
        if (trueDegree >= 2) {
            for (EnumFacing facing : EnumFacing.VALUES) {
                BlockPos offsetPos = nodePos.offset(facing);
                Node<NodeDataType> secondNode = getNodeAt(offsetPos);
                if (secondNode == null || !canNodesConnect(selfNode, facing, secondNode, this)) {
                    // if there isn't any neighbour node, or it wasn't connected with us, just skip it
                    continue;
                }
                HashMap<BlockPos, Node<NodeDataType>> thisENet = findAllConnectedBlocks(offsetPos);
                if (getAllNodes().equals(thisENet)) {
                    // if cable on some direction contains all nodes of this network
                    // the network didn't change so keep it as is
                    break;
                } else {
                    // and use them to create new network with caching active nodes set
                    PipeNet<NodeDataType> energyNet = worldData.createNetInstance();
                    // remove blocks that aren't connected with this network
                    thisENet.keySet().forEach(this::removeNodeWithoutRebuilding);
                    energyNet.transferNodeData(thisENet, this);
                    worldData.addPipeNet(energyNet);
                }
            }
        }
        if (getAllNodes().isEmpty()) {
            // if this energy net is empty now, remove it
            worldData.removePipeNet(this);
            if (traceEnabled) {
                // there's nothing left to track -- without this, a traced net that gets fully torn down keeps
                // reporting itself (as "nodes=0") in the periodic summary forever, since nothing else ever turns
                // tracing back off for it and PipeNetTraceTickHandler's WeakHashMap-backed registry only drops an
                // entry once the object itself becomes unreachable and gets garbage-collected, which can take an
                // arbitrarily long time (or never happen at all, if something incidental still holds a reference).
                setTraceEnabled(false, null);
            }
        }
        onNodeConnectionsUpdate();
        worldData.markDirty();
    }

    protected boolean areNodesCustomContactable(NodeDataType first, NodeDataType second,
                                                PipeNet<NodeDataType> secondNodePipeNet) {
        return true;
    }

    protected boolean canAttachNode(NodeDataType nodeData) {
        return true;
    }

    /**
     * Called during network split when one net needs to transfer some of it's nodes to another one
     * Use this for diving old net contents according to node amount of new network
     * For example, for fluid pipes it would remove amount of fluid contained in old nodes
     * from parent network and add it to it's own tank, keeping network contents when old network is split
     * Note that it should be called when parent net doesn't have transferredNodes in allNodes already
     */
    protected void transferNodeData(Map<BlockPos, Node<NodeDataType>> transferredNodes,
                                    PipeNet<NodeDataType> parentNet) {
        transferredNodes.forEach(this::addNodeSilently);
        onNodeConnectionsUpdate();
        worldData.markDirty();
    }

    /**
     * Serializes node data into specified tag compound
     * Used for writing persistent node data
     */
    protected abstract void writeNodeData(NodeDataType nodeData, NBTTagCompound tagCompound);

    /**
     * Deserializes node data from specified tag compound
     * Used for reading persistent node data
     */
    protected abstract NodeDataType readNodeData(NBTTagCompound tagCompound);

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag("Nodes", serializeAllNodeList(nodeByBlockPos));
        return compound;
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        this.nodeByBlockPos.clear();
        this.ownedChunks.clear();
        deserializeAllNodeList(nbt.getCompoundTag("Nodes"));
    }

    protected void deserializeAllNodeList(NBTTagCompound compound) {
        NBTTagList allNodesList = compound.getTagList("NodeIndexes", NBT.TAG_COMPOUND);
        NBTTagList wirePropertiesList = compound.getTagList("WireProperties", NBT.TAG_COMPOUND);
        Int2ObjectMap<NodeDataType> readProperties = new Int2ObjectOpenHashMap<>();

        for (int i = 0; i < wirePropertiesList.tagCount(); i++) {
            NBTTagCompound propertiesTag = wirePropertiesList.getCompoundTagAt(i);
            int wirePropertiesIndex = propertiesTag.getInteger("index");
            NodeDataType nodeData = readNodeData(propertiesTag);
            readProperties.put(wirePropertiesIndex, nodeData);
        }

        for (int i = 0; i < allNodesList.tagCount(); i++) {
            NBTTagCompound nodeTag = allNodesList.getCompoundTagAt(i);
            int x = nodeTag.getInteger("x");
            int y = nodeTag.getInteger("y");
            int z = nodeTag.getInteger("z");
            int wirePropertiesIndex = nodeTag.getInteger("index");
            BlockPos blockPos = new BlockPos(x, y, z);
            NodeDataType nodeData = readProperties.get(wirePropertiesIndex);
            int openConnections = nodeTag.getInteger("open");
            int mark = nodeTag.getInteger("mark");
            boolean isNodeActive = nodeTag.getBoolean("active");
            addNodeSilently(blockPos, new Node<>(nodeData, openConnections, mark, isNodeActive));
        }
    }

    protected NBTTagCompound serializeAllNodeList(Map<BlockPos, Node<NodeDataType>> allNodes) {
        NBTTagCompound compound = new NBTTagCompound();
        NBTTagList allNodesList = new NBTTagList();
        NBTTagList wirePropertiesList = new NBTTagList();
        Object2IntMap<NodeDataType> alreadyWritten = new Object2IntOpenHashMap<>(10, 0.5f);
        alreadyWritten.defaultReturnValue(-1);
        int currentIndex = 0;

        for (Entry<BlockPos, Node<NodeDataType>> entry : allNodes.entrySet()) {
            BlockPos nodePos = entry.getKey();
            Node<NodeDataType> node = entry.getValue();
            NBTTagCompound nodeTag = new NBTTagCompound();
            nodeTag.setInteger("x", nodePos.getX());
            nodeTag.setInteger("y", nodePos.getY());
            nodeTag.setInteger("z", nodePos.getZ());
            int wirePropertiesIndex = alreadyWritten.getInt(node.data);
            if (wirePropertiesIndex == -1) {
                wirePropertiesIndex = currentIndex;
                alreadyWritten.put(node.data, wirePropertiesIndex);
                currentIndex++;
            }
            nodeTag.setInteger("index", wirePropertiesIndex);
            if (node.mark != Node.DEFAULT_MARK) {
                nodeTag.setInteger("mark", node.mark);
            }
            if (node.openConnections > 0) {
                nodeTag.setInteger("open", node.openConnections);
            }
            if (node.isActive) {
                nodeTag.setBoolean("active", true);
            }
            allNodesList.appendTag(nodeTag);
        }

        for (Object2IntMap.Entry<NodeDataType> entry : alreadyWritten.object2IntEntrySet()) {
            NBTTagCompound propertiesTag = new NBTTagCompound();
            propertiesTag.setInteger("index", entry.getIntValue());
            writeNodeData(entry.getKey(), propertiesTag);
            wirePropertiesList.appendTag(propertiesTag);
        }

        compound.setTag("NodeIndexes", allNodesList);
        compound.setTag("WireProperties", wirePropertiesList);
        return compound;
    }
}
