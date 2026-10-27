package campusmesh.routing;

import campusmesh.buffer.MessageBuffer;
import campusmesh.model.Message;
import campusmesh.model.Peer;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Common interface for Delay-Tolerant Networking (DTN) routing strategies in CampusMesh.
 *
 * Separation of Concerns:
 * - RoutingStrategy decides WHAT messages should be forwarded to a contact peer.
 * - TCPManager decides HOW messages are physically transferred over the socket.
 */
public interface RoutingStrategy {

    /**
     * Returns the human-readable identifier of this routing strategy.
     */
    String getName();

    /**
     * Inspects the local message buffer and selects messages eligible for forwarding
     * to the remote contact peer.
     *
     * @param localPeer              Local node executing the routing decision
     * @param remotePeer             Remote contact node currently encountered
     * @param buffer                 Local message storage buffer
     * @param remoteKnownMessageIds  Set of message IDs already in possession of the remote peer
     * @return Ordered list of messages selected for transmission
     */
    List<Message> selectMessages(
            Peer localPeer,
            Peer remotePeer,
            MessageBuffer buffer,
            Set<String> remoteKnownMessageIds
    );

    /**
     * Overloaded convenience method assuming remote peer has no prior knowledge.
     */
    default List<Message> selectMessages(
            Peer localPeer,
            Peer remotePeer,
            MessageBuffer buffer
    ) {
        return selectMessages(localPeer, remotePeer, buffer, Collections.emptySet());
    }
}
