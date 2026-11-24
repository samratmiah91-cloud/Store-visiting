package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class ConnectivityObserver(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isCurrentlyOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun observeIsOnline(): Flow<Boolean> = callbackFlow {
        trySend(isCurrentlyOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isCurrentlyOnline())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val valid = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                trySend(valid)
            }

            override fun onLost(network: Network) {
                trySend(isCurrentlyOnline())
            }
        }

        try {
            connectivityManager.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
            trySend(isCurrentlyOnline())
        }

        awaitClose {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }.distinctUntilChanged()
}
