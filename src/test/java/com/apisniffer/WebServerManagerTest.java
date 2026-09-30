package com.apisniffer;

import com.apisniffer.cert.CertificateManager;
import com.apisniffer.device.DeviceManager;
import com.apisniffer.model.InterceptedTraffic;
import com.apisniffer.web.WebServerManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class WebServerManagerTest {

    @TempDir
    Path tempDir;

    private WebServerManager webServer;
    private DeviceManager deviceManager;
    private CertificateManager certManager;
    private int port;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        File certDir = tempDir.resolve("certs").toFile();
        certManager = new CertificateManager(certDir);
        certManager.initialize();

        deviceManager = new DeviceManager();
        deviceManager.recordActivity("192.168.137.99", "Mozilla/5.0 (Linux; Android 14; Pixel 7)");

        webServer = new WebServerManager(0, 8080, "192.168.137.1", deviceManager, certManager);
        webServer.start();
        port = webServer.getWebPort();
        assertTrue(port > 0);
    }

    @AfterEach
    void tearDown() {
        if (webServer != null) {
            webServer.stop();
        }
    }

    @Test
    void testGetIndexHtml() throws Exception {
        URL url = new URL("http://127.0.0.1:" + port + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getContentType().contains("text/html"));

        try (InputStream is = conn.getInputStream()) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(html.contains("ApiSniffer"));
            assertTrue(html.contains("Connected Devices"));
        }
    }

    @Test
    void testGetStatusApi() throws Exception {
        URL url = new URL("http://127.0.0.1:" + port + "/api/status");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        assertEquals(200, conn.getResponseCode());

        try (InputStream is = conn.getInputStream()) {
            JsonNode root = mapper.readTree(is);
            assertEquals("running", root.get("status").asText());
            assertEquals(8080, root.get("proxyPort").asInt());
            assertEquals("192.168.137.1", root.get("hotspotIp").asText());
        }
    }

    @Test
    void testGetDevicesApi() throws Exception {
        URL url = new URL("http://127.0.0.1:" + port + "/api/devices");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        assertEquals(200, conn.getResponseCode());

        try (InputStream is = conn.getInputStream()) {
            JsonNode array = mapper.readTree(is);
            assertTrue(array.isArray());
            boolean found = false;
            for (JsonNode d : array) {
                if ("192.168.137.99".equals(d.get("ip").asText())) {
                    found = true;
                    assertTrue(d.get("isHotspot").asBoolean());
                    break;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    void testTrafficApiAndDeviceFiltering() throws Exception {
        // Broadcast two traffic items from different devices
        InterceptedTraffic t1 = new InterceptedTraffic();
        t1.setId(1);
        t1.setClientIp("192.168.137.99");
        t1.setMethod("POST");
        t1.setUrl("https://api.example.com/v1/auth");
        t1.setStatusCode(200);
        webServer.broadcastTraffic(t1);

        InterceptedTraffic t2 = new InterceptedTraffic();
        t2.setId(2);
        t2.setClientIp("10.0.0.5");
        t2.setMethod("GET");
        t2.setUrl("https://cdn.example.com/logo.png");
        t2.setStatusCode(200);
        webServer.broadcastTraffic(t2);

        // 1. Fetch all traffic
        URL allUrl = new URL("http://127.0.0.1:" + port + "/api/traffic");
        HttpURLConnection conn1 = (HttpURLConnection) allUrl.openConnection();
        try (InputStream is = conn1.getInputStream()) {
            JsonNode list = mapper.readTree(is);
            assertEquals(2, list.size());
        }

        // 2. Fetch traffic filtered by device 192.168.137.99
        URL filteredUrl = new URL("http://127.0.0.1:" + port + "/api/traffic?device=192.168.137.99");
        HttpURLConnection conn2 = (HttpURLConnection) filteredUrl.openConnection();
        try (InputStream is = conn2.getInputStream()) {
            JsonNode list = mapper.readTree(is);
            assertEquals(1, list.size());
            assertEquals("192.168.137.99", list.get(0).get("clientIp").asText());
            assertEquals("https://api.example.com/v1/auth", list.get(0).get("url").asText());
        }
    }

    @Test
    void testDownloadCaCertificate() throws Exception {
        URL url = new URL("http://127.0.0.1:" + port + "/ca.pem");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        assertEquals(200, conn.getResponseCode());
        assertTrue(conn.getHeaderField("Content-Disposition").contains("ca.pem"));

        try (InputStream is = conn.getInputStream()) {
            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(pem.contains("-----BEGIN CERTIFICATE-----"));
        }
    }
}
