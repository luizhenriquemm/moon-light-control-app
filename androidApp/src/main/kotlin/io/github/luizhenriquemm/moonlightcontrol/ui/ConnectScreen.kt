package io.github.luizhenriquemm.moonlightcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.luizhenriquemm.moonlightcontrol.R
import io.github.luizhenriquemm.moonlightcontrol.shared.ParseError
import io.github.luizhenriquemm.moonlightcontrol.shared.ParseResult
import io.github.luizhenriquemm.moonlightcontrol.shared.ServerAddress

object ConnectScreenTags {
    const val ADDRESS = "connect.address"
    const val OPEN = "connect.open"
    const val ERROR = "connect.error"
    const val LOCAL_NETWORK_DENIED = "connect.localNetworkDenied"
}

/** Milestone 1: type a server address and open it. Replaced by the host list in milestone 2. */
@Composable
fun ConnectScreen(
    onOpen: (ServerAddress) -> Unit,
    modifier: Modifier = Modifier,
    localNetworkDenied: Boolean = false,
    onOpenAppSettings: () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<ParseError?>(null) }

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
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
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
    }
}

@Preview(widthDp = 1000, heightDp = 600)
@Composable
private fun ConnectScreenPreview() {
    MoonLightTheme(darkTheme = true) {
        ConnectScreen(onOpen = {}, localNetworkDenied = true)
    }
}
