package com.phantom.mesh.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface

data class NetworkProfile(
    val isWifi: Boolean = false,
    val isCellular: Boolean = false,
    val localIp: String = "",
    val gatewayIp: String = "",
    val subnetPrefix: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "isWifi" to isWifi,
            "isCellular" to isCellular,
            "localIp" to localIp,
            "gatewayIp" to gatewayIp,
            "subnetPrefix" to subnetPrefix
        )
    }

    companion object {
        fun fromMap(map: Map<*, *>?): NetworkProfile {
            if (map == null) return NetworkProfile()
            return NetworkProfile(
                isWifi = map["isWifi"] as? Boolean ?: false,
                isCellular = map["isCellular"] as? Boolean ?: false,
                localIp = map["localIp"] as? String ?: "",
                gatewayIp = map["gatewayIp"] as? String ?: "",
                subnetPrefix = map["subnetPrefix"] as? String ?: ""
            )
        }
    }
}

/**
 * Detects current device network interfaces, distinguishing between
 * Wi-Fi (LAN) and Cellular (Mobile Data), and identifying local subnet/gateway.
 */
object LocalNetworkDetector {
    private const val TAG = "LocalNetworkDetector"

    fun getCurrentProfile(context: Context): NetworkProfile {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(activeNetwork)

        val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        var localIp = ""
        var gatewayIp = ""
        var subnetPrefix = ""

        if (isWifi) {
            localIp = getLocalIpv4Address() ?: ""

            // Extract router gateway IP from WifiManager DHCP info
            try {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val dhcp = wm?.dhcpInfo
                if (dhcp != null && dhcp.gateway != 0) {
                    @Suppress("DEPRECATION")
                    gatewayIp = android.text.format.Formatter.formatIpAddress(dhcp.gateway)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading DHCP gateway IP: ${e.message}")
            }

            if (localIp.isNotBlank()) {
                val parts = localIp.split(".")
                if (parts.size == 4) {
                    subnetPrefix = "${parts[0]}.${parts[1]}.${parts[2]}."
                }
            }

            if (gatewayIp.isBlank() && subnetPrefix.isNotBlank()) {
                gatewayIp = "${subnetPrefix}1" // Most common default router gateway
            }
        }

        return NetworkProfile(
            isWifi = isWifi,
            isCellular = isCellular,
            localIp = localIp,
            gatewayIp = gatewayIp,
            subnetPrefix = subnetPrefix
        )
    }

    /**
     * Determines whether two peers are connected to the exact same Wi-Fi network.
     */
    fun isSameWifiNetwork(local: NetworkProfile, remote: NetworkProfile): Boolean {
        if (!local.isWifi || !remote.isWifi) return false
        if (local.localIp.isBlank() || remote.localIp.isBlank()) return false
        if (local.localIp == remote.localIp) return false // Self

        // Match on gateway IP if present
        if (local.gatewayIp.isNotBlank() && remote.gatewayIp.isNotBlank()) {
            if (local.gatewayIp == remote.gatewayIp) return true
        }

        // Match on /24 subnet prefix (e.g. 192.168.1.xxx)
        if (local.subnetPrefix.isNotBlank() && remote.subnetPrefix.isNotBlank()) {
            if (local.subnetPrefix == remote.subnetPrefix) return true
        }

        return false
    }

    private fun getLocalIpv4Address(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                // Prioritize wlan0 or Wi-Fi wireless interfaces
                val name = iface.name.lowercase()
                val isWireless = name.startsWith("wlan") || name.startsWith("eth") || name.contains("wifi")

                val addresses = iface.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            if (isWireless) return host
                        }
                    }
                }
            }

            // Fallback: any non-loopback IPv4
            val fallbackInterfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in fallbackInterfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                for (addr in iface.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error finding local IPv4 address: ${e.message}")
        }
        return null
    }
}
