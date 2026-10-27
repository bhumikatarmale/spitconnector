package campusmesh.model;

/**
 * Message delivery priority levels for CampusMesh.
 * Used by Adaptive Forwarding to calculate utility score P(M).
 */
public enum Priority {
    LOW(0.33),
    MEDIUM(0.67),
    HIGH(1.00);

    private final double weight;

    Priority(double weight) {
        this.weight = weight;
    }

    /**
     * Gets the normalized priority score P(M) in the range [0.0, 1.0].
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Helper to safely parse string into Priority enum.
     */
    public static Priority fromString(String val) {
        if (val == null) return MEDIUM;
        try {
            return Priority.valueOf(val.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
