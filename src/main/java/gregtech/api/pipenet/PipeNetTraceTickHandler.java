package gregtech.api.pipenet;

import gregtech.api.GTValues;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Drives the PipeNet execution-trace dev tool's periodic summary lines (see {@link PipeNet#setTraceEnabled}):
 * every {@link #SUMMARY_INTERVAL_TICKS} server ticks, every currently-traced net gets one aggregated
 * {@link PipeNetTraceLog} line covering everything it did since the previous summary (a single per-operation
 * trace line per merge/split/traversal is useful for drilling into one specific event, but doesn't by itself
 * answer "how much load is this net putting on the server right now" -- that needs an interval aggregate).
 * <p>
 * A {@link WeakHashMap}-backed registry, not a normal one: a traced net that's since been discarded (e.g. fully
 * absorbed into another net via a merge, or unloaded) should simply stop appearing in summaries on its own,
 * without needing an explicit unregister call from every one of {@link PipeNet}'s removal paths.
 */
@EventBusSubscriber(modid = GTValues.MODID)
public final class PipeNetTraceTickHandler {

    /** 100 ticks = 5 seconds at a healthy 20 TPS -- frequent enough to see load trends develop, infrequent
     *  enough that the summary lines themselves stay a rounding error next to whatever they're measuring. */
    private static final int SUMMARY_INTERVAL_TICKS = 100;

    private static final Set<PipeNet<?>> tracedNets = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<PipeNet<?>, PipeNetTraceStats.Snapshot> lastSnapshots = new WeakHashMap<>();
    private static final Map<PipeNet<?>, Long> lastSummaryNanos = new WeakHashMap<>();

    private static int tickCounter = 0;

    private PipeNetTraceTickHandler() {}

    static void register(PipeNet<?> net) {
        long now = System.nanoTime();
        tracedNets.add(net);
        lastSnapshots.put(net, net.getTraceStats().snapshot());
        lastSummaryNanos.put(net, now);
    }

    static void unregister(PipeNet<?> net) {
        tracedNets.remove(net);
        lastSnapshots.remove(net);
        lastSummaryNanos.remove(net);
    }

    /** @return a snapshot of every currently-traced net, for {@code /gt dumppipenet list}. */
    @NotNull
    public static List<PipeNet<?>> getTracedNets() {
        return new ArrayList<>(tracedNets);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (tracedNets.isEmpty()) return;
        if (++tickCounter < SUMMARY_INTERVAL_TICKS) return;
        tickCounter = 0;

        long now = System.nanoTime();
        // copy first: flushing can run arbitrary PipeNet#getTraceLabel/getTraceStats/describeMemoryProxy code,
        // and a traced net could in principle be GC'd out of the weak set mid-iteration otherwise
        List<PipeNet<?>> snapshot = new ArrayList<>(tracedNets);
        for (PipeNet<?> net : snapshot) {
            PipeNetTraceStats.Snapshot current = net.getTraceStats().snapshot();
            PipeNetTraceStats.Snapshot previous = lastSnapshots.getOrDefault(net, PipeNetTraceStats.Snapshot.zero());
            long periodNanos = now - lastSummaryNanos.getOrDefault(net, now);
            PipeNetTraceStats.Snapshot delta = current.minus(previous);

            PipeNetTraceLog.log(net.getTraceLabel(),
                    "[interval summary] " + net.describeMemoryProxy() + " | " + delta.describe(periodNanos));

            lastSnapshots.put(net, current);
            lastSummaryNanos.put(net, now);
        }
    }
}
