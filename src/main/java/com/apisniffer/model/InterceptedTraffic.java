package com.apisniffer.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * Represents a complete intercepted HTTP/HTTPS request and response pair.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterceptedTraffic {

    private long id;
    private String timestamp;
    private long durationMs;

    private String clientIp;
    private String clientDevice;

    private String method;
    private String url;
    private String baseUrl;
    private String path;
    private String scheme;
    private String host;
    private int port;

    private Map<String, String> requestHeaders;
    private String requestBody;

    private int statusCode;
    private String statusMessage;
    private Map<String, String> responseHeaders;
    private String responseBody;
    private boolean responseBodyTruncated;
    private int originalResponseBodyLength;

    public InterceptedTraffic() {
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getClientDevice() {
        return clientDevice;
    }

    public void setClientDevice(String clientDevice) {
        this.clientDevice = clientDevice;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public boolean isResponseBodyTruncated() {
        return responseBodyTruncated;
    }

    public void setResponseBodyTruncated(boolean responseBodyTruncated) {
        this.responseBodyTruncated = responseBodyTruncated;
    }

    public int getOriginalResponseBodyLength() {
        return originalResponseBodyLength;
    }

    public void setOriginalResponseBodyLength(int originalResponseBodyLength) {
        this.originalResponseBodyLength = originalResponseBodyLength;
    }
}
