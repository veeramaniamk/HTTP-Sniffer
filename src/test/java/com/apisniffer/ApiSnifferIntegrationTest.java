package com.apisniffer;

import com.apisniffer.cert.CertificateManager;
import com.apisniffer.filter.HostFilter;
import com.apisniffer.logging.TrafficLogger;
import com.apisniffer.proxy.HttpTrafficInterceptor;
import com.apisniffer.proxy.ProxyServerManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ApiSnifferIntegrationTest {

    @TempDir
    Path tempDir;

    private HttpServer backendServer;
    private int backendPort;

    private ProxyServerManager proxyManager;
    private TrafficLogger trafficLogger;
    private File jsonlFile;

    @BeforeEach
    void setUp() throws Exception {
        // Mock backend HTTP server
        backendServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backendServer.createContext("/api/v1/users", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String reqBody = new String(body, StandardCharsets.UTF_8);

            String response = "{\"id\":42,\"name\":\"Alice\",\"status\":\"created\"}";
            byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }
        });
        backendServer.start();
        backendPort = backendServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (proxyManager != null) {
            proxyManager.stop();
        }
        if (trafficLogger != null) {
            trafficLogger.close();
        }
        if (backendServer != null) {
            backendServer.stop(0);
        }
    }

    @Test
    void testEndToEndProxyInterceptionAndLogging() throws Exception {
        File certsDir = tempDir.resolve("certs").toFile();
        jsonlFile = tempDir.resolve("traffic.jsonl").toFile();

        CertificateManager certManager = new CertificateManager(certsDir);
        certManager.initialize();

        HostFilter hostFilter = new HostFilter(List.of("127.0.0.1"));
        trafficLogger = new TrafficLogger(true, false, jsonlFile);
        HttpTrafficInterceptor interceptor = new HttpTrafficInterceptor(hostFilter, trafficLogger);

        proxyManager = new ProxyServerManager(0, certManager, interceptor);
        proxyManager.start();

        int proxyPort = proxyManager.getPort();
        assertTrue(proxyPort > 0);

        // Make HTTP request through proxy
        Proxy httpProxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", proxyPort));
        URL url = new URL("http://127.0.0.1:" + backendPort + "/api/v1/users?role=admin");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(httpProxy);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Custom-Client", "TestDevice");

        String reqJson = "{\"username\":\"alice\",\"email\":\"alice@example.com\"}";
        try (OutputStream os = conn.getOutputStream()) {
            os.write(reqJson.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = conn.getResponseCode();
        assertEquals(201, statusCode);

        try (InputStream is = conn.getInputStream()) {
            String respStr = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(respStr.contains("\"id\":42"));
        }

        // Allow Netty response filter event loop to complete
        Thread.sleep(800);

        // Verify metrics
        assertEquals(1, trafficLogger.getTotalRequestsLogged());
        assertEquals(1, trafficLogger.getUniqueHostsCount());

        // Verify JSONL file content
        assertTrue(jsonlFile.exists());
        List<String> lines = Files.readAllLines(jsonlFile.toPath());
        assertEquals(1, lines.size());

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(lines.get(0));

        assertEquals("POST", root.get("method").asText());
        assertEquals(201, root.get("statusCode").asInt());
        assertEquals("Created", root.get("statusMessage").asText());
        assertEquals("/api/v1/users?role=admin", root.get("path").asText());
        assertEquals("http://127.0.0.1:" + backendPort, root.get("baseUrl").asText());
        assertEquals(reqJson, root.get("requestBody").asText());
        assertTrue(root.get("responseBody").asText().contains("\"name\":\"Alice\""));
        assertNotNull(root.get("requestHeaders"));
        assertNotNull(root.get("responseHeaders"));
    }
}
