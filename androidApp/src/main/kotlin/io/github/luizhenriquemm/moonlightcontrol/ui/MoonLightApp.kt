package io.github.luizhenriquemm.moonlightcontrol.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.luizhenriquemm.moonlightcontrol.LocalNetworkAccess
import io.github.luizhenriquemm.moonlightcontrol.shared.ServerAddress

/**
 * Milestone 1 navigation: a connect screen (typed address) and the console. The host list replaces
 * the connect screen in milestone 2.
 */
@Composable
fun MoonLightApp() {
    val context = LocalContext.current
    // The opened server's root URL; null = connect screen. Survives process recreation.
    var consoleUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var localNetworkDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        localNetworkDenied = !granted
        if (granted) consoleUrl = pendingUrl
        pendingUrl = null
    }

    val url = consoleUrl
    if (url != null) {
        ConsoleScreen(url = url, onExit = { consoleUrl = null })
    } else {
        ConnectScreen(
            localNetworkDenied = localNetworkDenied,
            onOpen = { address: ServerAddress ->
                val permission = LocalNetworkAccess.permission
                if (permission == null || LocalNetworkAccess.isGranted(context)) {
                    localNetworkDenied = false
                    consoleUrl = address.rootUrl
                } else {
                    pendingUrl = address.rootUrl
                    permissionLauncher.launch(permission)
                }
            },
            onOpenAppSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    }
}
