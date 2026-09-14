package gregtech.common.pipelike.laser.net;

import gregtech.api.capability.GregtechTileCapabilities;
import gregtech.api.capability.ILaserContainer;
import gregtech.api.pipenet.IRoutePath;
import gregtech.common.pipelike.laser.tile.TileEntityLaserPipe;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.LongSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// jabel moment
public class LaserRoutePath implements IRoutePath<TileEntityLaserPipe> {

    private final TileEntityLaserPipe targetPipe;
    private final EnumFacing faceToHandler;
    private final int distance;
    /**
     * Every pipe position the walk actually traversed to reach {@link #targetPipe}, from the source pipe
     * (inclusive) to {@link #targetPipe} (inclusive), encoded via {@link BlockPos#toLong()} to avoid one
     * {@code BlockPos} allocation per visited position. Used by {@link LaserPipeNet} (via {@link
     * #passesThrough}) to invalidate only the cache entries whose route is actually affected by a given
     * position change, instead of clearing the whole net's cache on every connection/unload event.
     */
    private final LongSet traversedPositions;

    public LaserRoutePath(TileEntityLaserPipe targetPipe, EnumFacing faceToHandler, int distance,
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

    /**
     * Gets the current face to handler
     *
     * @return The face to handler
     */
    @NotNull
    public EnumFacing getFaceToHandler() {
        return faceToHandler;
    }

    @NotNull
    @Override
    public TileEntityLaserPipe getTargetPipe() {
        return targetPipe;
    }

    @NotNull
    @Override
    public EnumFacing getTargetFacing() {
        return faceToHandler;
    }

    /**
     * Gets the manhattan distance traveled during walking
     *
     * @return The distance in blocks
     */
    public int getDistance() {
        return distance;
    }

    /**
     * Gets the handler if it exists
     *
     * @return the handler
     */
    @Nullable
    public ILaserContainer getHandler() {
        return getTargetCapability(GregtechTileCapabilities.CAPABILITY_LASER);
    }
}
