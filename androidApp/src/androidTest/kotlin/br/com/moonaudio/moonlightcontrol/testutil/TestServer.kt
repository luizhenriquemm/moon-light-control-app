package br.com.moonaudio.moonlightcontrol.testutil

import androidx.test.platform.app.InstrumentationRegistry
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread

/** One HTTP response of [TestServer]. */
data class TestResponse(
    val status: Int = 200,
    val body: String = "",
    val headers: Map<String, String> = mapOf("Content-Type" to "text/html; charset=utf-8"),
)

/** A request [TestServer] received: request line plus headers (lower-case names). */
data class TestRequest(val method: String, val path: String, val headers: Map<String, String>)

/**
 * A tiny HTTP/1.1 (or HTTPS, with a test certificate from the androidTest assets) server on the
 * device's loopback, so the WebView and the app's HTTP code can be tested without any real server.
 */
class TestServer private constructor(
    private val socket: ServerSocket,
    val https: Boolean,
    private val routes: (TestRequest) -> TestResponse,
) : Closeable {
    val port: Int get() = socket.localPort
    val origin: String get() = "${if (https) "https" else "http"}://127.0.0.1:$port"
    val requests: MutableList<TestRequest> = Collections.synchronizedList(mutableListOf())

    init {
        thread(isDaemon = true, name = "TestServer") {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { serve(client) }
            }
        }
    }

    private fun serve(client: Socket) = try {
        client.use {
            val input = it.getInputStream().bufferedReader(Charsets.ISO_8859_1)
            val requestLine = input.readLine() ?: return@use
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            val parts = requestLine.split(' ')
            val request = TestRequest(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" }, headers)
            requests += request
            val response = routes(request)
            val body = response.body.toByteArray(Charsets.UTF_8)
            val head = buildString {
                append("HTTP/1.1 ${response.status} ${if (response.status == 200) "OK" else "Status"}\r\n")
                response.headers.forEach { (k, v) -> append("$k: $v\r\n") }
                append("Content-Length: ${body.size}\r\nConnection: close\r\n\r\n")
            }
            it.getOutputStream().apply { write(head.toByteArray(Charsets.ISO_8859_1)); write(body); flush() }
        }
    } catch (e: Exception) {
        // The client went away (e.g. TLS rejected by the WebView): fine for tests.
    }

    override fun close() = socket.close()

    companion object {
        private const val PASSWORD = "testpass"

        fun http(port: Int = 0, routes: (TestRequest) -> TestResponse): TestServer =
            TestServer(ServerSocket(port, 50, InetAddress.getByName("127.0.0.1")), https = false, routes = routes)

        /** HTTPS with the self-signed test identity [identity] ("a" or "b"). */
        fun https(identity: String = "a", routes: (TestRequest) -> TestResponse): TestServer {
            val keyStore = loadKeyStore(identity)
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, PASSWORD.toCharArray())
            val context = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
            val socket = context.serverSocketFactory.createServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            return TestServer(socket, https = true, routes = routes)
        }

        /** The DER bytes of test identity [identity]'s certificate. */
        fun certificateDer(identity: String): ByteArray =
            (loadKeyStore(identity).getCertificate("server") as X509Certificate).encoded

        private fun loadKeyStore(identity: String): KeyStore {
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            return KeyStore.getInstance("PKCS12").apply {
                assets.open("test-server-$identity.p12").use { load(it, PASSWORD.toCharArray()) }
            }
        }
    }
}
