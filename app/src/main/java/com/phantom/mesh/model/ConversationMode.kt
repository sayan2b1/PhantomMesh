package com.phantom.mesh.model

/**
 * Defines the user's selected communication and transport strategy.
 *
 * 1. HYBRID: Both Offline + Online. Does not force radios; shows optional recommendations.
 * 2. ONLINE: Only cloud services (Firebase). Does not force or recommend mesh radios.
 * 3. OFFLINE: Only bluetooth + wifi direct. Enforces mesh radios; strictly does not use cloud.
 */
enum class ConversationMode(
    val title: String,
    val subtitle: String,
    val description: String
) {
    HYBRID(
        title = "Hybrid Mode (Both Offline + Online)",
        subtitle = "Dual-channel: Cloud & Mesh both enabled",
        description = "Uses internet cloud when available, seamlessly switching or bridging to local Bluetooth & Wi-Fi Direct mesh offline. Hardware radios recommended on an optional basis."
    ),
    ONLINE(
        title = "Online mode (only cloud services)",
        subtitle = "Strictly cloud routing via Firebase",
        description = "All messages and calls route strictly through Firebase Cloud. Bluetooth, Wi-Fi, and Location services are never forced, and no radio warnings or recommendations are shown."
    ),
    OFFLINE(
        title = "Offline mode (Only bluetooth + wifi direct services)",
        subtitle = "Strictly local peer-to-peer mesh",
        description = "Strictly relies on direct Bluetooth & Wi-Fi Direct mesh communication. Cloud services are strictly bypassed even if cellular data or internet is connected. Bluetooth, Wi-Fi, and Location are strictly required."
    );

    companion object {
        fun fromString(value: String?): ConversationMode {
            return when (value?.uppercase()) {
                "ONLINE" -> ONLINE
                "OFFLINE" -> OFFLINE
                else -> HYBRID
            }
        }
    }
}

