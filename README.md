# ApiSniffer 🕵️‍♂️

A lightweight, high-performance Java command-line application that acts as a local HTTPS-capable Man-in-the-Middle (MITM) proxy. Designed for developers and QA engineers to inspect, format, and log HTTP/HTTPS API traffic from mobile devices (such as an Android phone connected via a PC Wi-Fi hotspot) and other local clients.

Built with **Java 17+**, **Maven**, and **BrowserUp Proxy** (`com.browserup:browserup-proxy-core`).

---

## Features

- 🔐 **Automated TLS MITM**: Generates (or reuses) a self-signed Root CA certificate (`./certs/ca.pem` and `./certs/ca.crt`) on startup for decrypting HTTPS traffic.
- 🎨 **Colorized Terminal Output**: Beautifully formats timestamp, HTTP method, base URL, endpoint path, query parameters, request headers, request body, response status code, response headers, and response body.
- 📐 **Smart JSON Pretty-Printing & Truncation**: Automatically detects and pretty-prints JSON payloads; truncates response bodies exceeding 5,000 characters to keep console output readable.
- 🔍 **Host Filtering**: Filter traffic by exact hostname, wildcard, or domain suffix using repeatable `--filter-host` flags (e.g. `--filter-host api.example.com`).
- 💾 **JSON Lines Logging**: Stream captured traffic records directly to a `.jsonl` file with `--save <file.jsonl>`.
- ⚡ **Lightweight CLI Modes**: Supports `--no-body` (headers and bodies skipped) and `--quiet` (console output muted, writes to file only).
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
    │   │   └── shutdown/
    │   │       └── ShutdownHookHandler.java   # Clean shutdown hook & summary handler
    │   └── resources/
    │       └── simplelogger.properties        # SLF4J logging configuration
    └── test/
        └── java/com/apisniffer/
            ├── ApiSnifferIntegrationTest.java # End-to-end proxy integration test
            ├── CertificateManagerTest.java    # Root CA generation & reload tests
            ├── ConsoleFormatterTest.java      # JSON formatting & truncation tests
            ├── HostFilterTest.java            # Host matching unit tests
            └── JsonLineWriterTest.java        # JSONL persistence unit tests
```

---

## Requirements

- **Java 17 or higher** (tested on Java 17 and Java 21 LTS)
- **Apache Maven 3.8+**

---

## Build Instructions

Build the project and produce the executable standalone JAR:

```bash
mvn clean package
```

The executable shaded JAR will be generated at:
```
target/apisniffer.jar
```

To run tests only:
```bash
mvn test
```

---

## Running ApiSniffer

### 1. Default Run (Listening on port 8080)
```bash
java -jar target/apisniffer.jar
```

### 2. Custom Port
```bash
java -jar target/apisniffer.jar --port 9090
```

### 3. Filter Specific Hosts / Domains
Only log requests going to matching hosts (non-matching traffic passes through unhindered):
```bash
java -jar target/apisniffer.jar --filter-host api.example.com --filter-host auth.example.com
```
*Note: Domain suffix matching is supported. Specifying `--filter-host example.com` matches `example.com`, `api.example.com`, and `v2.auth.example.com`.*

### 4. Save Captured Traffic to JSON Lines (`.jsonl`)
```bash
java -jar target/apisniffer.jar --save ./traffic.jsonl
```

### 5. Quiet Mode (Headless / File Logging Only)
```bash
java -jar target/apisniffer.jar --quiet --save ./traffic.jsonl
```

### 6. Fast / No-Body Mode (Method, URL, Status Only)
```bash
java -jar target/apisniffer.jar --no-body
```

### CLI Flag Reference

| Flag | Description | Default |
|---|---|---|
| `-p, --port <port>` | Proxy listening port on `0.0.0.0` | `8080` |
| `--filter-host <host>` | Only log requests matching this host (repeatable) | All hosts |
| `-s, --save <file>` | Append logged traffic as JSON Lines (`.jsonl`) | Disabled |
| `-q, --quiet` | Suppress console output, only write to file | Disabled |
| `--no-body` | Log only method, URL, and status (skip headers & bodies) | Disabled |
| `-h, --help` | Show help and available options | — |
| `-V, --version` | Display version information | — |

---

## Android Device Setup (via Windows Wi-Fi Hotspot)

Follow these steps to route network traffic from your physical Android test device through ApiSniffer:

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
   - **Bypass proxy for**: Leave empty or default.
5. Tap **Save**.

---

## Installing the Root CA Certificate on Android

To inspect HTTPS traffic without SSL handshake errors, install the generated Root CA on your device:

### Step 1: Transfer Certificate to Android
When ApiSniffer runs for the first time, it creates `./certs/ca.pem` and `./certs/ca.crt`. Transfer either file to your phone:
- **Via ADB**:
  ```bash
  adb push ./certs/ca.pem /sdcard/Download/ca.pem
  ```
- **Via USB cable**: Copy `./certs/ca.pem` into the phone's **Downloads** folder.
- **Via Local HTTP server** (optional quick transfer):
  ```bash
  # In another terminal from the certs directory:
  python -m http.server 9999 --directory ./certs
  # On the phone's browser, visit: http://<PC-IP>:9999/ca.pem
  ```

### Step 2: Install as a Trusted CA Certificate
1. On Android, open **Settings** > **Security & Privacy** > **More security settings** > **Encryption & credentials**.
   *(On older Android versions: Settings > Security > Install from storage or Install a certificate).*
2. Tap **Install a certificate** > **CA certificate**.
3. If prompted with a warning (*"Your data won't be private"*), tap **Install anyway**.
4. Use the file picker to navigate to your **Downloads** folder and select `ca.pem` (or `ca.crt`).
5. Confirm your device PIN / pattern.
6. A toast message will indicate: **"CA certificate installed"**.

### Step 3: Android App Network Security Configuration (Android 7.0+ / API 24+)
For Android 7.0 and newer, apps by default trust only system CA certificates. If you are inspecting an Android app you are developing or testing, ensure its debug build trusts user-installed certificates:

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

## Example Outputs

### Console Formatted Log

```
────────────────────────────────────────────────────────────────────────────────
[2026-09-30 15:35:12.450] #1 POST https://api.mybackend.com/v1/auth/login
┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄
▶ REQUEST
   Method:    POST
   Base URL:  https://api.mybackend.com
   Endpoint:  /v1/auth/login
   Headers:
     Host: api.mybackend.com
     Content-Type: application/json
     User-Agent: OkHttp/4.9.0
   Body:
     {
       "username" : "test_user",
       "device_id" : "pixel-7-debug"
     }

◀ RESPONSE
   Status:    200 OK (85 ms)
   Headers:
     Content-Type: application/json; charset=utf-8
     Date: Wed, 30 Sep 2026 10:05:12 GMT
   Body:
     {
       "token" : "eyJhbGciOi...",
       "expires_in" : 3600,
       "status" : "success"
     }
────────────────────────────────────────────────────────────────────────────────
```

### Session Summary (on Ctrl+C)

```
════════════════════════════════════════════════════════════════════════════════
  ApiSniffer Session Summary
════════════════════════════════════════════════════════════════════════════════
  Duration:                00:03:42 (222 seconds)
  Total Requests Logged:   38
  Unique Hosts Seen:       2

  Host Breakdown:
    • api.mybackend.com                        32 requests
    • cdn.mybackend.com                        6 requests

  Saved JSONL File:        A:\Windows App, Tool\Http log tool\traffic.jsonl
════════════════════════════════════════════════════════════════════════════════
```

---

## Constraints & Responsible Use

ApiSniffer is designed solely for debugging, performance testing, and reverse-engineering applications on devices you own or have explicit authorization to inspect on a private network under your control. It does not include features for stealth operation, hiding proxy indicators, or circumventing certificate pinning.
