package gregtech.common.pipelike.optical.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.IDataAccessHatch;
import gregtech.api.capability.IOpticalComputationProvider;
import gregtech.api.capability.IOpticalDataAccessHatch;
import gregtech.api.pipenet.IRoutePath;
import gregtech.common.pipelike.optical.tile.TileEntityOpticalPipe;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OpticalRoutePath implements IRoutePath<TileEntityOpticalPipe> {

    private final TileEntityOpticalPipe targetPipe;
    private final EnumFacing faceToHandler;
    private final int distance;
    /**
     * Every pipe position the walk actually traversed to reach {@link #targetPipe}, from the source pipe
     * (inclusive) to {@link #targetPipe} (inclusive), encoded via {@link BlockPos#toLong()} to avoid one
     * {@code BlockPos} allocation per visited position. Unlike {@code LaserNetWalker}, {@code
     * OpticalNetWalker} traverses in all 6 directions (see its lack of a {@code getSurroundingPipeSides}
     * override), so this path can bend around corners -- it must be recorded explicitly rather than derived
     * from the two endpoints. Used by {@link OpticalPipeNet} (via {@link #passesThrough}) to invalidate only
     * the cache entries whose route is actually affected by a given position change, instead of clearing the
     * whole net's cache on every connection/unload event.
     */
    private final LongSet traversedPositions;

    public OpticalRoutePath(TileEntityOpticalPipe targetPipe, EnumFacing faceToHandler, int distance,
                            LongSet traversedPositions) {
        this.targetPipe = targetPipe;
        this.faceToHandler = faceToHandler;
        this.distance = distance;
        this.traversedPositions = traversedPositions;
    }

    /**
     * @param pos the position to check
     * @return whether the walk that produced this route actually passed through {@code pos}
     */
    public boolean passesThrough(@NotNull BlockPos pos) {
        return traversedPositions.contains(pos.toLong());
    }

    @NotNull
    @Override
    public TileEntityOpticalPipe getTargetPipe() {
        return targetPipe;
    }

    @NotNull
    @Override
    public EnumFacing getTargetFacing() {
        return faceToHandler;
    }

    public int getDistance() {
        return distance;
    }

    @Nullable
    public IOpticalDataAccessHatch getDataHatch() {
        IDataAccessHatch dataAccessHatch = getTargetCapability(GregtechTileCapabilities.CAPABILITY_DATA_ACCESS);
        return dataAccessHatch instanceof IOpticalDataAccessHatch opticalHatch ? opticalHatch : null;
    }

    @Nullable
    public IOpticalComputationProvider getComputationHatch() {
        return getTargetCapability(GregtechTileCapabilities.CABABILITY_COMPUTATION_PROVIDER);
    }
}
