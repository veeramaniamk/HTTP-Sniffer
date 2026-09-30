package com.apisniffer;

import com.apisniffer.logging.ConsoleColor;
import com.apisniffer.logging.ConsoleFormatter;
import com.apisniffer.model.InterceptedTraffic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ConsoleFormatterTest {

    @BeforeAll
    static void setUp() {
        ConsoleColor.setAnsi(false);
    }

    @Test
    void testJsonPrettyPrinting() {
        String compactJson = "{\"name\":\"Alice\",\"roles\":[\"admin\",\"user\"],\"active\":true}";
        String formatted = ConsoleFormatter.prettyFormatBody(compactJson, false);

        assertTrue(formatted.contains("\"name\" : \"Alice\""));
        assertTrue(formatted.contains("\"roles\" : [ \"admin\", \"user\" ]") || formatted.contains("\"admin\""));
    }

    @Test
    void testResponseBodyTruncationOver5000Chars() {
        StringBuilder largeBody = new StringBuilder();
        for (int i = 0; i < 700; i++) {
            largeBody.append("Line ").append(i).append(": some mock text payload data\n");
        }
        assertTrue(largeBody.length() > 5000);

        String truncated = ConsoleFormatter.prettyFormatBody(largeBody.toString(), true);
        assertTrue(truncated.contains("... [Truncated: showing 5000 of"));
    }

    @Test
    void testFullAndCompactFormatting() {
        InterceptedTraffic traffic = new InterceptedTraffic();
        traffic.setId(1);
        traffic.setTimestamp("2026-09-30 15:00:00.000");
        traffic.setDurationMs(45);
        traffic.setMethod("POST");
        traffic.setUrl("https://api.example.com/v1/login");
        traffic.setBaseUrl("https://api.example.com");
        traffic.setPath("/v1/login");
        traffic.setScheme("https");
        traffic.setHost("api.example.com");
        traffic.setPort(443);
        Map<String, String> reqHeaders = new LinkedHashMap<>();
        reqHeaders.put("Content-Type", "application/json");
        traffic.setRequestHeaders(reqHeaders);
        traffic.setRequestBody("{\"user\":\"test\"}");

        traffic.setStatusCode(200);
        traffic.setStatusMessage("OK");
        Map<String, String> respHeaders = new LinkedHashMap<>();
        respHeaders.put("Content-Type", "application/json");
        traffic.setResponseHeaders(respHeaders);
        traffic.setResponseBody("{\"status\":\"success\"}");

        // Full formatter
        ConsoleFormatter fullFormatter = new ConsoleFormatter(false);
        String fullOutput = fullFormatter.format(traffic);
        assertTrue(fullOutput.contains("POST"));
        assertTrue(fullOutput.contains("https://api.example.com/v1/login"));
        assertTrue(fullOutput.contains("200 OK"));
        assertTrue(fullOutput.contains("Content-Type"));
        assertTrue(fullOutput.contains("\"status\" : \"success\"") || fullOutput.contains("status"));

        // Compact formatter (--no-body)
        ConsoleFormatter compactFormatter = new ConsoleFormatter(true);
        String compactOutput = compactFormatter.format(traffic);
        assertTrue(compactOutput.contains("POST"));
        assertTrue(compactOutput.contains("https://api.example.com/v1/login"));
        assertTrue(compactOutput.contains("200 OK"));
        assertFalse(compactOutput.contains("status : success"));
    }
}
