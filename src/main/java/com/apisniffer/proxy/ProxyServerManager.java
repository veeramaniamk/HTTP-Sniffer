package com.apisniffer.proxy;

import com.apisniffer.cert.CertificateManager;
import com.browserup.bup.BrowserUpProxyServer;
import com.browserup.bup.mitm.manager.ImpersonatingMitmManager;

import java.net.InetAddress;

/**
 * Manages the lifecycle and configuration of the BrowserUp Proxy server.
 */
public class ProxyServerManager {

    private final int port;
    private final CertificateManager certificateManager;
    private final HttpTrafficInterceptor trafficInterceptor;

    private BrowserUpProxyServer proxyServer;

    public ProxyServerManager(int port, CertificateManager certificateManager, HttpTrafficInterceptor trafficInterceptor) {
        this.port = port;
        this.certificateManager = certificateManager;
        this.trafficInterceptor = trafficInterceptor;
    }

    /**
     * Starts the MITM proxy listening on 0.0.0.0:port.
     */
    public synchronized void start() throws Exception {
        if (proxyServer != null && proxyServer.isStarted()) {
            return;
        }

        // Configure TLS MITM manager with our custom root CA
        ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
                .rootCertificateSource(certificateManager.getCertificateAndKeySource())
                .trustAllServers(true)
                .build();

        proxyServer = new BrowserUpProxyServer();
        proxyServer.setMitmManager(mitmManager);
        proxyServer.setTrustAllServers(true);

        // Attach request and response interceptors
        proxyServer.addRequestFilter(trafficInterceptor);
        proxyServer.addResponseFilter(trafficInterceptor);

        // Bind to 0.0.0.0:port
        InetAddress bindAddress = InetAddress.getByName("0.0.0.0");
        proxyServer.start(port, bindAddress);
    }

    /**
     * Cleanly stops the proxy server.
     */
    public synchronized void stop() {
        if (proxyServer != null) {
            try {
                if (proxyServer.isStarted()) {
                    proxyServer.stop();
                }
            } catch (Exception ignored) {
            } finally {
                proxyServer = null;
            }
        }
    }

    public synchronized boolean isStarted() {
        return proxyServer != null && proxyServer.isStarted();
    }

    public int getPort() {
        return (proxyServer != null && proxyServer.isStarted()) ? proxyServer.getPort() : port;
    }
}
