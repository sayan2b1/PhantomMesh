package com.phantom.mesh.network

/**
 * Represents the active transport layer for the dual-network engine.
 *
 * The UI reads this to display the correct network indicator icon
 * in the top app bar (Cloud ☁, Wi-Fi Direct 📶, or Bluetooth 🔵).
 */
sealed class NetworkTransport {
    /** Internet available — routing through Firebase */
    data object Cloud : NetworkTransport()

    /** No internet — connected to peers via Wi-Fi Direct (high-bandwidth) */
    data object WifiDirect : NetworkTransport()

    /** No internet — connected to peers via Bluetooth (handshake/low-bandwidth) */
    data object Bluetooth : NetworkTransport()

    /** Completely offline — no internet and no nearby peers discovered */
    data object Offline : NetworkTransport()

    val displayName: String
        get() = when (this) {
            Cloud -> "Cloud"
            WifiDirect -> "Wi-Fi Direct"
            Bluetooth -> "Bluetooth"
            Offline -> "Offline"
        }
}
