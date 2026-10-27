package org.campusmesh.android.model

/**
 * Message delivery priority levels for CampusMesh Android.
 * Normalized scores match the core Java DTN engine.
 */
enum class Priority(val weight: Double) {
    LOW(0.33),
    MEDIUM(0.67),
    HIGH(1.00);

    companion object {
        fun fromString(value: String?): Priority {
            if (value.isNullOrBlank()) return MEDIUM
            return try {
                valueOf(value.trim().uppercase())
            } catch (e: IllegalArgumentException) {
                MEDIUM
            }
        }
    }
}
