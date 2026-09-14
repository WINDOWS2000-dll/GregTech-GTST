package gregtech.api.pipenet;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for {@link PipeNet#findAllConnectedBlocks} (see that method's own JavaDoc for why it is
 * a plain iterative breadth-first search over {@link PipeNet}'s own node map, with no persistent
 * graph/adjacency structure backing it). Confirms these connectivity queries agree with the network's actual
 * split/merge behaviour across a straight line, a cyclic topology (a redundant path, which stresses branching
 * during the search differently than a simple tree-shaped walk), and node removal that either does or does not
 * sever the network.
 */
class PipeNetTest {

    private static final class TestPipeNet extends PipeNet<Object> {

        TestPipeNet(WorldPipeNet<Object, ? extends PipeNet<Object>> world) {
            super(world);
        }

        @Override
        protected void writeNodeData(Object nodeData, NBTTagCompound tagCompound) {}

        @Override
        protected Object readNodeData(NBTTagCompound tagCompound) {
            return new Object();
        }
    }

    private static final class TestWorldPipeNet extends WorldPipeNet<Object, TestPipeNet> {

        TestWorldPipeNet() {
            super("test");
        }

        @Override
        protected TestPipeNet createNetInstance() {
            return new TestPipeNet(this);
        }
    }

    /** All 6 {@link net.minecraft.util.EnumFacing} bits set, i.e. blocked on no side. */
    private static final int ALL_OPEN = 0b111111;

    private TestWorldPipeNet world;

    @BeforeEach
    void setUp() {
        world = new TestWorldPipeNet();
    }

    private void addNode(BlockPos pos) {
        addNode(pos, Node.DEFAULT_MARK);
    }

    private void addNode(BlockPos pos, int mark) {
        world.addNode(pos, new Object(), mark, ALL_OPEN, false);
    }

    @Test
    void straightLineFormsOneConnectedComponent() {
        BlockPos base = new BlockPos(0, 0, 0);
        for (int i = 0; i < 5; i++) {
            addNode(base.add(i, 0, 0));
        }

        PipeNet<Object> net = world.getNetFromPos(base);
        assertNotNull(net);
        for (int i = 1; i < 5; i++) {
            assertSame(net, world.getNetFromPos(base.add(i, 0, 0)), "all nodes should end up in the same net");
        }

        Map<BlockPos, Node<Object>> connected = net.findAllConnectedBlocks(base);
        assertEquals(5, connected.size());
    }

    @Test
    void cyclicTopologyIsOneConnectedComponentDespiteRedundantPath() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(1, 0, 0);
        BlockPos c = new BlockPos(1, 0, 1);
        BlockPos d = new BlockPos(0, 0, 1);
        addNode(a);
        addNode(b);
        addNode(c);
        addNode(d);

        PipeNet<Object> net = world.getNetFromPos(a);
        assertNotNull(net);
        assertSame(net, world.getNetFromPos(b));
        assertSame(net, world.getNetFromPos(c));
        assertSame(net, world.getNetFromPos(d));
        assertEquals(4, net.findAllConnectedBlocks(a).size());
    }

    @Test
    void removingMiddleOfCycleDoesNotSplitTheNetwork() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(1, 0, 0);
        BlockPos c = new BlockPos(1, 0, 1);
        BlockPos d = new BlockPos(0, 0, 1);
        addNode(a);
        addNode(b);
        addNode(c);
        addNode(d);

        world.removeNode(b);

        PipeNet<Object> net = world.getNetFromPos(a);
        assertNotNull(net);
        assertSame(net, world.getNetFromPos(c));
        assertSame(net, world.getNetFromPos(d));
        assertEquals(3, net.findAllConnectedBlocks(a).size());
    }

    @Test
    void removingMiddleOfStraightLineSplitsIntoTwoNetworks() {
        BlockPos base = new BlockPos(0, 0, 0);
        for (int i = 0; i < 5; i++) {
            addNode(base.add(i, 0, 0));
        }

        world.removeNode(base.add(2, 0, 0));

        PipeNet<Object> leftNet = world.getNetFromPos(base);
        PipeNet<Object> rightNet = world.getNetFromPos(base.add(4, 0, 0));
        assertNotNull(leftNet);
        assertNotNull(rightNet);
        assertNotSame(leftNet, rightNet, "removing the middle node should split the line into two nets");
        assertEquals(2, leftNet.findAllConnectedBlocks(base).size());
        assertEquals(2, rightNet.findAllConnectedBlocks(base.add(4, 0, 0)).size());
    }

    @Test
    void addingConnectingNodeMergesTwoExistingNets() {
        BlockPos left = new BlockPos(0, 0, 0);
        BlockPos gap = new BlockPos(1, 0, 0);
        BlockPos right = new BlockPos(2, 0, 0);
        addNode(left);
        addNode(right);

        assertNotSame(world.getNetFromPos(left), world.getNetFromPos(right),
                "non-adjacent nodes must start out in separate nets");

        addNode(gap);

        PipeNet<Object> net = world.getNetFromPos(left);
        assertNotNull(net);
        assertSame(net, world.getNetFromPos(gap));
        assertSame(net, world.getNetFromPos(right));
        assertEquals(3, net.findAllConnectedBlocks(left).size());
    }

    /**
     * Regression coverage for {@link PipeNet#mergeWithSizeOrdering}: confirms that bridging two pre-existing,
     * differently-sized nets via {@link WorldPipeNet#addNode} always leaves the <em>larger</em> net's object as
     * the survivor, regardless of which side of the bridge happens to be discovered first (see the {@code
     * EnumFacing.VALUES} iteration order in {@link WorldPipeNet#addNode}).
     */
    @Test
    void bridgingViaAddNodePicksLargerNetAsSurvivor() {
        BlockPos largeBase = new BlockPos(0, 0, 0);
        for (int i = 0; i < 10; i++) {
            addNode(largeBase.add(i, 0, 0));
        }
        BlockPos smallBase = new BlockPos(11, 0, 0);
        addNode(smallBase);
        addNode(smallBase.add(1, 0, 0));

        PipeNet<Object> largeNetBefore = world.getNetFromPos(largeBase);
        PipeNet<Object> smallNetBefore = world.getNetFromPos(smallBase);
        assertNotSame(largeNetBefore, smallNetBefore);

        // the bridge at x=10 is adjacent to the large net (WEST, x=9) and the small net (EAST, x=11)
        addNode(largeBase.add(10, 0, 0));

        PipeNet<Object> survivor = world.getNetFromPos(largeBase);
        assertSame(largeNetBefore, survivor, "the larger net should survive the merge");
        assertSame(survivor, world.getNetFromPos(smallBase.add(1, 0, 0)));
        assertEquals(13, survivor.findAllConnectedBlocks(largeBase).size());
    }

    /**
     * As {@link #bridgingViaAddNodePicksLargerNetAsSurvivor}, but triggering the merge through {@link
     * PipeNet#updateMark} instead of {@link WorldPipeNet#addNode}: an isolated single-node "net" (mark
     * incompatible with its neighbour, so it didn't already merge when placed) is made compatible by changing
     * its own mark. {@code updateMark} is called on this smaller net's own (only) node, so without
     * size-ordering, the smaller net would always be the one absorbing (since {@code uniteNetworks} always
     * kept the caller as survivor) -- this confirms the size comparison overrides that and the larger net
     * survives instead. (Deliberately a single-node "small" net, not two: {@code updateMark} only changes the
     * mark of the node it's called on, not every node in its net, so a multi-node small net would have its own
     * internal edge broken by the same mark change instead of testing the merge in isolation.)
     */
    @Test
    void bridgingViaUpdateMarkPicksLargerNetAsSurvivor() {
        BlockPos largeBase = new BlockPos(0, 0, 0);
        for (int i = 0; i < 10; i++) {
            addNode(largeBase.add(i, 0, 0), 1);
        }
        BlockPos smallBase = new BlockPos(10, 0, 0);
        addNode(smallBase, 2);

        PipeNet<Object> largeNetBefore = world.getNetFromPos(largeBase);
        PipeNet<Object> smallNetBefore = world.getNetFromPos(smallBase);
        assertNotSame(largeNetBefore, smallNetBefore, "incompatible marks must keep adjacent nets separate");

        // updateMark is dispatched to smallNetBefore, since smallBase belongs to it
        world.updateMark(smallBase, 1);

        PipeNet<Object> survivor = world.getNetFromPos(smallBase);
        assertSame(largeNetBefore, survivor,
                "the larger net should survive even though updateMark was called on the smaller net's own node");
        assertEquals(11, survivor.findAllConnectedBlocks(largeBase).size());
    }

    /**
     * Regression coverage for the specific hazard {@link PipeNet#mergeWithSizeOrdering}'s doc warns about:
     * a single {@link PipeNet#updateMark} call can trigger more than one merge (one per qualifying facing), and
     * if an earlier merge flips which object survives, later iterations comparing against a stale {@code this}
     * (instead of the refreshed current net) would wrongly treat an already-merged neighbour as a different
     * net. This builds a 5-node ring that already surrounds a single isolated node ({@code A}) on both sides,
     * so a mark change on {@code A} merges it into the ring via one facing (a reversal, since the ring is
     * larger) and then must recognise the *other* facing's neighbour as already part of the same, just-merged
     * net (rather than attempting a second, corrupting merge against a now-dead {@code this}). If the second
     * facing's edge sync were skipped instead (the bug this guards against), the ring would silently degrade
     * into a tree, which the final assertion (connectivity survives removing one of the two connecting nodes)
     * would then fail.
     */
    @Test
    void multipleMergesInOneUpdateMarkCallKeepGraphConsistent() {
        BlockPos a = new BlockPos(10, 0, 0);
        BlockPos west = new BlockPos(9, 0, 0);
        BlockPos east = new BlockPos(11, 0, 0);
        BlockPos westArm = new BlockPos(9, 0, 1);
        BlockPos midArm = new BlockPos(10, 0, 1);
        BlockPos eastArm = new BlockPos(11, 0, 1);

        // the ring: west -- westArm -- midArm -- eastArm -- east, all mark=1, NOT touching `a`'s position
        addNode(west, 1);
        addNode(westArm, 1);
        addNode(midArm, 1);
        addNode(eastArm, 1);
        addNode(east, 1);
        // `a`: adjacent to both `west` and `east`, but mark-incompatible so it stays its own net for now
        addNode(a, 2);

        PipeNet<Object> ringBefore = world.getNetFromPos(west);
        assertNotSame(ringBefore, world.getNetFromPos(a));
        assertEquals(5, ringBefore.findAllConnectedBlocks(west).size());

        // now compatible with the ring on BOTH the WEST and EAST facings in the same call
        world.updateMark(a, 1);

        PipeNet<Object> survivor = world.getNetFromPos(a);
        assertSame(ringBefore, survivor, "the larger (ring) net should survive");
        assertEquals(6, survivor.findAllConnectedBlocks(a).size());

        // if the second (EAST) facing's edge sync were skipped, `a` would only be reachable via `west`;
        // removing `west` would then disconnect it. With both edges present, the `east` side keeps it connected.
        world.removeNode(west);

        PipeNet<Object> afterRemoval = world.getNetFromPos(a);
        assertNotNull(afterRemoval);
        assertSame(afterRemoval, world.getNetFromPos(east),
                "`a` must still be reachable via the east side of the ring after removing the west connector");
        assertEquals(5, afterRemoval.findAllConnectedBlocks(a).size());
    }

    /**
     * Regression coverage for the PipeNet execution-trace dev tool (see {@link PipeNet#setTraceEnabled}):
     * confirms that enabling tracing actually causes {@link PipeNet#addNode}/{@link
     * PipeNet#findAllConnectedBlocks} to record into {@link PipeNetTraceStats}, and that stats stay untouched
     * (all zero) while tracing is off.
     */
    @Test
    void enablingTraceRecordsStatsForAddNodeAndFindConnected() {
        BlockPos base = new BlockPos(0, 0, 0);
        addNode(base);

        PipeNet<Object> net = world.getNetFromPos(base);
        assertNotNull(net);
        assertEquals(0, net.getTraceStats().snapshot().addNodeCount, "untraced nets must record nothing");

        net.setTraceEnabled(true, "test-net");
        addNode(base.add(1, 0, 0));
        net.findAllConnectedBlocks(base);

        PipeNetTraceStats.Snapshot after = net.getTraceStats().snapshot();
        assertEquals(1, after.addNodeCount, "the addNode call made while tracing was on must be recorded");
        assertTrue(after.findConnectedCount >= 1,
                "the findAllConnectedBlocks call made while tracing was on must be recorded");

        net.setTraceEnabled(false);
        addNode(base.add(2, 0, 0));
        assertEquals(1, net.getTraceStats().snapshot().addNodeCount,
                "no further recording should happen once tracing is off again");
    }

    /**
     * Regression coverage for {@link PipeNet#uniteNetworks}' trace-following behaviour: confirms tracing
     * transfers onto whichever net survives a merge (see that method's own note on why tracing follows the
     * network's identity, not a specific Java object), even when the traced net is the one absorbed (the
     * common case here, since {@link PipeNet#mergeWithSizeOrdering} keeps the *larger* net, and the smaller
     * one is what a player would realistically have started tracing first).
     */
    @Test
    void tracingFollowsTheSurvivingNetThroughAMerge() {
        BlockPos largeBase = new BlockPos(0, 0, 0);
        for (int i = 0; i < 10; i++) {
            addNode(largeBase.add(i, 0, 0));
        }
        BlockPos smallBase = new BlockPos(20, 0, 0);
        addNode(smallBase);

        PipeNet<Object> largeNetBefore = world.getNetFromPos(largeBase);
        PipeNet<Object> smallNetBefore = world.getNetFromPos(smallBase);
        smallNetBefore.setTraceEnabled(true, "small-net-label");

        // bridge them: x=19 is adjacent to both the large net (x=9..18? no -- large net ends at x=9; use a
        // midpoint bridge instead) -- place the bridge directly between the two bases.
        addNode(new BlockPos(19, 0, 0));
        addNode(new BlockPos(18, 0, 0));
        addNode(new BlockPos(17, 0, 0));
        addNode(new BlockPos(16, 0, 0));
        addNode(new BlockPos(15, 0, 0));
        addNode(new BlockPos(14, 0, 0));
        addNode(new BlockPos(13, 0, 0));
        addNode(new BlockPos(12, 0, 0));
        addNode(new BlockPos(11, 0, 0));
        addNode(new BlockPos(10, 0, 0)); // this closes the gap between the large net (x=0..9) and x=11..19/small

        PipeNet<Object> survivor = world.getNetFromPos(largeBase);
        assertSame(largeNetBefore, survivor, "the larger net should still survive the merge");
        assertTrue(survivor.isTraceEnabled(), "tracing must follow onto the surviving net");
        assertEquals("small-net-label", survivor.getTraceLabel());
        assertFalse(smallNetBefore.isTraceEnabled(), "the absorbed (discarded) net's own trace state is cleared");
    }

    /**
     * Regression coverage for a self-audit finding: a traced net that gets torn down entirely (its last node
     * removed, triggering {@code rebuildNetworkOnNodeRemoval}'s {@code worldData.removePipeNet(this)} path) must
     * stop tracing itself. Without this, the net object keeps being registered with the periodic-summary tick
     * handler indefinitely (or until it happens to be garbage-collected, which is not guaranteed to happen
     * promptly, or at all, if something incidental still references it), spamming meaningless "nodes=0"
     * summaries forever -- exactly what was observed at the tail of a real playtest's trace log.
     */
    @Test
    void tracingIsAutomaticallyDisabledWhenTheTracedNetBecomesEmpty() {
        BlockPos pos = new BlockPos(0, 0, 0);
        addNode(pos);

        PipeNet<Object> net = world.getNetFromPos(pos);
        assertNotNull(net);
        net.setTraceEnabled(true, "solo-net");

        world.removeNode(pos); // the net's only node is removed -> it becomes empty -> discarded

        assertFalse(net.isTraceEnabled(), "tracing must be disabled once the traced net has no nodes left");
        assertNull(net.getTraceLabel());
    }

    /**
     * Regression coverage for {@link PipeNet#describeMemoryProxy}'s edge count, computed on demand by scanning
     * every node's neighbours (see that method's own note on why there is no persistent structure to just read
     * a count from). A 3-node straight line has exactly 2 edges; a 4-node cycle (see {@link
     * #cyclicTopologyIsOneConnectedComponentDespiteRedundantPath}'s topology) has exactly 4.
     */
    @Test
    void describeMemoryProxyReportsTheActualEdgeCount() {
        for (int i = 0; i < 3; i++) {
            addNode(new BlockPos(i, 0, 0));
        }
        PipeNet<Object> net = world.getNetFromPos(new BlockPos(0, 0, 0));
        assertNotNull(net);
        assertTrue(net.describeMemoryProxy().contains("nodes=3, edges=2"), net.describeMemoryProxy());
    }

    @Test
    void describeMemoryProxyReportsFourEdgesForAFourNodeCycle() {
        BlockPos a = new BlockPos(0, 0, 0);
        BlockPos b = new BlockPos(1, 0, 0);
        BlockPos c = new BlockPos(1, 0, 1);
        BlockPos d = new BlockPos(0, 0, 1);
        addNode(a);
        addNode(b);
        addNode(c);
        addNode(d);

        PipeNet<Object> net = world.getNetFromPos(a);
        assertNotNull(net);
        assertTrue(net.describeMemoryProxy().contains("nodes=4, edges=4"), net.describeMemoryProxy());
    }
}
