package br.com.moonaudio.moonlightcontrol.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.moonaudio.moonlightcontrol.shared.ServerAddress
import br.com.moonaudio.moonlightcontrol.shared.ServerHealth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/** Runs the real HTTP probe against a tiny HTTP server on the device's loopback (no LAN involved). */
@RunWith(AndroidJUnit4::class)
class HttpHealthProbeTest {

    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val address = ServerAddress(false, "127.0.0.1", server.localPort)
    private val requests = mutableListOf<String>()

    /** Answers every connection with [status] and [body] (or stays silent when [silent]). */
    private fun serve(status: String = "200 OK", body: String = "", silent: Boolean = false) {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (e: Exception) { return@thread }
                thread(isDaemon = true) {
                    socket.use {
                        val requestLine = it.getInputStream().bufferedReader().readLine().orEmpty()
                        synchronized(requests) { requests += requestLine }
                        if (silent) { Thread.sleep(5_000); return@use }
                        val bytes = body.toByteArray()
                        it.getOutputStream().write(
                            ("HTTP/1.1 $status\r\nContent-Type: application/json\r\n" +
                                "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n").toByteArray() + bytes,
                        )
                    }
                }
            }
        }
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun recognizesAMoonLightControlServer() = runBlocking {
        serve(body = """{"status":"ok","version":"4b9dade","commit":"4b9dade","uptimeSeconds":232}""")
        assertEquals(ServerHealth("4b9dade", "4b9dade"), httpHealthProbe(address, null))
        assertEquals(listOf("GET /api/v1/health HTTP/1.1"), requests)
    }

    @Test
    fun ignoresOtherDevices() = runBlocking {
        serve(body = "<html>router</html>")
        assertNull(httpHealthProbe(address, null))
    }

    @Test
    fun ignoresErrorStatus() = runBlocking {
        serve(status = "404 Not Found", body = """{"status":"ok","version":"1","commit":"","uptimeSeconds":1}""")
        assertNull(httpHealthProbe(address, null))
    }

    @Test
    fun slowDeviceTimesOut() = runBlocking {
        serve(silent = true)
        val start = System.currentTimeMillis()
        assertNull(httpHealthProbe(address, null))
        assertTrue("must give up quickly", System.currentTimeMillis() - start < 4_000)
    }

    @Test
    fun nothingListening() = runBlocking {
        server.close()
        assertNull(httpHealthProbe(address, null))
    }
}
