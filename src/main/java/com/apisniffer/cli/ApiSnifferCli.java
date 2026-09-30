package com.apisniffer.cli;

import com.apisniffer.cert.CertificateManager;
import com.apisniffer.filter.HostFilter;
import com.apisniffer.logging.TrafficLogger;
import com.apisniffer.proxy.HttpTrafficInterceptor;
import com.apisniffer.proxy.ProxyServerManager;
import com.apisniffer.shutdown.ShutdownHookHandler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static com.apisniffer.logging.ConsoleColor.*;

@Command(
        name = "apisniffer",
        mixinStandardHelpOptions = true,
        version = "ApiSniffer 1.0.0",
        description = "HTTPS-capable local MITM proxy to log API calls from connected mobile devices.",
        sortOptions = false
)
public class ApiSnifferCli implements Callable<Integer> {

    @Option(
            names = {"-p", "--port"},
            description = "Proxy listening port (default: ${DEFAULT-VALUE})",
            defaultValue = "8080"
    )
    private int port;

    @Option(
            names = {"--filter-host"},
            description = "Only log requests matching this host/domain (repeatable for multiple hosts)",
            paramLabel = "<host>"
    )
    private List<String> filterHosts = new ArrayList<>();

    @Option(
            names = {"-s", "--save"},
            description = "Append every logged entry as JSON Lines to a file",
            paramLabel = "<file.jsonl>"
    )
    private File saveFile;

    @Option(
            names = {"-q", "--quiet"},
            description = "Suppress console output, only write to file"
    )
    private boolean quiet;

    @Option(
            names = {"--no-body"},
            description = "Log only method/URL/status, skip headers and bodies"
    )
    private boolean noBody;

    private final CountDownLatch keepAliveLatch = new CountDownLatch(1);

    @Override
    public Integer call() {
        printBanner();

        try {
            // 1. Root CA setup
            CertificateManager certManager = new CertificateManager();
            certManager.initialize();

            if (certManager.isNewlyGenerated()) {
                System.out.println(colorize(BOLD + GREEN, "[+] Generated new self-signed Root CA certificate:"));
                System.out.println("    Cert PEM: " + colorize(BOLD, certManager.getCaCertPemFile().getAbsolutePath()));
                System.out.println("    Cert CRT: " + colorize(BOLD, certManager.getCaCrtFile().getAbsolutePath()));
                System.out.println("    Key PEM:  " + colorize(DIM, certManager.getCaKeyPemFile().getAbsolutePath()));
                System.out.println(colorize(YELLOW, "    >> ACTION: Install ./certs/ca.pem on your Android device as a trusted CA certificate."));
            } else {
                System.out.println(colorize(BOLD + GREEN, "[+] Reusing existing Root CA certificate:"));
                System.out.println("    Cert PEM: " + colorize(BOLD, certManager.getCaCertPemFile().getAbsolutePath()));
            }

            // 2. Host filter setup
            HostFilter hostFilter = new HostFilter(filterHosts);
            if (hostFilter.isFilterActive()) {
                System.out.println(colorize(BOLD + CYAN, "[+] Host filter active:") + " " + hostFilter.getTargetHosts());
            } else {
                System.out.println(colorize(DIM, "[+] Host filter: (all hosts allowed)"));
            }

            // 3. Logger setup
            if (quiet && saveFile == null) {
                System.out.println(colorize(YELLOW, "[WARN] --quiet is set without --save. No output will be saved or displayed!"));
            }
            if (saveFile != null) {
                System.out.println(colorize(BOLD + CYAN, "[+] JSON Lines logging:") + " " + saveFile.getAbsolutePath());
            }
            if (noBody) {
                System.out.println(colorize(BOLD + CYAN, "[+] Mode:") + " --no-body (logging method, URL, and status only)");
            }

            TrafficLogger trafficLogger = new TrafficLogger(quiet, noBody, saveFile);

            // 4. Traffic interceptor setup
            HttpTrafficInterceptor interceptor = new HttpTrafficInterceptor(hostFilter, trafficLogger);

            // 5. Proxy server setup
            ProxyServerManager proxyServerManager = new ProxyServerManager(port, certManager, interceptor);

            // 6. Graceful shutdown hook
            ShutdownHookHandler shutdownHandler = new ShutdownHookHandler(proxyServerManager, trafficLogger);
            shutdownHandler.register();

            // 7. Start proxy
            proxyServerManager.start();
            System.out.println();
            System.out.println(colorize(BOLD + BRIGHT_GREEN, "[✓] ApiSniffer proxy listening on 0.0.0.0:" + proxyServerManager.getPort()));

            // Print local IP addresses to make hotspot setup painless
            printHostNetworkInfo(proxyServerManager.getPort());

            System.out.println();
            System.out.println(colorize(DIM, "Intercepting HTTP/HTTPS traffic. Press Ctrl+C to stop..."));
            System.out.println();

            // 8. Keep process running until shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(keepAliveLatch::countDown));
            keepAliveLatch.await();

            return 0;
        } catch (Exception e) {
            System.err.println(colorize(BOLD + RED, "[ERROR] ApiSniffer failed to start: " + e.getMessage()));
            e.printStackTrace();
            return 1;
        }
    }

    private void printBanner() {
        System.out.println(colorize(BOLD + CYAN,
                """
                  _             _ ____            _  __  __\s
                 / \\   _ __ ___(_) ___| _ __ (_) |/ _|/ _| ___ _ __\s
                / _ \\ | '_ ` _ \\ \\___ \\| '_ \\| | | |_| |_ / _ \\ '__|
               / ___ \\| | | | | | |___) | | | | | |  _|  _|  __/ |\s
              /_/   \\_\\_| |_| |_|_|____/|_| |_|_|_|_| |_|  \\___|_|\s
               HTTPS-capable Local MITM API Sniffer & Debugging Proxy
                """));
    }

    private void printHostNetworkInfo(int proxyPort) {
        System.out.println(colorize(DIM, "  Configure your test device (Android Wi-Fi > Proxy > Manual):"));
        List<String> ips = getLocalIpAddresses();
        if (ips.isEmpty()) {
            System.out.println("    Proxy Host: <Your PC's IP address>");
        } else {
            for (String ip : ips) {
                String note = ip.startsWith("192.168.137.") ? " (Typical Windows Hotspot IP)" : "";
                System.out.println("    Proxy Host: " + colorize(BOLD + BRIGHT_WHITE, ip) + note);
            }
        }
        System.out.println("    Proxy Port: " + colorize(BOLD + BRIGHT_WHITE, String.valueOf(proxyPort)));
    }

    private List<String> getLocalIpAddresses() {
        List<String> list = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        list.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }
}
