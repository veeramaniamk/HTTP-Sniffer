package com.apisniffer.filter;

import java.util.*;

/**
 * Filters requests based on configured host patterns.
 * Supports exact hostname matches and domain suffix matches (e.g. "example.com" matches "api.example.com").
 */
public class HostFilter {

    private final Set<String> targetHosts = new HashSet<>();

    public HostFilter(List<String> hosts) {
        if (hosts != null) {
            for (String host : hosts) {
                if (host != null && !host.isBlank()) {
                    String clean = cleanHost(host);
                    targetHosts.add(clean.toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    /**
     * Determines whether the given host matches the configured filter.
     * If no hosts were configured, all requests match.
     */
    public boolean matches(String host) {
        if (targetHosts.isEmpty()) {
            return true;
        }
        if (host == null || host.isBlank()) {
            return false;
        }

        String normalized = cleanHost(host).toLowerCase(Locale.ROOT);

        for (String target : targetHosts) {
            // Exact match
            if (normalized.equals(target)) {
                return true;
            }
            // Wildcard prefix match, e.g. *.example.com
            if (target.startsWith("*.") && normalized.endsWith(target.substring(1))) {
                return true;
            }
            // Domain suffix match, e.g. target "example.com" matches "api.example.com"
            if (normalized.endsWith("." + target)) {
                return true;
            }
        }

        return false;
    }

    public boolean isFilterActive() {
        return !targetHosts.isEmpty();
    }

    public Set<String> getTargetHosts() {
        return Collections.unmodifiableSet(targetHosts);
    }

    private static String cleanHost(String host) {
        String h = host.trim();
        // Remove scheme if passed inadvertently (e.g. https://api.example.com)
        if (h.contains("://")) {
            h = h.substring(h.indexOf("://") + 3);
        }
        // Remove path if present
        int slashIdx = h.indexOf('/');
        if (slashIdx != -1) {
            h = h.substring(0, slashIdx);
        }
        // Remove port if present
        int colonIdx = h.indexOf(':');
        if (colonIdx != -1) {
            h = h.substring(0, colonIdx);
        }
        return h;
    }
}
