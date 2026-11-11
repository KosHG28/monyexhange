package app.dirhamrub.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Какая сейчас сеть — для настройки «Только по Wi-Fi». */
object NetworkState {
    /** Безлимитная сеть (Wi-Fi, Ethernet): система не считает её платной. Не знаем — считаем платной. */
    fun unmetered(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)
}
