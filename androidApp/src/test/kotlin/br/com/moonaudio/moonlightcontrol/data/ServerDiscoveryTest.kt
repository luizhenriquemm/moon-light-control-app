package br.com.moonaudio.moonlightcontrol.data

import br.com.moonaudio.moonlightcontrol.shared.Ipv4Interface
import br.com.moonaudio.moonlightcontrol.shared.ServerHealth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class ServerDiscoveryTest {

    private val health = ServerHealth("4b9dade", "4b9dade")

    @Test
    fun noLocalNetworkMeansNothingToScan() = runBlocking {
        val discovery = ServerDiscovery(networks = { emptyList() }, probe = { _, _ -> error("must not probe") })
        assertEquals(emptyList<String>(), discovery.ranges())
        assertEquals(emptyList<Any>(), discovery.scan(onFound = {}))
    }

    @Test
    fun scansEveryLocalNetworkOnceAndReportsServers() = runBlocking {
        val probed = Collections.synchronizedList(mutableListOf<String>())
        val discovery = ServerDiscovery(
            networks = {
                listOf(
                    LocalIpv4Network(null, Ipv4Interface("10.1.1.202", 24)),
                    LocalIpv4Network(null, Ipv4Interface("10.1.1.202", 24)), // same range twice
                    LocalIpv4Network(null, Ipv4Interface("192.168.0.5", 30)),
                )
            },
            probe = { address, _ ->
                probed += address.host
                if (address.host == "10.1.1.200" || address.host == "192.168.0.6") health else null
            },
        )

        assertEquals(listOf("10.1.1.0/24", "192.168.0.4/30"), discovery.ranges())

        val reported = Collections.synchronizedList(mutableListOf<String>())
        val found = discovery.scan(onFound = { reported += it.address.host })

        assertEquals(listOf("10.1.1.200", "192.168.0.6"), found.map { it.address.host })
        assertEquals(found.map { it.address.host }.toSet(), reported.toSet())
        assertEquals("each host is probed once", probed.size, probed.toSet().size)
        assertEquals(253 + 1, probed.size)
        assertTrue(found.all { it.address.port == 8080 && !it.address.https && it.health == health })
    }
}
