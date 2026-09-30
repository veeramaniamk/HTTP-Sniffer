package com.apisniffer.web;

import com.apisniffer.cert.CertificateManager;
import com.apisniffer.device.DeviceInfo;
import com.apisniffer.device.DeviceManager;
import com.apisniffer.model.InterceptedTraffic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * Embedded HTTP server hosting the ApiSniffer real-time web dashboard
 * and REST / SSE streaming endpoints.
 */
public class WebServerManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_BUFFERED_TRAFFIC = 1000;

    private final int webPort;
    private final int proxyPort;
    private final String hotspotIp;
    private final DeviceManager deviceManager;
    private final CertificateManager certificateManager;

    private HttpServer server;
    private final List<InterceptedTraffic> trafficBuffer = Collections.synchronizedList(new LinkedList<>());
    private final Set<OutputStream> sseClients = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public WebServerManager(int webPort, int proxyPort, String hotspotIp,
                            DeviceManager deviceManager, CertificateManager certificateManager) {
        this.webPort = webPort;
        this.proxyPort = proxyPort;
        this.hotspotIp = hotspotIp;
        this.deviceManager = deviceManager;
        this.certificateManager = certificateManager;
    }

    public synchronized void start() throws IOException {
        if (server != null) return;

        server = HttpServer.create(new InetSocketAddress("0.0.0.0", webPort), 0);
        server.setExecutor(executor);

        // Web Dashboard UI
        server.createContext("/", new IndexHandler());

        // API endpoints
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/devices", new DevicesHandler());
        server.createContext("/api/traffic", new TrafficHandler());
        server.createContext("/api/traffic/stream", new SseStreamHandler());
        server.createContext("/api/clear", new ClearHandler());
        server.createContext("/api/ca.pem", new CertDownloadHandler());
        server.createContext("/ca.pem", new CertDownloadHandler());

        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            try {
                // Close all SSE streams
                for (OutputStream os : sseClients) {
                    try { os.close(); } catch (Exception ignored) {}
                }
                sseClients.clear();
                server.stop(0);
                executor.shutdownNow();
            } catch (Exception ignored) {
            } finally {
                server = null;
            }
        }
    }

    /**
     * Broadcasts newly intercepted traffic to in-memory buffer and all live SSE web clients.
     */
    public void broadcastTraffic(InterceptedTraffic traffic) {
        if (traffic == null) return;

        synchronized (trafficBuffer) {
            trafficBuffer.add(0, traffic); // Most recent first
            if (trafficBuffer.size() > MAX_BUFFERED_TRAFFIC) {
                trafficBuffer.remove(trafficBuffer.size() - 1);
            }
        }

        try {
            String json = MAPPER.writeValueAsString(traffic);
            String eventData = "event: traffic\ndata: " + json + "\n\n";
            byte[] bytes = eventData.getBytes(StandardCharsets.UTF_8);

            List<OutputStream> deadClients = new ArrayList<>();
            for (OutputStream os : sseClients) {
                try {
                    os.write(bytes);
                    os.flush();
                } catch (Exception e) {
                    deadClients.add(os);
                }
            }
            sseClients.removeAll(deadClients);
        } catch (Exception ignored) {
        }
    }

    public int getWebPort() {
        return (server != null) ? server.getAddress().getPort() : webPort;
    }

    // --- HTTP Handlers ---

    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            byte[] htmlBytes = loadIndexHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            exchange.sendResponseHeaders(200, htmlBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(htmlBytes);
            }
        }

        private byte[] loadIndexHtml() {
            try (InputStream is = getClass().getResourceAsStream("/web/index.html")) {
                if (is != null) {
                    return is.readAllBytes();
                }
            } catch (Exception ignored) {}

            File file = new File("src/main/resources/web/index.html");
            if (file.exists()) {
                try {
                    return Files.readAllBytes(file.toPath());
                } catch (Exception ignored) {}
            }

            return "<h1>ApiSniffer Web Dashboard (Loading...)</h1>".getBytes(StandardCharsets.UTF_8);
        }
    }

    private class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("status", "running");
            map.put("proxyPort", proxyPort);
            map.put("webPort", getWebPort());
            map.put("hotspotIp", hotspotIp);
            map.put("totalBuffered", trafficBuffer.size());
            map.put("activeClients", sseClients.size());

            sendJsonResponse(exchange, 200, map);
        }
    }

    private class DevicesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<DeviceInfo> list = (deviceManager != null) ? deviceManager.getAllDevices() : Collections.emptyList();
            sendJsonResponse(exchange, 200, list);
        }
    }

    private class TrafficHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String deviceFilter = params.get("device");

            List<InterceptedTraffic> copy;
            synchronized (trafficBuffer) {
                copy = new ArrayList<>(trafficBuffer);
            }

            if (deviceFilter != null && !deviceFilter.isBlank() && !"all".equalsIgnoreCase(deviceFilter)) {
                copy.removeIf(t -> !deviceFilter.equalsIgnoreCase(t.getClientIp()));
            }

            int limit = 300;
            if (params.containsKey("limit")) {
                try { limit = Integer.parseInt(params.get("limit")); } catch (Exception ignored) {}
            }
            if (copy.size() > limit) {
                copy = copy.subList(0, limit);
            }

            sendJsonResponse(exchange, 200, copy);
        }
    }

    private class SseStreamHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");

            // Chunked response for streaming
            exchange.sendResponseHeaders(200, 0);

            OutputStream os = exchange.getResponseBody();
            sseClients.add(os);

            // Send initial ping
            try {
                os.write("event: connected\ndata: {\"status\":\"ok\"}\n\n".getBytes(StandardCharsets.UTF_8));
                os.flush();
            } catch (Exception e) {
                sseClients.remove(os);
            }
        }
    }

    private class ClearHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            trafficBuffer.clear();
            sendJsonResponse(exchange, 200, Map.of("status", "cleared"));
        }
    }

    private class CertDownloadHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File caPem = certificateManager.getCaCertPemFile();
            if (!caPem.exists()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            byte[] bytes = Files.readAllBytes(caPem.toPath());
            exchange.getResponseHeaders().set("Content-Type", "application/x-x509-ca-cert");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"ca.pem\"");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void sendJsonResponse(HttpExchange exchange, int statusCode, Object data) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) return Collections.emptyMap();
        Map<String, String> map = new HashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                map.put(k, v);
            }
        }
        return map;
    }
}
