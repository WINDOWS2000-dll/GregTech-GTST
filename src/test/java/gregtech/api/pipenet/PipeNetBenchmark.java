package gregtech.api.pipenet;

import gregtech.Bootstrap;
import gregtech.api.pipenet.block.IPipeType;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.PipeCoverableImplementation;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class PipeNetBenchmark {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    // ================================================================================================
    // Shared reporting helpers
    // ================================================================================================

    private static void report(String label, long[] samplesNanos) {
        long[] sorted = samplesNanos.clone();
        Arrays.sort(sorted);
        double min = sorted[0] / 1e6;
        double median = sorted[sorted.length / 2] / 1e6;
        double max = sorted[sorted.length - 1] / 1e6;
        double total = Arrays.stream(sorted).sum() / 1e6;
        System.out.printf(
                "[PipeNetBenchmark] %-70s min=%10.3fms  median=%10.3fms  max=%10.3fms  total=%12.3fms  (n=%d)%n",
                label, min, median, max, total, samplesNanos.length);
    }

    // ================================================================================================
    // Scenario A: PipeNetWalker BFS traversal (straight line + comb topologies)
    // ================================================================================================

    private static final class TestWorld extends DummyWorld {

        private final Map<BlockPos, TileEntity> tiles = new HashMap<>();

        @Override
        public TileEntity getTileEntity(@NotNull BlockPos pos) {
            return tiles.get(pos);
        }
    }

    private enum FakePipeType implements IPipeType<Object> {

        INSTANCE;

        @Override
        public float getThickness() {
            return 1.0f;
        }

        @Override
        public Object modifyProperties(Object baseProperties) {
            return baseProperties;
        }

        @Override
        public boolean isPaintable() {
            return false;
        }

        @Override
        public String getName() {
            return "instance";
        }
    }

    private static final class FakePipeTile extends TileEntity implements IPipeTile<FakePipeType, Object> {

        private final Map<BlockPos, FakePipeTile> allTiles;
        private int connections = 0;

        FakePipeTile(Map<BlockPos, FakePipeTile> allTiles) {
            this.allTiles = allTiles;
        }

        void setConnected(EnumFacing... sides) {
            for (EnumFacing side : sides) {
                connections |= 1 << side.getIndex();
            }
        }

        @Override
        public World getPipeWorld() {
            return getWorld();
        }

        @Override
        public BlockPos getPipePos() {
            return getPos();
        }

        @Override
        public @Nullable TileEntity getNeighbor(@NotNull EnumFacing facing) {
            return allTiles.get(getPos().offset(facing));
        }

        @Override
        public void onNeighborChanged(@NotNull EnumFacing facing) {}

        @Override
        public boolean isConnected(EnumFacing side) {
            return (connections & (1 << side.getIndex())) != 0;
        }

        @Override
        public boolean isFaceBlocked(EnumFacing side) {
            return false;
        }

        @Override
        public gregtech.api.pipenet.block.BlockPipe<FakePipeType, Object, ?> getPipeBlock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void transferDataFrom(IPipeTile<FakePipeType, Object> sourceTile) {}

        @Override
        public int getPaintingColor() {
            return 0xFFFFFF;
        }

        @Override
        public void setPaintingColor(int paintingColor) {}

        @Override
        public boolean isPainted() {
            return false;
        }

        @Override
        public int getDefaultPaintingColor() {
            return 0xFFFFFF;
        }

        @Override
        public int getConnections() {
            return connections;
        }

        @Override
        public int getNumConnections() {
            return Integer.bitCount(connections);
        }

        @Override
        public void setConnection(EnumFacing side, boolean connected, boolean fromNeighbor) {}

        @Override
        public int getBlockedConnections() {
            return 0;
        }

        @Override
        public void setFaceBlocked(EnumFacing side, boolean blocked) {}

        @Override
        public int getVisualConnections() {
            return connections;
        }

        @Override
        public FakePipeType getPipeType() {
            return FakePipeType.INSTANCE;
        }

        @Override
        public Object getNodeData() {
            return new Object();
        }

        @Override
        public PipeCoverableImplementation getCoverableImplementation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable gregtech.api.unification.material.Material getFrameMaterial() {
            return null;
        }

        @Override
        public boolean supportsTicking() {
            return false;
        }

        @Override
        public IPipeTile<FakePipeType, Object> setSupportsTicking() {
            return this;
        }

        @Override
        public boolean canPlaceCoverOnSide(EnumFacing side) {
            return false;
        }

        @Override
        public <C> C getCapability(Capability<C> capability, EnumFacing side) {
            return null;
        }

        @Override
        public <C> C getCapabilityInternal(Capability<C> capability, EnumFacing side) {
            return null;
        }

        @Override
        public void notifyBlockUpdate() {}

        @Override
        public void writeCoverCustomData(int id, Consumer<PacketBuffer> writer) {}

        @Override
        public void markAsDirty() {}

        @Override
        public boolean isValidTile() {
            return true;
        }

        @Override
        public void scheduleChunkForRenderUpdate() {}
    }

    private static final class BenchWalker extends PipeNetWalker<FakePipeTile> {

        private long visited = 0;

        static BenchWalker create(World world, BlockPos start) {
            return new BenchWalker(world, start, 1);
        }

        private BenchWalker(World world, BlockPos start, int walkedBlocks) {
            super(world, start, walkedBlocks);
        }

        @Override
        protected PipeNetWalker<FakePipeTile> createSubWalker(World world, EnumFacing facingToNextPos,
                                                              BlockPos nextPos, int walkedBlocks) {
            return new BenchWalker(world, nextPos, walkedBlocks);
        }

        @Override
        protected void checkPipe(FakePipeTile pipeTile, BlockPos pos) {
            visited++;
        }

        @Override
        protected void checkNeighbour(FakePipeTile pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                      TileEntity neighbourTile) {}

        @Override
        protected Class<FakePipeTile> getBasePipeClass() {
            return FakePipeTile.class;
        }
    }

    private static Map<BlockPos, FakePipeTile> buildStraightLine(BlockPos base, int length) {
        Map<BlockPos, FakePipeTile> tiles = new HashMap<>();
        for (int i = 0; i < length; i++) {
            BlockPos pos = base.add(i, 0, 0);
            FakePipeTile tile = new FakePipeTile(tiles);
            tile.setPos(pos);
            List<EnumFacing> sides = new ArrayList<>();
            if (i > 0) sides.add(EnumFacing.WEST);
            if (i < length - 1) sides.add(EnumFacing.EAST);
            tile.setConnected(sides.toArray(new EnumFacing[0]));
            tiles.put(pos, tile);
        }
        return tiles;
    }

    private static Map<BlockPos, FakePipeTile> buildComb(BlockPos base, int trunkLength) {
        Map<BlockPos, FakePipeTile> tiles = new HashMap<>();
        for (int i = 0; i < trunkLength; i++) {
            BlockPos trunkPos = base.add(i, 0, 0);
            FakePipeTile trunk = new FakePipeTile(tiles);
            trunk.setPos(trunkPos);
            tiles.put(trunkPos, trunk);

            BlockPos stubPos = trunkPos.offset(EnumFacing.SOUTH);
            FakePipeTile stub = new FakePipeTile(tiles);
            stub.setPos(stubPos);
            stub.setConnected(EnumFacing.NORTH);
            tiles.put(stubPos, stub);
        }
        for (int i = 0; i < trunkLength; i++) {
            FakePipeTile trunk = tiles.get(base.add(i, 0, 0));
            List<EnumFacing> sides = new ArrayList<>();
            if (i > 0) sides.add(EnumFacing.WEST);
            if (i < trunkLength - 1) sides.add(EnumFacing.EAST);
            sides.add(EnumFacing.SOUTH);
            trunk.setConnected(sides.toArray(new EnumFacing[0]));
        }
        return tiles;
    }

    private static long[] timeTraversals(TestWorld world, BlockPos start, int trials) {
        long[] samples = new long[trials];
        for (int t = 0; t < trials; t++) {
            BenchWalker walker = BenchWalker.create(world, start);
            long begin = System.nanoTime();
            walker.traversePipeNet();
            samples[t] = System.nanoTime() - begin;
        }
        return samples;
    }

    @Test
    void bfsTraversalBenchmark() {
        int trials = 20;
        for (int length : new int[] { 1_000, 10_000, 100_000 }) {
            TestWorld world = new TestWorld();
            world.tiles.putAll(buildStraightLine(new BlockPos(0, 200, 0), length));
            long[] samples = timeTraversals(world, new BlockPos(0, 200, 0), trials);
            report("straight line, length=" + length, samples);
        }

        int combTrunkLength = 1_500;
        TestWorld combWorld = new TestWorld();
        combWorld.tiles.putAll(buildComb(new BlockPos(0, 210, 0), combTrunkLength));
        long[] combSamples = timeTraversals(combWorld, new BlockPos(0, 210, 0), trials);
        report("comb, trunkLength=" + combTrunkLength, combSamples);

        int largeCombTrunkLength = 50_000;
        TestWorld largeCombWorld = new TestWorld();
        largeCombWorld.tiles.putAll(buildComb(new BlockPos(0, 220, 0), largeCombTrunkLength));
        try {
            long[] largeCombSamples = timeTraversals(largeCombWorld, new BlockPos(0, 220, 0), 5);
            report("comb, trunkLength=" + largeCombTrunkLength, largeCombSamples);
        } catch (StackOverflowError e) {
            System.out.printf(
                    "[PipeNetBenchmark] comb, trunkLength=%-10d CRASHED with StackOverflowError (this is the exact " +
                            "failure mode phase 1a's iterative rewrite eliminates)%n",
                    largeCombTrunkLength);
        }
    }

    // ================================================================================================
    // Scenario B: node removal churn (findAllConnectedBlocks-backed split detection)
    // ================================================================================================

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
            super("benchmark");
        }

        @Override
        protected TestPipeNet createNetInstance() {
            return new TestPipeNet(this);
        }
    }

    private static final int ALL_OPEN = 0b111111;

    @Test
    void nodeRemovalChurnBenchmark() {
        int length = 100_000;
        int removals = 500;

        TestWorldPipeNet world = new TestWorldPipeNet();
        BlockPos base = new BlockPos(0, 0, 0);
        for (int i = 0; i < length; i++) {
            world.addNode(base.add(i, 0, 0), new Object(), Node.DEFAULT_MARK, ALL_OPEN, false);
        }

        long[] samples = new long[removals];
        for (int i = 0; i < removals; i++) {
            int x = (i + 1) * length / (removals + 1);
            BlockPos pos = base.add(x, 0, 0);
            long begin = System.nanoTime();
            world.removeNode(pos);
            samples[i] = System.nanoTime() - begin;
        }
        report("interior removals on length=" + length + " line, removals=" + removals, samples);
    }

    @Test
    void trimmingALeafFromAHugeNetBenchmark() {
        int bigNetSize = 100_000;
        int cycles = 200;

        TestWorldPipeNet world = new TestWorldPipeNet();
        BlockPos bigBase = new BlockPos(0, 0, 0);
        for (int i = 0; i < bigNetSize; i++) {
            world.addNode(bigBase.add(i, 0, 0), new Object(), Node.DEFAULT_MARK, ALL_OPEN, false);
        }

        BlockPos leafPos = bigBase.add(-1, 0, 0); // adjacent to the big net's x=0 end only

        long[] samples = new long[cycles];
        for (int c = 0; c < cycles; c++) {
            world.addNode(leafPos, new Object(), Node.DEFAULT_MARK, ALL_OPEN, false);
            long begin = System.nanoTime();
            world.removeNode(leafPos); // degree-1 removal -- no split possible, but still exercises the fast
                                       // path's own trueDegree computation; see the variant below for the
                                       // split-triggering case
            samples[c] = System.nanoTime() - begin;
        }
        report("trim degree-1 leaf off bigNetSize=" + bigNetSize + " net, cycles=" + cycles, samples);

        BlockPos leafPos2 = bigBase.add(-1, 1, 0);
        BlockPos leafPos3 = bigBase.add(-1, 2, 0);
        world.addNode(leafPos, new Object(), Node.DEFAULT_MARK, ALL_OPEN, false);
        world.addNode(leafPos2, new Object(), Node.DEFAULT_MARK, ALL_OPEN, false);
        world.addNode(leafPos3, new Object(), Node.DEFAULT_MARK, ALL_OPEN, false);
        long[] blockSamples = new long[cycles];
        for (int c = 0; c < cycles; c++) {
            long begin = System.nanoTime();
            world.updateBlockedConnections(leafPos, EnumFacing.EAST, true);
            blockSamples[c] = System.nanoTime() - begin;
            world.updateBlockedConnections(leafPos, EnumFacing.EAST, false);
        }
        report("block/unblock a 3-node leaf's link to bigNetSize=" + bigNetSize + " net, cycles=" + cycles,
                blockSamples);
    }

    // ================================================================================================
    // Scenario D: union-by-size pathological pattern (small net repeatedly bridged onto a large one)
    // ================================================================================================

    @Test
    void unionBySizeBenchmark() {
        int bigNetSize = 100_000;
        int cycles = 50;

        TestWorldPipeNet world = new TestWorldPipeNet();
        BlockPos bigBase = new BlockPos(1_000, 0, 0);
        for (int i = 0; i < bigNetSize; i++) {
            world.addNode(bigBase.add(i, 0, 0), new Object(), Node.DEFAULT_MARK, ALL_OPEN, false);
        }

        BlockPos smallPos = bigBase.add(-2, 0, 0);
        BlockPos bridgePos = bigBase.add(-1, 0, 0);

        long[] samples = new long[cycles];
        for (int c = 0; c < cycles; c++) {
            world.addNode(smallPos, new Object(), Node.DEFAULT_MARK, ALL_OPEN, false);
            long begin = System.nanoTime();
            world.addNode(bridgePos, new Object(), Node.DEFAULT_MARK, ALL_OPEN, false);
            samples[c] = System.nanoTime() - begin;
            // untimed cleanup: restore to a lone bigNetSize-node net for the next cycle
            world.removeNode(bridgePos);
            world.removeNode(smallPos);
        }
        report("bridge-small-into-big, bigNetSize=" + bigNetSize + ", cycles=" + cycles, samples);
    }
}
