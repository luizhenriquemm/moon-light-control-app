package br.com.moonaudio.moonlightcontrol.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import br.com.moonaudio.moonlightcontrol.shared.RecentServer
import br.com.moonaudio.moonlightcontrol.shared.RecentServers
import br.com.moonaudio.moonlightcontrol.shared.ServerAddress
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream

/** Recently used servers, persisted on the device (JSON file, see [RecentServers.encode]). */
class RecentServersStore(
    private val dataStore: DataStore<List<RecentServer>>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val servers: Flow<List<RecentServer>> = dataStore.data

    suspend fun recordSuccess(address: ServerAddress) {
        dataStore.updateData { RecentServers.recordSuccess(it, address, clock()) }
    }

    suspend fun remove(address: ServerAddress) {
        dataStore.updateData { RecentServers.remove(it, address) }
    }
}

object RecentServersSerializer : Serializer<List<RecentServer>> {
    override val defaultValue: List<RecentServer> = emptyList()

    override suspend fun readFrom(input: InputStream): List<RecentServer> =
        RecentServers.decode(input.readBytes().decodeToString())
            ?: throw CorruptionException("Unreadable recent servers file")

    override suspend fun writeTo(t: List<RecentServer>, output: OutputStream) {
        output.write(RecentServers.encode(t).encodeToByteArray())
    }
}

/** A corrupted file is replaced by an empty list: losing shortcuts is better than crashing. */
val recentServersCorruptionHandler = ReplaceFileCorruptionHandler<List<RecentServer>> { emptyList() }

/** The app's single DataStore for the recent servers file. */
val Context.recentServersDataStore: DataStore<List<RecentServer>> by dataStore(
    fileName = "recent_servers.json",
    serializer = RecentServersSerializer,
    corruptionHandler = recentServersCorruptionHandler,
)
