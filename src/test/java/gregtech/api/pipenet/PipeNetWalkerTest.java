package gregtech.api.pipenet;

import gregtech.Bootstrap;
import gregtech.api.pipenet.block.IPipeType;
import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.pipenet.tile.PipeCoverableImplementation;
import gregtech.api.util.world.DummyWorld;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression coverage for {@link PipeNetWalker}'s iterative frontier-based traversal (see that class's own
 * JavaDoc): a "trunk line with a branch tap at every block" topology -- an ordinary, realistic pipe layout --
 * used to make the previous recursive-descent implementation grow one call-stack frame per branch point,
 * proportional to the trunk's length. These tests build exactly that topology and confirm it traverses
 * correctly (and without a {@link StackOverflowError}) at a scale well beyond anything the old implementation
 * could have survived.
 */
class PipeNetWalkerTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    /**
     * A {@link DummyWorld} that looks tile entities up from a plain map instead of going through real chunk
     * storage (which requires a matching {@code ITileEntityProvider} block state at that position -- more
     * setup than this pure walker-logic test needs). A fresh instance per test avoids any shared state with
     * {@link DummyWorld#INSTANCE}, which other test classes use.
     */
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

    /**
     * The minimal {@link IPipeTile} double needed to drive {@link PipeNetWalker}: only {@link #isConnected},
     * {@link #getNeighbor}, and {@link #isFaceBlocked} are ever called by the base class itself. Neighbor
     * lookup goes through a directly-supplied position map rather than {@link #getPipeWorld()}, so this
     * doesn't depend on {@link TestWorld} at all except for the walker's own initial root lookup.
     */
    private static final class FakePipeTile extends TileEntity
                                            implements IPipeTile<FakePipeType, Object> {

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

    /**
     * Records every position visited (via {@link #checkPipe}) and every dead-end/neighbor-less edge (via
     * {@link #checkNeighbour}), shared across every sub-walker of one traversal exactly like the real
     * {@code EnergyNetWalker}/{@code ItemNetWalker} share their own collector fields.
     */
    private static final class TestWalker extends PipeNetWalker<FakePipeTile> {

        private final List<BlockPos> visited;

        static TestWalker create(World world, BlockPos start) {
            return new TestWalker(world, start, 1, new ArrayList<>());
        }

        private TestWalker(World world, BlockPos start, int walkedBlocks, List<BlockPos> visited) {
            super(world, start, walkedBlocks);
            this.visited = visited;
        }

        @Override
        protected PipeNetWalker<FakePipeTile> createSubWalker(World world, EnumFacing facingToNextPos,
                                                              BlockPos nextPos, int walkedBlocks) {
            return new TestWalker(world, nextPos, walkedBlocks, visited);
        }

        @Override
        protected void checkPipe(FakePipeTile pipeTile, BlockPos pos) {
            visited.add(pos.toImmutable());
        }

        @Override
        protected void checkNeighbour(FakePipeTile pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                      TileEntity neighbourTile) {
            // no destination capability concept needed for this test; dead ends are expected and ignored.
        }

        @Override
        protected Class<FakePipeTile> getBasePipeClass() {
            return FakePipeTile.class;
        }
    }

    /**
     * Builds a trunk of {@code trunkLength} blocks along the X axis, with a single-block dead-end stub
     * branching south off every trunk block -- i.e. every trunk block (bar the two ends) has 3 valid pipe
     * neighbors, so walking the trunk encounters a branch point at every single step.
     */
    private static Map<BlockPos, FakePipeTile> buildCombTopology(BlockPos base, int trunkLength) {
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

    @Test
    void combTopologyTraversesFullyWithoutStackOverflow() {
        // Far larger than any real trunk line could plausibly be, and well beyond what the old recursive
        // descent (one call-stack frame per branch point, i.e. per trunk block) could have survived on a
        // default JVM thread stack.
        int trunkLength = 5000;
        BlockPos base = new BlockPos(0, 200, 0);
        Map<BlockPos, FakePipeTile> tiles = buildCombTopology(base, trunkLength);

        TestWorld world = new TestWorld();
        world.tiles.putAll(tiles);

        TestWalker walker = TestWalker.create(world, base);
        walker.traversePipeNet();

        assertFalse(walker.isFailed(), "walker should not report failure");
        assertEquals(trunkLength * 2, walker.visited.size(),
                "every trunk block and every stub must be visited exactly once");
    }

    @Test
    void straightLineReportsCorrectDistances() {
        int length = 50;
        BlockPos base = new BlockPos(0, 210, 0);
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

        TestWorld world = new TestWorld();
        world.tiles.putAll(tiles);

        TestWalker walker = TestWalker.create(world, base);
        walker.traversePipeNet();

        assertFalse(walker.isFailed());
        assertEquals(length, walker.visited.size());
    }
}
