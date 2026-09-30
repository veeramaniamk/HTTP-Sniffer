package com.apisniffer.proxy;

import com.apisniffer.filter.HostFilter;
import com.apisniffer.logging.TrafficLogger;
import com.apisniffer.model.InterceptedTraffic;
import com.browserup.bup.filters.RequestFilter;
import com.browserup.bup.filters.ResponseFilter;
import com.browserup.bup.util.HttpMessageContents;
import com.browserup.bup.util.HttpMessageInfo;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intercepts HTTP/HTTPS requests and responses in BrowserUp Proxy,
 * correlates them, applies host filtering, and logs them.
 */
public class HttpTrafficInterceptor implements RequestFilter, ResponseFilter {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final HostFilter hostFilter;
    private final TrafficLogger trafficLogger;

    // LRU-bounded map to prevent memory leak for disconnected/aborted requests
    private final Map<HttpRequest, PendingRequest> pendingRequests = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<HttpRequest, PendingRequest> eldest) {
                    return size() > 2000;
                }
            }
    );

    public HttpTrafficInterceptor(HostFilter hostFilter, TrafficLogger trafficLogger) {
        this.hostFilter = hostFilter;
        this.trafficLogger = trafficLogger;
    }

    private static final io.netty.util.AttributeKey<PendingRequest> ATTR_PENDING =
            io.netty.util.AttributeKey.valueOf("APISNIFFER_PENDING_REQ");

    @Override
    public HttpResponse filterRequest(HttpRequest request, HttpMessageContents contents, HttpMessageInfo messageInfo) {
        try {
            ParsedUrl parsedUrl = parseUrl(request, messageInfo);

            // Check if this host matches the filter
            if (!hostFilter.matches(parsedUrl.host)) {
                return null;
            }

            PendingRequest pending = new PendingRequest();
            pending.timestamp = ZonedDateTime.now().format(ISO_FORMATTER);
            pending.startTimeMs = System.currentTimeMillis();
            pending.method = request.method().name();
            pending.parsedUrl = parsedUrl;
            pending.headers = extractHeaders(request.headers());
            pending.body = (contents != null) ? contents.getTextContents() : "";

            HttpRequest key = (messageInfo != null && messageInfo.getOriginalRequest() != null)
                    ? messageInfo.getOriginalRequest() : request;
            pendingRequests.put(key, pending);
            pendingRequests.put(request, pending);

            if (messageInfo != null && messageInfo.getChannelHandlerContext() != null) {
                messageInfo.getChannelHandlerContext().channel().attr(ATTR_PENDING).set(pending);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void filterResponse(HttpResponse response, HttpMessageContents contents, HttpMessageInfo messageInfo) {
        try {
            PendingRequest pending = null;

            if (messageInfo != null && messageInfo.getOriginalRequest() != null) {
                pending = pendingRequests.remove(messageInfo.getOriginalRequest());
            }

            if (pending == null && messageInfo != null && messageInfo.getChannelHandlerContext() != null) {
                pending = messageInfo.getChannelHandlerContext().channel().attr(ATTR_PENDING).getAndSet(null);
            }

            if (pending == null) {
                // Either didn't match host filter or already processed
                return;
            }

            long durationMs = System.currentTimeMillis() - pending.startTimeMs;

            InterceptedTraffic traffic = new InterceptedTraffic();
            traffic.setTimestamp(pending.timestamp);
            traffic.setDurationMs(durationMs);
            traffic.setMethod(pending.method);
            traffic.setUrl(pending.parsedUrl.fullUrl);
            traffic.setBaseUrl(pending.parsedUrl.baseUrl);
            traffic.setPath(pending.parsedUrl.path);
            traffic.setScheme(pending.parsedUrl.scheme);
            traffic.setHost(pending.parsedUrl.host);
            traffic.setPort(pending.parsedUrl.port);

            traffic.setRequestHeaders(pending.headers);
            traffic.setRequestBody(pending.body);

            traffic.setStatusCode(response.status().code());
            traffic.setStatusMessage(response.status().reasonPhrase());
            traffic.setResponseHeaders(extractHeaders(response.headers()));

            String respBody = (contents != null) ? contents.getTextContents() : "";
            traffic.setResponseBody(respBody);
            traffic.setOriginalResponseBodyLength(respBody != null ? respBody.length() : 0);

            trafficLogger.logTraffic(traffic);
        } catch (Exception e) {
            // Ignore filter error to avoid interrupting proxy client traffic
        }
    }

    private Map<String, String> extractHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (map.containsKey(key)) {
                map.put(key, map.get(key) + ", " + value);
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private ParsedUrl parseUrl(HttpRequest request, HttpMessageInfo messageInfo) {
        String urlStr = messageInfo.getUrl();
        if (urlStr == null || urlStr.isBlank()) {
            urlStr = messageInfo.getOriginalUrl();
        }
        if (urlStr == null || urlStr.isBlank()) {
            urlStr = request.uri();
        }

        boolean isHttps = messageInfo.isHttps();
        String defaultScheme = isHttps ? "https" : "http";

        try {
            if (!urlStr.contains("://")) {
                // Relative URL, look for Host header
                String hostHeader = request.headers().get("Host");
                if (hostHeader != null && !hostHeader.isBlank()) {
                    urlStr = defaultScheme + "://" + hostHeader + (urlStr.startsWith("/") ? urlStr : "/" + urlStr);
                } else {
                    urlStr = defaultScheme + "://localhost" + (urlStr.startsWith("/") ? urlStr : "/" + urlStr);
                }
            }

            URI uri = URI.create(urlStr);
            String scheme = (uri.getScheme() != null) ? uri.getScheme().toLowerCase(Locale.ROOT) : defaultScheme;
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                String hostHeader = request.headers().get("Host");
                host = (hostHeader != null) ? hostHeader : "unknown";
                if (host.contains(":")) {
                    host = host.substring(0, host.indexOf(':'));
                }
            }

            int port = uri.getPort();
            int defaultPort = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            String baseUrl;
            if (port != -1 && port != defaultPort) {
                baseUrl = scheme + "://" + host + ":" + port;
            } else {
                baseUrl = scheme + "://" + host;
            }

            String path = uri.getRawPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                path += "?" + uri.getRawQuery();
            }

            return new ParsedUrl(urlStr, baseUrl, path, scheme, host, port != -1 ? port : defaultPort);
        } catch (Exception e) {
            return new ParsedUrl(urlStr, defaultScheme + "://unknown", "/", defaultScheme, "unknown", isHttps ? 443 : 80);
        }
    }

    private static class PendingRequest {
        String timestamp;
        long startTimeMs;
        String method;
        ParsedUrl parsedUrl;
        Map<String, String> headers;
        String body;
    }

    private static class ParsedUrl {
        final String fullUrl;
        final String baseUrl;
        final String path;
        final String scheme;
        final String host;
        final int port;

        ParsedUrl(String fullUrl, String baseUrl, String path, String scheme, String host, int port) {
            this.fullUrl = fullUrl;
            this.baseUrl = baseUrl;
            this.path = path;
            this.scheme = scheme;
            this.host = host;
            this.port = port;
        }
    }
}
