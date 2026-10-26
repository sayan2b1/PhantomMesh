package com.phantom.mesh.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Continuously monitors internet connectivity and drives the seamless
 * handoff between cloud (Firebase) and mesh (Nearby Connections) modes.
 *
 * ## Handoff Logic
 *
 * ```
 * Internet AVAILABLE  → NetworkTransport.Cloud
 *                        • Messages route through Firestore
 *                        • Nearby Connections advertising STOPS
 *
 * Internet LOST       → NetworkTransport.Offline (briefly)
 *                        • Nearby Connections advertising + discovery START automatically
 *                        • Once a peer is found:
 *                          → Wi-Fi Direct bandwidth confirmed → NetworkTransport.WifiDirect
 *                          → BLE-only connection              → NetworkTransport.Bluetooth
 * ```
 *
 * The [MeshConnectionManager] observes [isInternetAvailable] to trigger
 * the automatic Nearby Connections start/stop cycle.
 */
@Singleton
class NetworkStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isInternetAvailable = MutableStateFlow(checkCurrentConnectivity())
    val isInternetAvailable: StateFlow<Boolean> = _isInternetAvailable.asStateFlow()

    private val _activeTransport = MutableStateFlow<NetworkTransport>(
        if (checkCurrentConnectivity()) NetworkTransport.Cloud else NetworkTransport.Offline
    )
    val activeTransport: StateFlow<NetworkTransport> = _activeTransport.asStateFlow()

    /**
     * Reactive flow of internet connectivity changes.
     * Uses the system [ConnectivityManager.NetworkCallback] under the hood.
     */
    val connectivityFlow: Flow<Boolean> = callbackFlow {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isInternetAvailable.value = true
                _activeTransport.value = NetworkTransport.Cloud
                trySend(true)
            }

            override fun onLost(network: Network) {
                _isInternetAvailable.value = false
                _activeTransport.value = NetworkTransport.Offline
                trySend(false)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                caps: NetworkCapabilities
            ) {
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                _isInternetAvailable.value = hasInternet
                if (hasInternet) {
                    _activeTransport.value = NetworkTransport.Cloud
                }
                trySend(hasInternet)
            }
        }

        connectivityManager.registerNetworkCallback(networkRequest, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    /**
     * Called by [MeshConnectionManager] when a Nearby Connections peer
     * is established, to update the active transport to the correct type.
     */
    fun updateMeshTransport(transport: NetworkTransport) {
        if (!_isInternetAvailable.value) {
            _activeTransport.value = transport
        }
    }

    /**
     * Synchronously check current connectivity state.
     * Used for initial state on startup.
     */
    private fun checkCurrentConnectivity(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
