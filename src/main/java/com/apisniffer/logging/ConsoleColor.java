package com.apisniffer.logging;

/**
 * ANSI color and style constants for terminal output.
 */
public class ConsoleColor {

    private static boolean ansiSupported = isAnsiSupported();

    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String UNDERLINE = "\u001B[4m";

    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_MAGENTA = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_WHITE = "\u001B[97m";

    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_GREEN = "\u001B[42m";
    public static final String BG_YELLOW = "\u001B[43m";
    public static final String BG_RED = "\u001B[41m";

    public static boolean isAnsi() {
        return ansiSupported;
    }

    public static void setAnsi(boolean enabled) {
        ansiSupported = enabled;
    }

    public static String colorize(String color, String text) {
        if (!ansiSupported || color == null) {
            return text;
        }
        return color + text + RESET;
    }

    public static String methodColor(String method) {
        if (method == null) return WHITE;
        return switch (method.toUpperCase()) {
            case "GET" -> BRIGHT_CYAN;
            case "POST" -> BRIGHT_GREEN;
            case "PUT" -> BRIGHT_YELLOW;
            case "DELETE" -> BRIGHT_RED;
            case "PATCH" -> BRIGHT_MAGENTA;
            case "OPTIONS", "HEAD" -> DIM;
            default -> BRIGHT_WHITE;
        };
    }

    public static String statusColor(int code) {
        if (code >= 200 && code < 300) {
            return BRIGHT_GREEN;
        } else if (code >= 300 && code < 400) {
            return BRIGHT_CYAN;
        } else if (code >= 400 && code < 500) {
            return BRIGHT_YELLOW;
        } else if (code >= 500) {
            return BRIGHT_RED;
        }
        return WHITE;
    }

    private static boolean isAnsiSupported() {
        if (System.getenv("NO_COLOR") != null) {
            return false;
        }
        // Windows 10/11 console natively supports ANSI
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return true;
        }
        return System.console() != null || System.getenv("TERM") != null;
    }
}
