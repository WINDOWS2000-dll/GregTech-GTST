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

@EventBusSubscriber(modid = GTValues.MODID)
public final class PipeNetTraceTickHandler {

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
