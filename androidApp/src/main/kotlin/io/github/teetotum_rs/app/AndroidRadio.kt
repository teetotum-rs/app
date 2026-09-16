package io.github.teetotum_rs.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Joins through a network request, which the system confirms in its own dialog. The Knob's
 * network has no internet, so the process is bound to it; otherwise traffic takes mobile data.
 */
class AndroidRadio(context: Context) : Radio {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    override suspend fun join(code: JoinCode) = suspendCancellableCoroutine { continuation ->
        leave()
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(code.ssid)
            .setWpa2Passphrase(code.password)
            .build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val joining = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivity.bindProcessToNetwork(network)
                if (continuation.isActive) continuation.resume(Unit)
            }

            override fun onUnavailable() {
                if (continuation.isActive) {
                    continuation.resumeWithException(JoinFailed("Could not join ${code.ssid}."))
                }
            }

            override fun onLost(network: Network) {
                connectivity.bindProcessToNetwork(null)
            }
        }
        callback = joining
        connectivity.requestNetwork(request, joining, JOIN_TIMEOUT_MS)
        continuation.invokeOnCancellation { leave() }
    }

    override fun leave() {
        val joined = callback ?: return
        callback = null
        connectivity.bindProcessToNetwork(null)
        runCatching { connectivity.unregisterNetworkCallback(joined) }
    }

    private companion object {
        const val JOIN_TIMEOUT_MS = 60_000
    }
}
