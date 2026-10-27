package campusmesh.routing;

import campusmesh.buffer.MessageBuffer;
import campusmesh.model.Message;
import campusmesh.model.Peer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Spray-and-Wait Routing Strategy (Spyropoulos et al.):
 * Controls DTN flooding by bounding the total number of message replicas
 * using copy tokens (L).
 *
 * Phases:
 * 1. Spray Phase (copyTokens > 1):
 *    The node splits its remaining tokens with encountered relay peers.
 *    tokens_sender = ceil(L / 2)
 *    tokens_receiver = floor(L / 2)
 *
 * 2. Wait Phase (copyTokens == 1):
 *    The node halts opportunistic replication and carries the message until
 *    directly encountering the destination node.
 */
public class SprayAndWaitRouter implements RoutingStrategy {

    public static class TokenSplitResult {
        public final int senderRemaining;
        public final int receiverAssigned;

        public TokenSplitResult(int senderRemaining, int receiverAssigned) {
            this.senderRemaining = senderRemaining;
            this.receiverAssigned = receiverAssigned;
        }
    }

    @Override
    public String getName() {
        return "SPRAY_AND_WAIT";
    }

    /**
     * Splits copy tokens between sender and receiver.
     * Example: 4 -> sender: 2, receiver: 2
     *          3 -> sender: 2, receiver: 1
     *          2 -> sender: 1, receiver: 1
     */
    public static TokenSplitResult splitTokens(int currentTokens) {
        if (currentTokens <= 1) {
            return new TokenSplitResult(currentTokens, 0);
        }
        int receiverTokens = currentTokens / 2;
        int senderTokens = currentTokens - receiverTokens;
        return new TokenSplitResult(senderTokens, receiverTokens);
    }

    @Override
    public List<Message> selectMessages(
            Peer localPeer,
            Peer remotePeer,
            MessageBuffer buffer,
            Set<String> remoteKnownMessageIds) {

        List<Message> selected = new ArrayList<>();
        if (buffer == null || remotePeer == null) {
            return selected;
        }
        List<Message> allMessages = buffer.getAllMessages();

        for (Message msg : allMessages) {
            if (msg.isExpired()) {
                continue;
            }
            if (remoteKnownMessageIds != null && remoteKnownMessageIds.contains(msg.getMessageId())) {
                continue;
            }

            boolean isDirectDestination = remotePeer.getPeerId().equalsIgnoreCase(msg.getDestinationId());

            if (isDirectDestination) {
                // If remote peer is final destination, always deliver regardless of token count
                selected.add(msg);
            } else if (msg.getCopyTokens() > 1) {
                // Spray phase: can replicate half tokens to relay peer
                selected.add(msg);
            }
            // If copyTokens == 1 and not destination: Wait Phase, do not forward to relay
        }

        return selected;
    }

    /**
     * Demonstrates token splitting for terminal output per project specification.
     */
    public void printTokenSplittingDemo(Peer sender, Peer receiver, Message message) {
        int initialCopies = message.getCopyTokens();
        TokenSplitResult split = splitTokens(initialCopies);

        System.out.println("=== SPRAY-AND-WAIT ===");
        System.out.println();
        System.out.printf("Message: %s%n", message.getMessageId());
        System.out.printf("Initial copies: %d%n", initialCopies);
        System.out.println();
        System.out.printf("%s forwards %s to %s%n", sender.getPeerId(), message.getMessageId(), receiver.getPeerId());
        System.out.println();
        System.out.println("Before:");
        System.out.printf("%s = %d%n", sender.getPeerId(), initialCopies);
        System.out.printf("%s = 0%n", receiver.getPeerId());
        System.out.println();
        System.out.println("After:");
        System.out.printf("%s = %d%n", sender.getPeerId(), split.senderRemaining);
        System.out.printf("%s = %d%n", receiver.getPeerId(), split.receiverAssigned);
        System.out.println();
    }
}
