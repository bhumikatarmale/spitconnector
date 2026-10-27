package campusmesh.demo;

import campusmesh.ble.BleChunker;
import campusmesh.ble.BleConstants;
import campusmesh.ble.BlePacket;
import campusmesh.ble.BleTransferAssembler;
import campusmesh.buffer.MessageBuffer;
import campusmesh.discovery.DiscoveredPeer;
import campusmesh.model.Message;
import campusmesh.model.Peer;
import campusmesh.model.Priority;
import campusmesh.networking.MultiTransportSender;
import campusmesh.networking.TCPManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Demonstrates an end-to-end multi-hop DTN message transfer across a heterogeneous mesh:
 * [Windows Laptop] --(Wi-Fi TCP)--> [Android Phone] --(BLE GATT Chunks)--> [iPhone] --(Wi-Fi TCP)--> [Ubuntu Laptop]
 */
public class MultiTransportMeshDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("================================================================================");
        System.out.println("          CAMPUSMESH HETEROGENEOUS MULTI-TRANSPORT MESH DEMONSTRATION           ");
        System.out.println("   Topology: [Windows Laptop] -> [Android Phone] -> [iPhone] -> [Ubuntu Laptop]   ");
        System.out.println("================================================================================\n");

        // 1. Initial Source Message on Windows Laptop
        Message originalMessage = new Message(
                "CAMPUS_ALERT_01",
                "LAPTOP_WIN",
                "LAPTOP_UBU",
                Priority.HIGH,
                "URGENT: Power outage scheduled for science block at 22:30. Secure all equipment.",
                3600,
                4 // 4 copy tokens initially
        );

        MessageBuffer bufferWin = new MessageBuffer("LAPTOP_WIN");
        bufferWin.addMessage(originalMessage);

        System.out.println("[STEP 1: MESSAGE CREATION ON WINDOWS LAPTOP]");
        System.out.printf("  Message ID:    %s%n", originalMessage.getMessageId());
        System.out.printf("  Source:        %s -> Final Destination: %s%n", originalMessage.getSourceId(), originalMessage.getDestinationId());
        System.out.printf("  Priority:      %s (Score=%.2f)%n", originalMessage.getPriority(), originalMessage.getPriority().getWeight());
        System.out.printf("  Payload:       \"%s\"%n", originalMessage.getPayload());
        System.out.printf("  Initial Tokens: %d%n%n", originalMessage.getCopyTokens());

        // 2. Hop 1: Windows Laptop -> Android Phone via Wi-Fi TCP
        System.out.println("[STEP 2: HOP 1 (Windows Laptop -> Android Phone via Wi-Fi)]");
        int androidPort = 5971;
        CountDownLatch androidLatch = new CountDownLatch(1);
        AtomicReference<Message> androidReceivedRef = new AtomicReference<>();

        try (TCPManager androidTcpServer = new TCPManager()) {
            androidTcpServer.startServer(androidPort, (msg, socket) -> {
                androidReceivedRef.set(msg);
                androidLatch.countDown();
            });

            DiscoveredPeer androidPeer = new DiscoveredPeer("PHONE_AND", "Galaxy S23", "ANDROID", "127.0.0.1", androidPort);
            androidPeer.addTransport(DiscoveredPeer.TRANSPORT_WIFI);

            try (MultiTransportSender sender = new MultiTransportSender()) {
                MultiTransportSender.TransferResult res = sender.send(androidPeer, originalMessage.copyWithTokens(2));
                System.out.printf("  Transport Selected: %s%n", res.transportUsed);
                System.out.printf("  Bytes Transferred:  %d bytes%n", res.bytesTransferred);
                System.out.printf("  Status:             %s%n", res.statusDetail);
            }

            boolean ok = androidLatch.await(2, TimeUnit.SECONDS);
            if (!ok || androidReceivedRef.get() == null) {
                throw new RuntimeException("Hop 1 failed: Android did not receive message");
            }
            System.out.println("  -> [SUCCESS] Android node received and stored message in buffer.");
        }
        System.out.println();

        // 3. Hop 2: Android Phone -> iPhone via Bluetooth Low Energy (Out of Wi-Fi range)
        System.out.println("[STEP 3: HOP 2 (Android Phone -> iPhone via BLE GATT Slicing)]");
        System.out.println("  Notice: Both devices are away from Wi-Fi access points. Establishing BLE GATT link...");

        Message msgOnAndroid = androidReceivedRef.get();
        Message msgForIphone = msgOnAndroid.copyWithTokens(1); // Token split: 2 -> 1

        // iPhone BLE Assembler
        CountDownLatch iphoneLatch = new CountDownLatch(1);
        AtomicReference<Message> iphoneReceivedRef = new AtomicReference<>();

        BleTransferAssembler iphoneBleAssembler = new BleTransferAssembler(new BleTransferAssembler.AssemblyListener() {
            @Override
            public void onTransferComplete(int transferId, Message message) {
                iphoneReceivedRef.set(message);
                iphoneLatch.countDown();
            }
            @Override
            public void onTransferFailed(int transferId, String error) {
                System.err.println("  [BLE ERROR] " + error);
            }
        });

        // Android Chunker slices at standard 20-byte BLE MTU payload
        List<BlePacket> bleChunks = BleChunker.chunkMessage(msgForIphone, 20);
        System.out.printf("  Sliced %d bytes into %d BLE packets (MTU 23-byte default)%n",
                msgForIphone.toJson().getBytes().length, bleChunks.size());
        System.out.printf("  Calculated CRC32: 0x%08X%n", bleChunks.get(0).getCrc32());

        // Stream chunks over simulated GATT characteristic 0000FE62
        BlePacket finalAck = null;
        for (int i = 0; i < bleChunks.size(); i++) {
            byte[] packetBytes = bleChunks.get(i).toBytes();
            BlePacket ack = iphoneBleAssembler.processIncomingBytes(packetBytes);
            if (ack != null) {
                finalAck = ack;
            }
        }

        boolean bleOk = iphoneLatch.await(2, TimeUnit.SECONDS);
        if (!bleOk || iphoneReceivedRef.get() == null || finalAck == null) {
            throw new RuntimeException("Hop 2 failed: iPhone did not receive or validate BLE transfer");
        }
        System.out.printf("  iPhone GATT Receiver verified CRC32 and issued ACK (Status=0x%02X)%n", finalAck.getPayload()[0]);
        System.out.println("  -> [SUCCESS] iPhone node received and stored message via BLE GATT.");
        System.out.println();

        // 4. Hop 3: iPhone -> Ubuntu Laptop via Wi-Fi TCP (Destination reached)
        System.out.println("[STEP 4: HOP 3 (iPhone -> Ubuntu Laptop via Wi-Fi)]");
        System.out.println("  Notice: iPhone moves into science lab, discovers Ubuntu Laptop via mDNS.");

        int ubuntuPort = 5972;
        CountDownLatch ubuntuLatch = new CountDownLatch(1);
        AtomicReference<Message> ubuntuReceivedRef = new AtomicReference<>();

        try (TCPManager ubuntuTcpServer = new TCPManager()) {
            ubuntuTcpServer.startServer(ubuntuPort, (msg, socket) -> {
                ubuntuReceivedRef.set(msg);
                ubuntuLatch.countDown();
            });

            DiscoveredPeer ubuntuPeer = new DiscoveredPeer("LAPTOP_UBU", "Lab Workstation", "UBUNTU", "127.0.0.1", ubuntuPort);
            ubuntuPeer.addTransport(DiscoveredPeer.TRANSPORT_WIFI);

            try (MultiTransportSender sender = new MultiTransportSender()) {
                MultiTransportSender.TransferResult res = sender.send(ubuntuPeer, iphoneReceivedRef.get());
                System.out.printf("  Transport Selected: %s%n", res.transportUsed);
                System.out.printf("  Bytes Transferred:  %d bytes%n", res.bytesTransferred);
                System.out.printf("  Status:             %s%n", res.statusDetail);
            }

            boolean ok = ubuntuLatch.await(2, TimeUnit.SECONDS);
            if (!ok || ubuntuReceivedRef.get() == null) {
                throw new RuntimeException("Hop 3 failed: Ubuntu Laptop did not receive message");
            }
        }

        Message delivered = ubuntuReceivedRef.get();
        System.out.println("  -> [SUCCESS] Final destination reached! Ubuntu Laptop accepted payload.");
        System.out.println();

        // 5. Verification Summary
        System.out.println("================================================================================");
        System.out.println("                       END-TO-END VERIFICATION SUMMARY                          ");
        System.out.println("================================================================================");
        System.out.printf("  Delivered Message ID:  %s%n", delivered.getMessageId());
        System.out.printf("  Original Source:       %s%n", delivered.getSourceId());
        System.out.printf("  Final Destination:     %s%n", delivered.getDestinationId());
        System.out.printf("  Delivered Payload:     \"%s\"%n", delivered.getPayload());
        System.out.printf("  Remaining TTL:         %d seconds%n", delivered.getRemainingTTL());
        System.out.printf("  Hops Traversed:        LAPTOP_WIN -> PHONE_AND (Wi-Fi) -> IPHONE_IOS (BLE) -> LAPTOP_UBU (Wi-Fi)%n");
        System.out.println("================================================================================");
        System.out.println("MULTI-TRANSPORT HETEROGENEOUS MESH DEMONSTRATION COMPLETE: ALL HOPS VERIFIED!");
    }
}
