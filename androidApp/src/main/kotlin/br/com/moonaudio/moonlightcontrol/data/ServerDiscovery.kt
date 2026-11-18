package br.com.moonaudio.moonlightcontrol.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import br.com.moonaudio.moonlightcontrol.shared.DiscoveredServer
import br.com.moonaudio.moonlightcontrol.shared.Discovery
import br.com.moonaudio.moonlightcontrol.shared.Ipv4Interface
import br.com.moonaudio.moonlightcontrol.shared.ServerAddress
import br.com.moonaudio.moonlightcontrol.shared.ServerHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

/** A local network this device is on (Wi-Fi or Ethernet) and its IPv4 address there. */
data class LocalIpv4Network(val network: Network?, val iface: Ipv4Interface)

/** Scans the device's local networks for MoonLightControl servers (BRIEF §7). */
class ServerDiscovery(
    private val networks: () -> List<LocalIpv4Network>,
    private val probe: suspend (ServerAddress, Network?) -> ServerHealth?,
    private val parallelism: Int = PARALLELISM,
) {
    /** The ranges that [scan] would cover, e.g. `10.1.1.0/24`; empty when not on a local network. */
    fun ranges(): List<String> = networks().mapNotNull { Discovery.scanRange(it.iface) }.distinct()

    suspend fun scan(onFound: (DiscoveredServer) -> Unit): List<DiscoveredServer> {
        val networkOf = LinkedHashMap<String, Network?>()
        for (local in networks()) {
            for (host in Discovery.candidateHosts(local.iface)) networkOf.putIfAbsent(host, local.network)
        }
        return Discovery.scan(
            hosts = networkOf.keys.toList(),
            probe = { address -> probe(address, networkOf[address.host]) },
            parallelism = parallelism,
            onFound = onFound,
        )
    }

    companion object {
        /** Unanswered hosts cost a connect timeout each, so probe many at once. */
        const val PARALLELISM = 64

        fun create(context: Context): ServerDiscovery {
            val appContext = context.applicationContext
            return ServerDiscovery(networks = { localIpv4Networks(appContext) }, probe = ::httpHealthProbe)
        }
    }
}

/**
 * Wi-Fi and Ethernet networks with an IPv4 address. All of them, not only the default network: on a
 * phone the lighting Wi-Fi (no internet) may not be the default while mobile data is on.
 */
@Suppress("DEPRECATION") // allNetworks: still the simplest way to list current networks once.
fun localIpv4Networks(context: Context): List<LocalIpv4Network> {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
    return cm.allNetworks.flatMap { network ->
        val caps = cm.getNetworkCapabilities(network)
        val isLan = caps != null && (
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
        if (!isLan) return@flatMap emptyList()
        cm.getLinkProperties(network)?.linkAddresses.orEmpty()
            .filter { it.address is Inet4Address && !it.address.isLoopbackAddress }
            .mapNotNull { link -> link.address.hostAddress?.let { LocalIpv4Network(network, Ipv4Interface(it, link.prefixLength)) } }
    }
}

private const val CONNECT_TIMEOUT_MS = 700
private const val READ_TIMEOUT_MS = 1500
private const val MAX_BODY_CHARS = 4096

/**
 * `GET /api/v1/health` on [address], through [network] when given. Any failure (refused, timeout,
 * another kind of device) is "no server here".
 */
suspend fun httpHealthProbe(address: ServerAddress, network: Network?): ServerHealth? =
    withContext(Dispatchers.IO) {
        val url = URL(address.origin + Discovery.HEALTH_PATH)
        val connection = try {
            (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        } catch (e: Exception) {
            return@withContext null
        }
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val body = InputStreamReader(connection.inputStream, Charsets.UTF_8).use { reader ->
                val buffer = CharArray(MAX_BODY_CHARS)
                var length = 0
                while (length < buffer.size) {
                    val read = reader.read(buffer, length, buffer.size - length)
                    if (read < 0) break
                    length += read
                }
                String(buffer, 0, length)
            }
            Discovery.parseHealth(body)
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
