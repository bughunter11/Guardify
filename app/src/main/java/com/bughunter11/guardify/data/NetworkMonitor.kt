package com.bughunter11.guardify.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class NetType { WIFI, MOBILE, OFFLINE }

/** Abhi phone kis network par hai, live. WiFi/mobile badalte hi UI apne aap update hoti hai. */
class NetworkMonitor(context: Context) {

    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val _type = MutableStateFlow(current())
    val type: StateFlow<NetType> = _type

    private val _roaming = MutableStateFlow(currentRoaming())

    /** Mobile data par ho aur network roaming par ho to true. Live badalta hai. */
    val roaming: StateFlow<Boolean> = _roaming

    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _type.value = classify(caps)
            _roaming.value = RoamingMode.isRoaming(caps)
        }

        override fun onLost(network: Network) {
            _type.value = NetType.OFFLINE
            _roaming.value = false
        }
    }

    fun start() {
        if (registered) return
        registered = runCatching { cm.registerDefaultNetworkCallback(callback) }.isSuccess
    }

    fun stop() {
        if (!registered) return
        runCatching { cm.unregisterNetworkCallback(callback) }
        registered = false
    }

    private fun current(): NetType {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return NetType.OFFLINE
        return classify(caps)
    }

    private fun currentRoaming(): Boolean {
        val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
        return RoamingMode.isRoaming(caps)
    }

    private fun classify(caps: NetworkCapabilities): NetType = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetType.MOBILE
        else -> NetType.OFFLINE
    }
}
