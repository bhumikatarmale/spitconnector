package campusmesh.routing;

import campusmesh.buffer.MessageBuffer;
import campusmesh.model.Message;
import campusmesh.model.Peer;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * RoutingEngine acts as the central coordinator between opportunistic contact events
 * and the specific routing algorithms.
 *
 * Architecture Flow:
 *   Contact Event -> RoutingEngine -> RoutingStrategy -> Selected Messages
 *
 * Note: Per architectural separation rules, RoutingEngine does NOT implement
 * routing algorithms itself; it delegates all message selection logic to the
 * plugged-in RoutingStrategy implementation.
 */
public class RoutingEngine {

    public enum StrategyType {
        EPIDEMIC,
        SPRAY_AND_WAIT,
        ADAPTIVE
    }

    private RoutingStrategy strategy;

    /**
     * Constructs RoutingEngine with an initial strategy.
     */
    public RoutingEngine(RoutingStrategy strategy) {
        setStrategy(strategy);
    }

    /**
     * Constructs RoutingEngine by strategy enum type.
     */
    public RoutingEngine(StrategyType type) {
        setStrategyByType(type);
    }

    public RoutingStrategy getStrategy() {
        return strategy;
    }

    public void setStrategy(RoutingStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Routing strategy cannot be null");
        }
        this.strategy = strategy;
    }

    public void setStrategyByType(StrategyType type) {
        switch (type) {
            case EPIDEMIC:
                this.strategy = new EpidemicRouter();
                break;
            case SPRAY_AND_WAIT:
                this.strategy = new SprayAndWaitRouter();
                break;
            case ADAPTIVE:
                this.strategy = new AdaptiveRouter();
                break;
            default:
                throw new IllegalArgumentException("Unknown strategy type: " + type);
        }
    }

    /**
     * Executes the routing algorithm on contact encounter.
     *
     * @param localPeer             Node performing routing
     * @param remotePeer            Contact node
     * @param buffer                Local message buffer
     * @param remoteKnownMessageIds Set of messages remote node already has
     * @return List of messages decided to be forwarded
     */
    public List<Message> route(
            Peer localPeer,
            Peer remotePeer,
            MessageBuffer buffer,
            Set<String> remoteKnownMessageIds) {

        if (strategy == null) {
            throw new IllegalStateException("No routing strategy configured in RoutingEngine");
        }

        return strategy.selectMessages(localPeer, remotePeer, buffer, remoteKnownMessageIds);
    }

    /**
     * Overloaded route method when remote known IDs are empty or unknown.
     */
    public List<Message> route(Peer localPeer, Peer remotePeer, MessageBuffer buffer) {
        return route(localPeer, remotePeer, buffer, Collections.emptySet());
    }

    @Override
    public String toString() {
        return "RoutingEngine[Strategy=" + (strategy != null ? strategy.getName() : "None") + "]";
    }
}
