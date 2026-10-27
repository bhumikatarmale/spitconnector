package campusmesh.demo;

import campusmesh.buffer.MessageBuffer;
import campusmesh.model.Message;
import campusmesh.model.Peer;
import campusmesh.model.Priority;
import campusmesh.routing.AdaptiveRouter;
import campusmesh.routing.EpidemicRouter;
import campusmesh.routing.RoutingEngine;
import campusmesh.routing.SprayAndWaitRouter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ExperimentRunner:
 * Functional verification runner comparing routing decisions across:
 * - Epidemic Routing
 * - Source Spray-and-Wait
 * - Adaptive Forwarding
 *
 * Demonstrates that when presented with the exact same input messages in the buffer,
 * the three routing strategies evaluate constraints and select messages differently.
 *
 * NOTE: As noted in project specifications, this is purely a functional decision
 * comparison to verify algorithmic logic, NOT a performance benchmark.
 */
public class ExperimentRunner {

    public static void main(String[] args) {
        Peer peerA = new Peer("A", "127.0.0.1", 5001);
        Peer peerB = new Peer("B", "127.0.0.1", 5002);

        MessageBuffer buffer = new MessageBuffer("A");

        // Populate a diverse pool of 10 messages:
        // Msg 1: Active, high priority, sprayable (4 tokens)
        buffer.addMessage(new Message("M01", "A", "C", Priority.HIGH, "Data 1", 300, 4));
        // Msg 2: Active, high priority, sprayable (2 tokens)
        buffer.addMessage(new Message("M02", "A", "D", Priority.HIGH, "Data 2", 200, 2));
        // Msg 3: Direct destination is B! (1 token, wait phase, but remote is dest)
        buffer.addMessage(new Message("M03", "A", "B", Priority.MEDIUM, "Direct delivery for B", 150, 1));
        // Msg 4: Active, medium priority, sprayable (3 tokens)
        buffer.addMessage(new Message("M04", "A", "D", Priority.MEDIUM, "Data 4", 250, 3));
        // Msg 5: Wait phase (copyTokens = 1), dest is C (NOT B)
        buffer.addMessage(new Message("M05", "A", "C", Priority.LOW, "Wait phase message", 180, 1));
        // Msg 6: Wait phase (copyTokens = 1), dest is D (NOT B)
        buffer.addMessage(new Message("M06", "A", "D", Priority.HIGH, "Wait phase message", 90, 1));
        // Msg 7: Active, low priority, sprayable (2 tokens)
        buffer.addMessage(new Message("M07", "A", "C", Priority.LOW, "Data 7", 350, 2));
        // Msg 8: Urgent TTL (few seconds left), sprayable (4 tokens)
        Instant pastTime = Instant.now().minusSeconds(190);
        Instant expireSoon = pastTime.plusSeconds(200); // 10s left of 200s
        buffer.addMessage(new Message("M08", "A", "D", pastTime, expireSoon, Priority.HIGH, "Urgent alert", 4));
        // Msg 9: Active, medium priority, sprayable (2 tokens)
        buffer.addMessage(new Message("M09", "A", "C", Priority.MEDIUM, "Data 9", 220, 2));
        // Msg 10: Wait phase (copyTokens = 1), dest is E (NOT B)
        buffer.addMessage(new Message("M10", "A", "E", Priority.LOW, "Wait phase data", 140, 1));

        // Remote peer B already has message M09 in its cache (summary vector)
        Set<String> remoteKnown = new HashSet<>();
        remoteKnown.add("M09");

        System.out.println("====================================");
        System.out.println("   CAMPUSMESH ROUTING COMPARISON");
        System.out.println("====================================");
        System.out.println();
        System.out.printf("Total Messages in Buffer: %d%n", buffer.size());
        System.out.printf("Local Peer: %s%n", peerA.getPeerId());
        System.out.printf("Remote Peer: %s%n", peerB.getPeerId());
        System.out.printf("Messages already known by Remote: %s%n", remoteKnown);
        System.out.println();

        // 1. Epidemic
        EpidemicRouter epidemic = new EpidemicRouter();
        List<Message> epidemicResult = epidemic.selectMessages(peerA, peerB, buffer, remoteKnown);

        System.out.println("Epidemic:");
        System.out.printf("  Selected = %d%n", epidemicResult.size());
        System.out.print("  Message IDs: ");
        printMessageIds(epidemicResult);
        System.out.println("  Behavior: Floods all eligible non-expired messages not known by remote.");
        System.out.println();

        // 2. Spray-and-Wait
        SprayAndWaitRouter sprayAndWait = new SprayAndWaitRouter();
        List<Message> sprayResult = sprayAndWait.selectMessages(peerA, peerB, buffer, remoteKnown);

        System.out.println("Spray-and-Wait:");
        System.out.printf("  Selected = %d%n", sprayResult.size());
        System.out.print("  Message IDs: ");
        printMessageIds(sprayResult);
        System.out.println("  Behavior: Only sprays messages with tokens > 1, or directly delivers M03.");
        System.out.println();

        // 3. Adaptive
        AdaptiveRouter adaptive = new AdaptiveRouter(0.40, 0.40, 0.20);
        List<Message> adaptiveResult = adaptive.selectMessages(peerA, peerB, buffer, remoteKnown);

        System.out.println("Adaptive:");
        System.out.printf("  Selected = %d%n", adaptiveResult.size());
        System.out.print("  Message IDs: ");
        printMessageIds(adaptiveResult);
        System.out.println("  Behavior: Prioritizes by Utility U(M) = wP*P + wT*T + wB*B, bounds replication.");
        System.out.println();

        System.out.println("====================================");
        System.out.println("SUMMARY OF COMPARISON:");
        System.out.printf("  Input messages:               %d%n", buffer.size());
        System.out.printf("  Epidemic selected:            %d (%.1f%%)%n",
                epidemicResult.size(), (epidemicResult.size() * 100.0 / buffer.size()));
        System.out.printf("  Spray-and-Wait selected:      %d (%.1f%%)%n",
                sprayResult.size(), (sprayResult.size() * 100.0 / buffer.size()));
        System.out.printf("  Adaptive selected:            %d (%.1f%%)%n",
                adaptiveResult.size(), (adaptiveResult.size() * 100.0 / buffer.size()));
        System.out.println("====================================");
        System.out.println("Functional verification completed successfully.");
    }

    private static void printMessageIds(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            sb.append(messages.get(i).getMessageId());
            if (i < messages.size() - 1) sb.append(", ");
        }
        System.out.println(sb.toString());
    }
}
