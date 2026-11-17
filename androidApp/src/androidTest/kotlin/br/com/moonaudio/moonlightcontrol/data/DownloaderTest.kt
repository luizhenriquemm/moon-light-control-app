package br.com.moonaudio.moonlightcontrol.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.moonaudio.moonlightcontrol.testutil.TestResponse
import br.com.moonaudio.moonlightcontrol.testutil.TestServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DownloaderTest {

    private val servers = mutableListOf<TestServer>()

    @After
    fun tearDown() = servers.forEach { it.close() }

    private val show = """{"name":"Culto de domingo – ação","scenes":[]}"""

    @Test
    fun downloadsWithTheLoginCookieAndUserAgent() = runBlocking {
        val server = TestServer.http { TestResponse(body = show, headers = mapOf("Content-Type" to "application/json")) }
            .also { servers += it }
        val out = ByteArrayOutputStream()
        val size = download(server.origin + "/api/v1/shows/3/export", "mlc_login=abc", "UA/1", null, out)

        assertEquals(show, out.toString(Charsets.UTF_8.name()))
        assertEquals(out.size().toLong(), size)
        val request = server.requests.single()
        assertEquals("/api/v1/shows/3/export", request.path)
        assertEquals("mlc_login=abc", request.headers["cookie"])
        assertEquals("UA/1", request.headers["user-agent"])
    }

    @Test
    fun errorStatusFails() = runBlocking {
        val server = TestServer.http { TestResponse(status = 401, body = "login") }.also { servers += it }
        val result = runCatching { download(server.origin + "/x", null, null, null, ByteArrayOutputStream()) }
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun httpsWithThePinnedCertificate() = runBlocking {
        val server = TestServer.https("a") { TestResponse(body = show) }.also { servers += it }
        val pin = sha256Hex(TestServer.certificateDer("a"))
        val out = ByteArrayOutputStream()
        download(server.origin + "/x", null, null, pin, out)
        assertEquals(show, out.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun httpsWithAnotherCertificateIsRefused() = runBlocking {
        val server = TestServer.https("b") { TestResponse(body = show) }.also { servers += it }
        val pin = sha256Hex(TestServer.certificateDer("a"))
        val out = ByteArrayOutputStream()
        val result = runCatching { download(server.origin + "/x", null, null, pin, out) }
        assertTrue("a different certificate must be refused", result.isFailure)
        assertEquals(0, out.size())
    }

    @Test
    fun httpsWithoutPinUsesTheSystemTrustAndRefusesSelfSigned() = runBlocking {
        val server = TestServer.https("a") { TestResponse(body = show) }.also { servers += it }
        val result = runCatching { download(server.origin + "/x", null, null, null, ByteArrayOutputStream()) }
        assertTrue(result.isFailure)
    }
}
