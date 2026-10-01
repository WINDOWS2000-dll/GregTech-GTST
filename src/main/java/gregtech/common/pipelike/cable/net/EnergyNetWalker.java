package gregtech.common.pipelike.cable.net;

import gregtech.api.capability.GregtechCapabilities;
import gregtech.api.capability.IEnergyContainer;
import gregtech.api.pipenet.PipeNetWalker;
import gregtech.common.pipelike.cable.tile.TileEntityCable;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EnergyNetWalker extends PipeNetWalker<TileEntityCable> {

    public static List<EnergyRoutePath> createNetData(World world, BlockPos sourcePipe) {
        if (!(world.getTileEntity(sourcePipe) instanceof TileEntityCable)) {
            return null;
        }
        EnergyNetWalker walker = new EnergyNetWalker(world, sourcePipe, 1, new ArrayList<>());
        walker.traversePipeNet();
        return walker.isFailed() ? null : walker.routes;
    }

    private final List<EnergyRoutePath> routes;
    /**
     * The cables walked so far, from the source pipe up to and including the current one. Grown with spare
     * capacity (like {@link ArrayList}'s own doubling strategy) via {@link #checkPipe} instead of being
     * recreated at exact size on every single hop -- the previous implementation used {@code
     * ArrayUtils.add(pipes, pipeTile)}, which allocates a new array and copies every existing element on
     * *every* hop, costing O(distance walked so far) per hop and therefore O(distance²) in total for a single
     * straight run (the common case for cable networks in real bases). Only {@link #pipeCount} elements
     * (indices {@code [0, pipeCount)}) are valid; anything beyond that is unused spare capacity.
     */
    private TileEntityCable[] pipes = new TileEntityCable[8];
    private int pipeCount = 0;
    private int loss;

    protected EnergyNetWalker(World world, BlockPos sourcePipe, int walkedBlocks, List<EnergyRoutePath> routes) {
        super(world, sourcePipe, walkedBlocks);
        this.routes = routes;
    }

    @Override
    protected PipeNetWalker<TileEntityCable> createSubWalker(World world, EnumFacing facingToNextPos, BlockPos nextPos,
                                                             int walkedBlocks) {
        EnergyNetWalker walker = new EnergyNetWalker(world, nextPos, walkedBlocks, routes);
        walker.loss = loss;
        // branching is the one point where an exact-size copy is actually required: from here on, this walker
        // and the new sub-walker each grow their own array independently via checkPipe, so they can no longer
        // share one backing array (or a sibling's future append would silently overwrite into this one's spare
        // capacity, or vice versa). This copy is O(pipeCount), paid once per branch -- not once per hop.
        walker.pipes = Arrays.copyOf(pipes, pipeCount);
        walker.pipeCount = pipeCount;
        return walker;
    }

    @Override
    protected void checkPipe(TileEntityCable pipeTile, BlockPos pos) {
        if (pipeCount == pipes.length) {
            pipes = Arrays.copyOf(pipes, pipes.length * 2);
        }
        pipes[pipeCount++] = pipeTile;
        loss += pipeTile.getNodeData().getLossPerBlock();
    }

    @Override
    protected void checkNeighbour(TileEntityCable pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                  @Nullable TileEntity neighbourTile) {
        // assert that the last added pipe is the current pipe
        if (pipeTile != pipes[pipeCount - 1]) throw new IllegalStateException(
                "The current pipe is not the last added pipe. Something went seriously wrong!");
        if (neighbourTile != null) {
            IEnergyContainer container = neighbourTile.getCapability(GregtechCapabilities.CAPABILITY_ENERGY_CONTAINER,
                    faceToNeighbour.getOpposite());
            if (container != null) {
                // trim to the exact walked length -- pipes may have spare capacity beyond pipeCount, and this
                // array is handed to EnergyRoutePath to keep long-term, so it must be an independent, exact-size
                // snapshot (this walker's own pipes array keeps growing/mutating in place after this point).
                routes.add(new EnergyRoutePath(faceToNeighbour, Arrays.copyOf(pipes, pipeCount), getWalkedBlocks(),
                        loss));
            }
        }
    }

    @Override
    protected Class<TileEntityCable> getBasePipeClass() {
        return TileEntityCable.class;
    }
}
