package gregtech.api.recipes.logic.statemachine.experimental;

import gregtech.api.util.GTLog;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Records {@link ExperimentalRecipeLogicRegistry}'s circuit-breaker failures (a post-processor that threw during
 * graph construction) to their own dedicated log file, mirroring {@code GTStateMachineTraceLog}'s established
 * pattern exactly: a dedicated {@link Logger} (so the lines are also visible in the normal log/console, not hidden
 * exclusively in the file below) plus a {@link BlockingQueue}-fed background writer thread, so the rare synchronous
 * file write never has a chance to stall the server tick thread that triggered it (the same class of bug the
 * StateMachine trace log itself once had, fixed by adopting this exact design).
 */
public final class ExperimentalExtensionFailureLog {

    private static final String LAUNCH_TIMESTAMP = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());

    static final Logger logger = LogManager.getLogger("GregTech-Experimental-Extension-Failures");

    private static final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private static volatile boolean writerStarted = false;

    private ExperimentalExtensionFailureLog() {}

    /**
     * Logs one circuit-breaker trip: {@code modid}'s post-processor targeting {@code targetType} threw
     * {@code failure} and has been permanently disabled for the remainder of this session (see {@code
     * ExperimentalRecipeLogicRegistry#applyPostProcessorsFor}).
     */
    public static void log(@NotNull String modid, @NotNull Class<?> targetType, @NotNull Throwable failure) {
        StringWriter stackTrace = new StringWriter();
        failure.printStackTrace(new PrintWriter(stackTrace));
        String message = "[" + modid + "] post-processor targeting " + targetType.getName() +
                " threw during graph construction and has been permanently disabled for this machine type:\n" +
                stackTrace;
        logger.error(message);
        ensureWriterStarted();
        queue.add(message);
    }

    private static synchronized void ensureWriterStarted() {
        if (writerStarted) return;
        writerStarted = true;
        Thread writer = new Thread(ExperimentalExtensionFailureLog::runWriter,
                "GregTech-Experimental-Extension-Failures-Writer");
        writer.setDaemon(true);
        writer.start();
    }

    /** Runs entirely on the dedicated writer thread; never touches the caller (server tick) thread. */
    private static void runWriter() {
        File file = new File(new File("logs"), "gtst_experimental_extension_failures_" + LAUNCH_TIMESTAMP + ".log");
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
            GTLog.logger.warn("Experimental extension failure log file writer stopped unexpectedly; failures will " +
                    "still reach the console/main log.", e);
        }
    }
}
