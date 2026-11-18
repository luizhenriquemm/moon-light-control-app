package br.com.moonaudio.moonlightcontrol.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecentServersTest {

    private fun addr(host: String, port: Int = DEFAULT_SERVER_PORT, https: Boolean = false) =
        ServerAddress(https, host, port)

    @Test
    fun newServerGoesOnTop() {
        var list = RecentServers.recordSuccess(emptyList(), addr("10.1.1.1"), now = 1)
        list = RecentServers.recordSuccess(list, addr("10.1.1.2"), now = 2)
        assertEquals(listOf("10.1.1.2", "10.1.1.1"), list.map { it.address.host })
        assertEquals(listOf(2L, 1L), list.map { it.lastConnectedAt })
    }

    @Test
    fun reconnectingMovesToTopWithoutDuplicate() {
        var list = emptyList<RecentServer>()
        list = RecentServers.recordSuccess(list, addr("10.1.1.1"), now = 1)
        list = RecentServers.recordSuccess(list, addr("10.1.1.2"), now = 2)
        list = RecentServers.recordSuccess(list, addr("10.1.1.1"), now = 3)
        assertEquals(listOf("10.1.1.1", "10.1.1.2"), list.map { it.address.host })
        assertEquals(3L, list.first().lastConnectedAt)
    }

    @Test
    fun samehostOnAnotherPortOrSchemeIsAnotherServer() {
        var list = emptyList<RecentServer>()
        list = RecentServers.recordSuccess(list, addr("10.1.1.1"), now = 1)
        list = RecentServers.recordSuccess(list, addr("10.1.1.1", port = 9000), now = 2)
        list = RecentServers.recordSuccess(list, addr("10.1.1.1", https = true), now = 3)
        assertEquals(3, list.size)
    }

    @Test
    fun keepsAtMostFiveDroppingTheOldest() {
        var list = emptyList<RecentServer>()
        for (i in 1..7) list = RecentServers.recordSuccess(list, addr("10.1.1.$i"), now = i.toLong())
        assertEquals(MAX_RECENT_SERVERS, list.size)
        assertEquals(listOf("10.1.1.7", "10.1.1.6", "10.1.1.5", "10.1.1.4", "10.1.1.3"), list.map { it.address.host })
    }

    @Test
    fun removeDropsOnlyThatServer() {
        var list = emptyList<RecentServer>()
        list = RecentServers.recordSuccess(list, addr("10.1.1.1"), now = 1)
        list = RecentServers.recordSuccess(list, addr("10.1.1.2"), now = 2)
        list = RecentServers.remove(list, addr("10.1.1.1"))
        assertEquals(listOf("10.1.1.2"), list.map { it.address.host })
        assertEquals(list, RecentServers.remove(list, addr("10.9.9.9")))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        var list = emptyList<RecentServer>()
        list = RecentServers.recordSuccess(list, addr("10.1.1.1"), now = 100)
        list = RecentServers.recordSuccess(list, addr("fe80::1", port = 9000, https = true), now = 200)
        list = RecentServers.recordSuccess(list, addr("stage-pi.local"), now = 300)
        assertEquals(list, RecentServers.decode(RecentServers.encode(list)))
    }

    @Test
    fun decodeEmptyOrBlankIsEmptyList() {
        assertEquals(emptyList(), RecentServers.decode(""))
        assertEquals(emptyList(), RecentServers.decode("  "))
        assertEquals(emptyList(), RecentServers.decode(RecentServers.encode(emptyList())))
    }

    @Test
    fun decodeCorruptedTextIsNull() {
        assertNull(RecentServers.decode("{not json"))
        assertNull(RecentServers.decode("[1,2,3]"))
        assertNull(RecentServers.decode("""{"servers":[]}""")) // version is required
    }

    @Test
    fun decodeSkipsInvalidEntriesAndToleratesUnknownFields() {
        val text = """
            {"version":1,"future":true,"servers":[
              {"https":false,"host":"10.1.1.1","port":8080,"lastConnectedAt":5,"name":"Igreja"},
              {"https":false,"host":"10.1.1.999","port":8080,"lastConnectedAt":4},
              {"https":false,"host":"10.1.1.2","port":0,"lastConnectedAt":3},
              {"https":false,"host":"STAGE.local","port":8080,"lastConnectedAt":2},
              {"host":"10.1.1.3","lastConnectedAt":1}
            ]}
        """.trimIndent()
        val list = RecentServers.decode(text)!!
        assertEquals(listOf("10.1.1.1", "10.1.1.3"), list.map { it.address.host })
        assertEquals(ServerAddress(false, "10.1.1.3", DEFAULT_SERVER_PORT), list[1].address)
    }

    @Test
    fun decodeRestoresOrderSizeAndUniqueness() {
        val entries = (1..7).joinToString(",") { """{"host":"10.1.1.$it","lastConnectedAt":$it}""" } +
            """,{"host":"10.1.1.7","lastConnectedAt":1}"""
        val list = RecentServers.decode("""{"version":1,"servers":[$entries]}""")!!
        assertEquals(listOf("10.1.1.7", "10.1.1.6", "10.1.1.5", "10.1.1.4", "10.1.1.3"), list.map { it.address.host })
        assertEquals(7L, list.first().lastConnectedAt)
    }
}
