package br.com.moonaudio.moonlightcontrol.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.moonaudio.moonlightcontrol.LocalNetworkAccess
import br.com.moonaudio.moonlightcontrol.data.RecentServersStore
import br.com.moonaudio.moonlightcontrol.data.ServerDiscovery
import br.com.moonaudio.moonlightcontrol.data.recentServersDataStore
import br.com.moonaudio.moonlightcontrol.shared.ServerAddress
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Saves a nullable [ServerAddress] across configuration changes and process recreation. */
private val AddressSaver = Saver<ServerAddress?, List<Any>>(
    save = { address -> address?.let { listOf(it.https, it.host, it.port) } ?: emptyList() },
    restore = { parts ->
        if (parts.size == 3) ServerAddress.fromParts(parts[0] as Boolean, parts[1] as String, parts[2] as Int) else null
    },
)

/**
 * Navigation between the start screen (recent servers, servers found on the network, typed
 * address) and the console. Named hosts / editing / auto-resume come later in milestone 2.
 */
@Composable
fun MoonLightApp() {
    val context = LocalContext.current
    val store = remember { RecentServersStore(context.applicationContext.recentServersDataStore) }
    val discovery = remember { ServerDiscovery.create(context) }
    val recentServers by store.servers.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    // The opened server; null = start screen. Survives process recreation.
    var consoleAddress by rememberSaveable(stateSaver = AddressSaver) { mutableStateOf(null) }
    // What to do once the local network permission is granted: open a server, or scan.
    var pendingAddress by rememberSaveable(stateSaver = AddressSaver) { mutableStateOf(null) }
    var pendingScan by rememberSaveable { mutableStateOf(false) }
    var localNetworkDenied by rememberSaveable { mutableStateOf(false) }

    // Discovery results live as long as the app does (also while a server is open).
    var discoveryUi by remember { mutableStateOf(DiscoveryUi()) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun startScan() {
        val ranges = discovery.ranges()
        if (ranges.isEmpty()) {
            discoveryUi = DiscoveryUi(status = DiscoveryUi.Status.NO_NETWORK)
            return
        }
        scanJob?.cancel()
        discoveryUi = DiscoveryUi(status = DiscoveryUi.Status.SCANNING, ranges = ranges)
        scanJob = scope.launch {
            val found = discovery.scan(
                onFound = { server -> discoveryUi = discoveryUi.copy(found = discoveryUi.found + server) },
            )
            discoveryUi = discoveryUi.copy(status = DiscoveryUi.Status.DONE, found = found)
        }
    }

    fun open(target: ServerAddress) {
        scanJob?.cancel() // leave the network to the console
        if (discoveryUi.status == DiscoveryUi.Status.SCANNING) {
            discoveryUi = discoveryUi.copy(status = DiscoveryUi.Status.IDLE)
        }
        consoleAddress = target
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        localNetworkDenied = !granted
        if (granted) {
            pendingAddress?.let(::open)
            if (pendingScan) startScan()
        }
        pendingAddress = null
        pendingScan = false
    }

    /** Runs [action] now if local network access is allowed, else asks first (Android 17+). */
    fun withLocalNetwork(onAsk: () -> Unit, action: () -> Unit) {
        val permission = LocalNetworkAccess.permission
        if (permission == null || LocalNetworkAccess.isGranted(context)) {
            localNetworkDenied = false
            action()
        } else {
            onAsk()
            permissionLauncher.launch(permission)
        }
    }

    val address = consoleAddress
    if (address != null) {
        ConsoleScreen(
            url = address.rootUrl,
            onExit = { consoleAddress = null },
            // A server goes to the recent list once its page actually loaded.
            onLoaded = { scope.launch { store.recordSuccess(address) } },
        )
    } else {
        ConnectScreen(
            recentServers = recentServers,
            onRemoveRecent = { scope.launch { store.remove(it) } },
            discovery = discoveryUi,
            onFindServers = { withLocalNetwork(onAsk = { pendingScan = true }) { startScan() } },
            localNetworkDenied = localNetworkDenied,
            onOpen = { target -> withLocalNetwork(onAsk = { pendingAddress = target }) { open(target) } },
            onOpenAppSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null)),
                )
            },
        )
    }
}
