package br.com.moonaudio.moonlightcontrol.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiscoveryTest {

    @Test
    fun slash24ScansTheOther253Hosts() {
        val hosts = Discovery.candidateHosts(Ipv4Interface("10.1.1.202", 24))
        assertEquals(253, hosts.size)
        assertEquals("10.1.1.1", hosts.first())
        assertEquals("10.1.1.254", hosts.last())
        assertTrue("10.1.1.202" !in hosts)
        assertTrue("10.1.1.0" !in hosts && "10.1.1.255" !in hosts)
    }

    @Test
    fun widerPrefixIsLimitedToOwnSlash24() {
        val hosts = Discovery.candidateHosts(Ipv4Interface("192.168.7.10", 16))
        assertEquals(253, hosts.size)
        assertTrue(hosts.all { it.startsWith("192.168.7.") })
    }

    @Test
    fun narrowerPrefixScansOnlyItsRange() {
        assertEquals(
            listOf("10.0.0.65", "10.0.0.66", "10.0.0.68", "10.0.0.69", "10.0.0.70"),
            Discovery.candidateHosts(Ipv4Interface("10.0.0.67", 29)),
        )
        assertEquals(listOf("10.0.0.2"), Discovery.candidateHosts(Ipv4Interface("10.0.0.1", 30)))
    }

    @Test
    fun scanRangeForDisplay() {
        assertEquals("10.1.1.0/24", Discovery.scanRange(Ipv4Interface("10.1.1.202", 24)))
        assertEquals("192.168.7.0/24", Discovery.scanRange(Ipv4Interface("192.168.7.10", 16)))
        assertEquals("10.0.0.64/29", Discovery.scanRange(Ipv4Interface("10.0.0.67", 29)))
        assertNull(Discovery.scanRange(Ipv4Interface("nope", 24)))
    }

    @Test
    fun noPeersOrInvalidInput() {
        assertEquals(emptyList(), Discovery.candidateHosts(Ipv4Interface("10.0.0.1", 31)))
        assertEquals(emptyList(), Discovery.candidateHosts(Ipv4Interface("10.0.0.1", 32)))
        assertEquals(emptyList(), Discovery.candidateHosts(Ipv4Interface("10.0.0", 24)))
        assertEquals(emptyList(), Discovery.candidateHosts(Ipv4Interface("10.0.0.1", 33)))
    }

    @Test
    fun highAddressesDoNotOverflow() {
        val hosts = Discovery.candidateHosts(Ipv4Interface("255.255.255.1", 24))
        assertEquals("255.255.255.2", hosts.first())
        assertEquals("255.255.255.254", hosts.last())
    }

    @Test
    fun severalInterfacesWithoutDuplicates() {
        val hosts = Discovery.candidateHosts(
            listOf(Ipv4Interface("10.1.1.202", 24), Ipv4Interface("10.1.1.203", 24), Ipv4Interface("10.2.0.1", 30)),
        )
        assertEquals(254 + 1, hosts.size) // 10.1.1.1–254 (each device excludes only itself) + 10.2.0.2
        assertEquals(hosts.size, hosts.toSet().size)
    }

    private val health = ServerHealth(version = "4b9dade", commit = "4b9dade")

    @Test
    fun scanReturnsAnsweringServersInHostOrder() = runTest {
        val hosts = Discovery.candidateHosts(Ipv4Interface("10.1.1.202", 24))
        val answering = setOf("10.1.1.200", "10.1.1.7")
        val found = mutableListOf<String>()
        val result = Discovery.scan(
            hosts = hosts,
            parallelism = hosts.size, // all at once, so the answer order is decided by the delays
            probe = { address ->
                // Later hosts answer first, to check that the result is still in host order.
                delay(1000L - address.host.substringAfterLast('.').toLong())
                if (address.host in answering) health else null
            },
            onFound = { found += it.address.host },
        )
        assertEquals(listOf("10.1.1.7", "10.1.1.200"), result.map { it.address.host })
        assertEquals(listOf("10.1.1.200", "10.1.1.7"), found) // reported as they answer
        assertEquals(ServerAddress(false, "10.1.1.200", 8080), result[1].address)
        assertEquals(health, result[1].health)
    }

    @Test
    fun scanRespectsParallelism() = runTest {
        var inFlight = 0
        var maxInFlight = 0
        Discovery.scan(
            hosts = (1..100).map { "10.0.0.$it" },
            parallelism = 8,
            probe = {
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                delay(10)
                inFlight--
                null
            },
        )
        assertEquals(8, maxInFlight)
    }

    @Test
    fun scanUsesTheGivenPort() = runTest {
        val ports = mutableSetOf<Int>()
        Discovery.scan(hosts = listOf("10.0.0.1"), port = 9000, probe = { ports += it.port; null })
        assertEquals(setOf(9000), ports)
    }

    @Test
    fun parseHealthAcceptsTheServerResponse() {
        assertEquals(
            ServerHealth("4b9dade", "4b9dade"),
            Discovery.parseHealth("""{"status":"ok","version":"4b9dade","commit":"4b9dade","uptimeSeconds":232}"""),
        )
        assertEquals(
            ServerHealth("1.2.0", ""),
            Discovery.parseHealth("""{"status":"ok","version":"1.2.0","commit":"","uptimeSeconds":0,"extra":1}"""),
        )
    }

    @Test
    fun parseHealthRejectsOtherDevices() {
        for (body in listOf(
            "",
            "<html>router login</html>",
            """{"status":"ok"}""",
            """{"status":"degraded","version":"1","commit":"x","uptimeSeconds":1}""",
            """{"status":"ok","version":"","commit":"x","uptimeSeconds":1}""",
            """{"status":"ok","version":"1","commit":"x","uptimeSeconds":-1}""",
            """{"status":"ok","version":1,"commit":"x","uptimeSeconds":"soon"}""",
            "[]",
        )) {
            assertNull(Discovery.parseHealth(body), body)
        }
    }
}
