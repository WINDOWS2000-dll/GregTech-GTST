package gregtech.api.statemachine;

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

public final class GTStateMachineTraceLog {

    private static final String LAUNCH_TIMESTAMP = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());

    static final Logger logger = LogManager.getLogger("GregTech-StateMachine-Trace");

    private static final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private static volatile boolean writerStarted = false;

    private GTStateMachineTraceLog() {}

    /**
     * Logs one traced operator visit, prefixed with a caller-supplied label distinguishing multiple traced machines.
     */
    public static void log(@NotNull String label, @NotNull String debugName) {
        String line = "[" + label + "] -> " + debugName;
        logger.info(line);
        ensureWriterStarted();
        queue.add(line);
    }

    private static synchronized void ensureWriterStarted() {
        if (writerStarted) return;
        writerStarted = true;
        Thread writer = new Thread(GTStateMachineTraceLog::runWriter, "GregTech-StateMachine-Trace-Writer");
        writer.setDaemon(true);
        writer.start();
    }

    /** Runs entirely on the dedicated writer thread; never touches the caller (server tick) thread. */
    private static void runWriter() {
        File file = new File(new File("logs"), "gtst_statemachine_trace_" + LAUNCH_TIMESTAMP + ".log");
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
            GTLog.logger.warn("StateMachine trace file writer stopped unexpectedly; trace lines will still reach " +
                    "the console/main log.", e);
        }
    }
}
