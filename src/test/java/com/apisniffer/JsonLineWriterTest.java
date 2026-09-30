package com.apisniffer;

import com.apisniffer.logging.JsonLineWriter;
import com.apisniffer.model.InterceptedTraffic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class JsonLineWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void testWriteAndReadJsonLines() throws Exception {
        File jsonlFile = tempDir.resolve("traffic.jsonl").toFile();
        ObjectMapper mapper = new ObjectMapper();

        try (JsonLineWriter writer = new JsonLineWriter(jsonlFile, false)) {
            InterceptedTraffic entry1 = new InterceptedTraffic();
            entry1.setId(1);
            entry1.setTimestamp("2026-09-30 15:00:00.000");
            entry1.setDurationMs(20);
            entry1.setMethod("GET");
            entry1.setUrl("https://api.example.com/health");
            entry1.setBaseUrl("https://api.example.com");
            entry1.setPath("/health");
            entry1.setStatusCode(200);
            entry1.setStatusMessage("OK");
            entry1.setResponseBody("{\"status\":\"UP\"}");

            InterceptedTraffic entry2 = new InterceptedTraffic();
            entry2.setId(2);
            entry2.setTimestamp("2026-09-30 15:00:01.000");
            entry2.setDurationMs(80);
            entry2.setMethod("POST");
            entry2.setUrl("https://api.example.com/items");
            entry2.setBaseUrl("https://api.example.com");
            entry2.setPath("/items");
            entry2.setRequestHeaders(Map.of("X-Test", "123"));
            entry2.setRequestBody("{\"item\":\"gadget\"}");
            entry2.setStatusCode(201);
            entry2.setStatusMessage("Created");
            entry2.setResponseBody("{\"id\":10}");

            writer.writeEntry(entry1);
            writer.writeEntry(entry2);
        }

        assertTrue(jsonlFile.exists());
        List<String> lines = Files.readAllLines(jsonlFile.toPath());
        assertEquals(2, lines.size());

        JsonNode node1 = mapper.readTree(lines.get(0));
        assertEquals("GET", node1.get("method").asText());
        assertEquals(200, node1.get("statusCode").asInt());
        assertEquals("{\"status\":\"UP\"}", node1.get("responseBody").asText());

        JsonNode node2 = mapper.readTree(lines.get(1));
        assertEquals("POST", node2.get("method").asText());
        assertEquals(201, node2.get("statusCode").asInt());
        assertEquals("{\"id\":10}", node2.get("responseBody").asText());
    }

    @Test
    void testNoBodyModeStripsBodies() throws Exception {
        File jsonlFile = tempDir.resolve("nobody.jsonl").toFile();
        ObjectMapper mapper = new ObjectMapper();

        try (JsonLineWriter writer = new JsonLineWriter(jsonlFile, true)) {
            InterceptedTraffic entry = new InterceptedTraffic();
            entry.setId(1);
            entry.setTimestamp("2026-09-30 15:00:00.000");
            entry.setDurationMs(20);
            entry.setMethod("POST");
            entry.setUrl("https://api.example.com/login");
            entry.setBaseUrl("https://api.example.com");
            entry.setPath("/login");
            entry.setRequestHeaders(Map.of("Authorization", "Bearer secret"));
            entry.setRequestBody("{\"password\":\"secret\"}");
            entry.setStatusCode(200);
            entry.setStatusMessage("OK");
            entry.setResponseBody("{\"token\":\"12345\"}");

            writer.writeEntry(entry);
        }

        List<String> lines = Files.readAllLines(jsonlFile.toPath());
        assertEquals(1, lines.size());
        JsonNode node = mapper.readTree(lines.get(0));
        assertEquals("POST", node.get("method").asText());
        assertEquals(200, node.get("statusCode").asInt());
        assertNull(node.get("requestBody"));
        assertNull(node.get("responseBody"));
        assertNull(node.get("requestHeaders"));
    }
}
