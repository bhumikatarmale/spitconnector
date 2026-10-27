# CampusMesh for iOS (iPhone & iPad)

This directory contains the native iOS implementation of **CampusMesh**, enabling iPhones to participate as first-class peer nodes in decentralized offline mesh networks.

## Features
- **Modern Apple Network.framework**: Zero legacy BSD socket code; utilizes high-performance `NWListener` and `NWConnection` with peer-to-peer enablement.
- **Bonjour (mDNS) Discovery**: Automatically advertises and discovers nodes via `_campusmesh._tcp.` over Wi-Fi / local intranet.
- **UDP Multicast Beacon Fallback**: Broadcasts and listens on `239.255.77.88:5354` to guarantee discovery on enterprise or university networks where router mDNS reflection is filtered.
- **Strict 4-Byte Big-Endian TCP Framing**: Wire-compatible with Windows/Ubuntu Java nodes and Android nodes.
- **Bounded Message Buffer**: In-memory priority and TTL-aware store-and-forward buffer.
- **SwiftUI Interface**: Real-time peer radar and store-and-forward message composer.

## How to Open in Xcode
1. Launch Xcode on macOS (Xcode 15+ recommended).
2. Choose **File > Open** and select the `MiniProject/ios` directory (Swift Package) or create an iOS App project targeting iOS 15+ and drag `CampusMesh/` into it.
3. Select an iOS Simulator or connected iPhone.
4. Press **Cmd + R** to run.
