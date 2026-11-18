package br.com.moonaudio.moonlightcontrol.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.moonaudio.moonlightcontrol.R
import br.com.moonaudio.moonlightcontrol.shared.DiscoveredServer
import br.com.moonaudio.moonlightcontrol.shared.ParseError
import br.com.moonaudio.moonlightcontrol.shared.ParseResult
import br.com.moonaudio.moonlightcontrol.shared.RecentServer
import br.com.moonaudio.moonlightcontrol.shared.ServerAddress
import br.com.moonaudio.moonlightcontrol.shared.ServerHealth

object ConnectScreenTags {
    const val ADDRESS = "connect.address"
    const val OPEN = "connect.open"
    const val ERROR = "connect.error"
    const val LOCAL_NETWORK_DENIED = "connect.localNetworkDenied"
    const val RECENT_TITLE = "connect.recent.title"
    fun recent(address: ServerAddress) = "connect.recent.${address.origin}"
    fun recentRemove(address: ServerAddress) = "connect.recent.remove.${address.origin}"
    const val DISCOVER = "connect.discover"
    const val DISCOVER_PROGRESS = "connect.discover.progress"
    const val DISCOVER_NONE = "connect.discover.none"
    const val DISCOVER_NO_NETWORK = "connect.discover.noNetwork"
    fun found(address: ServerAddress) = "connect.found.${address.origin}"
    const val ABOUT = "connect.about"
    const val ABOUT_DIALOG = "connect.about.dialog"
}

/** What the "Servers on this network" section shows. */
data class DiscoveryUi(
    val status: Status = Status.IDLE,
    /** Ranges being searched, e.g. `10.1.1.0/24`. */
    val ranges: List<String> = emptyList(),
    val found: List<DiscoveredServer> = emptyList(),
) {
    enum class Status { IDLE, SCANNING, DONE, NO_NETWORK }
}

/**
 * Start screen: the recently used servers (one tap opens them; the login cookie is kept by the
 * WebView, so no new login is needed), servers found on the local network, and a field to type
 * another address.
 */
@Composable
fun ConnectScreen(
    onOpen: (ServerAddress) -> Unit,
    modifier: Modifier = Modifier,
    recentServers: List<RecentServer> = emptyList(),
    onRemoveRecent: (ServerAddress) -> Unit = {},
    discovery: DiscoveryUi = DiscoveryUi(),
    onFindServers: () -> Unit = {},
    localNetworkDenied: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
    now: Long = System.currentTimeMillis(),
) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<ParseError?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        when (val result = ServerAddress.parse(input)) {
            is ParseResult.Valid -> {
                error = null
                onOpen(result.address)
            }
            is ParseResult.Invalid -> error = result.error
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

                if (recentServers.isNotEmpty()) {
                    Text(
                        stringResource(R.string.recent_title),
                        modifier = Modifier.testTag(ConnectScreenTags.RECENT_TITLE),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Column(
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recentServers.forEach { recent ->
                            ServerRow(
                                title = recent.address.label,
                                subtitle = stringResource(
                                    R.string.recent_last_connected,
                                    relativeTime(recent.lastConnectedAt, now),
                                ),
                                tag = ConnectScreenTags.recent(recent.address),
                                onOpen = { onOpen(recent.address) },
                                removeTag = ConnectScreenTags.recentRemove(recent.address),
                                onRemove = { onRemoveRecent(recent.address) },
                            )
                        }
                    }
                }

                DiscoverySection(discovery = discovery, onFindServers = onFindServers, onOpen = onOpen)

                Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.titleMedium)

                val currentError = error
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        error = null
                    },
                    modifier = Modifier
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .testTag(ConnectScreenTags.ADDRESS),
                    label = { Text(stringResource(R.string.connect_address_label)) },
                    placeholder = { Text(stringResource(R.string.connect_address_hint)) },
                    singleLine = true,
                    isError = currentError != null,
                    supportingText = currentError?.let { err ->
                        {
                            Text(
                                stringResource(err.messageRes()),
                                modifier = Modifier.testTag(ConnectScreenTags.ERROR),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                )

                Button(onClick = ::submit, modifier = Modifier.testTag(ConnectScreenTags.OPEN)) {
                    Text(stringResource(R.string.connect_open))
                }

                if (localNetworkDenied) {
                    Text(
                        stringResource(R.string.local_network_denied),
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .testTag(ConnectScreenTags.LOCAL_NETWORK_DENIED),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    OutlinedButton(onClick = onOpenAppSettings) {
                        Text(stringResource(R.string.open_app_settings))
                    }
                }
            }
            // Drawn last, over the scrolling content.
            IconButton(
                onClick = { showAbout = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp)
                    .testTag(ConnectScreenTags.ABOUT),
            ) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.about))
            }
        }
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

@Composable
private fun DiscoverySection(
    discovery: DiscoveryUi,
    onFindServers: () -> Unit,
    onOpen: (ServerAddress) -> Unit,
) {
    val scanning = discovery.status == DiscoveryUi.Status.SCANNING
    Text(stringResource(R.string.discover_title), style = MaterialTheme.typography.titleMedium)
    Column(
        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onFindServers,
            enabled = !scanning,
            modifier = Modifier.testTag(ConnectScreenTags.DISCOVER),
        ) {
            Text(stringResource(R.string.discover_button))
        }
        when (discovery.status) {
            DiscoveryUi.Status.SCANNING -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().testTag(ConnectScreenTags.DISCOVER_PROGRESS),
                )
                Text(
                    stringResource(R.string.discover_scanning, discovery.ranges.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DiscoveryUi.Status.DONE -> if (discovery.found.isEmpty()) {
                Text(
                    stringResource(R.string.discover_none),
                    modifier = Modifier.testTag(ConnectScreenTags.DISCOVER_NONE),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            DiscoveryUi.Status.NO_NETWORK -> Text(
                stringResource(R.string.discover_no_network),
                modifier = Modifier.testTag(ConnectScreenTags.DISCOVER_NO_NETWORK),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            DiscoveryUi.Status.IDLE -> Unit
        }
        discovery.found.forEach { server ->
            ServerRow(
                title = server.address.label,
                subtitle = stringResource(R.string.discover_version, server.health.version),
                tag = ConnectScreenTags.found(server.address),
                onOpen = { onOpen(server.address) },
            )
        }
    }
}

@Composable
private fun ServerRow(
    title: String,
    subtitle: String,
    tag: String,
    onOpen: () -> Unit,
    removeTag: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onOpen)
                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.testTag(removeTag.orEmpty())) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recent_remove, title))
                }
            }
        }
    }
}

/** "just now", "5 minutes ago", "yesterday"… in the device language. */
@Composable
private fun relativeTime(time: Long, now: Long): String =
    if (now - time < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.recent_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }

@Preview(widthDp = 1000, heightDp = 800)
@Composable
private fun ConnectScreenPreview() {
    val now = 1_700_000_000_000
    MoonLightTheme(darkTheme = true) {
        ConnectScreen(
            onOpen = {},
            recentServers = listOf(
                RecentServer(ServerAddress(false, "10.1.1.200"), now - 30_000),
                RecentServer(ServerAddress(false, "stage-pi.local", 9000), now - 86_400_000),
            ),
            discovery = DiscoveryUi(
                status = DiscoveryUi.Status.SCANNING,
                ranges = listOf("10.1.1.0/24"),
                found = listOf(DiscoveredServer(ServerAddress(false, "10.1.1.200"), ServerHealth("1.0.0", ""))),
            ),
            localNetworkDenied = true,
            now = now,
        )
    }
}
