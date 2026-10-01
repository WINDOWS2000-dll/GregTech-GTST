package gregtech.api.pipenet;

import gregtech.api.util.GTLog;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The PipeNet execution-trace dev tool's log sink. Mirrors {@link gregtech.api.statemachine.GTStateMachineTraceLog}
 * exactly, including the dedicated writer thread + blocking queue: PipeNet operations (merges, splits, BFS
 * traversals) can happen on the server tick thread from ordinary block-update event handling, so -- exactly as
 * with the StateMachine trace log this is copied from -- logging must never block that thread on file I/O.
 * See {@link PipeNet#setTraceEnabled} for how a specific net opts into tracing.
 */
public final class PipeNetTraceLog {

    private static final String LAUNCH_TIMESTAMP = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());

    static final Logger logger = LogManager.getLogger("GregTech-PipeNet-Trace");

    private static final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private static volatile boolean writerStarted = false;

    private PipeNetTraceLog() {}

    /**
     * Logs one traced PipeNet event, prefixed with a caller-supplied label distinguishing multiple traced
     * networks.
     */
    public static void log(@NotNull String label, @NotNull String message) {
        String line = "[" + label + "] " + message;
        logger.info(line);
        ensureWriterStarted();
        queue.add(line);
    }

    private static synchronized void ensureWriterStarted() {
        if (writerStarted) return;
        writerStarted = true;
        Thread writer = new Thread(PipeNetTraceLog::runWriter, "GregTech-PipeNet-Trace-Writer");
        writer.setDaemon(true);
        writer.start();
    }

    /** Runs entirely on the dedicated writer thread; never touches the caller (server tick) thread. */
    private static void runWriter() {
        File file = new File(new File("logs"), "gtst_pipenet_trace_" + LAUNCH_TIMESTAMP + ".log");
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (BufferedWriter out = new BufferedWriter(new FileWriter(file, true))) {
                // noinspection InfiniteLoopStatement -- daemon thread, lives for the JVM's lifetime
                while (true) {
                    out.write(queue.take());
                    out.newLine();
                    if (queue.isEmpty()) out.flush();
                }
            }
        } catch (IOException | InterruptedException e) {
            GTLog.logger.warn("PipeNet trace file writer stopped unexpectedly; trace lines will still reach " +
                    "the console/main log.", e);
        }
    }
}
