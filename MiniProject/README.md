# CampusMesh Backend (Pranav + Bhumika's Parts)

A peer-to-peer (P2P) messaging system for intermittently connected campus and intranet networks using Delay-Tolerant Networking (DTN) and the **Store–Carry–Forward** paradigm.

---

## 1. Project Overview & Architecture Separation

In intermittent campus networks, continuous end-to-end paths rarely exist. Devices carry messages in local memory as users walk between classrooms and forward them opportunistically upon encountering other peers.

A core architectural principle governs CampusMesh:
* **Routing Strategy decides WHAT messages should be forwarded.**
* **TCP Socket Transport decides HOW messages are physically transferred.**

```
                 ┌───────────────────┐
                 │   ContactManager  │  (Bhumika)
                 └─────────┬─────────┘
                           │ Active Encounter
                           ↓
                 ┌───────────────────┐
                 │   RoutingEngine   │  (Pranav)
                 └─────────┬─────────┘
                           │
             ┌─────────────┼─────────────┐
             ↓             ↓             ↓
        Epidemic      Spray&Wait      Adaptive
       (Flooding)    (Token Split)  (Utility-based)
             └─────────────┼─────────────┘
                           ↓
                   Selected Messages
                           ↓
                 ┌───────────────────┐
                 │    TCPManager     │  (Shared Networking)
                 └─────────┬─────────┘
                           │ Length-Prefixed Stream
                           ↓
                      Remote Peer

MessageBuffer (Bhumika) and EventLogger (Bhumika) support the entire lifecycle.
```

---

## 2. Team Responsibilities

### Pranav's Modules
* `RoutingStrategy.java` — Unified routing interface.
* `EpidemicRouter.java` — Flood-based epidemic DTN routing.
* `SprayAndWaitRouter.java` — Controlled replication via binary token splitting (`copyTokens`).
* `AdaptiveRouter.java` — Multi-metric utility calculation ($U(M) = w_P \cdot P + w_T \cdot T + w_B \cdot B$).
* `RoutingEngine.java` — Strategy coordinator.
* `ExperimentRunner.java` — Comparative test harness evaluating routing decisions on identical message pools.

### Bhumika's Modules
* `Peer.java` — Peer endpoint representation.
* `Priority.java` — Normalized priority levels (`LOW=0.33`, `MEDIUM=0.67`, `HIGH=1.00`).
* `Message.java` — Timestamp-based TTL model (`createdAt`, `expiresAt`), copy tokens, and serialization.
* `MessageBuffer.java` — 10MB capacity-capped storage, duplicate detection, expired message purging.
* `ContactManager.java` — Logical application-level contact tracking.
* `EventLogger.java` — Thread-safe SQLite persistent event tracking.

### Shared Modules
* `TCPManager.java` — 4-byte length prefix framing + socket transport.
* `BackendDemo.java` — Complete terminal walkthrough.
* `CampusMeshTestSuite.java` — 10/10 automated verification test suite.

---

## 3. Package & Directory Structure

```text
CampusMesh/
├── lib/
│   └── sqlite-jdbc-3.36.0.3.jar   # Standalone SQLite JDBC driver
├── database/
│   └── campusmesh.db              # SQLite event storage
├── bin/                           # Compiled class files
├── src/
│   └── campusmesh/
│       ├── model/
│       │   ├── Peer.java
│       │   ├── Priority.java
│       │   └── Message.java
│       ├── buffer/
│       │   └── MessageBuffer.java
│       ├── contact/
│       │   └── ContactManager.java
│       ├── routing/
│       │   ├── RoutingStrategy.java
│       │   ├── RoutingEngine.java
│       │   ├── EpidemicRouter.java
│       │   ├── SprayAndWaitRouter.java
│       │   └── AdaptiveRouter.java
│       ├── networking/
│       │   └── TCPManager.java
│       ├── logging/
│       │   └── EventLogger.java
│       ├── demo/
│       │   ├── BackendDemo.java
│       │   └── ExperimentRunner.java
│       └── test/
│           └── CampusMeshTestSuite.java
└── README.md
```

---

## 4. Compilation & Execution Instructions

Standard Java tools (`javac` and `java`) are used with zero external build tools required.

### 4.1 Compile the Project

From the root project directory (`d:\MiniProject`):

**Windows (PowerShell / CMD):**
```bash
javac -cp "lib/*;src" -d bin src/campusmesh/model/*.java src/campusmesh/buffer/*.java src/campusmesh/contact/*.java src/campusmesh/routing/*.java src/campusmesh/logging/*.java src/campusmesh/networking/*.java src/campusmesh/demo/*.java src/campusmesh/test/*.java
```

**Linux / macOS (Bash):**
```bash
javac -cp "lib/*:src" -d bin src/campusmesh/model/*.java src/campusmesh/buffer/*.java src/campusmesh/contact/*.java src/campusmesh/routing/*.java src/campusmesh/logging/*.java src/campusmesh/networking/*.java src/campusmesh/demo/*.java src/campusmesh/test/*.java
```

---

### 4.2 Run BackendDemo

Demonstrates peer setup, message creation, buffer duplicate detection, contact creation, Epidemic decision, Spray-and-Wait token splitting, Adaptive utility ranking table, full multi-hop Store-Carry-Forward, TCP length-prefixed framing, and SQLite event logs.

**Windows:**
```bash
java -cp "bin;lib/*" campusmesh.demo.BackendDemo
```

**Linux / macOS:**
```bash
java -cp "bin:lib/*" campusmesh.demo.BackendDemo
```

---

### 4.3 Run ExperimentRunner

Runs comparative routing decision tests on a 10-message pool across Epidemic, Spray-and-Wait, and Adaptive algorithms.

**Windows:**
```bash
java -cp "bin;lib/*" campusmesh.demo.ExperimentRunner
```

**Linux / macOS:**
```bash
java -cp "bin:lib/*" campusmesh.demo.ExperimentRunner
```

---

### 4.4 Run Interactive Terminal Console (Send Messages & Pick Strategies Live)

Allows you to interactively compose messages, choose the routing strategy (Epidemic, Spray-and-Wait, Adaptive), manage contacts, evaluate routing tables, and simulate Store-Carry-Forward directly from your terminal.

**Interactive Menu Mode:**
```bash
# Windows
java -cp "bin;lib/*" campusmesh.demo.InteractiveCLI
# or simply: .\interactive.bat

# Linux / macOS
java -cp "bin:lib/*" campusmesh.demo.InteractiveCLI
```

**Direct Command-Line Mode (Single Execution):**
```bash
java -cp "bin;lib/*" campusmesh.demo.InteractiveCLI --id M001 --to D --priority HIGH --payload "Exam Hall 3" --ttl 120 --tokens 4 --strategy ADAPTIVE --contact B
```

---

### 4.5 Run Test Suite

Executes all 10 unit and functional tests.

**Windows:**
```bash
java -cp "bin;lib/*" campusmesh.test.CampusMeshTestSuite
```

**Linux / macOS:**
```bash
java -cp "bin:lib/*" campusmesh.test.CampusMeshTestSuite
```

---

## 5. Mathematical Utility Model (Adaptive Router)

Adaptive Forwarding computes utility $U(M)$ for candidate message $M$:

$$U(M) = w_P \cdot P(M) + w_T \cdot T(M) + w_B \cdot B(M)$$

where $w_P + w_T + w_B = 1.0$ (default: $w_P = 0.40, w_T = 0.40, w_B = 0.20$).

1. **Priority Factor $P(M)$:**
   * $\text{LOW} = 0.33$
   * $\text{MEDIUM} = 0.67$
   * $\text{HIGH} = 1.00$

2. **TTL Urgency Factor $T(M)$:**
   $$T(M) = 1 - \frac{\text{remainingTTL}}{\text{originalTTL}}$$
   Clamped to $[0.0, 1.0]$. Fresh messages have $T \approx 0.0$; messages near expiry have $T \approx 1.0$.

3. **Buffer Pressure Factor $B(M)$:**
   $$B(M) = \frac{\text{usedBuffer}}{\text{bufferCapacity}}$$
   Clamped to $[0.0, 1.0]$.

4. **Tie-Breaking Order:**
   1. Higher $U(M)$
   2. Higher $T(M)$
   3. Higher $P(M)$
   4. Earlier expiry timestamp
   5. Lexicographically smaller `messageId`

---

## 6. SQLite Database Schema

Located at: `database/campusmesh.db`

```sql
CREATE TABLE IF NOT EXISTS events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    timestamp TEXT NOT NULL,
    event_type TEXT NOT NULL,
    peer_id TEXT,
    message_id TEXT,
    remote_peer_id TEXT,
    routing TEXT,
    metadata_json TEXT
);
```

Supported event types:
* `PEER_STARTED`, `PEER_STOPPED`
* `CONTACT_STARTED`, `CONTACT_ENDED`
* `MESSAGE_CREATED`, `MESSAGE_STORED`, `MESSAGE_DUPLICATE`, `MESSAGE_EXPIRED`
* `MESSAGE_FORWARDED`, `MESSAGE_RECEIVED`, `MESSAGE_DELIVERED`, `MESSAGE_DROPPED`
* `ROUTING_DECISION` (stores $P, T, B, U$, rank, and selection status inside `metadata_json`)

---

## 7. TCP Framing Protocol

Stream-safe application layer framing:
```text
┌──────────────────────┬──────────────────────────────────┐
│  4-byte Big-Endian   │         UTF-8 JSON Payload       │
│     Length (int)     │              Bytes               │
└──────────────────────┴──────────────────────────────────┘
```
Uses `DataOutputStream.writeInt()` and `DataInputStream.readFully()` to guarantee that TCP segment boundaries do not corrupt message delivery.

---

## 8. Cross-Platform Offline Mesh Evolution (Windows ↔ Ubuntu ↔ Android ↔ iOS)

CampusMesh has evolved into a decentralized, multi-transport peer-to-peer mesh system inspired by Bitchat, supporting automatic peer discovery and store-carry-and-forward routing without internet access or shared Wi-Fi infrastructure.

### Supported Cross-Platform Topologies
- **Windows Laptop ↔ Windows Laptop** (Wi-Fi mDNS / BLE)
- **Windows Laptop ↔ Ubuntu Laptop** (Wi-Fi mDNS / BLE)
- **Laptop ↔ Android Phone** (Wi-Fi NSD + TCP / BLE GATT)
- **Laptop ↔ iPhone** (Bonjour + Apple Network.framework / CoreBluetooth)
- **Android Phone ↔ Android Phone** (NSD / BLE GATT)
- **Android Phone ↔ iPhone** (Wi-Fi / BLE GATT cross-platform)
- **iPhone ↔ iPhone** (Bonjour / CoreBluetooth)

### Multi-Transport Architecture
1. **High-Speed Wi-Fi Transport**:
   - Primary Discovery: mDNS Bonjour `_campusmesh._tcp.`
   - Fallback Discovery: Subnet UDP Multicast Beacon (`239.255.77.88:5354`)
   - Transport: 4-byte big-endian length-prefixed streaming sockets.
2. **Opportunistic Bluetooth Low Energy (BLE) Transport**:
   - Primary Service UUID: `0000FE60-0000-1000-8000-00805F9B34FB`
   - Characteristic 1 (`0000FE61`): Peer Identity & Capabilities (Read/Notify)
   - Characteristic 2 (`0000FE62`): Message Chunk Ingestion (Write/Write Without Response)
   - Characteristic 3 (`0000FE63`): Transfer Acknowledgment & Flow Control (Notify/Indicate)
   - Self-describing 13-byte binary header with end-to-end CRC32 checksum verification.
   - Dynamic MTU adaptation (slicing from 23-byte default MTU up to 512-byte negotiated high-speed MTU).

---

## 9. Web Dashboard Frontend (http://localhost:8080)

CampusMesh includes a visual, real-time web control center powered by an embedded JDK HTTP REST server with zero npm/node dependencies:

- **Interactive Canvas Mesh Radar**: Visualizes Windows, Ubuntu, Android, and iOS nodes, dynamic links, and traveling packet animations.
- **Store-Carry-Forward Buffer**: Displays real-time message cards with visual TTL countdown bars and buffer pressure gauge ($B$).
- **Adaptive Decision Matrix**: Live sliders for $w_P$, $w_T$, $w_B$ with auto-normalization that calculate candidate message utility scores $U(M)$ in real time.
- **Message Composer**: Compose and dispatch messages opportunistically over Wi-Fi TCP or BLE GATT chunks.
- **Bluetooth LE Packet Slicer**: Inspects 13-byte headers, MTU-sized chunk envelopes, CRC32 checksums, and ACK packets.
- **SQLite Audit Trail**: Real-time event log stream.

### Launching the Web Dashboard
```cmd
:: One-click launch (starts server and opens browser automatically)
run-web.bat

:: Or manually via Java:
java -cp "bin;lib/*" campusmesh.web.CampusMeshWebRunner 8080 LAPTOP_WIN
```

---

## 10. Test Suites & Verification (43 / 43 Tests Passing)

To build and run all test suites:

```cmd
:: 1. Compile everything
build.bat

:: 2. Run Embedded HTTP REST & Web Dashboard Suite (9 tests)
java -cp "bin;lib/*" campusmesh.test.HttpApiTestSuite

:: 3. Run Core DTN Routing & Buffer Regression Suite (12 tests)
java -cp "bin;lib/*" campusmesh.test.CampusMeshTestSuite

:: 4. Run Wi-Fi & mDNS Discovery Suite (7 tests)
java -cp "bin;lib/*" campusmesh.test.DiscoveryTestSuite

:: 5. Run Android Native Interoperability Suite (4 tests)
java -cp "bin;lib/*" campusmesh.test.AndroidInteroperabilityTest

:: 6. Run iOS Swift Interoperability Suite (4 tests)
java -cp "bin;lib/*" campusmesh.test.IOSInteroperabilityTest

:: 7. Run Bluetooth Low Energy Protocol Suite (7 tests)
java -cp "bin;lib/*" campusmesh.test.BleProtocolTestSuite

:: 8. Run Heterogeneous Multi-Hop Simulation Demo
java -cp "bin;lib/*" campusmesh.demo.MultiTransportMeshDemo

:: 9. Run Interactive Terminal Console
interactive.bat
```

