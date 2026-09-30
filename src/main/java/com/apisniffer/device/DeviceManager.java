package com.apisniffer.device;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages discovery and activity tracking for devices connected via
 * the PC Wi-Fi hotspot and local proxy clients.
 */
public class DeviceManager {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("Interface:\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARP_LINE_PATTERN = Pattern.compile("^\\s*([0-9.]+)\\s+([0-9a-fA-F-]+)\\s+(\\w+)", Pattern.CASE_INSENSITIVE);

    private final Map<String, DeviceInfo> devices = new ConcurrentHashMap<>();
    private long lastArpScanTime = 0;
    private static final long ARP_CACHE_TTL_MS = 5000;

    public DeviceManager() {
        // Run initial scan
        scanArpTable();
    }

    /**
     * Records network activity from a client IP and User-Agent.
     */
    public DeviceInfo recordActivity(String clientIp, String userAgent) {
        if (clientIp == null || clientIp.isBlank() || "unknown".equalsIgnoreCase(clientIp)) {
            return null;
        }

        String now = LocalTime.now().format(TIME_FMT);
        DeviceInfo device = devices.computeIfAbsent(clientIp, ip -> {
            boolean isHotspot = ip.startsWith("192.168.137.");
            String defaultName = isHotspot ? "Hotspot Device (" + ip + ")" : "Client (" + ip + ")";
            DeviceInfo d = new DeviceInfo(ip, null, defaultName, isHotspot);
            d.setFirstSeen(now);
            return d;
        });

        device.setLastSeen(now);
        device.setRequestCount(device.getRequestCount() + 1);
        device.setOnline(true);

        if (userAgent != null && !userAgent.isBlank()) {
            device.setUserAgent(userAgent);
            String inferredName = inferDeviceName(clientIp, userAgent, device.isHotspot());
            if (inferredName != null) {
                device.setName(inferredName);
            }
        }

        return device;
    }

    /**
     * Retrieves all known devices, updating from ARP table if cache expired.
     */
    public List<DeviceInfo> getAllDevices() {
        if (System.currentTimeMillis() - lastArpScanTime > ARP_CACHE_TTL_MS) {
            scanArpTable();
        }

        List<DeviceInfo> list = new ArrayList<>(devices.values());
        // Sort: Hotspot devices first, then highest request count, then IP
        list.sort((a, b) -> {
            if (a.isHotspot() != b.isHotspot()) {
                return a.isHotspot() ? -1 : 1;
            }
            if (a.getRequestCount() != b.getRequestCount()) {
                return Long.compare(b.getRequestCount(), a.getRequestCount());
            }
            return a.getIp().compareTo(b.getIp());
        });
        return list;
    }

    public DeviceInfo getDevice(String ip) {
        return (ip != null) ? devices.get(ip) : null;
    }

    /**
     * Scans the Windows ARP table to identify connected hotspot and LAN clients.
     */
    public synchronized void scanArpTable() {
        lastArpScanTime = System.currentTimeMillis();
        try {
            Process process = new ProcessBuilder("arp", "-a").start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                String currentInterface = null;
                boolean currentIsHotspot = false;

                while ((line = reader.readLine()) != null) {
                    Matcher ifaceMatcher = INTERFACE_PATTERN.matcher(line);
                    if (ifaceMatcher.find()) {
                        currentInterface = ifaceMatcher.group(1);
                        // Windows Hotspot default subnet is 192.168.137.x
                        currentIsHotspot = currentInterface.startsWith("192.168.137.");
                        continue;
                    }

                    Matcher arpMatcher = ARP_LINE_PATTERN.matcher(line);
                    if (arpMatcher.find()) {
                        String ip = arpMatcher.group(1);
                        String mac = arpMatcher.group(2).replace('-', ':').toLowerCase(Locale.ROOT);

                        // Skip broadcast and multicast addresses
                        if (ip.endsWith(".255") || ip.startsWith("224.") || ip.startsWith("239.") || ip.equals("255.255.255.255")) {
                            continue;
                        }
                        if (currentInterface != null && ip.equals(currentInterface)) {
                            continue; // Skip the host's own interface IP
                        }

                        boolean isHotspotDevice = currentIsHotspot || ip.startsWith("192.168.137.");
                        String now = LocalTime.now().format(TIME_FMT);

                        DeviceInfo existing = devices.get(ip);
                        if (existing != null) {
                            if (existing.getMac() == null || existing.getMac().isBlank()) {
                                existing.setMac(mac);
                            }
                            if (isHotspotDevice) {
                                existing.setHotspot(true);
                            }
                        } else {
                            String name = isHotspotDevice ? "Hotspot Device (" + ip + ")" : "Client (" + ip + ")";
                            DeviceInfo newDevice = new DeviceInfo(ip, mac, name, isHotspotDevice);
                            newDevice.setFirstSeen(now);
                            newDevice.setLastSeen(now);
                            devices.put(ip, newDevice);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Ignore ARP failure if OS prohibits or command unavailable
        }
    }

    /**
     * Deduces human-friendly device name from User-Agent.
     */
    public static String inferDeviceName(String ip, String ua, boolean isHotspot) {
        if (ua == null) {
            return isHotspot ? "Hotspot Device (" + ip + ")" : "Client (" + ip + ")";
        }

        String lower = ua.toLowerCase(Locale.ROOT);

        // Android phone models in User-Agent, e.g. "Linux; Android 14; Pixel 7 Build/..."
        if (lower.contains("android")) {
            Matcher m = Pattern.compile("Android\\s*([0-9.]+);\\s*([^);]+)").matcher(ua);
            if (m.find()) {
                String androidVer = m.group(1);
                String model = m.group(2).trim();
                if (model.contains("Build/")) {
                    model = model.substring(0, model.indexOf("Build/")).trim();
                }
                return model + " (Android " + androidVer + ")";
            }
            if (lower.contains("pixel")) {
                return "Google Pixel (Android)";
            }
            if (lower.contains("samsung") || lower.contains("sm-")) {
                return "Samsung Galaxy (Android)";
            }
            if (lower.contains("okhttp")) {
                return "Android App (" + ip + ")";
            }
            return "Android Device (" + ip + ")";
        }

        if (lower.contains("iphone")) {
            return "Apple iPhone (" + ip + ")";
        }
        if (lower.contains("ipad")) {
            return "Apple iPad (" + ip + ")";
        }
        if (lower.contains("macintosh") || lower.contains("mac os x")) {
            return "macOS Client (" + ip + ")";
        }
        if (lower.contains("windows")) {
            return "Windows PC (" + ip + ")";
        }
        if (lower.contains("curl")) {
            return "curl Client (" + ip + ")";
        }
        if (lower.contains("postman")) {
            return "Postman Client (" + ip + ")";
        }

        return isHotspot ? "Hotspot Device (" + ip + ")" : "Client (" + ip + ")";
    }
}
