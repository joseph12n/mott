package dev.mott.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

// Best-effort online check for deciding whether to drain the sync queue
// right after enqueueing. Offline-first still holds: a false negative only
// defers the drain to the next pass, never drops the op.
object NetStatus {
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return false
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
