package com.jarvis.assistant.data.monitors

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkInfo(
    val connected: Boolean,
    val type: String,        // Wi-Fi / Mobile / Ethernet / None
    val wifiEnabled: Boolean, val mobileData: Boolean,
)

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val _state = MutableStateFlow(NetworkInfo(false, "None", false, false))
    val state: StateFlow<NetworkInfo> = _state

    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
    }

    init {
        cm.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
            callback
        )
        refresh()
    }

    fun refreshOnce() = refresh()

    private fun refresh() {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val type = when {
            caps == null -> "None"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile (${caps.linkDownstreamBandwidthKbps / 1000} Mbps down)"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Other"
        }
        val wifiOn = cm.getNetworkInfo(android.net.NetworkCapabilities.TRANSPORT_WIFI) != null
        _state.value = NetworkInfo(connected, type, wifiOn, type.startsWith("Mobile"))
    }
}
