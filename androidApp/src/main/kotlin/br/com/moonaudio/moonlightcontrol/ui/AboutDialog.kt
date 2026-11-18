package br.com.moonaudio.moonlightcontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.moonaudio.moonlightcontrol.BuildConfig
import br.com.moonaudio.moonlightcontrol.R

/** App name, version, license and where the source code is (GPL-3.0-or-later). */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        modifier = Modifier.testTag(ConnectScreenTags.ABOUT_DIALOG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
                Text(stringResource(R.string.about_description))
                Text(stringResource(R.string.about_license), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.about_source, stringResource(R.string.source_url)),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(stringResource(R.string.about_copyright), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close)) } },
    )
}
