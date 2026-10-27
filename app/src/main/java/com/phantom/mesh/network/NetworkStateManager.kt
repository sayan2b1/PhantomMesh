package com.phantom.mesh.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Continuously monitors internet connectivity across Wi-Fi, Mobile Data (Cellular),
 * Ethernet, and VPN, and drives the seamless handoff between cloud (Firebase)
 * and mesh (Nearby Connections) modes.
 */
@Singleton
class NetworkStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "NetworkStateManager"

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
     * Backed by the permanent default network listener.
     */
    val connectivityFlow: Flow<Boolean> = _isInternetAvailable.asStateFlow()

    init {
        // Initial state evaluation on startup
        updateNetworkState(connectivityManager.activeNetwork)

        // Register default network callback for real-time tracking across Wi-Fi & Cellular
        try {
            val defaultCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "Default network available: $network")
                    updateNetworkState(network)
                }

                override fun onLost(network: Network) {
                    Log.i(TAG, "Network lost: $network. Checking fallback active network...")
                    // Check if another network (e.g. mobile data after Wi-Fi lost) is active
                    updateNetworkState(connectivityManager.activeNetwork)
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val online = isNetworkConnected(caps)
                    val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    Log.i(TAG, "Network caps changed: online=$online (Cellular=$isCellular, Wi-Fi=$isWifi)")
                    
                    _isInternetAvailable.value = online
                    if (online) {
                        _activeTransport.value = NetworkTransport.Cloud
                    } else if (_activeTransport.value == NetworkTransport.Cloud) {
                        _activeTransport.value = NetworkTransport.Offline
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.registerDefaultNetworkCallback(defaultCallback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(request, defaultCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register default network callback: ${e.message}", e)
        }
    }

    /**
     * Evaluates whether a network or set of capabilities has active internet access.
     * Supports both Wi-Fi and Mobile Data (Cellular) without strictly requiring
     * NET_CAPABILITY_VALIDATED (which can be delayed or fail captive-portal pings on cellular).
     */
    fun isNetworkConnected(caps: NetworkCapabilities?): Boolean {
        if (caps == null) return false
        val hasInternetCapability = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasValidTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        return hasInternetCapability && hasValidTransport
    }

    private fun updateNetworkState(network: Network?) {
        val caps = network?.let { connectivityManager.getNetworkCapabilities(it) }
            ?: connectivityManager.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val online = isNetworkConnected(caps)
        Log.i(TAG, "Updating network state: online=$online (activeNetwork=$network)")
        
        _isInternetAvailable.value = online
        if (online) {
            _activeTransport.value = NetworkTransport.Cloud
        } else {
            if (_activeTransport.value == NetworkTransport.Cloud) {
                _activeTransport.value = NetworkTransport.Offline
            }
        }
    }

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
        return isNetworkConnected(capabilities)
    }
}
