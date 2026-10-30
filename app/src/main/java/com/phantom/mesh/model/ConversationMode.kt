package com.phantom.mesh.model

/**
 * Defines the user's selected communication and transport strategy.
 *
 * 1. HYBRID: Dual-channel mode balancing Firebase Cloud with decentralized mesh.
 * 2. ONLINE: Cloud-exclusive routing via Firebase infrastructure. Radios optional & quiet.
 * 3. OFFLINE: Local peer-to-peer mesh exclusively. Radios mandatory; cloud strictly bypassed.
 */
enum class ConversationMode(
    val title: String,
    val subtitle: String,
    val description: String
) {
    HYBRID(
        title = "Hybrid Mode (Offline + Online)",
        subtitle = "Adaptive Cloud & Mesh Multi-Transport",
        description = "Intelligently balances Firebase Cloud with decentralized Bluetooth and Wi-Fi Direct mesh networking. Hardware radios (Bluetooth, Wi-Fi, Location) are recommended as an optional enhancement for offline fallback."
    ),
    ONLINE(
        title = "Online Mode (Cloud Services Only)",
        subtitle = "High-Speed Firebase Cloud Routing",
        description = "Exclusively routes all messaging and voice/video calling through secure Firebase Cloud infrastructure. Mesh radios are completely optional, and all hardware warnings or prompts are silenced."
    ),
    OFFLINE(
        title = "Offline Mode (Bluetooth & Wi-Fi Direct Mesh)",
        subtitle = "Decentralized Zero-Infrastructure Mesh",
        description = "Strictly limits communication to peer-to-peer Bluetooth and Wi-Fi Direct mesh protocols without contacting cloud servers, even when an active internet connection is present. Bluetooth, Wi-Fi, and Location services are mandatory."
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
