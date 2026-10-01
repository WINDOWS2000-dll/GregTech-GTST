package gregtech.common.pipelike.fluidpipe.net;

import gregtech.api.pipenet.Node;
import gregtech.api.unification.material.properties.FluidPipeProperties;

import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Spot-check microbenchmark confirming {@code FluidPipeNet} gets the same phase 1a/2 topology-management
 * improvements as the generic harness in {@code PipeNetBenchmark} -- it has no route-caching walker of its own
 * (see {@code GTST-pipenet-optimization-design/README.md}: fluid transport is tick-based hop relay, a
 * fundamentally different model with no {@code PipeNetWalker} subclass at all), but it still extends {@code
 * PipeNet<FluidPipeProperties>} directly for its own connectivity/split/merge bookkeeping (node add/remove,
 * {@code findAllConnectedBlocks}, {@code uniteNetworks}), which is exactly the shared code
 * {@code PipeNetBenchmark}'s scenarios B and D already exercise through a generic fake. This file re-runs
 * those same two scenarios through the *real* {@code FluidPipeNet}/{@code WorldFluidPipeNet} classes instead,
 * to confirm the numbers land in the same range rather than assume it from the shared code path alone. There
 * is no scenario A (no walker to benchmark) and no scenario C (no cache to invalidate).
 * <p>
 * Written to compile and run unchanged against both the pre-optimization tree and the current tree, same as
 * {@code PipeNetBenchmark}.
 */
class FluidPipeNetBenchmark {

    private static void report(String label, long[] samplesNanos) {
        long[] sorted = samplesNanos.clone();
        Arrays.sort(sorted);
        double min = sorted[0] / 1e6;
        double median = sorted[sorted.length / 2] / 1e6;
        double max = sorted[sorted.length - 1] / 1e6;
        double total = Arrays.stream(sorted).sum() / 1e6;
        System.out.printf(
                "[FluidPipeNetBenchmark] %-60s min=%10.3fms  median=%10.3fms  max=%10.3fms  total=%12.3fms  (n=%d)%n",
                label, min, median, max, total, samplesNanos.length);
    }

    private static final int ALL_OPEN = 0b111111;

    private static WorldFluidPipeNet freshWorld() {
        return new WorldFluidPipeNet("fluid-pipenet-benchmark");
    }

    private static void addNode(WorldFluidPipeNet world, BlockPos pos) {
        world.addNode(pos, new FluidPipeProperties(), Node.DEFAULT_MARK, ALL_OPEN, false);
    }

    @Test
    void nodeRemovalChurnBenchmark() {
        int length = 100_000;
        int removals = 500;

        WorldFluidPipeNet world = freshWorld();
        BlockPos base = new BlockPos(0, 0, 0);
        for (int i = 0; i < length; i++) {
            addNode(world, base.add(i, 0, 0));
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
    void unionBySizeBenchmark() {
        int bigNetSize = 100_000;
        int cycles = 50;

        WorldFluidPipeNet world = freshWorld();
        BlockPos bigBase = new BlockPos(1_000, 0, 0);
        for (int i = 0; i < bigNetSize; i++) {
            addNode(world, bigBase.add(i, 0, 0));
        }

        BlockPos smallPos = bigBase.add(-2, 0, 0);
        BlockPos bridgePos = bigBase.add(-1, 0, 0);

        long[] samples = new long[cycles];
        for (int c = 0; c < cycles; c++) {
            addNode(world, smallPos);
            long begin = System.nanoTime();
            addNode(world, bridgePos);
            samples[c] = System.nanoTime() - begin;
            world.removeNode(bridgePos);
            world.removeNode(smallPos);
        }
        report("bridge-small-into-big, bigNetSize=" + bigNetSize + ", cycles=" + cycles, samples);
    }
}
