# SPIT Connector - CampusMesh

> **Decentralized Delay-Tolerant Networking (DTN) P2P Cross-Platform Mesh Communication System**  
> Developed for Sardar Patel Institute of Technology (S.P.I.T.) campus and intranet networks.

---

## 🌟 Features

- **Store–Carry–Forward Paradigm**: Robust messaging across intermittent disconnections without requiring internet connectivity or active cellular infrastructure.
- **Heterogeneous Multi-Transport**: Opportunistic forwarding across Wi-Fi Direct / Hotspots (length-prefixed TCP streaming) and Bluetooth Low Energy (BLE GATT slicing with 20/512-byte MTU and CRC32 verification).
- **Intelligent DTN Routing Engines**:
  - **Epidemic Routing**: High-delivery epidemic flood replication.
  - **Spray-and-Wait**: Controlled replication with binary token splitting.
  - **Adaptive Multi-Metric Routing**: Dynamic scoring based on priority, remaining TTL, and buffer occupancy:
    $$U(M) = w_P \cdot P + w_T \cdot T + w_B \cdot B$$
- **Real-Time Web Dashboard**: Interactive HTML5 radar canvas visualizing dynamic topology (centering local operating device and radially tracking discovered active peers), transmission metrics, buffer occupancy, and SQLite event audit logs.
- **Cross-Platform Interoperability**: Seamless interaction between Windows laptops, Android mobile phones, and iOS iPhones.
- **SQLite Event Audit Trails**: Comprehensive persistent event logging for message creation, encounter contacts, forwarding hops, and delivery acknowledgments.

---

## 🚀 Quick Start

### 🐳 Run with Docker (Recommended)
You can run CampusMesh with Docker without needing Java installed on your host machine:

```powershell
# Start the Web Dashboard
docker compose up -d campusmesh

# Or use the helper script
.\run-docker.bat
```
- Open [http://localhost:8080](http://localhost:8080) in your browser.
- View container logs: `docker compose logs -f campusmesh`
- Stop container: `docker compose down`

```powershell
# Run the Multi-Transport Demo walkthrough in Docker
docker compose run --rm campusmesh-demo

# Run all 52 Automated Tests in Docker
docker compose run --rm campusmesh-test
```

---

### Local (Java 17+) Setup

### 1. Run the Demonstration Suite
Execute the end-to-end DTN and multi-transport demonstration walkthrough:
```powershell
.\run-demo.bat
```
*(Or in PowerShell: `.\run-demo.ps1`, or double-click `run-demo.bat` in File Explorer).*

### 2. Start the Web Dashboard

#### Option A: Running via Docker (Recommended)
```powershell
.\run-docker.ps1
# Or double-click run-docker.bat
```
- **Automatic Setup**: Auto-detects your laptop's Wi-Fi LAN IP, passes it to the container, and starts CampusMesh in Docker.
- **Local Dashboard**: [http://localhost:8080](http://localhost:8080)
- **Mobile Access**: Displays your exact mobile URL (e.g. `http://192.168.1.10:8080`).

#### Option B: Running Natively (Without Docker)
```powershell
.\run-web.bat
```
- **Local Access**: Open [http://localhost:8080](http://localhost:8080) in your browser.
- **Mobile Device Access**: Connect your phone to your laptop's Wi-Fi hotspot or local router, then navigate to `http://<your-laptop-lan-ip>:8080`.

### 3. Build & Compile Codebase
```powershell
.\build.bat
```

### 4. Run Automated Test Suites (52 Tests)
```bash
# HTTP API Test Suite (18 tests)
java -cp "MiniProject/bin;MiniProject/lib/*" campusmesh.test.HttpApiTestSuite

# Core Backend Test Suite (12 tests)
java -cp "MiniProject/bin;MiniProject/lib/*" campusmesh.test.CampusMeshTestSuite

# Subnet Beacon Discovery Suite (7 tests)
java -cp "MiniProject/bin;MiniProject/lib/*" campusmesh.test.DiscoveryTestSuite

# BLE Protocol & GATT Chunker Suite (7 tests)
java -cp "MiniProject/bin;MiniProject/lib/*" campusmesh.test.BleProtocolTestSuite

# Android Interoperability Test Suite (4 tests)
java -cp "MiniProject/bin;MiniProject/lib/*" campusmesh.test.AndroidInteroperabilityTest

# iOS Interoperability Test Suite (4 tests)
java -cp "MiniProject/bin;MiniProject/lib/*" campusmesh.test.IOSInteroperabilityTest
```

---

## 📁 Repository Structure

```
spitconnector/
├── run-demo.bat           # Run complete DTN & multi-hop demonstration suite
├── run-web.bat            # Start web control center dashboard
├── build.bat              # Compile Java codebase
├── interactive.bat        # Interactive terminal console
├── mini srs doc.pdf       # Software Requirements Specification (SRS) document
└── MiniProject/
    ├── src/               # Java source code
    │   └── campusmesh/
    │       ├── model/     # Data models (Message, Peer, Priority)
    │       ├── buffer/    # Capacity-capped buffer & duplicate detection
    │       ├── contact/   # Peer contact lifecycle management
    │       ├── routing/   # Epidemic, Spray-and-Wait, Adaptive engines
    │       ├── logging/   # SQLite event persistence
    │       ├── networking/# Length-prefixed TCP streaming framing
    │       ├── discovery/ # UDP beacon & mDNS peer discovery
    │       ├── ble/       # BLE GATT chunker, MTU slicing & CRC32
    │       ├── web/       # Embedded HTTP server & REST APIs
    │       ├── demo/      # CLI demos & multi-transport walkthrough
    │       └── test/      # Complete automated verification test suites
    ├── web/               # Responsive Web Dashboard (HTML, CSS, JS)
    ├── lib/               # External dependencies (Gson, SQLite JDBC, JmDNS, SLF4J)
    ├── android/           # Android integration module
    └── ios/               # iOS integration module
```

---

## 👥 Authors & Team
- **Bhumika Tarmale** ([@bhumikatarmale](https://github.com/bhumikatarmale))
- **Pranav**
- Sardar Patel Institute of Technology (S.P.I.T.)
