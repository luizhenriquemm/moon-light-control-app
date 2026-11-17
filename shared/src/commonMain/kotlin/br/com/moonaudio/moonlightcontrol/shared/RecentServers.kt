package br.com.moonaudio.moonlightcontrol.shared

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** How many recently used servers the start screen offers. */
const val MAX_RECENT_SERVERS: Int = 5

/** A server the app opened successfully, and when (epoch milliseconds). */
data class RecentServer(val address: ServerAddress, val lastConnectedAt: Long)

/**
 * The list of recently used servers: most recent first, one entry per address, at most
 * [MAX_RECENT_SERVERS]. Pure functions over immutable lists; storage is up to the platform.
 */
object RecentServers {

    /** Records a successful connection: [address] moves (or is added) to the top. */
    fun recordSuccess(
        servers: List<RecentServer>,
        address: ServerAddress,
        now: Long,
    ): List<RecentServer> =
        (listOf(RecentServer(address, now)) + servers.filterNot { it.address == address })
            .take(MAX_RECENT_SERVERS)

    fun remove(servers: List<RecentServer>, address: ServerAddress): List<RecentServer> =
        servers.filterNot { it.address == address }

    private val json = Json {
        ignoreUnknownKeys = true // files written by a newer app version stay readable
        encodeDefaults = true
    }

    fun encode(servers: List<RecentServer>): String =
        json.encodeToString(
            StoredRecentServers.serializer(),
            StoredRecentServers(
                version = StoredRecentServers.CURRENT_VERSION,
                servers = servers.map {
                    StoredServer(it.address.https, it.address.host, it.address.port, it.lastConnectedAt)
                },
            ),
        )

    /**
     * Reads what [encode] wrote. Entries that are not valid are skipped and the result is put back
     * in order and size; returns null when the text is not readable at all (corrupted file).
     */
    fun decode(text: String): List<RecentServer>? {
        if (text.isBlank()) return emptyList()
        val stored = try {
            json.decodeFromString(StoredRecentServers.serializer(), text)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        return stored.servers
            .mapNotNull { s ->
                ServerAddress.fromParts(s.https, s.host, s.port)?.let { RecentServer(it, s.lastConnectedAt) }
            }
            .sortedByDescending { it.lastConnectedAt }
            .distinctBy { it.address }
            .take(MAX_RECENT_SERVERS)
    }
}

@Serializable
private data class StoredRecentServers(
    val version: Int,
    val servers: List<StoredServer> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
private data class StoredServer(
    val https: Boolean = false,
    val host: String,
    val port: Int = DEFAULT_SERVER_PORT,
    @SerialName("lastConnectedAt") val lastConnectedAt: Long = 0,
)
