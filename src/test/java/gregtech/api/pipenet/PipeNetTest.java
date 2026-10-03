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
