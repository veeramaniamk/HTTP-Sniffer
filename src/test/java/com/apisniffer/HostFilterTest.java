package com.apisniffer;

import com.apisniffer.filter.HostFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HostFilterTest {

    @Test
    void testEmptyFilterMatchesAll() {
        HostFilter filter = new HostFilter(null);
        assertFalse(filter.isFilterActive());
        assertTrue(filter.matches("api.example.com"));
        assertTrue(filter.matches("google.com"));
    }

    @Test
    void testExactAndSuffixMatch() {
        HostFilter filter = new HostFilter(List.of("api.github.com", "mybackend.io"));
        assertTrue(filter.isFilterActive());

        // Exact matches
        assertTrue(filter.matches("api.github.com"));
        assertTrue(filter.matches("API.GITHUB.COM"));
        assertTrue(filter.matches("api.github.com:443"));

        // Subdomain / suffix match
        assertTrue(filter.matches("auth.mybackend.io"));
        assertTrue(filter.matches("v2.auth.mybackend.io"));

        // Non-matches
        assertFalse(filter.matches("github.com"));
        assertFalse(filter.matches("evilmybackend.io"));
        assertFalse(filter.matches("google.com"));
    }

    @Test
    void testWildcardMatch() {
        HostFilter filter = new HostFilter(List.of("*.test.org"));
        assertTrue(filter.matches("api.test.org"));
        assertTrue(filter.matches("sub.api.test.org"));
        assertFalse(filter.matches("other.org"));
    }
}
