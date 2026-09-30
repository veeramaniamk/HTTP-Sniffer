package com.apisniffer.shutdown;

import com.apisniffer.logging.TrafficLogger;
import com.apisniffer.proxy.ProxyServerManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles graceful shutdown on SIGINT (Ctrl+C) or termination.
 * Stops the proxy server, flushes logs, and outputs the session summary.
 */
public class ShutdownHookHandler {

    private final ProxyServerManager proxyServerManager;
    private final TrafficLogger trafficLogger;
    private final java.util.List<Runnable> cleanupTasks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicBoolean executed = new AtomicBoolean(false);

    public ShutdownHookHandler(ProxyServerManager proxyServerManager, TrafficLogger trafficLogger) {
        this.proxyServerManager = proxyServerManager;
        this.trafficLogger = trafficLogger;
    }

    public void addCleanupTask(Runnable task) {
        if (task != null) {
            cleanupTasks.add(task);
        }
    }

    public void register() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::performShutdown, "apisniffer-shutdown-hook"));
    }

    public void performShutdown() {
        if (!executed.compareAndSet(false, true)) {
            return;
        }

        // Run additional cleanup tasks (e.g. web server)
        for (Runnable task : cleanupTasks) {
            try {
                task.run();
            } catch (Exception ignored) {
            }
        }

        // 1. Stop proxy
        try {
            proxyServerManager.stop();
        } catch (Exception ignored) {
        }

        // 2. Print summary
        try {
            trafficLogger.printSummary();
        } catch (Exception ignored) {
        }

        // 3. Close logger resources
        try {
            trafficLogger.close();
        } catch (Exception ignored) {
        }
    }
}
