package com.apisniffer.logging;

import com.apisniffer.model.InterceptedTraffic;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.apisniffer.logging.ConsoleColor.*;

/**
 * Coordinates console formatting, JSON Lines file output, and session statistics.
 */
public class TrafficLogger implements AutoCloseable {

    private final boolean quiet;
    private final boolean noBody;
    private final ConsoleFormatter consoleFormatter;
    private final JsonLineWriter jsonLineWriter;

    private final long startTimeMs;
    private final AtomicLong totalRequestsLogged = new AtomicLong(0);
    private final Map<String, AtomicLong> hostRequestCounts = new ConcurrentHashMap<>();

    public TrafficLogger(boolean quiet, boolean noBody, File saveFile) throws Exception {
        this.quiet = quiet;
        this.noBody = noBody;
        this.consoleFormatter = new ConsoleFormatter(noBody);
        this.jsonLineWriter = (saveFile != null) ? new JsonLineWriter(saveFile, noBody) : null;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Logs an intercepted request/response pair.
     */
    public void logTraffic(InterceptedTraffic traffic) {
        long count = totalRequestsLogged.incrementAndGet();
        traffic.setId(count);

        if (traffic.getHost() != null && !traffic.getHost().isBlank()) {
            hostRequestCounts.computeIfAbsent(traffic.getHost().toLowerCase(Locale.ROOT), k -> new AtomicLong(0))
                    .incrementAndGet();
        }

        // Console output (unless quiet mode)
        if (!quiet) {
            String formatted = consoleFormatter.format(traffic);
            System.out.println(formatted);
        }

        // File output (if --save was provided)
        if (jsonLineWriter != null) {
            jsonLineWriter.writeEntry(traffic);
        }
    }

    /**
     * Prints a graceful shutdown summary to stdout.
     */
    public void printSummary() {
        long durationMs = System.currentTimeMillis() - startTimeMs;
        long totalSecs = durationMs / 1000;
        long hours = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long seconds = totalSecs % 60;

        String durationStr = String.format("%02d:%02d:%02d (%d seconds)", hours, minutes, seconds, totalSecs);

        System.out.println();
        System.out.println(colorize(BOLD + CYAN, "═".repeat(80)));
        System.out.println(colorize(BOLD + BRIGHT_WHITE, "  ApiSniffer Session Summary"));
        System.out.println(colorize(BOLD + CYAN, "═".repeat(80)));
        System.out.printf("  %-24s %s\n", colorize(DIM, "Duration:"), colorize(BOLD, durationStr));
        System.out.printf("  %-24s %s\n", colorize(DIM, "Total Requests Logged:"),
                colorize(BOLD + BRIGHT_GREEN, String.valueOf(totalRequestsLogged.get())));
        System.out.printf("  %-24s %s\n", colorize(DIM, "Unique Hosts Seen:"),
                colorize(BOLD + BRIGHT_YELLOW, String.valueOf(hostRequestCounts.size())));

        if (!hostRequestCounts.isEmpty()) {
            System.out.println();
            System.out.println(colorize(DIM, "  Host Breakdown:"));
            // Sort by count descending
            List<Map.Entry<String, AtomicLong>> sorted = new ArrayList<>(hostRequestCounts.entrySet());
            sorted.sort((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()));
            for (Map.Entry<String, AtomicLong> entry : sorted) {
                System.out.printf("    • %-40s %s requests\n",
                        colorize(CYAN, entry.getKey()),
                        colorize(BOLD, String.valueOf(entry.getValue().get())));
            }
        }

        if (jsonLineWriter != null) {
            System.out.println();
            System.out.printf("  %-24s %s\n", colorize(DIM, "Saved JSONL File:"),
                    colorize(BOLD + GREEN, jsonLineWriter.getOutputFile().getAbsolutePath()));
        }
        System.out.println(colorize(BOLD + CYAN, "═".repeat(80)));
    }

    public long getTotalRequestsLogged() {
        return totalRequestsLogged.get();
    }

    public int getUniqueHostsCount() {
        return hostRequestCounts.size();
    }

    public long getDurationMs() {
        return System.currentTimeMillis() - startTimeMs;
    }

    @Override
    public void close() {
        if (jsonLineWriter != null) {
            jsonLineWriter.close();
        }
    }
}
