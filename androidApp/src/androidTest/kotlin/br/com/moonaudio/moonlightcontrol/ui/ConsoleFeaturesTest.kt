package br.com.moonaudio.moonlightcontrol.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.moonaudio.moonlightcontrol.R
import br.com.moonaudio.moonlightcontrol.data.sha256Hex
import br.com.moonaudio.moonlightcontrol.testutil.TestResponse
import br.com.moonaudio.moonlightcontrol.testutil.TestServer
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

/** Milestone 3 console behavior, against loopback test servers (no real MoonLightControl server). */
@RunWith(AndroidJUnit4::class)
class ConsoleFeaturesTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val servers = mutableListOf<TestServer>()
    private fun track(server: TestServer) = server.also { servers += it }

    @After
    fun tearDown() = servers.forEach { it.close() }

    /** Chromium remembers accepted certificates per host for the whole process: start clean. */
    @Before
    fun forgetAcceptedCertificates() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            WebView(InstrumentationRegistry.getInstrumentation().targetContext).apply {
                clearSslPreferences()
                destroy()
            }
        }
    }

    private fun webView(): WebView? {
        var found: WebView? = null
        compose.runOnUiThread { found = findWebView(compose.activity.window.decorView) }
        return found
    }

    private fun findWebView(view: View): WebView? = when (view) {
        is WebView -> view
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findWebView(view.getChildAt(it)) }
        else -> null
    }

    private fun title(): String {
        var title = ""
        compose.runOnUiThread { title = webView()?.title.orEmpty() }
        return title
    }

    // --- Error view -------------------------------------------------------------------------

    @Test
    fun unreachableServerShowsErrorViewAndChangeServerLeaves() {
        val exits = AtomicInteger()
        compose.setContent {
            MoonLightTheme { ConsoleScreen(url = "http://127.0.0.1:9/", onExit = { exits.incrementAndGet() }) }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(ConsoleScreenTags.ERROR)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(ConsoleScreenTags.ERROR).assertIsDisplayed()
        compose.onNodeWithText("http://127.0.0.1:9/", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(ConsoleScreenTags.CHANGE_SERVER).performClick()
        assertEquals(1, exits.get())
    }

    @Test
    fun retryLoadsAgainOnceTheServerIsBack() {
        val port = ServerSocket(0).use { it.localPort } // free now; nothing listening yet
        val url = "http://127.0.0.1:$port/"
        val loads = AtomicInteger()
        compose.setContent {
            MoonLightTheme { ConsoleScreen(url = url, onExit = {}, onLoaded = { loads.incrementAndGet() }) }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(ConsoleScreenTags.ERROR)).fetchSemanticsNodes().isNotEmpty() }

        track(TestServer.http(port) { TestResponse(body = "<html><body>back</body></html>") })
        compose.onNodeWithTag(ConsoleScreenTags.RETRY).performClick()

        compose.waitUntil(10_000) { loads.get() > 0 }
        compose.onNodeWithTag(ConsoleScreenTags.ERROR).assertDoesNotExist()
    }

    @Test
    fun httpErrorOnTheMainPageShowsErrorView() {
        val server = track(TestServer.http { TestResponse(status = 502, body = "bad gateway") })
        compose.setContent { MoonLightTheme { ConsoleScreen(url = server.origin + "/", onExit = {}) } }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(ConsoleScreenTags.ERROR)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("502", substring = true).assertIsDisplayed()
    }

    // --- Full screen, keep awake ------------------------------------------------------------

    @Test
    fun consoleIsFullScreenAndKeepsTheScreenOn() {
        val server = track(TestServer.http { TestResponse(body = "<html><body>ok</body></html>") })
        compose.setContent { MoonLightTheme { ConsoleScreen(url = server.origin + "/", onExit = {}) } }
        compose.waitUntil(10_000) { webView() != null }

        var keepOn = false
        compose.runOnUiThread { keepOn = webView()!!.keepScreenOn }
        assertTrue("screen must stay on while the console is shown", keepOn)

        compose.waitUntil(5_000) {
            var barsVisible = true
            compose.runOnUiThread {
                barsVisible = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.systemBars()) ?: true
            }
            !barsVisible
        }
    }

    // --- App bridge (BRIEF §6) -------------------------------------------------------------

    private fun bridgePage(afterHello: String = "") = """
        <html><body><script>
          const app = window.MoonLightApp;
          if (!app) { document.title = 'no-bridge'; }
          else {
            app.addEventListener('message', (e) => {
              document.title = 'got:' + e.data;
              $afterHello
            });
            app.postMessage(JSON.stringify({ v: 1, type: 'ready', serverVersion: 'test' }));
          }
        </script></body></html>
    """.trimIndent()

    @Test
    fun bridgeAnswersReadyWithHello() {
        val server = track(TestServer.http { TestResponse(body = bridgePage()) })
        compose.setContent {
            MoonLightTheme { ConsoleScreen(url = server.origin + "/", origin = server.origin, onExit = {}) }
        }
        compose.waitUntil(10_000) { title().startsWith("got:") }
        val hello = title().removePrefix("got:")
        assertTrue(hello, hello.contains("\"type\":\"hello\""))
        assertTrue(hello, hello.contains("\"features\":[\"logout\",\"switchHost\"]"))
        assertTrue(hello, hello.contains("\"app\":\"android\""))
    }

    @Test
    fun bridgeIsOnlyGivenToTheServersOwnOrigin() {
        val server = track(TestServer.http { TestResponse(body = bridgePage()) })
        compose.setContent {
            MoonLightTheme { ConsoleScreen(url = server.origin + "/", origin = "http://127.0.0.1:1", onExit = {}) }
        }
        compose.waitUntil(10_000) { title() == "no-bridge" }
    }

    private fun assertLeavesOn(type: String) {
        val server = track(
            TestServer.http {
                TestResponse(body = bridgePage("app.postMessage(JSON.stringify({ v: 1, type: '$type' }));"))
            },
        )
        val exits = AtomicInteger()
        compose.setContent {
            MoonLightTheme {
                ConsoleScreen(url = server.origin + "/", origin = server.origin, onExit = { exits.incrementAndGet() })
            }
        }
        compose.waitUntil(10_000) { exits.get() == 1 }
    }

    @Test
    fun logoutLeavesTheConsole() = assertLeavesOn("logout")

    @Test
    fun switchHostLeavesTheConsole() = assertLeavesOn("switchHost")

    // --- Self-signed HTTPS, trust on first use (BRIEF §5) ----------------------------------

    private val page = TestResponse(body = "<html><body>secure</body></html>")

    @Test
    fun unknownCertificateAsksAndTrustingItLoadsThePage() {
        val server = track(TestServer.https("a") { page })
        val trusted = mutableListOf<Pair<String, String>>()
        val loads = AtomicInteger()
        compose.setContent {
            MoonLightTheme {
                ConsoleScreen(
                    url = server.origin + "/",
                    origin = server.origin,
                    onExit = {},
                    onLoaded = { loads.incrementAndGet() },
                    onTrustCertificate = { origin, fp -> trusted += origin to fp },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(ConsoleScreenTags.CERT_DIALOG)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("nothing loads before the user decides", 0, loads.get())

        compose.onNodeWithTag(ConsoleScreenTags.CERT_TRUST).performClick()

        compose.waitUntil(10_000) { loads.get() > 0 }
        assertEquals(listOf(server.origin to sha256Hex(TestServer.certificateDer("a"))), trusted)
    }

    @Test
    fun alreadyTrustedCertificateLoadsWithoutAsking() {
        val server = track(TestServer.https("a") { page })
        val loads = AtomicInteger()
        compose.setContent {
            MoonLightTheme {
                ConsoleScreen(
                    url = server.origin + "/",
                    origin = server.origin,
                    onExit = {},
                    onLoaded = { loads.incrementAndGet() },
                    trustedCertificates = mapOf(server.origin to sha256Hex(TestServer.certificateDer("a"))),
                )
            }
        }
        compose.waitUntil(10_000) { loads.get() > 0 }
        compose.onNodeWithTag(ConsoleScreenTags.CERT_DIALOG).assertDoesNotExist()
    }

    @Test
    fun changedCertificateWarnsAndCancelLeavesItBlocked() {
        val server = track(TestServer.https("b") { page })
        val loads = AtomicInteger()
        val trusted = mutableListOf<Pair<String, String>>()
        compose.setContent {
            MoonLightTheme {
                ConsoleScreen(
                    url = server.origin + "/",
                    origin = server.origin,
                    onExit = {},
                    onLoaded = { loads.incrementAndGet() },
                    trustedCertificates = mapOf(server.origin to sha256Hex(TestServer.certificateDer("a"))),
                    onTrustCertificate = { origin, fp -> trusted += origin to fp },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(ConsoleScreenTags.CERT_DIALOG)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(compose.activity.getString(R.string.cert_changed_title)).assertExists()
        compose.onNodeWithTag(ConsoleScreenTags.CERT_CANCEL).performClick()

        compose.onNodeWithTag(ConsoleScreenTags.ERROR).assertIsDisplayed()
        Thread.sleep(500)
        assertEquals(0, loads.get())
        assertTrue(trusted.isEmpty())
    }

    @Test
    fun certificateOfAnotherOriginIsNeverOffered() {
        val server = track(TestServer.https("a") { page })
        val loads = AtomicInteger()
        compose.setContent {
            MoonLightTheme {
                ConsoleScreen(
                    url = server.origin + "/",
                    origin = null, // e.g. not the chosen server
                    onExit = {},
                    onLoaded = { loads.incrementAndGet() },
                )
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(ConsoleScreenTags.ERROR)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(ConsoleScreenTags.CERT_DIALOG).assertDoesNotExist()
        assertEquals(0, loads.get())
    }
}
