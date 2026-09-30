package com.apisniffer.logging;

import com.apisniffer.model.InterceptedTraffic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import static com.apisniffer.logging.ConsoleColor.*;

/**
 * Formats intercepted HTTP traffic into clean, colorized terminal output.
 */
public class ConsoleFormatter {

    private static final int MAX_RESPONSE_BODY_LENGTH = 5000;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final boolean noBody;

    public ConsoleFormatter(boolean noBody) {
        this.noBody = noBody;
    }

    /**
     * Formats the intercepted traffic for console output.
     */
    public String format(InterceptedTraffic traffic) {
        if (noBody) {
            return formatCompact(traffic);
        }
        return formatFull(traffic);
    }

    private String formatCompact(InterceptedTraffic traffic) {
        String mColor = methodColor(traffic.getMethod());
        String sColor = statusColor(traffic.getStatusCode());
        String devStr = (traffic.getClientIp() != null) ? colorize(CYAN, "[" + traffic.getClientIp() + "] ") : "";

        return String.format("%s %s%s %s -> %s (%d ms)",
                colorize(DIM, "[" + traffic.getTimestamp() + "]"),
                devStr,
                colorize(BOLD + mColor, traffic.getMethod()),
                colorize(BOLD, traffic.getUrl()),
                colorize(BOLD + sColor, traffic.getStatusCode() + " " + traffic.getStatusMessage()),
                traffic.getDurationMs());
    }

    private String formatFull(InterceptedTraffic traffic) {
        StringBuilder sb = new StringBuilder();
        String mColor = methodColor(traffic.getMethod());
        String sColor = statusColor(traffic.getStatusCode());
        String devBadge = (traffic.getClientIp() != null) ? " " + colorize(CYAN, "[" + traffic.getClientIp() + "]") : "";

        // Header separator
        sb.append(colorize(DIM, "─".repeat(80))).append("\n");

        // Top line: [Timestamp] #ID [ClientIP] METHOD URL
        sb.append(String.format("%s %s%s %s %s\n",
                colorize(DIM, "[" + traffic.getTimestamp() + "]"),
                colorize(DIM, "#" + traffic.getId()),
                devBadge,
                colorize(BOLD + mColor, traffic.getMethod()),
                colorize(BOLD, traffic.getUrl())
        ));

        sb.append(colorize(DIM, "┄".repeat(80))).append("\n");

        // Request Section
        sb.append(colorize(BOLD + CYAN, "▶ REQUEST\n"));
        if (traffic.getClientIp() != null) {
            String devName = (traffic.getClientDevice() != null && !traffic.getClientDevice().equals(traffic.getClientIp()))
                    ? " (" + traffic.getClientDevice() + ")" : "";
            sb.append("   ").append(colorize(DIM, "Client:    ")).append(colorize(CYAN, traffic.getClientIp() + devName)).append("\n");
        }
        sb.append("   ").append(colorize(DIM, "Method:    ")).append(colorize(mColor, traffic.getMethod())).append("\n");
        sb.append("   ").append(colorize(DIM, "Base URL:  ")).append(traffic.getBaseUrl()).append("\n");
        sb.append("   ").append(colorize(DIM, "Endpoint:  ")).append(colorize(BOLD, traffic.getPath())).append("\n");

        // Request Headers
        if (traffic.getRequestHeaders() != null && !traffic.getRequestHeaders().isEmpty()) {
            sb.append("   ").append(colorize(DIM, "Headers:\n"));
            for (Map.Entry<String, String> header : traffic.getRequestHeaders().entrySet()) {
                sb.append(String.format("     %s: %s\n",
                        colorize(CYAN, header.getKey()),
                        header.getValue()));
            }
        }

        // Request Body
        if (traffic.getRequestBody() != null && !traffic.getRequestBody().isBlank()) {
            sb.append("   ").append(colorize(DIM, "Body:\n"));
            String prettyReqBody = prettyFormatBody(traffic.getRequestBody(), false);
            indent(sb, prettyReqBody, "     ");
            sb.append("\n");
        }

        sb.append("\n");

        // Response Section
        sb.append(colorize(BOLD + GREEN, "◀ RESPONSE\n"));
        sb.append("   ").append(colorize(DIM, "Status:    "))
                .append(colorize(BOLD + sColor, traffic.getStatusCode() + " " + traffic.getStatusMessage()))
                .append(" ").append(colorize(DIM, "(" + traffic.getDurationMs() + " ms)"))
                .append("\n");

        // Response Headers
        if (traffic.getResponseHeaders() != null && !traffic.getResponseHeaders().isEmpty()) {
            sb.append("   ").append(colorize(DIM, "Headers:\n"));
            for (Map.Entry<String, String> header : traffic.getResponseHeaders().entrySet()) {
                sb.append(String.format("     %s: %s\n",
                        colorize(GREEN, header.getKey()),
                        header.getValue()));
            }
        }

        // Response Body
        if (traffic.getResponseBody() != null && !traffic.getResponseBody().isBlank()) {
            sb.append("   ").append(colorize(DIM, "Body:\n"));
            String prettyRespBody = prettyFormatBody(traffic.getResponseBody(), true);
            indent(sb, prettyRespBody, "     ");
            sb.append("\n");
        }

        sb.append(colorize(DIM, "─".repeat(80)));
        return sb.toString();
    }

    /**
     * Pretty-prints JSON if valid, or returns raw string.
     * Truncates response bodies over 5000 characters.
     */
    public static String prettyFormatBody(String body, boolean isResponse) {
        if (body == null || body.isBlank()) {
            return "";
        }

        String formatted = body;
        String trimmed = body.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                JsonNode node = OBJECT_MAPPER.readTree(body);
                formatted = OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (Exception ignored) {
                // Not valid JSON, keep as is
            }
        }

        if (isResponse && formatted.length() > MAX_RESPONSE_BODY_LENGTH) {
            int originalLen = formatted.length();
            formatted = formatted.substring(0, MAX_RESPONSE_BODY_LENGTH)
                    + "\n" + colorize(YELLOW, String.format("... [Truncated: showing 5000 of %d characters]", originalLen));
        }

        return formatted;
    }

    private static void indent(StringBuilder sb, String text, String prefix) {
        String[] lines = text.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            sb.append(prefix).append(lines[i]);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
    }
}
