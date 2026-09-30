# ApiSniffer 🕵️‍♂️

A lightweight, high-performance Java application that acts as a local HTTPS-capable Man-in-the-Middle (MITM) proxy with an **interactive real-time Web Dashboard** and **Hotspot Device Inspector**. Designed for developers and QA engineers to inspect, filter, format, and log HTTP/HTTPS API traffic from mobile devices (such as an Android phone connected via a PC Wi-Fi hotspot).

Built with **Java 17+**, **Maven**, **BrowserUp Proxy** (`com.browserup:browserup-proxy-core`), and an embedded reactive Web Dashboard.

---

## Features

- 🌐 **Interactive Real-Time Web Dashboard**: Open `http://localhost:8081` in any browser to monitor live API traffic via Server-Sent Events (SSE).
- 📱 **Hotspot & Connected Device Detection**: Automatically discovers mobile devices connected to your Windows Wi-Fi Hotspot (`192.168.137.x` via ARP table and active proxy connections).
- 🎯 **Filter Traffic by Device**: Click any connected device in the Web UI sidebar to immediately isolate and inspect **only that device's** network calls!
- 🔐 **Automated TLS MITM**: Generates (or reuses) a self-signed Root CA certificate (`./certs/ca.pem` and `./certs/ca.crt`) on startup for decrypting HTTPS traffic.
- 📥 **One-Click Mobile CA Installation**: Download the CA certificate directly to your phone by opening `http://<PC-IP>:8081` in the mobile browser and tapping **"Download CA Cert"** (or visiting `http://<PC-IP>:8081/ca.pem`).
- 🎨 **Colorized Terminal Output**: Beautifully formats timestamp, HTTP method, base URL, endpoint path, query parameters, request headers, request body, response status code, response headers, and response body in the terminal.
- 📐 **Smart JSON Pretty-Printing & Truncation**: Automatically formats JSON payloads; truncates response bodies exceeding 5,000 characters to prevent console lag.
- 🔍 **Host Filtering**: Filter traffic by exact hostname, wildcard, or domain suffix using repeatable `--filter-host` flags (e.g. `--filter-host api.example.com`).
- 💾 **JSON Lines Logging**: Stream captured traffic records directly to a `.jsonl` file with `--save <file.jsonl>`.
- ⚡ **Lightweight CLI Modes**: Supports `--no-body` (headers and bodies skipped), `--quiet` (console output muted), and `--no-web` (headless CLI mode).
- 🛑 **Graceful Shutdown**: Traps `Ctrl+C` to cleanly stop the proxy and print an aggregated session summary (total requests, unique hosts seen, and duration).

---

## Project Structure

```
├── certs/                      # Generated Root CA certificate and private key
│   ├── ca.pem                  # CA certificate in PEM format (install on Android)
│   ├── ca.crt                  # CA certificate copy in CRT format
│   ├── ca.cer                  # CA certificate in DER format
│   └── ca-key.pem              # CA private key (reused on subsequent runs)
├── pom.xml                     # Maven project descriptor & fat-jar packaging
├── README.md                   # Documentation & setup guide
└── src/
    ├── main/
    │   ├── java/com/apisniffer/
    │   │   ├── Main.java                      # Main CLI entry point
    │   │   ├── cert/
    │   │   │   └── CertificateManager.java    # X.509 Root CA generation & reuse
    │   │   ├── cli/
    │   │   │   └── ApiSnifferCli.java         # Picocli command & network auto-detect
    │   │   ├── device/
    │   │   │   ├── DeviceInfo.java            # Connected device model
    │   │   │   └── DeviceManager.java         # ARP hotspot scanner & device naming
    │   │   ├── filter/
    │   │   │   └── HostFilter.java            # Domain & hostname matcher
    │   │   ├── logging/
    │   │   │   ├── ConsoleColor.java          # ANSI color definitions
    │   │   │   ├── ConsoleFormatter.java      # CLI formatter & JSON pretty-printer
    │   │   │   ├── JsonLineWriter.java        # Thread-safe JSONL file writer
    │   │   │   └── TrafficLogger.java         # Logger coordinator & stats summary
    │   │   ├── model/
    │   │   │   └── InterceptedTraffic.java    # Intercepted request/response model
    │   │   ├── proxy/
    │   │   │   ├── HttpTrafficInterceptor.java# Netty request/response interceptor
    │   │   │   └── ProxyServerManager.java    # BrowserUp Proxy lifecycle manager
    │   │   ├── shutdown/
    │   │   │   └── ShutdownHookHandler.java   # Clean shutdown hook & summary handler
    │   │   └── web/
    │   │       └── WebServerManager.java      # Embedded web server & SSE stream
    │   └── resources/
    │       ├── simplelogger.properties        # SLF4J logging configuration
    │       └── web/
    │           └── index.html                 # Sleek dark-mode Web Dashboard UI
    └── test/
        └── java/com/apisniffer/
            ├── ApiSnifferIntegrationTest.java # End-to-end proxy integration test
            ├── CertificateManagerTest.java    # Root CA generation & reload tests
            ├── ConsoleFormatterTest.java      # JSON formatting & truncation tests
            ├── DeviceManagerTest.java         # Device discovery & naming tests
            ├── HostFilterTest.java            # Host matching unit tests
            ├── JsonLineWriterTest.java        # JSONL persistence unit tests
            └── WebServerManagerTest.java      # Web API & dashboard tests
```

---

## Build Instructions

Build the standalone executable fat JAR:

```bash
mvn clean package
```

The executable shaded JAR will be generated at:
```
target/apisniffer.jar
```

To run all 17 automated tests:
```bash
mvn test
```

---

## Running ApiSniffer

### 1. Default Run (Proxy on 8080, Web Dashboard on 8081)
```bash
java -jar target/apisniffer.jar
```
When running, open your browser to **`http://localhost:8081`** to use the interactive Web Dashboard!

### 2. Custom Proxy Port and Web Port
```bash
java -jar target/apisniffer.jar --port 8080 --web-port 9090
```

### 3. Filter Specific Hosts / Domains
Only log requests going to matching hosts:
```bash
java -jar target/apisniffer.jar --filter-host api.example.com --filter-host auth.example.com
```

### 4. Save Captured Traffic to JSON Lines (`.jsonl`)
```bash
java -jar target/apisniffer.jar --save ./traffic.jsonl
```

### 5. Headless / CLI-Only Mode (Disable Web Dashboard)
```bash
java -jar target/apisniffer.jar --no-web
```

### 6. Fast / No-Body Mode (Method, URL, Status Only)
```bash
java -jar target/apisniffer.jar --no-body
```

### CLI Flag Reference

| Flag | Description | Default |
|---|---|---|
| `-p, --port <port>` | Proxy listening port on `0.0.0.0` | `8080` |
| `--web-port <port>` | Web dashboard listening port | `8081` |
| `--no-web` | Disable the web dashboard interface | Web UI enabled |
| `--filter-host <host>` | Only log requests matching this host (repeatable) | All hosts |
| `-s, --save <file>` | Append logged traffic as JSON Lines (`.jsonl`) | Disabled |
| `-q, --quiet` | Suppress console output, only write to file | Disabled |
| `--no-body` | Log only method, URL, and status (skip headers & bodies) | Disabled |
| `-h, --help` | Show help and available options | — |
| `-V, --version` | Display version information | — |

---

## Using the Web Dashboard

When ApiSniffer is running, navigate to **`http://localhost:8081`** (or `http://192.168.137.1:8081` from any device on your hotspot):

1. **Connected Devices Panel (Left Sidebar)**:
   - Lists all devices detected via Hotspot ARP and active network traffic.
   - Shows device name, IP address (`192.168.137.x`), MAC address, and total request counts.
   - **Clicking any device isolates the feed to show ONLY calls from that device!**
   - Click "All Devices" to view traffic from all clients combined.

2. **Live Traffic Stream (Middle Pane)**:
   - Incoming HTTP/HTTPS requests appear in real time via Server-Sent Events.
   - Search bar: filter by URL path, query param, method, or status.
   - Method chips: filter by `GET`, `POST`, `PUT`, `DELETE`.
   - Pause / Resume toggle button to freeze inspection on a specific call.

3. **Inspector (Right Pane)**:
   - Click any request in the table to inspect:
     - **Request**: Headers table, Body (pretty-printed JSON with one-click copy).
     - **Response**: Status, latency, Headers, Body (pretty-printed JSON).
     - **Overview**: Client IP, Client Device, Base URL, Endpoint.
     - **Copy cURL**: One-click generate full `curl` command for replay in terminal/Postman.

4. **One-Click CA Certificate Download**:
   - Tap the **"📥 Download CA Cert"** button in the top navigation bar to download `ca.pem` directly to your phone or desktop.

---

## Android Device Setup (via Windows Wi-Fi Hotspot)

### Step 1: Turn on Windows Mobile Hotspot
1. On your Windows PC, open **Settings** > **Network & internet** > **Mobile hotspot**.
2. Toggle Mobile hotspot to **On**.
3. Note the Network name (SSID) and Network password.

### Step 2: Connect Android Phone to Hotspot
1. On your Android phone, go to **Settings** > **Wi-Fi** (or **Network & internet** > **Internet**).
2. Connect to the PC hotspot network.

### Step 3: Configure Wi-Fi Manual Proxy on Android
1. On the Android phone, tap the **Gear icon** next to the connected hotspot network (or long-press the network name and tap **Modify network**).
2. Tap the **Pencil (Edit) icon** or expand **Advanced options**.
3. Under **Proxy**, change from **None** to **Manual**.
4. Set the fields:
   - **Proxy hostname**: Your PC's hotspot IP (e.g. `192.168.137.1` — *ApiSniffer automatically displays your detected hotspot IP in the terminal on startup*).
   - **Proxy port**: `8080` (or the port specified with `--port`).
   - **Bypass proxy for**: Leave empty.
5. Tap **Save**.

---

## Installing the Root CA Certificate on Android

To inspect HTTPS traffic without SSL handshake errors, install the generated Root CA on your device:

### Option A: Direct Download via Web Dashboard (Easiest)
1. On your Android phone's browser, visit:
   ```
   http://192.168.137.1:8081/ca.pem
   ```
   *(Or open `http://192.168.137.1:8081` and tap **"Download CA Cert"**).*
2. Android will download `ca.pem` directly into your **Downloads** folder.

### Option B: Transfer via ADB or USB
```bash
adb push ./certs/ca.pem /sdcard/Download/ca.pem
```

### Install as a Trusted CA Certificate
1. On Android, open **Settings** > **Security & Privacy** > **More security settings** > **Encryption & credentials**.
   *(On older Android versions: Settings > Security > Install from storage or Install a certificate).*
2. Tap **Install a certificate** > **CA certificate**.
3. If prompted with a warning (*"Your data won't be private"*), tap **Install anyway**.
4. Select `ca.pem` from your Downloads folder.
5. Confirm your device PIN / pattern.
6. A toast message will confirm: **"CA certificate installed"**.

### Android App Network Security Configuration (Android 7.0+ / API 24+)
If you are developing or testing an Android application, ensure debug builds trust user certificates:

In your Android project's `res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
            <certificates src="system" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

And in `AndroidManifest.xml`:
```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    ...>
```

---

## Constraints & Responsible Use

ApiSniffer is designed solely for debugging, performance testing, and inspecting applications on devices you own or have explicit authorization to test on a private network under your control. It does not include features for stealth operation, hiding proxy indicators, or circumventing certificate pinning.
