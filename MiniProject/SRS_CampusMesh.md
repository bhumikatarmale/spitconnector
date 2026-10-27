# Software Requirements Specification
## For
# CampusMesh: Decentralized Opportunistic Delay-Tolerant Network for Heterogeneous Campus Communication

### Version 1.0

---

### Prepared by:

| Name | Roll No. | Email |
| :--- | :--- | :--- |
| **Bhumika Anil Tarmale** | 2024300259 | bhumika.tarmale24@spit.ac.in |
| **Sahil Azad Valvi** | 2024300266 | sahil.valvi24@spit.ac.in |
| **Pranav Solunke** | 2024300246 | pranav.solunke24@spit.ac.in |

**Instructor:** Prof. Yogita Patil  
**Course:** Mini Project  
**Department:** Computer Engineering / Information Technology  
**Institution:** Sardar Patel Institute of Technology (S.P.I.T.), Andheri (W), Mumbai  
**Date:** 21 October 2026  

---

<div style="page-break-after: always;"></div>

## Revision History

| Version | Primary Author(s) | Description of Version | Date Completed |
| :--- | :--- | :--- | :--- |
| **Draft 1.0** | Bhumika Tarmale, Sahil Valvi, Pranav Solunke | Initial full draft of the IEEE 830 SRS document for CampusMesh decentralized delay-tolerant network. | 10/21/2026 |

---

<div style="page-break-after: always;"></div>

## Table of Contents

- [Revision History](#revision-history)
- [Table of Contents](#table-of-contents)
- [1. Introduction](#1-introduction)
  - [1.1 Purpose](#11-purpose)
  - [1.2 Document Conventions](#12-document-conventions)
  - [1.3 Intended Audience and Reading Suggestions](#13-intended-audience-and-reading-suggestions)
  - [1.4 Product Scope](#14-product-scope)
  - [1.5 References](#15-references)
- [2. Overall Description](#2-overall-description)
  - [2.1 Product Perspective](#21-product-perspective)
  - [2.2 Product Functions](#22-product-functions)
  - [2.3 User Classes and Characteristics](#23-user-classes-and-characteristics)
  - [2.4 Operating Environment](#24-operating-environment)
  - [2.5 Design and Implementation Constraints](#25-design-and-implementation-constraints)
  - [2.6 User Documentation](#26-user-documentation)
  - [2.7 Assumptions and Dependencies](#27-assumptions-and-dependencies)
- [3. External Interface Requirements](#3-external-interface-requirements)
  - [3.1 User Interfaces](#31-user-interfaces)
  - [3.2 Hardware Interfaces](#32-hardware-interfaces)
  - [3.3 Software Interfaces](#33-software-interfaces)
  - [3.4 Communications Interfaces](#34-communications-interfaces)
- [4. System Features](#4-system-features)
  - [4.1 Autonomous Peer Discovery (Wi-Fi mDNS & Subnet UDP Beaconing)](#41-autonomous-peer-discovery-wi-fi-mdns--subnet-udp-beaconing)
  - [4.2 Opportunistic Bluetooth Low Energy (BLE) Radio Discovery](#42-opportunistic-bluetooth-low-energy-ble-radio-discovery)
  - [4.3 Store–Carry–Forward Message Buffer Management](#43-storecarryforward-message-buffer-management)
  - [4.4 Dynamic Routing Engine (Epidemic, Spray-and-Wait, Adaptive Utility)](#44-dynamic-routing-engine-epidemic-spray-and-wait-adaptive-utility)
  - [4.5 Multi-Transport Message Dispatch & Socket Streaming (TCP)](#45-multi-transport-message-dispatch--socket-streaming-tcp)
  - [4.6 Bluetooth Low Energy GATT MTU Chunking & CRC32 Reassembly](#46-bluetooth-low-energy-gatt-mtu-chunking--crc32-reassembly)
  - [4.7 Persistent Event Logging & SQLite Audit Trail](#47-persistent-event-logging--sqlite-audit-trail)
  - [4.8 Embedded HTTP REST API Server & Web Control Plane](#48-embedded-http-rest-api-server--web-control-plane)
  - [4.9 Interactive Real-Time Topology Radar & Network Simulation](#49-interactive-real-time-topology-radar--network-simulation)
  - [4.10 Device Perspective Switching & Autonomous Mobile Node Identification](#410-device-perspective-switching--autonomous-mobile-node-identification)
- [5. Other Nonfunctional Requirements](#5-other-nonfunctional-requirements)
  - [5.1 Performance Requirements](#51-performance-requirements)
  - [5.2 Safety Requirements](#52-safety-requirements)
  - [5.3 Security Requirements](#53-security-requirements)
  - [5.4 Software Quality Attributes](#54-software-quality-attributes)
    - [5.4.1 Usability](#541-usability)
    - [5.4.2 Reliability](#542-reliability)
    - [5.4.3 Maintainability](#543-maintainability)
    - [5.4.4 Portability](#544-portability)
    - [5.4.5 Efficiency](#545-efficiency)
  - [5.5 Business Rules](#55-business-rules)
- [6. Other Requirements](#6-other-requirements)
- [Appendix A: Glossary](#appendix-a-glossary)
- [Appendix B: Analysis Models](#appendix-b-analysis-models)
- [Appendix C: Data Dictionary](#appendix-c-data-dictionary)
- [Appendix D: To Be Determined (TBD) List](#appendix-d-to-be-determined-tbd-list)

---

<div style="page-break-after: always;"></div>

# 1. Introduction

## 1.1 Purpose
This Software Requirements Specification (SRS) outlines the functional and non-functional requirements for **CampusMesh**, a decentralized, multi-transport, delay-tolerant opportunistic peer-to-peer messaging system engineered for disconnected and intermittently connected campus environments. It details the operational scope, intended user classes, architectural boundaries, external interfaces, algorithmic models, and detailed system features. This document serves as the formal technical specification for developers, network testers, academic evaluators, and project stakeholders in accordance with the IEEE 830 standard.

## 1.2 Document Conventions
This document adheres to the IEEE 830-1998 standard conventions:
- **Header:** Font Size: 10 pt, Font Style: Bold Italic, Font Family: Times New Roman
- **Heading 1:** Font Size: 20 pt, Font Style: Bold, Font Family: Times New Roman
- **Heading 2 / Sub Heading:** Font Size: 15 pt, Font Style: Bold, Font Family: Times New Roman
- **Body Content:** Font Size: 12 pt, Font Style: Regular, Line Spacing: 1.15, Font Family: Times New Roman
- **Requirement Identifiers:** Tagged hierarchically as `FR-<FeatureID>.<RequirementID>` for functional requirements and `NFR-<Category>.<ID>` for non-functional requirements.
- **Mathematical Formulations:** Rendered using standard LaTeX equations.

## 1.3 Intended Audience and Reading Suggestions
This document is prepared for:
- **Academic Evaluators & Course Instructors (Prof. Yogita Patil):** To systematically evaluate system completeness, architectural design integrity, algorithmic rigor, and adherence to software engineering standards for university mini-project assessment.
- **System Developers & Core Maintainers:** To guide the implementation of routing algorithms, multi-transport radio abstractions, thread-safe buffering, socket serialization, and UI control planes.
- **Network Engineers & Testers:** To design rigorous test harnesses, unit tests, and cross-platform simulation benchmarks across heterogeneous platforms (Windows, Linux, Android, iOS).
- **End Users (Campus Students & Faculty):** To understand the opportunistic store-carry-forward paradigm, message dispatch capabilities, radio constraints, and privacy-preserving zero-infrastructure operation.

*Reading Suggestions:* Readers seeking architectural and conceptual context should begin with **Section 1** and **Section 2**. Developers and testers should focus on **Section 3**, **Section 4**, and the formal specifications in **Appendix B** and **Appendix C**.

## 1.4 Product Scope
**CampusMesh** is a zero-dependency, infrastructure-less communications system designed to maintain peer-to-peer messaging across educational campuses during centralized network outages, cellular dead zones, or emergency crisis scenarios. The platform leverages the **Store–Carry–Forward** Delay-Tolerant Networking (DTN) paradigm: mobile and stationary devices running CampusMesh retain encrypted message bundles in local storage and opportunistically replicate or forward them upon encountering neighboring nodes within physical radio proximity.

Key operational capabilities include:
1. **Multi-Transport Physical Proximity Discovery:** Automatic discovery of nearby nodes using zero-configuration Wi-Fi mDNS (`_campusmesh._tcp.`), dynamic subnet UDP multicast beacons (`239.255.77.88:5354`), and Bluetooth Low Energy (BLE) advertisements (`Service UUID: 0000FE60-0000-1000-8000-00805F9B34FB`).
2. **Pluggable & Adaptive DTN Routing Engine:** Implementation of Epidemic Flooding, Spray-and-Wait (binary token splitting), and an Adaptive Multi-Metric Utility model evaluating message Priority ($P$), TTL Urgency ($T$), and Buffer Pressure ($B$).
3. **Cross-Platform Heterogeneous Transport:** 4-byte length-prefixed streaming over TCP sockets for high-speed Wi-Fi, paired with MTU-aware slicing, 13-byte binary headers, and CRC32 chunk reassembly over BLE GATT channels.
4. **Resilient Local Storage:** 10 MB bounded store-carry-forward message buffer featuring duplicate detection, deterministic tie-breaking, and timestamp-based TTL expiry management.
5. **Local Control Plane & Dashboard:** Embedded zero-dependency HTTP REST API server (`port 8080`) serving a real-time responsive web dashboard featuring an interactive HTML5 canvas radar, decision matrix sliders, BLE packet inspection, and SQLite event auditing.

## 1.5 References
1. V. Cerf et al., *"Delay-Tolerant Networking Architecture,"* RFC 4838, Informational, Internet Engineering Task Force (IETF), Apr. 2007. https://datatracker.ietf.org/doc/html/rfc4838
2. K. Fall, *"A Delay-Tolerant Network Architecture for Challenged Internets,"* in *Proc. ACM SIGCOMM 2003 Conference (SIGCOMM '03)*, Karlsruhe, Germany, 2003, pp. 27–34.
3. A. Vahdat and D. Becker, *"Epidemic Routing for Partially-Connected Ad Hoc Networks,"* Technical Report CS-200006, Duke University, Tech. Rep., Jul. 2000.
4. T. Spyropoulos, K. Psounis, and C. S. Raghavendra, *"Spray and Wait: An Efficient Routing Scheme for Intermittently Connected Mobile Networks,"* in *Proc. ACM SIGCOMM Workshop on Delay-Tolerant Networking (WDTN '05)*, Philadelphia, PA, USA, 2005, pp. 252–259.
5. Bluetooth Special Interest Group (SIG), *"Bluetooth Core Specification Version 5.3: Low Energy Generic Attribute Profile (GATT),"* Kirkland, WA, USA, Jul. 2021.
6. IEEE Computer Society, *"IEEE Recommended Practice for Software Requirements Specifications,"* IEEE Std 830-1998, Oct. 1998.

---

<div style="page-break-after: always;"></div>

# 2. Overall Description

## 2.1 Product Perspective
In traditional academic campus environments, digital communication depends entirely on centralized infrastructure: campus Wi-Fi access points, DHCP routers, optical fiber backbones, and internet service providers (ISPs). During power outages, severe weather, natural disasters, high-density congestion, or cyber incidents, centralized connectivity collapses entirely, creating an information blackout. 

CampusMesh operates on a fundamentally different paradigm. Rather than requiring continuous end-to-end paths, CampusMesh forms an opportunistic, decentralized mesh network where each student laptop, lab workstation, and smartphone functions as an autonomous, self-contained router and message carrier. As students and staff physically walk between academic departments, lecture halls, and dormitories, their devices physically "carry" buffered messages and opportunistically bridge partitioned network islands via direct peer-to-peer Wi-Fi sockets and Bluetooth Low Energy handshakes.

CampusMesh bridges the gap between desktop workstations and mobile platforms by offering a uniform wire protocol and a zero-install, browser-accessible local management plane. It operates fully client-side and edge-first: no external cloud servers, third-party authentication services, or active internet uplinks are required.

```
+-----------------------------------------------------------------------------------+
|                              CAMPUSMESH ARCHITECTURE                             |
+-----------------------------------------------------------------------------------+
|  [ Web Browser / Dashboard ]    [ Terminal CLI ]    [ Native Android / iOS Node ]  |
+-----------------------------------------------------------------------------------+
|                 Embedded HTTP Server (Port 8080) / REST APIs                      |
+-----------------------------------------------------------------------------------+
|   [ ContactManager ]  <--->  [ RoutingEngine ]  <--->  [ MessageBuffer (10 MB) ]  |
|   Tracks active              - Epidemic                - Store-Carry-Forward      |
|   radio encounters           - Spray & Wait            - Duplicate Detection      |
|                              - Adaptive Utility        - TTL Expiry Engine        |
+-----------------------------------------------------------------------------------+
|                        Multi-Transport Abstraction Layer                          |
+-----------------------------------------------------------------------------------+
|   Wi-Fi Engine (mDNS / UDP / TCP)       |    Bluetooth LE Engine (GATT Chunker)   |
|   - 239.255.77.88:5354 Discovery        |    - Service UUID: 0000FE60...          |
|   - 4-Byte Length-Prefixed Stream       |    - MTU 23-512B / 13B Header / CRC32   |
+-----------------------------------------------------------------------------------+
|                        SQLite Event Audit Log (Database)                          |
+-----------------------------------------------------------------------------------+
```

## 2.2 Product Functions
CampusMesh provides the following primary functions:
- **Autonomous Multi-Radio Peer Discovery:** Continuously discovers adjacent peers using zero-configuration Wi-Fi mDNS broadcasts, UDP subnet multicast beacons, and BLE advertising.
- **Dynamic Encounter Tracking:** Detects when nodes enter and exit physical communication range, initiating and closing formal contact encounters in the routing state machine.
- **Store–Carry–Forward Buffer Operations:** Stores messages in a bounded 10 MB local buffer, enforcing priority retention, binary duplicate rejection, and automatic TTL expiration purging.
- **Pluggable DTN Routing Selection:** Dynamically evaluates candidate messages for forwarding using three distinct strategies:
  1. *Epidemic Routing:* Unconstrained flooding replicating all non-duplicate messages.
  2. *Spray-and-Wait Routing:* Controlled replication with logarithmic binary token splitting until $L=1$ (wait phase).
  3. *Adaptive Forwarding:* Multi-metric utility ranking balancing message Priority ($P$), Remaining TTL Urgency ($T$), and Local Buffer Pressure ($B$).
- **Multi-Transport Physical Dispatch:** Marshals messages into binary streams and dispatches them across the optimal radio link: high-throughput length-prefixed TCP streaming for Wi-Fi or MTU-chunked GATT packets for Bluetooth LE.
- **BLE Transmission Framing & Reassembly:** Slices JSON message payloads into MTU-bounded GATT chunks with 13-byte headers, transmitting sequence frames with end-to-end CRC32 validation.
- **SQLite Audit Trail & Logging:** Thread-safely logs all node lifecycles, contact handshakes, routing evaluations, and packet transfers into a persistent local SQLite database.
- **Embedded Web Control Center:** Serves a comprehensive web dashboard with zero runtime dependencies, providing visual topology inspection, live radar tracking, algorithmic tuning, message composition, and radio frame analysis.
- **Autonomous Client Identity Recognition:** Detects connecting client platforms (desktop vs. mobile) via request metadata, automatically mapping independent node perspectives (`LAPTOP_WIN`, `PHONE_MOBILE`, etc.) without manual user configuration.

## 2.3 User Classes and Characteristics
The system identifies four primary user classes:

1. **Campus Users (Students, Faculty, Staff):**
   - *Technical Expertise:* Low to Moderate. Familiar with web browsers and standard mobile devices.
   - *Responsibilities:* Composing local messages, viewing received campus alerts, carrying devices across campus to facilitate opportunistic data hopping.
   - *Interface:* Intuitive web dashboard accessed via local browser (`http://localhost:8080` or `http://<laptop-ip>:8080`).

2. **Emergency Response & Field Coordinators:**
   - *Technical Expertise:* Moderate. Basic understanding of emergency protocols and dispatch.
   - *Responsibilities:* Broadcasting high-priority emergency alerts, monitoring buffer propagation status, overriding priority levels.
   - *Interface:* Web dashboard compose console with Priority override (`HIGH`, `MEDIUM`, `LOW`).

3. **Network Researchers & Lab Evaluators:**
   - *Technical Expertise:* High. Proficient in networking protocols, queuing theory, and routing models.
   - *Responsibilities:* Tuning adaptive utility weights ($w_P, w_T, w_B$), running comparative simulation benchmarks, analyzing SQLite event logs.
   - *Interface:* Adaptive decision matrix sliders, CLI test harnesses (`ExperimentRunner`, `BackendDemo`), and REST API endpoints.

4. **Academic Course Evaluator (Prof. Yogita Patil):**
   - *Technical Expertise:* Expert in Software Engineering, Distributed Systems, and IEEE standards.
   - *Responsibilities:* Verifying project architectural separation, test coverage, code documentation, and requirement satisfaction.
   - *Interface:* Automated test suites (`CampusMeshTestSuite`, `HttpApiTestSuite`, `DiscoveryTestSuite`, `BleProtocolTestSuite`), SRS specification document.

## 2.4 Operating Environment
CampusMesh operates across heterogeneous physical hardware and operating systems:

- **Host Hardware Platforms:**
  - Standard x86_64 Desktop/Laptop PCs (Windows 10/11, Ubuntu 20.04/22.04 LTS, macOS).
  - ARM64 Mobile Devices (Android 10+, iOS 14+).
  - Minimum Hardware: 1 GHz dual-core CPU, 512 MB available RAM, 100 MB free disk space.
- **Radio Interfaces:**
  - Standard 802.11 b/g/n/ac Wi-Fi Network Interface Card (supporting TCP socket binding and UDP broadcast/multicast).
  - Bluetooth 4.2 / 5.0+ Low Energy Controller (supporting BLE advertising and GATT peripheral/central roles).
- **Runtime Environment:**
  - Java Virtual Machine: OpenJDK 8 or higher (tested and verified on Oracle/OpenJDK Java 23).
  - Embedded HTTP Server: Built-in standard JDK `com.sun.net.httpserver.HttpServer` (zero external web frameworks).
  - Database Driver: `sqlite-jdbc-3.36.0.3.jar` for local SQLite relational auditing.
- **Client Presentation Environment:**
  - Modern HTML5/ECMAScript 6+ web browsers: Google Chrome 80+, Mozilla Firefox 75+, Safari 13+, Microsoft Edge 80+.
  - Mobile browsers: Chrome for Android, Mobile Safari for iOS.

## 2.5 Design and Implementation Constraints
1. **Zero-Dependency Native Backend:** The core backend must compile and execute using standard Java SE libraries and a single standalone SQLite JDBC driver. No heavy third-party application servers (Spring Boot, Tomcat), web frameworks, or build automation tools (Maven, Gradle) are permitted.
2. **Strict Architecture Separation:**
   - The *Routing Strategy* decides **WHAT** messages should be forwarded.
   - The *Transport Layer* decides **HOW** messages are physically transmitted.
   - The *Buffer* retains state independently of active physical sockets.
3. **Bounded Memory Footprint:** The message buffer is strictly capped at 10 MB ($10,485,760$ bytes) to prevent out-of-memory faults on resource-constrained embedded or mobile platforms.
4. **Non-Continuous Radio Connectivity:** All protocol engines must tolerate abrupt connection drops, network partitioning, packet loss, and asymmetric link encounters without data corruption.
5. **BLE MTU Constraints:** BLE transmission must accommodate default 23-byte MTU links (yielding 7 bytes of usable chunk payload) up to negotiated 512-byte MTUs without dropping bytes or exceeding GATT characteristic limits.
6. **Stateless Frontend Presentation:** The frontend control plane interacts strictly over REST JSON APIs, allowing any device on the local network to view or control the node without maintaining browser sessions.

## 2.6 User Documentation
The software delivery package includes the following documentation:
- **`README.md`:** Comprehensive technical overview detailing architectural modules, package structures, command-line compilation commands, and execution instructions.
- **Embedded Web Dashboard Guide:** Interactive contextual tooltips, modal inspectors, and radio topology legends embedded directly within the dashboard UI.
- **Terminal Interactive CLI:** Built-in `--help` flags and interactive command prompts for terminal-based operation.
- **Automated Test Suite Output:** Detailed console logs reporting pass/fail status and performance diagnostics across all 43 automated verification tests.

## 2.7 Assumptions and Dependencies
- **Physical Proximity:** It is assumed that mobile nodes move dynamically throughout the campus, creating periodic physical encounters within Wi-Fi (approx. 30–50 meters) or BLE (approx. 5–15 meters) range.
- **Radio Interface Availability:** It is assumed that host systems have either an active Wi-Fi interface (joined to a shared ad-hoc/local network) or a BLE controller enabled.
- **System Clocks:** It is assumed that devices have reasonably synchronized real-time clocks (within a few minutes of error) to ensure valid message TTL decrement calculations.
- **File System Permissions:** It is assumed that the application has write permissions to create and update the local SQLite database file located at `database/campusmesh.db`.

---

<div style="page-break-after: always;"></div>

# 3. External Interface Requirements

## 3.1 User Interfaces
The system provides two distinct user interfaces: a modern HTML5/CSS3 Web Dashboard and an Interactive Terminal Command-Line Interface (CLI).

### 3.1.1 Web Control Dashboard (Port 8080)
The web control plane features a dark/light responsive interface divided into logical monitoring and control cards:
1. **Top Navigation & Metric Bar:**
   - *Device Perspective Selector:* Dropdown allowing the client to switch the operating view (`LAPTOP_WIN`, `PHONE_MOBILE`, `PHONE_AND`, `IPHONE_IOS`, `LAPTOP_UBU`).
   - *Global Indicators:* Active routing strategy display, live buffer pressure percentage gauge, radio status badge, and theme toggle button (Dark / Light mode).
2. **Interactive Mesh Topology Radar:**
   - HTML5 Canvas visualizing nodes arranged radially with animated sweeping radar beams.
   - Dashed cyan lines indicate Wi-Fi TCP links; dashed purple lines indicate Bluetooth LE GATT channels.
   - Concentric green rings and `(YOU)` labels highlight the local active device perspective.
   - Animated glowing blue packets traverse the links upon message dispatch or simulation execution.
   - Node click handlers auto-populate the message composer with the target peer ID.
3. **Routing Strategy & Decision Matrix Panel:**
   - Strategy selection dropdown (`ADAPTIVE`, `EPIDEMIC`, `SPRAY_AND_WAIT`).
   - Real-time range sliders for $w_P$, $w_T$, and $w_B$ with automatic mathematical normalization summing to $1.00$.
   - Live ranked evaluation table displaying candidate Message ID, Priority ($P$), Urgency ($T$), Buffer Pressure ($B$), calculated Utility $U(M)$, and selection verdict (`FORWARD` vs. `HOLD`).
4. **Store–Carry–Forward Message Buffer:**
   - Visual progress bar showing buffer byte utilization out of 10 MB.
   - Message cards showing Message ID, Source $\rightarrow$ Destination paths, Priority badges, TTL countdown bars, and token counts.
   - "Purge Expired" action button to manually trigger stale message eviction.
5. **Message Composer Card:**
   - Input fields for Sender Node, Destination Peer ID (or `BROADCAST`), Priority level (`HIGH`, `MEDIUM`, `LOW`), TTL slider (1 to 1440 minutes), Copy Tokens ($L$), and payload text.
   - "Dispatch to Mesh Network" action button with real-time toast feedback.
6. **BLE Packet Slicer & Frame Inspector:**
   - Visual breakdown of the currently selected message into 13-byte binary headers, chunk sequence numbers, payload bytes, and CRC32 hex checksums.
7. **SQLite Audit Trail Stream:**
   - Real-time scrolling feed of the last 15 system events logged in the local SQLite database.

```
+-----------------------------------------------------------------------------------+
|  [CampusMesh Radar]  Operating As: [LAPTOP_WIN v]  Strategy: ADAPTIVE  Buffer: 0.1% |
+-----------------------------------------------------------------------------------+
|  +-------------------------------------+  +------------------------------------+  |
|  |     Live Mesh Topology Radar        |  |   Store-Carry-Forward Buffer       |  |
|  |   (Windows, Android, iOS, Ubuntu)   |  |   [||||..................] 566 KB  |  |
|  |   Wi-Fi Links: - - -                |  |   - M001: LAPTOP_WIN -> UBU (HIGH) |  |
|  |   BLE Links:   .....                |  |   - M002: LAPTOP_WIN -> AND (MED)  |  |
|  |   Active Node: LAPTOP_WIN (YOU)     |  |   - M401: PHONE_MOBILE -> WIN      |  |
|  +-------------------------------------+  +------------------------------------+  |
|  |   Adaptive Routing Decision Matrix  |  |   Compose & Enqueue DTN Message    |  |
|  |   wP: [0.40]  wT: [0.40]  wB: [0.20]|  |   Sender:      [LAPTOP_WIN       ] |  |
|  |   #1 M001 | P=1.00 | U=0.88 | FWD   |  |   Destination: [PHONE_MOBILE     ] |  |
|  |   #2 M002 | P=0.67 | U=0.54 | HOLD  |  |   Priority:    [HIGH (1.00)     v] |  |
|  +-------------------------------------+  |   Payload:     [Campus alert...  ] |  |
|  |   Bluetooth LE Frame Inspector      |  |   [ 🚀 Dispatch to Mesh Network ]  |  |
|  |   Chunks: 4 | CRC32: 0x4A2F8B10     |  +------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

### 3.1.2 Interactive Terminal Command-Line Interface (CLI)
Accessible via `campusmesh.demo.InteractiveCLI`:
- Provides an ANSI-formatted interactive text console with numeric menu navigation.
- Supports single-execution CLI mode using standard flags:
  `--id <ID> --to <PEER> --priority <HIGH|MEDIUM|LOW> --payload <TEXT> --ttl <SEC> --tokens <L> --strategy <STRATEGY> --contact <PEER>`

## 3.2 Hardware Interfaces
- **Wi-Fi Network Interface Controller (NIC):** Utilizes standard OS-level socket subsystems supporting IPv4/IPv6 broadcast, multicast group joining, and TCP socket server binding.
- **Bluetooth Low Energy Radio Controller:** Interfaces via host Bluetooth HCI subsystem to advertise Service UUID `0000FE60` and read/write characteristic buffers.
- **Persistent Storage Medium:** Local Solid-State Drive (SSD) or eMMC flash memory for SQLite database persistence and disk caching.

## 3.3 Software Interfaces
- **Java Virtual Machine (JVM):** Standard Java SE 8+ runtime environment.
- **SQLite Database Subsystem:** Interfaced via JDBC driver `org.sqlite.JDBC` managing file-based transactional tables.
- **Operating System Socket API:** Standard `java.net.Socket`, `java.net.ServerSocket`, `java.net.DatagramSocket`, and `java.net.MulticastSocket`.
- **Embedded Web Server Subsystem:** Standard JDK `com.sun.net.httpserver.HttpServer` listening on TCP port 8080.
- **Browser Presentation Engine:** Native HTML5 Document Object Model (DOM), Canvas 2D API (`HTMLCanvasElement`), Fetch API, and LocalStorage API.

## 3.4 Communications Interfaces
CampusMesh implements four formal communications protocols:

### 3.4.1 Wi-Fi Length-Prefixed TCP Stream Protocol
Used for physical node-to-node message transfer over high-throughput Wi-Fi sockets:
```
+-----------------------------------+-----------------------------------------------+
|   4-Byte Big-Endian Integer       |            Variable-Length Payload            |
|   Length Field (N bytes)          |            UTF-8 Encoded JSON String          |
+-----------------------------------+-----------------------------------------------+
```
- Implemented via `DataOutputStream.writeInt(length)` and `DataInputStream.readFully(buffer)`.
- Eliminates TCP stream fragmentation and boundary ambiguity without requiring complex delimiters.

### 3.4.2 Dynamic Wi-Fi Subnet Multicast Beacon Protocol
Used for zero-configuration discovery across local subnet nodes:
- **Multicast Group Address:** `239.255.77.88`
- **UDP Port:** `5354`
- **Beacon Payload Format (JSON):**
  ```json
  {
    "peerId": "LAPTOP_WIN",
    "displayName": "Laptop (Windows)",
    "platform": "WINDOWS",
    "port": 5001,
    "transports": ["WIFI", "BLE"],
    "timestamp": 1729525200000
  }
  ```
- **Interval:** Beacon broadcast every $5000\text{ ms}$; peer entry timeout threshold at $15000\text{ ms}$.

### 3.4.3 Bluetooth Low Energy GATT Framing Protocol
Used for low-power opportunistic transfers over BLE:
- **Service UUID:** `0000FE60-0000-1000-8000-00805F9B34FB`
- **Characteristic UUIDs:**
  - `0000FE61`: Peer Identity & Capabilities (Read/Notify)
  - `0000FE62`: Inbound Message Chunk Ingestion (Write Without Response)
  - `0000FE63`: Chunk Acknowledgment & Flow Control (Notify)
- **13-Byte Binary Chunk Header Format:**
  ```
  +---------+---------+---------+---------+---------+---------+---------+
  | Byte 0  | Byte 1  | Byte 2  | Bytes 3-4 | Bytes 5-6 | Bytes 7-8 | Bytes 9-12 |
  |  0x43   |  0x4D   |  0x02   | Tx ID   | Chunk # | Total # |  CRC32  |
  |  ('C')  |  ('M')  | (Proto) | (uint16)| (uint16)| (uint16)| (uint32)|
  +---------+---------+---------+---------+---------+---------+---------+
  ```
- **Payload:** Variable length ($N = \text{MTU} - 13$ bytes). Default MTU 23 yields 7 bytes payload; negotiated MTU 512 yields 499 bytes payload.

### 3.4.4 Local HTTP REST Control API Protocol
Standard HTTP/1.1 REST endpoints operating on port 8080 returning `application/json`:
- `GET /api/status`: Local node state, client IP detection, buffer pressure, and uptime.
- `GET /api/peers`: Discovered peer registry list across Wi-Fi and BLE.
- `GET /api/buffer`: Inspect active store-carry-forward messages and remaining TTLs.
- `POST /api/messages`: Enqueue and dispatch new DTN messages.
- `POST /api/strategy`: Switch active routing algorithm and update $w_P, w_T, w_B$ weights.
- `POST /api/contacts`: Toggle simulated encounter states with specific peers.
- `GET /api/evaluation`: Real-time ranked decision matrix evaluation.
- `GET /api/events`: Tail of SQLite audit log records.
- `POST /api/simulation/run`: Launch automated 4-node multi-hop mesh sequence.

---

<div style="page-break-after: always;"></div>

# 4. System Features

## 4.1 Autonomous Peer Discovery (Wi-Fi mDNS & Subnet UDP Beaconing)
### 4.1.1 Description and Priority
Enables a node to continuously broadcast its presence and discover adjacent nodes across local Wi-Fi networks without requiring central directory servers or manual IP configuration.  
**Priority:** High.

### 4.1.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 1 in Appendix B)*
1. Node launches and initializes the `DiscoveryManager`.
2. `WifiDiscoveryService` binds to UDP multicast socket `239.255.77.88:5354`.
3. Background thread periodically broadcasts JSON identity beacons every 5 seconds.
4. Remote nodes receive the packet, extract peer capabilities, and register the node in `PeerRegistry`.
5. If no beacon is received from a peer within 15 seconds, the peer is marked as stale and evicted.

### 4.1.3 Functional Requirements
- **FR-4.1.1 Subnet Beacon Emission:** The system shall broadcast a UDP beacon packet containing `peerId`, `platform`, `ipAddress`, `port`, and `transports` to `239.255.77.88:5354` every $5000\text{ ms}$.
- **FR-4.1.2 Peer Registry Ingestion:** Upon receiving a valid beacon, the system shall add or update the peer entry in `PeerRegistry` with the current system timestamp.
- **FR-4.1.3 Automatic Stale Eviction:** The system shall run a scavenging thread that purges peers whose `lastSeenTimestamp` exceeds $15000\text{ ms}$.
- **FR-4.1.4 Contact Synchronization:** When a new peer is discovered over Wi-Fi, the system shall notify `ContactManager` to register an active contact encounter.

## 4.2 Opportunistic Bluetooth Low Energy (BLE) Radio Discovery
### 4.2.1 Description and Priority
Enables physical node discovery in infrastructure-less environments where Wi-Fi networks do not exist, utilizing BLE advertisement packets.  
**Priority:** High.

### 4.2.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 2 in Appendix B)*
1. Node enables Bluetooth Low Energy discovery engine.
2. Node advertises Service UUID `0000FE60-0000-1000-8000-00805F9B34FB`.
3. Nearby devices scanning the BLE band detect the service UUID and initiate GATT connection.
4. Characteristic `0000FE61` is queried to retrieve the remote peer's identity string.
5. Node is registered in `PeerRegistry` with transport capability `BLE`.

### 4.2.3 Functional Requirements
- **FR-4.2.1 BLE Service Advertising:** The system shall broadcast BLE advertising packets specifying Service UUID `0000FE60`.
- **FR-4.2.2 GATT Characteristic Discovery:** The system shall read Characteristic `0000FE61` to ingest peer ID and transport parameters.
- **FR-4.2.3 Multi-Transport Unification:** If a peer is discovered via both Wi-Fi and BLE, `PeerRegistry` shall merge transport capabilities into a single peer record rather than creating duplicates.

## 4.3 Store–Carry–Forward Message Buffer Management
### 4.3.1 Description and Priority
Maintains message persistence in local RAM/disk while nodes physically travel between network partitions, ensuring integrity and enforcing capacity boundaries.  
**Priority:** High.

### 4.3.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 3 in Appendix B)*
1. Message arrives via local composition or inbound socket reception.
2. Buffer verifies whether `messageId` already exists.
3. Buffer verifies that message has not expired ($\text{remainingTTL} > 0$).
4. Buffer verifies available capacity; if full, evicts lower-priority or expired entries.
5. Message is committed to the buffer, updating buffer utilization statistics.

### 4.3.3 Functional Requirements
- **FR-4.3.1 Capacity Cap Enforcement:** The buffer shall strictly enforce a maximum memory capacity of 10 MB ($10,485,760\text{ bytes}$).
- **FR-4.3.2 Duplicate Rejection:** The system shall reject any message whose `messageId` is already present in the buffer, returning status `DUPLICATE`.
- **FR-4.3.3 TTL Expiration Purging:** The system shall calculate $\text{remainingTTL} = \text{expiresAt} - \text{currentTime}$ and automatically purge expired messages upon buffer queries.
- **FR-4.3.4 Thread-Safe Access:** All additions, deletions, and queries to `MessageBuffer` shall be guarded by internal concurrency synchronization locks.

## 4.4 Dynamic Routing Engine (Epidemic, Spray-and-Wait, Adaptive Utility)
### 4.4.1 Description and Priority
Selects which messages in the buffer should be forwarded across an active contact encounter based on the selected algorithmic strategy.  
**Priority:** High.

### 4.4.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 4 in Appendix B)*
1. `ContactManager` signals an encounter with remote peer $P_{\text{remote}}$.
2. `RoutingEngine` invokes the active `RoutingStrategy.evaluateCandidates()`.
3. The strategy filters, scores, and ranks all candidate messages in the buffer.
4. The strategy returns a ranked list of selected messages for transmission.
5. In Spray-and-Wait, copy tokens are halved; in Adaptive, utility scores are logged.

### 4.4.3 Functional Requirements
- **FR-4.4.1 Pluggable Strategy Architecture:** The system shall support runtime switching between `EPIDEMIC`, `SPRAY_AND_WAIT`, and `ADAPTIVE` strategies without restarting the application.
- **FR-4.4.2 Epidemic Flooding Logic:** Under Epidemic routing, the system shall select all non-expired messages in the buffer that have not yet been delivered to the encountered peer.
- **FR-4.4.3 Spray-and-Wait Binary Splitting:**
  - If a message has $L > 1$ tokens, the system shall forward a copy with $\lfloor L / 2 \rfloor$ tokens and retain $\lceil L / 2 \rceil$ tokens locally.
  - If $L = 1$, the message shall enter the *Wait Phase* and be forwarded only if the encountered peer is the final destination.
- **FR-4.4.4 Adaptive Multi-Metric Utility Calculation:**
  The system shall calculate the forwarding utility $U(M)$ for each candidate message using:
  $$U(M) = w_P \cdot P(M) + w_T \cdot T(M) + w_B \cdot B(M)$$
  where:
  - $w_P + w_T + w_B = 1.0$ (configurable, default: $0.40, 0.40, 0.20$).
  - Priority Factor $P(M)$: $\text{LOW} = 0.33$, $\text{MEDIUM} = 0.67$, $\text{HIGH} = 1.00$.
  - TTL Urgency Factor $T(M) = 1.0 - \frac{\text{remainingTTL}}{\text{originalTTL}}$, clamped to $[0.0, 1.0]$.
  - Buffer Pressure Factor $B(M) = \frac{\text{usedBufferBytes}}{\text{totalCapacityBytes}}$, clamped to $[0.0, 1.0]$.
- **FR-4.4.5 Deterministic Tie-Breaking:** In the event of identical utility scores, candidates shall break ties using: (1) Higher $T(M)$, (2) Higher $P(M)$, (3) Earlier expiry timestamp, (4) Lexicographically lower `messageId`.

## 4.5 Multi-Transport Message Dispatch & Socket Streaming (TCP)
### 4.5.1 Description and Priority
Marshals validated DTN messages into stream-safe binary frames and delivers them across TCP sockets over Wi-Fi networks.  
**Priority:** High.

### 4.5.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 5 in Appendix B)*
1. Routing engine selects messages for forwarding.
2. System establishes a client TCP socket to the remote peer's IP and port.
3. System serializes the message into UTF-8 JSON bytes.
4. System prepends a 4-byte big-endian integer representing payload length.
5. System transmits the framed packet and awaits remote application-level ACK.

### 4.5.3 Functional Requirements
- **FR-4.5.1 4-Byte Length Prefix Framing:** The system shall write the byte length of the serialized message as a 4-byte big-endian integer prior to writing payload bytes.
- **FR-4.5.2 Framing Ingestion Safety:** The receiving socket shall use `DataInputStream.readFully()` to ingest the exact number of bytes indicated by the length prefix.
- **FR-4.5.3 Connection Timeout Handling:** The system shall enforce a 5-second socket connect timeout; if the remote peer fails to respond, the encounter is terminated without data loss.

## 4.6 Bluetooth Low Energy GATT MTU Chunking & CRC32 Reassembly
### 4.6.1 Description and Priority
Slices large DTN messages into MTU-compliant BLE GATT packets and reassembles them sequentially at the receiving node with CRC32 integrity checks.  
**Priority:** High.

### 4.6.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 6 in Appendix B)*
1. Outbound message is routed over Bluetooth Low Energy.
2. `BleChunker` reads the negotiated link MTU (23 to 512 bytes).
3. Payload is partitioned into chunks of size $S = \text{MTU} - 13$ bytes.
4. 13-byte self-describing binary headers and CRC32 checksums are attached to each chunk.
5. Receiver reassembles out-of-order chunks and validates the checksum prior to buffer ingestion.

### 4.6.3 Functional Requirements
- **FR-4.6.1 13-Byte Binary Header Attachment:** Every BLE chunk shall include: magic bytes `0x43 0x4D`, protocol version `0x02`, 16-bit Transfer ID, 16-bit Chunk Index, 16-bit Total Chunks, and 32-bit CRC32 checksum.
- **FR-4.6.2 Out-of-Order Reassembly:** The receiving `BleTransferAssembler` shall accept chunks arriving out of sequence, storing them into an index map until all chunks are present.
- **FR-4.6.3 CRC32 Checksum Verification:** The receiver shall compute the CRC32 of the reassembled payload and compare it against the header checksum; mismatched transfers shall be rejected.
- **FR-4.6.4 Incomplete Transfer Timeout:** The system shall evict partial transfers that do not complete within 30 seconds.

## 4.7 Persistent Event Logging & SQLite Audit Trail
### 4.7.1 Description and Priority
Maintains a thread-safe, queryable audit log of all system transitions, peer encounters, and message routing operations in a local SQLite database.  
**Priority:** Medium.

### 4.7.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 7 in Appendix B)*
1. An event occurs (e.g., `MESSAGE_CREATED`, `CONTACT_STARTED`, `ROUTING_DECISION`).
2. Event details are queued to `EventLogger`.
3. Background SQLite connection writes the record into table `events`.
4. Event becomes queryable via CLI and REST API `GET /api/events`.

### 4.7.3 Functional Requirements
- **FR-4.7.1 Relational Schema Persistence:** The system shall persist events into SQLite table `events` containing: `id`, `timestamp`, `event_type`, `peer_id`, `message_id`, `remote_peer_id`, `routing`, and `metadata_json`.
- **FR-4.7.2 Thread-Safe Logging:** The `EventLogger` shall synchronize database write operations to prevent concurrent transaction locking.
- **FR-4.7.3 Event Querying:** The system shall support querying the last $N$ logged events via the REST interface for real-time frontend auditing.

## 4.8 Embedded HTTP REST API Server & Web Control Plane
### 4.8.1 Description and Priority
Hosts an embedded HTTP server providing REST endpoints and serving the static dashboard frontend with zero external dependencies.  
**Priority:** High.

### 4.8.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 8 in Appendix B)*
1. Application runner initializes `CampusMeshHttpServer` on port 8080.
2. Web browser connects to `http://localhost:8080`.
3. Server returns HTML, CSS, and JavaScript files with proper MIME types.
4. Browser polling loop calls `/api/status`, `/api/peers`, `/api/buffer`, and `/api/evaluation`.

### 4.8.3 Functional Requirements
- **FR-4.8.1 Zero-Dependency HTTP Hosting:** The web server shall utilize standard JDK `com.sun.net.httpserver.HttpServer` without external servlet containers.
- **FR-4.8.2 REST API Delivery:** The server shall provide JSON endpoints for status, peers, buffer, routing configuration, and event inspection.
- **FR-4.8.3 CORS Support:** The server shall include Cross-Origin Resource Sharing (`CORS`) headers allowing distributed mesh nodes to interact via local API calls.

## 4.9 Interactive Real-Time Topology Radar & Network Simulation
### 4.9.1 Description and Priority
Renders an interactive HTML5 canvas radar displaying network nodes, links, and animated packet hops, with support for multi-hop simulation scripts.  
**Priority:** Medium.

### 4.9.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 9 in Appendix B)*
1. User loads the web dashboard.
2. Radar loop renders concentric radar rings and sweeping beam at 60 FPS.
3. Nodes (`LAPTOP_WIN`, `PHONE_MOBILE`, `PHONE_AND`, `IPHONE_IOS`, `LAPTOP_UBU`) are plotted with distinct platform colors.
4. User clicks "Run Multi-Hop Demo".
5. Backend simulation executes and visual packet animations travel across the radar screen.

### 4.9.3 Functional Requirements
- **FR-4.9.1 60 FPS HTML5 Canvas Rendering:** The radar canvas shall render smooth 60 FPS animations showing sweeping beams and packet coordinates.
- **FR-4.9.2 Interactive Node Targeting:** Clicking any node on the canvas radar shall automatically populate its ID into the composer's destination field.
- **FR-4.9.3 Multi-Hop Simulation Runner:** The system shall support an automated 4-node heterogeneous simulation demonstrating multi-hop DTN hopping across Windows, Android, iOS, and Ubuntu nodes.

## 4.10 Device Perspective Switching & Autonomous Mobile Node Identification
### 4.10.1 Description and Priority
Enables client browsers accessing the dashboard to dynamically operate under distinct device identities, automatically recognizing mobile devices connecting over Wi-Fi.  
**Priority:** High.

### 4.10.2 Stimulus/Response Sequences
*(Cross-referenced to Use Case 10 in Appendix B)*
1. Mobile phone navigates to `http://<laptop-ip>:8080`.
2. Backend inspects remote client IP; if remote, marks `isRemoteClient: true` and registers `PHONE_MOBILE` in `PeerRegistry`.
3. Frontend detects mobile User-Agent / remote flag and sets active node to `PHONE_MOBILE`.
4. Radar canvas highlights `PHONE_MOBILE (YOU)` in green.
5. Message composer auto-populates `Sender: PHONE_MOBILE` and `Destination: LAPTOP_WIN`.

### 4.10.3 Functional Requirements
- **FR-4.10.1 Remote Client Auto-Detection:** The backend shall detect remote client IP connections and auto-register `PHONE_MOBILE` in `PeerRegistry` and `ContactManager`.
- **FR-4.10.2 Client-Side Perspective Persistence:** The frontend shall persist active node perspective in browser `localStorage`.
- **FR-4.10.3 Smart Compose Form Inversion:** Selecting `PHONE_MOBILE` shall invert default compose fields (`Sender: PHONE_MOBILE`, `Destination: LAPTOP_WIN`); selecting `LAPTOP_WIN` shall set `Destination: PHONE_MOBILE`.
- **FR-4.10.4 Radar Perspective Synchronization:** The radar canvas shall dynamically render the glowing local green ring and `(YOU)` text on whichever node is currently active.

---

<div style="page-break-after: always;"></div>

# 5. Other Nonfunctional Requirements

## 5.1 Performance Requirements
- **NFR-5.1.1 Buffer Access Latency:** Adding, retrieving, or checking duplicate status of any message in `MessageBuffer` shall execute in under $5\text{ ms}$ for buffers containing up to 1,000 entries.
- **NFR-5.1.2 Adaptive Utility Evaluation Speed:** Ranking and scoring 100 candidate messages in the decision matrix shall complete in less than $2\text{ ms}$.
- **NFR-5.1.3 Socket Framing Throughput:** Over standard Wi-Fi (802.11n/ac), the TCP length-prefixed streaming protocol shall transfer up to $10\text{ MB/s}$ without CPU throttling.
- **NFR-5.1.4 BLE Slicing & Reassembly Overhead:** Slicing a $10\text{ KB}$ message into MTU-bounded GATT chunks with CRC32 calculation shall complete in less than $20\text{ ms}$.
- **NFR-5.1.5 Web Dashboard Responsiveness:** The embedded HTTP server shall respond to REST API queries within $25\text{ ms}$; the HTML5 canvas radar shall render smoothly at $60\text{ FPS}$.

## 5.2 Safety Requirements
- **NFR-5.2.1 Memory Exhaustion Protection:** The system shall strictly enforce the 10 MB buffer threshold to guarantee that running instances never cause JVM `OutOfMemoryError` failures.
- **NFR-5.2.2 Loop Flooding Prevention:** Spray-and-Wait token splitting and Epidemic delivery caches shall prevent infinite message looping and network broadcast storms.
- **NFR-5.2.3 Radio Duty-Cycle Protection:** Discovery beacons shall adhere to minimum 5-second intervals to prevent radio overheating and battery exhaustion on mobile devices.

## 5.3 Security Requirements
- **NFR-5.3.1 Payload Integrity Verification:** All BLE transfers shall undergo end-to-end CRC32 checksum verification; corrupted or altered chunks shall be discarded immediately.
- **NFR-5.3.2 Stream Injection Prevention:** The 4-byte length prefix framing shall enforce strict boundary reading, preventing TCP segment injection or stream desynchronization.
- **NFR-5.3.3 Input Sanitization:** All payload text submitted via CLI or web composer shall be sanitized against cross-site scripting (XSS) and buffer overflow vulnerabilities.
- **NFR-5.3.4 Zero Telemetry / Privacy by Design:** The system shall never transmit telemetry, user profiles, or location coordinates to external cloud entities; all operations remain strictly local to the ad-hoc mesh.

## 5.4 Software Quality Attributes

### 5.4.1 Usability
- The web control plane shall require no technical command-line knowledge to operate.
- Visual status indicators, colored priority badges, and intuitive slider controls shall provide immediate visual feedback on system state.

### 5.4.2 Reliability
- The system shall handle abrupt socket disconnections without data loss or buffer corruption.
- If a physical transmission fails mid-transfer, the message shall remain safely retained in the local store-carry-forward buffer for future contact encounters.

### 5.4.3 Maintainability
- The codebase shall follow strict object-oriented modular design principles, separating model, buffer, routing, discovery, networking, and logging into isolated Java packages.
- All classes shall be fully documented with standard Javadoc comments.

### 5.4.4 Portability
- The core Java backend shall run identically across Windows, Linux, and macOS without code modifications.
- The web dashboard shall display responsively on both desktop monitors and mobile smartphone viewports.

### 5.4.5 Efficiency
- The software shall maintain an idle memory footprint under $64\text{ MB}$ of RAM.
- Background discovery threads shall sleep between intervals to conserve battery power on mobile endpoints.

## 5.5 Business Rules
- **BR-1 (Single Copy Terminal Rule):** In Spray-and-Wait, a message with $L = 1$ token shall never be replicated to an intermediate relay node; it may only be delivered to its final destination.
- **BR-2 (TTL Expiration Dominance):** A message whose TTL has expired ($\text{remainingTTL} \le 0$) shall never be forwarded or accepted, regardless of its priority or utility score.
- **BR-3 (Utility Weight Conservation):** The sum of adaptive routing weights must strictly equal unity ($w_P + w_T + w_B = 1.00$).

---

<div style="page-break-after: always;"></div>

# 6. Other Requirements

1. **SQLite Event Audit Log Export:** The system shall support querying and exporting the local SQLite event log (`database/campusmesh.db`) into structured JSON or CSV formats for experimental evaluation.
2. **Buffer Data Reset Option:** The web dashboard and CLI shall provide a clean reset capability to purge all stored messages and event tables for reproducible benchmarking.
3. **Accessibility Compliance:** The web dashboard shall implement high-contrast dark and light color themes, adhering to WCAG 2.1 Level AA color contrast guidelines.
4. **Offline Operational Guarantee:** The system shall guarantee complete functionality in isolated environments with zero internet connectivity.
5. **Academic Project Compliance:** The software and documentation shall satisfy all academic requirements for the Mini Project course at Sardar Patel Institute of Technology (SPIT).

---

<div style="page-break-after: always;"></div>

# Appendix A: Glossary

| Sr. No. | Term | Description |
| :---: | :--- | :--- |
| 1 | **DTN** | **Delay-Tolerant Networking:** A networking architecture designed to operate effectively in environments characterized by intermittent connectivity, high latency, and lack of continuous end-to-end paths. |
| 2 | **Store–Carry–Forward** | The fundamental DTN paradigm where intermediate nodes store message bundles in persistent memory, physically carry them during movement, and forward them upon opportunistic encounters. |
| 3 | **P2P** | **Peer-to-Peer:** A decentralized network architecture where all participating nodes hold equivalent privileges and routing capabilities. |
| 4 | **mDNS** | **Multicast DNS:** A zero-configuration protocol allowing devices to discover hostnames and IP services on local subnets without a central DNS server. |
| 5 | **BLE** | **Bluetooth Low Energy:** A low-power wireless personal area network technology designed for opportunistic short-range physical communications. |
| 6 | **GATT** | **Generic Attribute Profile:** The BLE specification establishing hierarchical Service and Characteristic data structures for reading, writing, and notifying data. |
| 7 | **MTU** | **Maximum Transmission Unit:** The maximum packet size allowable across a physical network layer (e.g., 23 bytes default for BLE 4.2; 512 bytes negotiated for BLE 5.0). |
| 8 | **CRC32** | **Cyclic Redundancy Check 32-bit:** An error-detecting polynomial checksum algorithm used to verify raw packet integrity over wireless radio channels. |
| 9 | **TTL** | **Time-To-Live:** A lifespan parameter defining the duration in seconds during which a DTN message remains valid before automatic buffer eviction. |
| 10 | **Epidemic Routing** | A flooding-based DTN routing algorithm where nodes replicate stored messages to every newly encountered peer that does not yet possess a copy. |
| 11 | **Spray-and-Wait** | A controlled DTN replication protocol that sprays a finite number of message copies ($L$) across nodes via binary token splitting before entering a direct wait phase. |
| 12 | **Adaptive Routing** | A multi-criteria DTN routing strategy evaluating message Priority ($P$), TTL Urgency ($T$), and Buffer Pressure ($B$) to dynamically rank forwarding candidates. |
| 13 | **REST** | **Representational State Transfer:** An architectural style utilizing HTTP methods (GET, POST) and JSON payloads for client-server communication. |
| 14 | **SRS** | **Software Requirements Specification:** A formal document detailing the complete operational and behavioral requirements of a software system. |
| 15 | **SPIT** | **Sardar Patel Institute of Technology:** Autonomous academic institution in Mumbai affiliated with the University of Mumbai. |

---

<div style="page-break-after: always;"></div>

# Appendix B: Analysis Models

*The use cases below serve as the formal analysis model for the system, directly cross-referenced from the Stimulus/Response sequences in Section 4.*

### Use Case 1: Autonomous Peer Discovery via Wi-Fi Beacon
- **Actor:** Participating Campus Node (Laptop / Mobile)
- **Description:** A node discovers adjacent peers across the local Wi-Fi subnet without manual configuration.
- **Steps:**
  1. Node initializes `WifiDiscoveryService`.
  2. Node joins multicast group `239.255.77.88:5354`.
  3. Node emits periodic UDP beacons containing peer ID, platform, IP, and port.
  4. Remote peers receive beacon, validate schema, and record node into `PeerRegistry`.
  5. Node registers discovered peers and notifies `ContactManager`.

### Use Case 2: BLE Service Discovery and Characteristic Handshake
- **Actor:** BLE-Enabled Mobile / Laptop Node
- **Description:** A node detects nearby devices using Bluetooth Low Energy advertising and GATT characteristics.
- **Steps:**
  1. Node activates `BleDiscoveryService`.
  2. Advertises Service UUID `0000FE60-0000-1000-8000-00805F9B34FB`.
  3. Remote scanner detects advertisement and establishes GATT link.
  4. Reads Characteristic `0000FE61` to ingest peer identity and capabilities.
  5. Peer is registered with transport `BLE` in local `PeerRegistry`.

### Use Case 3: Enqueue and Store Message in Local Buffer
- **Actor:** Campus User / Application Node
- **Description:** A new message is created and committed to the bounded local DTN buffer.
- **Steps:**
  1. User composes message with destination, priority, TTL, tokens, and payload.
  2. System checks buffer for duplicate `messageId`.
  3. System checks message expiration timestamp.
  4. System verifies buffer capacity ($\le 10\text{ MB}$).
  5. Message is added to buffer; event `MESSAGE_CREATED` is logged to SQLite.

### Use Case 4: Evaluate Candidates via Adaptive Routing Matrix
- **Actor:** Routing Engine
- **Description:** The system evaluates all buffered messages to rank forwarding candidates during an encounter.
- **Steps:**
  1. `ContactManager` triggers contact encounter with peer $P$.
  2. `RoutingEngine` queries active `AdaptiveRouter`.
  3. Router computes $P(M)$, $T(M)$, $B(M)$ and utility $U(M) = w_P \cdot P + w_T \cdot T + w_B \cdot B$ for each message.
  4. Candidates are sorted descending by utility with deterministic tie-breaking.
  5. Top-ranked messages are returned for physical transport dispatch.

### Use Case 5: Deliver Message via Length-Prefixed TCP Stream
- **Actor:** Transport Layer
- **Description:** The system transfers an outbound message to a discovered peer over a direct Wi-Fi TCP socket.
- **Steps:**
  1. `TCPManager` opens socket connection to remote peer's IP and port.
  2. JSON payload is serialized to UTF-8 bytes.
  3. 4-byte big-endian integer length prefix is written to `DataOutputStream`.
  4. Payload bytes are written and flushed.
  5. Remote peer reads length prefix and ingests payload with `DataInputStream.readFully()`.
  6. Event `MESSAGE_FORWARDED` is logged in SQLite.

### Use Case 6: Chunk and Reassemble BLE GATT Transmission
- **Actor:** BLE Transport Layer
- **Description:** A large DTN message is transferred over BLE via chunking and reassembly.
- **Steps:**
  1. Outbound message payload is passed to `BleChunker`.
  2. Sliced into chunks matching negotiated MTU ($N = \text{MTU} - 13$).
  3. 13-byte binary headers with sequence indices and CRC32 checksums are attached.
  4. Chunks are transmitted over Characteristic `0000FE62`.
  5. Receiving `BleTransferAssembler` verifies CRC32 and reassembles payload.
  6. Message is committed to receiver's `MessageBuffer`.

### Use Case 7: Audit Event Logging to SQLite Database
- **Actor:** System Logging Subsystem
- **Description:** System operations are recorded into relational tables for auditing.
- **Steps:**
  1. Component generates event (e.g., `ROUTING_DECISION`).
  2. Event parameters are packaged into `EventLogger.logEvent()`.
  3. SQLite database transaction inserts record into table `events`.
  4. Database confirms write; event appears in web dashboard audit stream.

### Use Case 8: Inspect and Control Node via Web Dashboard
- **Actor:** Campus User / Network Researcher
- **Description:** User monitors node metrics, buffer status, and radar links via web browser.
- **Steps:**
  1. User opens `http://localhost:8080`.
  2. Embedded server returns static assets and JSON status.
  3. Browser renders live radar, buffer list, and decision table.
  4. User modifies adaptive weight sliders; backend updates routing weights in real time.

### Use Case 9: Execute Automated Multi-Hop Mesh Simulation
- **Actor:** Network Researcher / Academic Evaluator
- **Description:** User runs an automated simulation sequence demonstrating multi-hop data dissemination.
- **Steps:**
  1. User triggers simulation via button click or CLI script.
  2. Simulation executes packet transfers: `LAPTOP_WIN -> PHONE_MOBILE -> IPHONE_IOS -> LAPTOP_UBU`.
  3. Animated packets traverse the canvas radar screen.
  4. SQLite logs reflect multi-hop routing events across simulated nodes.

### Use Case 10: Auto-Detect Mobile Client and Invert Operating Perspective
- **Actor:** Mobile Phone User
- **Description:** Mobile browser connects to laptop server and automatically operates as an independent node.
- **Steps:**
  1. User opens `http://<laptop-ip>:8080` on mobile smartphone.
  2. Backend detects remote Wi-Fi IP and registers `PHONE_MOBILE`.
  3. Frontend initializes active perspective as `PHONE_MOBILE`.
  4. Canvas radar highlights `PHONE_MOBILE (YOU)` with glowing green ring.
  5. Composer sets `Sender: PHONE_MOBILE` and `Destination: LAPTOP_WIN`.
  6. User sends message; message is logged and buffered as originating from the mobile phone.

### Use Case 11: Evaluate SRS and System Test Compliance
- **Actor:** Academic Course Evaluator (Prof. Yogita Patil)
- **Description:** Evaluator validates system execution, test suite passes, and requirement compliance.
- **Steps:**
  1. Evaluator executes `build.bat` to compile codebase.
  2. Runs test suites (`CampusMeshTestSuite`, `HttpApiTestSuite`, `DiscoveryTestSuite`, `BleProtocolTestSuite`).
  3. Verifies that all 43 unit and functional tests pass with 0 failures.
  4. Cross-verifies implemented features with SRS requirements in Section 4.

---

<div style="page-break-after: always;"></div>

# Appendix C: Data Dictionary

| Name | Type | Category | Possible Values / States | Used In | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`messageId`** | String | State Variable | e.g., `"M001"`, `"M1_4920"` | Message, Buffer, Logs | Globally unique identifier assigned to every message bundle. |
| **`sourceId`** | String | State Variable | e.g., `"LAPTOP_WIN"`, `"PHONE_MOBILE"` | Message, Composer, Routing | Peer identifier of the originating creator node. |
| **`destinationId`** | String | State Variable | e.g., `"LAPTOP_UBU"`, `"BROADCAST"` | Message, Composer, Routing | Intended destination node ID or broadcast identifier. |
| **`priority`** | Enum | State Variable | `LOW`, `MEDIUM`, `HIGH` | Message, Routing, UI | Priority level determining routing urgency factor $P(M)$. |
| **`priorityWeight`** | Double | Derived Value | `0.33` (Low), `0.67` (Med), `1.00` (High) | AdaptiveRouter, Table | Normalized numeric weight $P(M)$ used in utility calculation. |
| **`payload`** | String | Input / State | UTF-8 text string (max 10 MB) | Message, Sockets, BLE | Textual message content carried across the network mesh. |
| **`createdAt`** | Instant | State Variable | ISO-8601 UTC timestamp | Message, Expiry | Exact creation timestamp of the message bundle. |
| **`expiresAt`** | Instant | State Variable | ISO-8601 UTC timestamp | Message, Expiry, Buffer | Calculated expiration timestamp ($createdAt + ttlSeconds$). |
| **`ttlSeconds`** | Long | Input / Parameter | $60$ to $86400$ seconds | Composer, Message | Initial Time-To-Live duration assigned to the message. |
| **`remainingTTL`** | Long | Derived Value | $\ge 0$ seconds | Message, AdaptiveRouter | Seconds remaining before message is considered expired. |
| **`copyTokens`** | Integer | State Variable | $1$ to $16$ (default: $4$) | SprayAndWaitRouter | Number of forwarding replication tokens remaining ($L$). |
| **`isExpired`** | Boolean | Derived Value | `true`, `false` | Buffer, Routing | Flag indicating whether message has passed its expiration time. |
| **`peerId`** | String | State Variable | e.g., `"LAPTOP_WIN"`, `"PHONE_AND"` | Peer, Discovery, Routing | Unique logical identifier representing a participating mesh node. |
| **`displayName`** | String | Metadata | e.g., `"Galaxy S23"`, `"Laptop (Win)"` | Peer, UI Radar | Human-readable platform name displayed on radar and chips. |
| **`platform`** | String | Metadata | `WINDOWS`, `ANDROID`, `IOS`, `UBUNTU`, `MOBILE` | Peer, UI Radar | Operating system platform of the peer node. |
| **`ipAddress`** | String | Network State | Valid IPv4/IPv6 string (e.g., `"10.180.24.62"`) | Sockets, Discovery | Network IP address used for Wi-Fi TCP socket connections. |
| **`port`** | Integer | Network State | $1024$ to $65535$ (default: $5001$, $8080$) | Sockets, HTTP Server | TCP port number for socket binding and REST services. |
| **`transports`** | List\<String\> | Metadata | `["WIFI"]`, `["BLE"]`, `["WIFI", "BLE"]` | Peer, Discovery | List of active physical radio channels supported by the peer. |
| **`lastSeenTimestamp`** | Long | State Variable | Epoch milliseconds | DiscoveryRegistry | Timestamp of last received discovery beacon from this peer. |
| **`inContact`** | Boolean | Dynamic State | `true`, `false` | ContactManager, UI | Indicates whether an active communication encounter exists. |
| **`wP`** | Double | Parameter | $0.00$ to $1.00$ (default: $0.40$) | AdaptiveRouter, Sliders | User-configurable weight assigned to Priority Factor. |
| **`wT`** | Double | Parameter | $0.00$ to $1.00$ (default: $0.40$) | AdaptiveRouter, Sliders | User-configurable weight assigned to TTL Urgency Factor. |
| **`wB`** | Double | Parameter | $0.00$ to $1.00$ (default: $0.20$) | AdaptiveRouter, Sliders | User-configurable weight assigned to Buffer Pressure Factor. |
| **`utility`** | Double | Derived Value | $0.00$ to $1.00$ | AdaptiveRouter, Table | Overall forwarding score $U(M) = w_P \cdot P + w_T \cdot T + w_B \cdot B$. |
| **`bufferCapacity`** | Long | Constant | $10,485,760$ bytes ($10\text{ MB}$) | MessageBuffer | Hard upper memory limit enforced for local message storage. |
| **`bufferUsedBytes`** | Long | State Variable | $0$ to $10,485,760$ bytes | MessageBuffer, Metrics | Total serialized byte volume currently retained in the buffer. |
| **`bufferPressure`** | Double | Derived Value | $0.0000$ to $1.0000$ | Buffer, AdaptiveRouter | Ratio of used buffer bytes to total buffer capacity ($B$). |
| **`transferId`** | Integer | State Variable | $0$ to $65535$ (uint16) | BleChunker, BLE Header | Unique identifier distinguishing concurrent BLE file transfers. |
| **`chunkIndex`** | Integer | State Variable | $1$ to `totalChunks` (uint16) | BleChunker, BLE Header | 1-based sequential index of the current BLE packet chunk. |
| **`totalChunks`** | Integer | State Variable | $\ge 1$ (uint16) | BleChunker, BLE Header | Total number of chunks comprising the complete message transfer. |
| **`crc32Checksum`** | Long | Checksum | 32-bit unsigned integer (hex) | BleChunker, Assembler | End-to-end CRC32 checksum verifying reassembled data integrity. |
| **`activeNodeId`** | String | UI State Variable | e.g., `"LAPTOP_WIN"`, `"PHONE_MOBILE"` | Web Dashboard, Storage | Active device perspective currently simulated or operated by UI. |
| **`eventType`** | String | Audit State | `MESSAGE_CREATED`, `CONTACT_STARTED`, etc. | EventLogger, SQLite | Formal event classification tag logged in the database. |

---

<div style="page-break-after: always;"></div>

# Appendix D: To Be Determined (TBD) List

No TBD (To Be Determined) items are currently identified in this document. All architectural parameters, radio interface specifications, routing equations, external protocols, and verification criteria have been fully specified and validated as of Version 1.0. This appendix will be updated with a numbered list of any TBD markers introduced in future revisions.
