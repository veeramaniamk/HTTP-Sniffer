package com.apisniffer.device;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceInfo {

    private String ip;
    private String mac;
    private String name;
    private String userAgent;
    private boolean isHotspot;
    private String firstSeen;
    private String lastSeen;
    private long requestCount;
    private boolean online;

    public DeviceInfo() {
    }

    public DeviceInfo(String ip, String mac, String name, boolean isHotspot) {
        this.ip = ip;
        this.mac = mac;
        this.name = name;
        this.isHotspot = isHotspot;
        this.online = true;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("isHotspot")
    public boolean isHotspot() {
        return isHotspot;
    }

    public void setHotspot(boolean hotspot) {
        isHotspot = hotspot;
    }

    public String getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(String firstSeen) {
        this.firstSeen = firstSeen;
    }

    public String getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(String lastSeen) {
        this.lastSeen = lastSeen;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
