package br.com.moonaudio.moonlightcontrol.data

import androidx.datastore.core.DataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.moonaudio.moonlightcontrol.shared.ServerAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecentServersStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "recent-test-${UUID.randomUUID()}.json")
    private val scopes = mutableListOf<CoroutineScope>()
    private var clock = 1_000L

    /** A fresh DataStore over [file], as after an app restart. */
    private fun openStore(): RecentServersStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val dataStore = DataStoreFactory.create(
            serializer = RecentServersSerializer,
            corruptionHandler = recentServersCorruptionHandler,
            scope = scope,
            produceFile = { file },
        )
        return RecentServersStore(dataStore, clock = { clock })
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        file.delete()
    }

    @Test
    fun recordedServersSurviveARestart() = runBlocking {
        val store = openStore()
        store.recordSuccess(ServerAddress(false, "10.1.1.1"))
        clock = 2_000
        store.recordSuccess(ServerAddress(false, "10.1.1.2", 9000))
        scopes.forEach { it.cancel() } // "close the app"

        val reopened = openStore().servers.first()
        assertEquals(listOf("10.1.1.2", "10.1.1.1"), reopened.map { it.address.host })
        assertEquals(listOf(2_000L, 1_000L), reopened.map { it.lastConnectedAt })
    }

    @Test
    fun removeIsPersisted() = runBlocking {
        val store = openStore()
        store.recordSuccess(ServerAddress(false, "10.1.1.1"))
        store.recordSuccess(ServerAddress(false, "10.1.1.2"))
        store.remove(ServerAddress(false, "10.1.1.1"))
        assertEquals(listOf("10.1.1.2"), store.servers.first().map { it.address.host })
    }

    @Test
    fun corruptedFileStartsEmptyAndIsReplaced() = runBlocking {
        file.writeText("{definitely not json")
        val store = openStore()
        assertEquals(emptyList<Any>(), store.servers.first())
        store.recordSuccess(ServerAddress(false, "10.1.1.1"))
        assertEquals(listOf("10.1.1.1"), store.servers.first().map { it.address.host })
    }

    @Test
    fun missingFileIsEmpty() = runBlocking {
        assertEquals(emptyList<Any>(), openStore().servers.first())
    }
}
