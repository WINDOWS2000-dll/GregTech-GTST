package gregtech.common.pipelike.optical.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.pipenet.PipeNetWalker;
import gregtech.api.util.GTUtility;
import gregtech.common.pipelike.optical.BlockOpticalPipe;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import org.jetbrains.annotations.Nullable;

public class OpticalNetWalker extends PipeNetWalker<TileEntityOpticalPipe> {

    public static final OpticalRoutePath FAILED_MARKER = new OpticalRoutePath(null, null, 0, LongSets.EMPTY_SET);

    @Nullable
    public static OpticalRoutePath createNetData(World world, BlockPos sourcePipe, EnumFacing faceToSourceHandler) {
        OpticalNetWalker walker = new OpticalNetWalker(world, sourcePipe, 1, new LongOpenHashSet());
        walker.sourcePipe = sourcePipe;
        walker.facingToHandler = faceToSourceHandler;
        walker.traversePipeNet();
        return walker.isFailed() ? FAILED_MARKER : walker.routePath;
    }

    private OpticalRoutePath routePath;
    private BlockPos sourcePipe;
    private EnumFacing facingToHandler;
    /**
     * Positions visited by this walker's own lineage so far (root-to-current), used to build the eventual
     * {@link OpticalRoutePath}'s dependency set -- see {@link #checkPipe}.
     */
    private final LongSet path;

    protected OpticalNetWalker(World world, BlockPos sourcePipe, int distance, LongSet path) {
        super(world, sourcePipe, distance);
        this.path = path;
    }

    @Override
    protected PipeNetWalker<TileEntityOpticalPipe> createSubWalker(World world, EnumFacing facingToNextPos,
                                                                   BlockPos nextPos, int walkedBlocks) {
        OpticalNetWalker walker = new OpticalNetWalker(world, nextPos, walkedBlocks, new LongOpenHashSet(path));
        walker.facingToHandler = facingToHandler;
        walker.sourcePipe = sourcePipe;
        return walker;
    }

    @Override
    protected void checkPipe(TileEntityOpticalPipe pipeTile, BlockPos pos) {
        path.add(pos.toLong());
    }

    @Override
    protected void checkNeighbour(TileEntityOpticalPipe pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                  @Nullable TileEntity neighbourTile) {
        if (neighbourTile == null ||
                (GTUtility.arePosEqual(pipePos, sourcePipe) && faceToNeighbour == facingToHandler)) {
            return;
        }

        if (((OpticalNetWalker) root).routePath == null && isValidRouteTarget(neighbourTile, faceToNeighbour)) {
            ((OpticalNetWalker) root).routePath = new OpticalRoutePath(pipeTile, faceToNeighbour, getWalkedBlocks(),
                    path);
            stop();
        }
    }

    /**
     * True if {@code neighbourTile} exposes one of the two built-in capability types, or one registered via
     * {@link BlockOpticalPipe#registerConnectableCapability} -- see that registry's own JavaDoc for why both
     * this check and {@link BlockOpticalPipe#canPipeConnectToBlock} need to agree on what counts as connectable.
     */
    private static boolean isValidRouteTarget(TileEntity neighbourTile, EnumFacing faceToNeighbour) {
        EnumFacing side = faceToNeighbour.getOpposite();
        if (neighbourTile.hasCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS, side) ||
                neighbourTile.hasCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER, side)) {
            return true;
        }
        for (Capability<?> capability : BlockOpticalPipe.getExtraConnectableCapabilities()) {
            if (neighbourTile.hasCapability(capability, side)) return true;
        }
        return false;
    }

    @Override
    protected Class<TileEntityOpticalPipe> getBasePipeClass() {
        return TileEntityOpticalPipe.class;
    }
}
