package br.com.moonaudio.moonlightcontrol.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import br.com.moonaudio.moonlightcontrol.BuildConfig
import br.com.moonaudio.moonlightcontrol.R
import br.com.moonaudio.moonlightcontrol.data.download
import br.com.moonaudio.moonlightcontrol.data.sha256
import br.com.moonaudio.moonlightcontrol.shared.AppBridge
import br.com.moonaudio.moonlightcontrol.shared.DownloadFileName
import br.com.moonaudio.moonlightcontrol.shared.TrustedCertificates
import kotlinx.coroutines.launch

object ConsoleScreenTags {
    const val ERROR = "console.error"
    const val RETRY = "console.retry"
    const val CHANGE_SERVER = "console.changeServer"
    const val CERT_DIALOG = "console.certificate"
    const val CERT_TRUST = "console.certificate.trust"
    const val CERT_CANCEL = "console.certificate.cancel"
}

/** A certificate waiting for the user's decision. */
private data class CertificatePrompt(
    val origin: String,
    val fingerprint: String,
    val verdict: TrustedCertificates.Verdict,
    val previous: String?,
)

/** A download waiting for the user to choose where to save it. */
private data class PendingDownload(val url: String, val userAgent: String?, val fileName: String)

/**
 * The MoonLightControl web UI, loaded from the server itself (same origin — BRIEF §4), full screen
 * with the screen kept on (BRIEF §5).
 *
 * - [origin]: the server's origin (`ServerAddress.origin`): only this origin gets the app bridge
 *   (BRIEF §6) and can be trusted with a self-signed certificate. Null for pages without one (tests).
 * - [onLoaded] runs each time the page (main frame) finished loading without error.
 * - [onExit] returns to the start screen (system back at the first page, "Change server", logout).
 * - [trustedCertificates] / [onTrustCertificate]: accepted self-signed certificates per origin.
 */
@Composable
fun ConsoleScreen(
    url: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    origin: String? = null,
    onLoaded: () -> Unit = {},
    trustedCertificates: Map<String, String> = emptyMap(),
    onTrustCertificate: (origin: String, fingerprint: String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Bumped when the WebView's renderer dies, to build a fresh WebView instead of crashing the app.
    var generation by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var certificatePrompt by remember { mutableStateOf<CertificatePrompt?>(null) }
    // Accepted in this session, before the persisted store catches up (avoids asking twice).
    val sessionTrust = remember { mutableStateMapOf<String, String>() }
    val currentOnLoaded by rememberUpdatedState(onLoaded)
    val currentOnExit by rememberUpdatedState(onExit)
    val currentTrusted by rememberUpdatedState(trustedCertificates + sessionTrust)

    FullScreen()

    // Show import: <input type="file"> → the system file picker.
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        fileCallback?.onReceiveValue(uri?.let { arrayOf(it) })
        fileCallback = null
    }

    // Show export: the server's attachment → "save as" with the name the server gave.
    var pendingDownload by remember { mutableStateOf<PendingDownload?>(null) }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { target ->
        val job = pendingDownload
        pendingDownload = null
        if (job == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(target)!!.use { out ->
                    download(
                        url = job.url,
                        cookie = CookieManager.getInstance().getCookie(job.url),
                        userAgent = job.userAgent,
                        pinnedSha256 = origin?.let { currentTrusted[it] },
                        output = out,
                    )
                }
            }.onFailure { Log.w(TAG, "download failed", it) }.isSuccess
            val message = if (ok) R.string.download_saved else R.string.download_failed
            Toast.makeText(context, resources.getString(message, job.fileName), Toast.LENGTH_LONG).show()
        }
    }

    // System back: web history first (hash routes create entries), then leave the console.
    BackHandler {
        val view = webView
        if (loadError == null && view != null && view.canGoBack()) view.goBack() else currentOnExit()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            // System bars are hidden and appear over the page on a swipe; only the camera cutout
            // is kept clear, so the page never resizes (and never re-lays out) mid-show.
            .windowInsetsPadding(WindowInsets.displayCutout),
    ) {
        key(generation) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        // MATCH_PARENT is essential: with AndroidView's default WRAP_CONTENT the
                        // WebView sizes its viewport to the content and CSS `100vh` becomes 0,
                        // which collapses the faders, scenes and fixtures views.
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        keepScreenOn = true // no sleeping tablet mid-show
                        configure()
                        webViewClient = ConsoleWebViewClient(
                            expectedOrigin = origin,
                            trusted = { currentTrusted },
                            onRendererGone = { generation++ },
                            onMainFrameLoaded = { currentOnLoaded() },
                            onMainFrameError = { description -> loadError = description },
                            onCertificate = { prompt -> certificatePrompt = prompt },
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                view: WebView,
                                callback: ValueCallback<Array<Uri>>,
                                params: FileChooserParams,
                            ): Boolean {
                                fileCallback?.onReceiveValue(null)
                                fileCallback = callback
                                try {
                                    // .mlcshow has no registered MIME type: allow any file.
                                    openDocument.launch(arrayOf("*/*"))
                                } catch (e: ActivityNotFoundException) {
                                    fileCallback = null
                                    callback.onReceiveValue(null)
                                }
                                return true
                            }
                        }
                        setDownloadListener { downloadUrl, userAgent, contentDisposition, _, _ ->
                            val name = DownloadFileName.from(contentDisposition, downloadUrl, fallback = "show.mlcshow")
                            pendingDownload = PendingDownload(downloadUrl, userAgent, name)
                            createDocument.launch(name)
                        }
                        if (origin != null) installBridge(origin, onLeave = { logout ->
                            if (logout) CookieManager.getInstance().flush()
                            currentOnExit()
                        })
                        loadUrl(url)
                        webView = this
                    }
                },
                onRelease = { view ->
                    if (webView === view) webView = null
                    view.destroy()
                },
            )
        }

        loadError?.let { description ->
            ErrorView(
                address = url,
                description = description,
                onRetry = {
                    loadError = null
                    webView?.loadUrl(url)
                },
                onChangeServer = { currentOnExit() },
            )
        }
    }

    certificatePrompt?.let { prompt ->
        CertificateDialog(
            prompt = prompt,
            onTrust = {
                certificatePrompt = null
                sessionTrust[prompt.origin] = prompt.fingerprint
                onTrustCertificate(prompt.origin, prompt.fingerprint)
                loadError = null
                webView?.loadUrl(url)
            },
            onCancel = { certificatePrompt = null },
        )
    }
}

/** Hides the system bars while the console is shown; a swipe shows them for a moment. */
@Composable
private fun FullScreen() {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun ErrorView(
    address: String,
    description: String,
    onRetry: () -> Unit,
    onChangeServer: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize().testTag(ConsoleScreenTags.ERROR),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(
                stringResource(R.string.console_error_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.console_error_detail, address, description),
                modifier = Modifier.widthIn(max = 560.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onChangeServer, modifier = Modifier.testTag(ConsoleScreenTags.CHANGE_SERVER)) {
                    Text(stringResource(R.string.console_change_server))
                }
                Button(onClick = onRetry, modifier = Modifier.testTag(ConsoleScreenTags.RETRY)) {
                    Text(stringResource(R.string.console_retry))
                }
            }
        }
    }
}

@Composable
private fun CertificateDialog(prompt: CertificatePrompt, onTrust: () -> Unit, onCancel: () -> Unit) {
    val changed = prompt.verdict == TrustedCertificates.Verdict.CHANGED
    val presented = TrustedCertificates.formatFingerprint(prompt.fingerprint.hexToBytes())
    AlertDialog(
        modifier = Modifier.testTag(ConsoleScreenTags.CERT_DIALOG),
        onDismissRequest = onCancel,
        title = {
            Text(
                stringResource(if (changed) R.string.cert_changed_title else R.string.cert_unknown_title),
                color = if (changed) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        },
        text = {
            Text(
                if (changed) {
                    stringResource(
                        R.string.cert_changed_message,
                        prompt.origin,
                        TrustedCertificates.formatFingerprint(prompt.previous.orEmpty().hexToBytes()),
                        presented,
                    )
                } else {
                    stringResource(R.string.cert_unknown_message, prompt.origin, presented)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onTrust, modifier = Modifier.testTag(ConsoleScreenTags.CERT_TRUST)) {
                Text(
                    stringResource(if (changed) R.string.cert_trust_new else R.string.cert_trust),
                    color = if (changed) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton = {
            // The safe choice is the prominent one.
            Button(onClick = onCancel, modifier = Modifier.testTag(ConsoleScreenTags.CERT_CANCEL)) {
                Text(stringResource(R.string.cert_cancel))
            }
        },
    )
}

private fun String.hexToBytes(): ByteArray = chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()

/** Injects `window.MoonLightApp` into [origin]'s pages only (BRIEF §6). */
private fun WebView.installBridge(origin: String, onLeave: (logout: Boolean) -> Unit) {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return // plain browser shell
    try {
        WebViewCompat.addWebMessageListener(this, AppBridge.OBJECT_NAME, setOf(origin)) { _, message, _, isMainFrame, reply ->
            if (!isMainFrame) return@addWebMessageListener
            when (AppBridge.parse(message.data)) {
                is AppBridge.Incoming.Ready -> reply.postMessage(AppBridge.hello(BuildConfig.VERSION_NAME))
                AppBridge.Incoming.Logout -> onLeave(true)
                AppBridge.Incoming.SwitchHost -> onLeave(false)
                null -> Unit
            }
        }
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "app bridge not installed for $origin", e) // malformed origin rule
    }
}

/**
 * Keeps navigation inside the WebView, reports successful loads and main-frame errors, handles
 * self-signed certificates (trust on first use, never blindly) and survives renderer crashes: the
 * dead WebView must not be used again, so [onRendererGone] asks for a new one.
 */
private class ConsoleWebViewClient(
    private val expectedOrigin: String?,
    private val trusted: () -> Map<String, String>,
    private val onRendererGone: () -> Unit,
    private val onMainFrameLoaded: () -> Unit,
    private val onMainFrameError: (String) -> Unit,
    private val onCertificate: (CertificatePrompt) -> Unit,
) : WebViewClient() {
    private var mainFrameFailed = false

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        mainFrameFailed = false
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        mainFrameFailed = true
        onMainFrameError(error.description?.toString().orEmpty().ifEmpty { "error ${error.errorCode}" })
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (!request.isForMainFrame) return
        mainFrameFailed = true
        onMainFrameError("HTTP ${errorResponse.statusCode} ${errorResponse.reasonPhrase.orEmpty()}".trim())
    }

    @SuppressLint("WebViewClientOnReceivedSslError") // Proceeds only for the certificate the user accepted.
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val origin = originOf(error.url)
        val fingerprint = error.certificate?.sha256()
        if (expectedOrigin == null || origin != expectedOrigin || fingerprint == null) {
            // Only the chosen server's own certificate can ever be trusted.
            handler.cancel()
            mainFrameFailed = true
            onMainFrameError(view.context.getString(R.string.console_error_certificate))
            return
        }
        val known = trusted()
        when (val verdict = TrustedCertificates.verdict(known, origin, fingerprint)) {
            TrustedCertificates.Verdict.TRUSTED -> handler.proceed()
            else -> {
                handler.cancel()
                mainFrameFailed = true
                onMainFrameError(view.context.getString(R.string.console_error_certificate))
                onCertificate(CertificatePrompt(origin, fingerprint, verdict, known[origin]))
            }
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (!mainFrameFailed) onMainFrameLoaded()
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.w(TAG, "WebView renderer gone (crashed: ${detail.didCrash()}); recreating the console")
        onRendererGone()
        return true
    }
}

/** `https://10.1.1.20:8080/x` → `https://10.1.1.20:8080` (default ports omitted, like a browser). */
internal fun originOf(url: String?): String? {
    val uri = url?.let { runCatching { it.toUri() }.getOrNull() } ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val hostPart = if (':' in host && !host.startsWith("[")) "[$host]" else host
    val port = uri.port
    val defaultPort = when (scheme) { "http" -> 80; "https" -> 443; else -> -1 }
    return if (port == -1 || port == defaultPort) "$scheme://$hostPart" else "$scheme://$hostPart:$port"
}

private const val TAG = "ConsoleScreen"

@SuppressLint("SetJavaScriptEnabled") // The web UI is a JavaScript app; only the chosen server is loaded.
private fun WebView.configure() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true // the UI keeps device preferences in localStorage
        textZoom = 100 // system font scaling must not break the fader layout
        builtInZoomControls = false
        displayZoomControls = false
        setSupportZoom(false)
        userAgentString = "$userAgentString MoonLightControlAndroid/${BuildConfig.VERSION_NAME}"
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        // The UI has its own dark theme; don't let WebView invert it again.
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
    }
    overScrollMode = View.OVER_SCROLL_NEVER
    setBackgroundColor(android.graphics.Color.BLACK)
    CookieManager.getInstance().setAcceptCookie(true)
}
