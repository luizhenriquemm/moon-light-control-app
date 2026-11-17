package io.github.luizhenriquemm.moonlightcontrol.ui

import android.annotation.SuppressLint
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.github.luizhenriquemm.moonlightcontrol.BuildConfig

/**
 * The MoonLightControl web UI, loaded from the server itself (same origin — BRIEF §4).
 * Milestone 1 keeps it minimal; full screen, error view, downloads, file chooser, HTTPS trust and the
 * app bridge come in milestone 3 (BRIEF §5–6).
 */
@Composable
fun ConsoleScreen(url: String, onExit: () -> Unit, modifier: Modifier = Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Bumped when the WebView's renderer dies, to build a fresh WebView instead of crashing the app.
    var generation by remember { mutableIntStateOf(0) }

    // System back: web history first (hash routes create entries), then leave the console.
    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onExit()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding(),
    ) {
        key(generation) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        configure(onRendererGone = { generation++ })
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
    }
}

/**
 * Keeps navigation inside the WebView (instead of opening a browser) and survives renderer
 * crashes: the dead WebView must not be used again, so [onRendererGone] asks for a new one.
 */
private class ConsoleWebViewClient(private val onRendererGone: () -> Unit) : WebViewClient() {
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.w(TAG, "WebView renderer gone (crashed: ${detail.didCrash()}); recreating the console")
        onRendererGone()
        return true
    }
}

private const val TAG = "ConsoleScreen"

@SuppressLint("SetJavaScriptEnabled") // The web UI is a JavaScript app; only the chosen server is loaded.
private fun WebView.configure(onRendererGone: () -> Unit) {
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
    webViewClient = ConsoleWebViewClient(onRendererGone)
}
