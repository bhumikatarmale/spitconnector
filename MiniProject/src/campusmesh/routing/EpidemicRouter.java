package campusmesh.routing;

import campusmesh.buffer.MessageBuffer;
import campusmesh.model.Message;
import campusmesh.model.Peer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Epidemic Routing Strategy:
 * Implements the classic DTN flooding protocol (Vahdat & Becker, 2000).
 *
 * Rules:
 * 1. Inspect all messages in the local peer's buffer.
 * 2. Ignore messages that have expired (TTL elapsed).
 * 3. Ignore messages already known to the remote peer (avoids redundant transmissions).
 * 4. Forward ALL remaining eligible messages without replication limits.
 */
public class EpidemicRouter implements RoutingStrategy {

    @Override
    public String getName() {
        return "EPIDEMIC";
    }

    @Override
    public List<Message> selectMessages(
            Peer localPeer,
            Peer remotePeer,
            MessageBuffer buffer,
            Set<String> remoteKnownMessageIds) {

        List<Message> eligible = new ArrayList<>();
        if (buffer == null) {
            return eligible;
        }
        List<Message> allMessages = buffer.getAllMessages();

        for (Message msg : allMessages) {
            // Ignore expired messages
            if (msg.isExpired()) {
                continue;
            }
            // Ignore messages already possessed by the remote peer
            if (remoteKnownMessageIds != null && remoteKnownMessageIds.contains(msg.getMessageId())) {
                continue;
            }
            eligible.add(msg);
        }

        // In pure Epidemic Routing, all eligible messages are selected for forwarding
        return eligible;
    }

    /**
     * Demonstrates epidemic routing decision with terminal output matching project specifications.
     */
    public void printDecisionDemo(Peer localPeer, Peer remotePeer, MessageBuffer buffer, Set<String> remoteKnown) {
        System.out.println("=== EPIDEMIC ROUTING ===");
        System.out.printf("Local Peer: %s%n", localPeer.getPeerId());
        System.out.printf("Remote Peer: %s%n%n", remotePeer.getPeerId());

        List<Message> eligible = selectMessages(localPeer, remotePeer, buffer, remoteKnown);

        System.out.println("Eligible messages:");
        if (eligible.isEmpty()) {
            System.out.println("  (None)");
        } else {
            for (Message msg : eligible) {
                System.out.printf("  %s%n", msg.getMessageId());
            }
        }

        System.out.println("\nSelected for forwarding:");
        if (eligible.isEmpty()) {
            System.out.println("  (None)");
        } else {
            for (Message msg : eligible) {
                System.out.printf("  %s%n", msg.getMessageId());
            }
        }
        System.out.println();
    }
}
