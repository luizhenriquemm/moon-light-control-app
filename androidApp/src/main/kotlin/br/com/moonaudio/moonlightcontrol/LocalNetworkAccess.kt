package br.com.moonaudio.moonlightcontrol

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) blocks traffic to the local network — the WebView's included — for apps
 * targeting 37+, unless the user grants `ACCESS_LOCAL_NETWORK` ("Nearby devices" group). Older
 * Android versions allow it with the INTERNET permission alone.
 * See https://developer.android.com/privacy-and-security/local-network-permission
 */
object LocalNetworkAccess {
    private const val API_ANDROID_17 = 37

    /** The runtime permission to request on this device, or null when none is needed. */
    val permission: String?
        get() = if (Build.VERSION.SDK_INT >= API_ANDROID_17) {
            Manifest.permission.ACCESS_LOCAL_NETWORK
        } else {
            null
        }

    fun isGranted(context: Context): Boolean {
        val permission = permission ?: return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
