package gregtech.api.pipenet;

import gregtech.api.pipenet.tile.IPipeTile;
import gregtech.api.util.GTLog;
import gregtech.common.pipelike.itempipe.net.ItemNetWalker;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * This is a helper class to get information about a pipe net
 * <p>
 * The walker is written that it will always find the shortest path to any destination
 * <p>
 * On the way it can collect information about the pipes and it's neighbours
 * <p>
 * After creating a walker simply call {@link #traversePipeNet()} to start walking, then you can just collect the data
 * <p>
 * <b>Do not walk a walker more than once</b>
 * <p>
 * For example implementations look at {@link ItemNetWalker}
 * <p>
 * <b>Implementation note:</b> the traversal itself is a breadth-first search driven entirely by
 * {@link #traversePipeNet(int)}'s own loop: every tick, every currently active walker is advanced by exactly
 * one hop via {@link #step(List)}, and whatever it produces (itself, if it has a single successor; new
 * sub-walkers, if it just branched) becomes next tick's frontier. No walker ever calls another walker's step
 * directly -- this bounds the call stack to a small constant depth regardless of how many consecutive branch
 * points a pipe network has (a "trunk line with a branch tap at every block" topology, common in real builds,
 * previously produced a recursive descent one stack frame per branch point, i.e. proportional to the trunk's
 * length). {@link #walkedBlocks} (and therefore every recorded distance) is unaffected by this: it is
 * incremented exactly once per hop regardless of how many ticks that takes to schedule, so this is purely an
 * internal scheduling change, not a behavioral one.
 */
public abstract class PipeNetWalker<T extends IPipeTile<?, ?>> {

    protected PipeNetWalker<T> root;
    @NotNull
    private final World world;
    private Set<T> walked;
    private final List<EnumFacing> nextPipeFacings = new ArrayList<>(5);
    private final List<T> nextPipes = new ArrayList<>(5);
    /** The walker whose branch this one was spawned from, or {@code null} for the root. */
    private PipeNetWalker<T> parentWalker;
    /** Only meaningful once this walker has branched: how many of its children are still unfinished. */
    private int pendingChildCount;
    @NotNull
    private final BlockPos.MutableBlockPos currentPos;
    private T currentPipe;
    private EnumFacing from = null;
    private int walkedBlocks;
    private boolean invalid;
    private boolean running;
    private boolean failed = false;

    protected PipeNetWalker(@NotNull World world, @NotNull BlockPos sourcePipe, int walkedBlocks) {
        this.world = Objects.requireNonNull(world);
        this.walkedBlocks = walkedBlocks;
        this.currentPos = new BlockPos.MutableBlockPos(Objects.requireNonNull(sourcePipe));
        this.root = this;
    }

    /**
     * Creates a sub walker
     * Will be called when a pipe has multiple valid pipes
     *
     * @param world        world
     * @param nextPos      next pos to check
     * @param walkedBlocks distance from source in blocks
     * @return new sub walker
     */
    protected abstract PipeNetWalker<T> createSubWalker(World world, EnumFacing facingToNextPos, BlockPos nextPos,
                                                        int walkedBlocks);

    /**
     * You can increase walking stats here. for example
     *
     * @param pipeTile current checking pipe
     * @param pos      current pipe pos
     */
    protected abstract void checkPipe(T pipeTile, BlockPos pos);

    /**
     * Checks the neighbour of the current pos
     *
     * @param pipePos         current pos
     * @param faceToNeighbour face to neighbour
     * @param neighbourTile   neighbour tile
     */
    protected abstract void checkNeighbour(T pipeTile, BlockPos pipePos, EnumFacing faceToNeighbour,
                                           @Nullable TileEntity neighbourTile);

    /**
     * If the pipe is valid to perform a walk on
     *
     * @param currentPipe     current pipe
     * @param neighbourPipe   neighbour pipe to check
     * @param pipePos         current pos (tile.getPipePos() != pipePos)
     * @param faceToNeighbour face to pipeTile
     * @return if the pipe is valid
     */
    protected boolean isValidPipe(T currentPipe, T neighbourPipe, BlockPos pipePos, EnumFacing faceToNeighbour) {
        return true;
    }

    protected abstract Class<T> getBasePipeClass();

    /**
     * The directions that this net can traverse from this pipe
     *
     * @return the array of valid EnumFacings
     */
    protected EnumFacing[] getSurroundingPipeSides() {
        return EnumFacing.VALUES;
    }

    /**
     * Called when a sub walker is done walking
     *
     * @param subWalker the finished sub walker
     */
    protected void onRemoveSubWalker(PipeNetWalker<T> subWalker) {}

    public void traversePipeNet() {
        traversePipeNet(32768);
    }

    /**
     * Starts walking the pipe net and gathers information.
     *
     * @param maxWalks max walks to prevent possible infinite loops. Since every tick advances the whole
     *                 frontier by exactly one hop (see the class-level note), this bounds the network's
     *                 traversable depth in blocks, not the total number of pipes visited.
     * @throws IllegalStateException if the walker already walked
     */
    public void traversePipeNet(int maxWalks) {
        if (invalid)
            throw new IllegalStateException("This walker already walked. Create a new one if you want to walk again");
        root = this;
        walked = new ObjectOpenHashSet<>();
        running = true;

        List<PipeNetWalker<T>> frontier = new ArrayList<>();
        frontier.add(this);
        int i = 0;
        while (running && !frontier.isEmpty() && i++ < maxWalks) {
            List<PipeNetWalker<T>> nextFrontier = new ArrayList<>();
            for (PipeNetWalker<T> walker : frontier) {
                walker.step(nextFrontier);
            }
            frontier = nextFrontier;
        }
        running = false;
        walked = null;
        if (i >= maxWalks)
            GTLog.logger.fatal("The walker reached the maximum amount of walks {}", i);
        invalid = true;
    }

    /**
     * Advances this walker by exactly one hop. Appends whatever should be processed next tick to
     * {@code nextFrontier}: this same walker again (if it has exactly one valid successor and is still
     * running), or the new sub-walkers spawned for each branch (if it has multiple). Adds nothing, and bubbles
     * completion up to {@link #parentWalker} via {@link #finish()}, on a dead end, a failed
     * {@link #checkPos()}, or being told to stop.
     */
    private void step(List<PipeNetWalker<T>> nextFrontier) {
        if (!checkPos()) {
            root.failed = true;
            finish();
            return;
        }

        if (nextPipeFacings.isEmpty()) {
            finish();
            return;
        }
        if (nextPipeFacings.size() == 1) {
            currentPos.setPos(nextPipes.get(0).getPipePos());
            currentPipe = nextPipes.get(0);
            from = nextPipeFacings.get(0).getOpposite();
            walkedBlocks++;
            if (isRunning()) {
                nextFrontier.add(this);
            } else {
                finish();
            }
            return;
        }

        pendingChildCount = nextPipeFacings.size();
        for (int i = 0; i < nextPipeFacings.size(); i++) {
            EnumFacing side = nextPipeFacings.get(i);
            PipeNetWalker<T> walker = Objects.requireNonNull(
                    createSubWalker(world, side, currentPos.offset(side), walkedBlocks + 1),
                    "Walker can't be null");
            walker.root = root;
            walker.parentWalker = this;
            walker.currentPipe = nextPipes.get(i);
            walker.from = side.getOpposite();
            nextFrontier.add(walker);
        }
    }

    /**
     * Marks this walker as finished and bubbles completion up through {@link #parentWalker}, calling
     * {@link #onRemoveSubWalker} on each ancestor for which this was (transitively) the last remaining active
     * descendant -- exactly where the original recursive implementation would have.
     */
    private void finish() {
        PipeNetWalker<T> child = this;
        PipeNetWalker<T> parent = child.parentWalker;
        while (parent != null) {
            parent.onRemoveSubWalker(child);
            if (--parent.pendingChildCount > 0) {
                return;
            }
            child = parent;
            parent = child.parentWalker;
        }
    }

    private boolean checkPos() {
        nextPipeFacings.clear();
        nextPipes.clear();
        if (currentPipe == null) {
            TileEntity thisPipe = world.getTileEntity(currentPos);
            if (!(thisPipe instanceof IPipeTile<?, ?>)) {
                GTLog.logger.fatal("PipeWalker expected a pipe, but found {} at {}", thisPipe, currentPos);
                return false;
            }
            if (!getBasePipeClass().isAssignableFrom(thisPipe.getClass())) {
                return false;
            }
            // noinspection unchecked
            currentPipe = (T) thisPipe;
        }
        T pipeTile = currentPipe;
        checkPipe(pipeTile, currentPos);
        root.walked.add(pipeTile);

        // check for surrounding pipes and item handlers
        for (EnumFacing accessSide : getSurroundingPipeSides()) {
            // skip sides reported as blocked by pipe network
            if (accessSide == from || !pipeTile.isConnected(accessSide))
                continue;

            TileEntity tile = pipeTile.getNeighbor(accessSide);
            if (tile != null && getBasePipeClass().isAssignableFrom(tile.getClass())) {
                // noinspection unchecked
                T otherPipe = (T) tile;
                if (!otherPipe.isConnected(accessSide.getOpposite()) ||
                        otherPipe.isFaceBlocked(accessSide.getOpposite()) || isWalked(otherPipe))
                    continue;
                if (isValidPipe(pipeTile, otherPipe, currentPos, accessSide)) {
                    nextPipeFacings.add(accessSide);
                    nextPipes.add(otherPipe);
                    continue;
                }
            }
            checkNeighbour(pipeTile, currentPos, accessSide, tile);
        }
        return true;
    }

    protected boolean isWalked(T pipe) {
        return root.walked.contains(pipe);
    }

    /**
     * Will cause the root walker to stop after the next walk
     */
    public void stop() {
        root.running = false;
    }

    public boolean isRunning() {
        return root.running;
    }

    public @NotNull World getWorld() {
        return world;
    }

    public @NotNull BlockPos getCurrentPos() {
        return currentPos;
    }

    public int getWalkedBlocks() {
        return walkedBlocks;
    }

    public boolean isRoot() {
        return this.root == this;
    }

    public boolean isFailed() {
        return failed;
    }
}
