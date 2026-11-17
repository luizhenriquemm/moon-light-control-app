package br.com.moonaudio.moonlightcontrol.ui

import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsoleScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /**
     * Regression: with a WRAP_CONTENT WebView, CSS `100vh` was 0 and the web UI's faders, scenes and
     * fixtures views collapsed. A local page (no server involved) reports its `100vh` height.
     */
    @Test
    fun viewportHeightUnitsAreNotZero() {
        val page = """
            <html><body style="margin:0"><div id="d" style="height:100vh"></div>
            <script>
              addEventListener('load', () => setTimeout(() => {
                document.title = 'vh:' + document.getElementById('d').getBoundingClientRect().height;
              }, 200));
            </script></body></html>
        """.trimIndent()
        val url = "data:text/html;base64," + Base64.encodeToString(page.toByteArray(), Base64.NO_WRAP)

        compose.setContent { MoonLightTheme { ConsoleScreen(url = url, onExit = {}) } }

        var title = ""
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.runOnUiThread {
                title = findWebView(compose.activity.window.decorView)?.title.orEmpty()
            }
            title.startsWith("vh:")
        }
        val height = title.removePrefix("vh:").toFloat()
        assertTrue("100vh must fill the screen, was $height", height > 100f)
    }

    @Test
    fun successfulLoadIsReported() {
        val page = "<html><body>ok</body></html>"
        val url = "data:text/html;base64," + Base64.encodeToString(page.toByteArray(), Base64.NO_WRAP)
        val loads = AtomicInteger()

        compose.setContent {
            MoonLightTheme { ConsoleScreen(url = url, onExit = {}, onLoaded = { loads.incrementAndGet() }) }
        }

        compose.waitUntil(timeoutMillis = 10_000) { loads.get() > 0 }
    }

    @Test
    fun failedLoadIsNotReported() {
        // Nothing listens on the discard port of the device itself: connection refused at once.
        val url = "http://127.0.0.1:9/"
        val loads = AtomicInteger()

        compose.setContent {
            MoonLightTheme { ConsoleScreen(url = url, onExit = {}, onLoaded = { loads.incrementAndGet() }) }
        }

        var progress = 0
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.runOnUiThread { progress = findWebView(compose.activity.window.decorView)?.progress ?: 0 }
            progress == 100
        }
        Thread.sleep(500) // let onPageFinished run
        compose.waitForIdle()
        assertEquals("a page that failed to load must not count as a successful connection", 0, loads.get())
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }
}
