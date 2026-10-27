package campusmesh.routing;

import campusmesh.buffer.MessageBuffer;
import campusmesh.model.Message;
import campusmesh.model.Peer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Adaptive Forwarding Strategy:
 * Calculates dynamic utility scores U(M) based on:
 * - Priority weight P(M)
 * - TTL urgency T(M)
 * - Local buffer pressure B(M)
 *
 * Formula:
 *   U(M) = wP * P(M) + wT * T(M) + wB * B(M)
 *   where wP + wT + wB = 1.0
 *
 * Uses controlled replication budget (Spray-and-Wait semantics)
 * and strict deterministic tie-breaking.
 */
public class AdaptiveRouter implements RoutingStrategy {

    public static final double DEFAULT_W_P = 0.40;
    public static final double DEFAULT_W_T = 0.40;
    public static final double DEFAULT_W_B = 0.20;

    private final double wP;
    private final double wT;
    private final double wB;

    /**
     * Evaluation record holding utility breakdown for a single candidate message.
     */
    public static class UtilityScore {
        public final Message message;
        public final double p;
        public final double t;
        public final double b;
        public final double utility;
        public int rank;
        public boolean selected;
        public String selectionReason;

        public UtilityScore(Message message, double p, double t, double b, double utility) {
            this.message = message;
            this.p = p;
            this.t = t;
            this.b = b;
            this.utility = utility;
            this.selected = false;
            this.selectionReason = "";
        }

        public String toJson() {
            return String.format(Locale.US,
                    "{\"messageId\":\"%s\",\"p\":%.3f,\"t\":%.3f,\"b\":%.3f,\"utility\":%.4f,\"rank\":%d,\"selected\":%b,\"reason\":\"%s\"}",
                    message.getMessageId(), p, t, b, utility, rank, selected, selectionReason);
        }
    }

    public AdaptiveRouter() {
        this(DEFAULT_W_P, DEFAULT_W_T, DEFAULT_W_B);
    }

    public AdaptiveRouter(double wP, double wT, double wB) {
        if (wP < 0.0 || wT < 0.0 || wB < 0.0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        double sum = wP + wT + wB;
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalArgumentException(String.format("Weights must sum to 1.0! Given sum: %.3f", sum));
        }
        this.wP = wP;
        this.wT = wT;
        this.wB = wB;
    }

    @Override
    public String getName() {
        return "ADAPTIVE";
    }

    /**
     * Calculates Priority factor P(M):
     * LOW = 0.33, MEDIUM = 0.67, HIGH = 1.00
     */
    public double calculateP(Message message) {
        return message.getPriority().getWeight();
    }

    /**
     * Calculates TTL urgency factor T(M):
     * T(M) = 1 - (remainingTTL / originalTTL)
     * Clamped to [0.0, 1.0].
     * Fresh message -> T near 0.0 (low urgency)
     * Approaching expiry -> T near 1.0 (high urgency)
     */
    public double calculateT(Message message) {
        long remaining = message.getRemainingTTL();
        long original = message.getOriginalTTL();
        if (original <= 0 || remaining <= 0) {
            return 1.0;
        }
        double ratio = (double) remaining / (double) original;
        double urgency = 1.0 - ratio;
        return Math.max(0.0, Math.min(1.0, urgency));
    }

    /**
     * Calculates Buffer pressure factor B(M):
     * B(M) = usedBuffer / bufferCapacity
     * Clamped to [0.0, 1.0].
     */
    public double calculateB(MessageBuffer buffer) {
        return buffer.getBufferPressure();
    }

    /**
     * Calculates total utility score U(M).
     */
    public double calculateUtility(Message message, MessageBuffer buffer) {
        double p = calculateP(message);
        double t = calculateT(message);
        double b = calculateB(buffer);
        return (wP * p) + (wT * t) + (wB * b);
    }

    /**
     * Evaluates all buffer messages, computes scores, and ranks them.
     */
    public List<UtilityScore> evaluateMessages(
            Peer localPeer,
            Peer remotePeer,
            MessageBuffer buffer,
            Set<String> remoteKnownMessageIds) {

        List<UtilityScore> scores = new ArrayList<>();
        if (buffer == null || remotePeer == null) {
            return scores;
        }
        double b = calculateB(buffer);

        for (Message msg : buffer.getAllMessages()) {
            if (msg.isExpired()) {
                continue;
            }
            if (remoteKnownMessageIds != null && remoteKnownMessageIds.contains(msg.getMessageId())) {
                continue;
            }

            double p = calculateP(msg);
            double t = calculateT(msg);
            double u = (wP * p) + (wT * t) + (wB * b);

            scores.add(new UtilityScore(msg, p, t, b, u));
        }

        // Sort descending based on strict tie-breaking rules:
        // 1. Higher U
        // 2. Higher T
        // 3. Higher P
        // 4. Earlier expiry
        // 5. Smaller messageId
        scores.sort(new Comparator<UtilityScore>() {
            @Override
            public int compare(UtilityScore s1, UtilityScore s2) {
                int cmpU = Double.compare(s2.utility, s1.utility);
                if (cmpU != 0) return cmpU;

                int cmpT = Double.compare(s2.t, s1.t);
                if (cmpT != 0) return cmpT;

                int cmpP = Double.compare(s2.p, s1.p);
                if (cmpP != 0) return cmpP;

                int cmpExpiry = s1.message.getExpiresAt().compareTo(s2.message.getExpiresAt());
                if (cmpExpiry != 0) return cmpExpiry;

                return s1.message.getMessageId().compareTo(s2.message.getMessageId());
            }
        });

        // Assign ranks and check controlled copy budget (Spray-and-Wait semantics)
        for (int i = 0; i < scores.size(); i++) {
            UtilityScore item = scores.get(i);
            item.rank = i + 1;

            boolean isDirectDestination = remotePeer.getPeerId().equalsIgnoreCase(item.message.getDestinationId());
            if (isDirectDestination) {
                item.selected = true;
                item.selectionReason = "Destination encountered";
            } else if (item.message.getCopyTokens() > 1) {
                item.selected = true;
                item.selectionReason = "Spray phase active (tokens > 1)";
            } else {
                item.selected = false;
                item.selectionReason = "Wait phase (copyTokens = 1, relay cannot replicate)";
            }
        }

        return scores;
    }

    @Override
    public List<Message> selectMessages(
            Peer localPeer,
            Peer remotePeer,
            MessageBuffer buffer,
            Set<String> remoteKnownMessageIds) {

        List<UtilityScore> evaluations = evaluateMessages(localPeer, remotePeer, buffer, remoteKnownMessageIds);
        List<Message> selected = new ArrayList<>();
        for (UtilityScore score : evaluations) {
            if (score.selected) {
                selected.add(score.message);
            }
        }
        return selected;
    }

    /**
     * Formats and prints adaptive routing evaluation matching project specifications.
     */
    public void printEvaluationDemo(Peer localPeer, Peer remotePeer, MessageBuffer buffer, Set<String> remoteKnown) {
        List<UtilityScore> evaluations = evaluateMessages(localPeer, remotePeer, buffer, remoteKnown);

        System.out.println("=== ADAPTIVE ROUTING ===");
        System.out.printf("Local Peer: %s | Remote Peer: %s%n", localPeer.getPeerId(), remotePeer.getPeerId());
        System.out.printf(Locale.US, "Weights: wP=%.2f, wT=%.2f, wB=%.2f%n%n", wP, wT, wB);

        System.out.printf("%-10s%-8s%-8s%-8s%-10s%n", "Message", "P", "T", "B", "Utility");
        System.out.println("------------------------------------------------");

        for (UtilityScore score : evaluations) {
            System.out.printf(Locale.US, "%-10s%-8.2f%-8.2f%-8.2f%-10.4f%n",
                    score.message.getMessageId(),
                    score.p,
                    score.t,
                    score.b,
                    score.utility);
        }

        System.out.println("\nRanking:");
        for (UtilityScore score : evaluations) {
            System.out.printf("  %d. %s (Utility: %.4f, Tokens: %d)%n",
                    score.rank, score.message.getMessageId(), score.utility, score.message.getCopyTokens());
        }

        System.out.println("\nSelected:");
        boolean anySelected = false;
        for (UtilityScore score : evaluations) {
            if (score.selected) {
                System.out.printf("  %s [%s]%n", score.message.getMessageId(), score.selectionReason);
                anySelected = true;
            }
        }
        if (!anySelected) {
            System.out.println("  (None selected)");
        }
        System.out.println();
    }

    public double getWP() { return wP; }
    public double getWT() { return wT; }
    public double getWB() { return wB; }
}
