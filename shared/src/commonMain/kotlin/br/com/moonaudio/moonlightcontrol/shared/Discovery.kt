package br.com.moonaudio.moonlightcontrol.shared

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** What a MoonLightControl server says about itself at `GET /api/v1/health` (public, no login). */
data class ServerHealth(val version: String, val commit: String)

/** A server found on the local network. */
data class DiscoveredServer(val address: ServerAddress, val health: ServerHealth)

/** An IPv4 address of this device with its network prefix, e.g. 10.1.1.202/24. */
data class Ipv4Interface(val address: String, val prefixLength: Int)

/**
 * Finding MoonLightControl servers on the local network (BRIEF §7): the server does not advertise
 * itself, so the device's own IPv4 subnet is scanned on the default port with `GET /api/v1/health`.
 */
object Discovery {
    /** Requests in flight at once. */
    const val DEFAULT_PARALLELISM: Int = 32

    /** Never scan more than a /24 (254 hosts): a wider prefix scans only this device's /24. */
    const val NARROWEST_SCAN_PREFIX: Int = 24

    const val HEALTH_PATH: String = "/api/v1/health"

    /**
     * Addresses to probe around [iface], in ascending order, without the network and broadcast
     * addresses and without the device itself. Empty for prefixes that hold no other host (/31, /32)
     * or for an invalid address.
     */
    fun candidateHosts(iface: Ipv4Interface): List<String> {
        val octets = Ipv4.parseOctets(iface.address) ?: return emptyList()
        if (iface.prefixLength !in 0..32) return emptyList()
        val own = octets.fold(0L) { acc, o -> (acc shl 8) or o.toLong() }
        val prefix = maxOf(iface.prefixLength, NARROWEST_SCAN_PREFIX)
        val size = 1L shl (32 - prefix)
        if (size < 4) return emptyList() // /31 and /32: no network/broadcast pair to skip, no peers
        val network = own and (0xFFFFFFFFL shl (32 - prefix) and 0xFFFFFFFFL)
        return ((network + 1) until (network + size - 1))
            .filter { it != own }
            .map { ip -> (0..3).joinToString(".") { i -> ((ip shr (24 - 8 * i)) and 0xFF).toString() } }
    }

    /** The range [candidateHosts] covers, in CIDR notation (e.g. `10.1.1.0/24`), for display. */
    fun scanRange(iface: Ipv4Interface): String? {
        val octets = Ipv4.parseOctets(iface.address) ?: return null
        if (iface.prefixLength !in 0..32) return null
        val prefix = maxOf(iface.prefixLength, NARROWEST_SCAN_PREFIX)
        val own = octets.fold(0L) { acc, o -> (acc shl 8) or o.toLong() }
        val network = own and (0xFFFFFFFFL shl (32 - prefix) and 0xFFFFFFFFL)
        val text = (0..3).joinToString(".") { i -> ((network shr (24 - 8 * i)) and 0xFF).toString() }
        return "$text/$prefix"
    }

    /** Hosts to probe for several interfaces (e.g. Wi-Fi and Ethernet), without duplicates. */
    fun candidateHosts(ifaces: List<Ipv4Interface>): List<String> =
        ifaces.flatMap { candidateHosts(it) }.distinct()

    /**
     * Probes every host on [port] with at most [parallelism] requests at a time. [probe] returns
     * the server's health, or null when nothing (or something else) answers. [onFound] is called as
     * servers answer; the result lists them in the order of [hosts].
     */
    suspend fun scan(
        hosts: List<String>,
        probe: suspend (ServerAddress) -> ServerHealth?,
        port: Int = DEFAULT_SERVER_PORT,
        parallelism: Int = DEFAULT_PARALLELISM,
        onFound: (DiscoveredServer) -> Unit = {},
    ): List<DiscoveredServer> = coroutineScope {
        val permits = Semaphore(parallelism)
        hosts.map { host ->
            async {
                permits.withPermit {
                    val address = ServerAddress.fromParts(https = false, host = host, port = port)
                        ?: return@withPermit null
                    probe(address)?.let { DiscoveredServer(address, it).also(onFound) }
                }
            }
        }.awaitAll().filterNotNull()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reads a health response body. Only a MoonLightControl server's shape is accepted (status
     * "ok", a version, a commit and an uptime), so other devices answering on the port are ignored.
     */
    fun parseHealth(body: String): ServerHealth? {
        val parsed = try {
            json.decodeFromString(HealthBody.serializer(), body)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (parsed.status != "ok" || parsed.version.isBlank() || parsed.uptimeSeconds < 0) return null
        return ServerHealth(version = parsed.version, commit = parsed.commit)
    }
}

@Serializable
private data class HealthBody(
    val status: String,
    val version: String,
    val commit: String,
    val uptimeSeconds: Long,
)
