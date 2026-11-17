package br.com.moonaudio.moonlightcontrol.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class ServerAddressTest {

    private fun valid(input: String): ServerAddress {
        val result = ServerAddress.parse(input)
        if (result !is ParseResult.Valid) fail("expected valid for '$input', got $result")
        return result.address
    }

    private fun invalid(input: String): ParseError {
        val result = ServerAddress.parse(input)
        if (result !is ParseResult.Invalid) fail("expected invalid for '$input', got $result")
        return result.error
    }

    private fun rootUrl(input: String) = valid(input).rootUrl

    @Test
    fun ipv4WithDefaults() {
        assertEquals(ServerAddress(https = false, host = "10.1.1.20", port = 8080), valid("10.1.1.20"))
        assertEquals("http://10.1.1.20:8080/", rootUrl("10.1.1.20"))
    }

    @Test
    fun surroundingSpacesAreIgnored() {
        assertEquals("http://10.1.1.20:8080/", rootUrl("  10.1.1.20 \t"))
    }

    @Test
    fun explicitPort() {
        assertEquals("http://10.1.1.20:9000/", rootUrl("10.1.1.20:9000"))
    }

    @Test
    fun schemeWithoutPortStillUsesServerDefaultPort() {
        assertEquals("http://10.1.1.20:8080/", rootUrl("http://10.1.1.20"))
        assertEquals("https://10.1.1.20:8080/", rootUrl("https://10.1.1.20"))
        assertEquals("https://10.1.1.20:8080/", rootUrl("HTTPS://10.1.1.20"))
    }

    @Test
    fun pathQueryAndFragmentAreDropped() {
        assertEquals("http://10.1.1.20:8080/", rootUrl("10.1.1.20/"))
        assertEquals("http://10.1.1.20:9000/", rootUrl("http://10.1.1.20:9000/#/faders"))
        assertEquals("http://10.1.1.20:8080/", rootUrl("10.1.1.20?x=1"))
    }

    @Test
    fun hostnamesAreLowerCased() {
        assertEquals("http://stage-pi.local:8080/", rootUrl("Stage-Pi.LOCAL"))
        assertEquals("http://localhost:8080/", rootUrl("localhost"))
        assertEquals("http://stage-pi.local:8080/", rootUrl("stage-pi.local."))
    }

    @Test
    fun bareIpv6() {
        assertEquals("fe80::1", valid("fe80::1").host)
        assertEquals("http://[fe80::1]:8080/", rootUrl("fe80::1"))
    }

    @Test
    fun bracketedIpv6WithPort() {
        assertEquals("http://[fe80::1]:9000/", rootUrl("[fe80::1]:9000"))
        assertEquals("https://[2001:db8::7]:8080/", rootUrl("https://[2001:db8::7]"))
    }

    @Test
    fun ipv6IsCanonicalized() {
        assertEquals("fe80::1", valid("FE80:0000:0000:0000:0000:0000:0000:0001").host)
        assertEquals("2001:db8::1:0:0:1", valid("2001:db8:0:0:1:0:0:1").host) // first longest run
        assertEquals("2001:db8:0:1:1:1:1:1", valid("2001:db8:0:1:1:1:1:1").host) // single zero kept
        assertEquals("::", valid("::").host)
        assertEquals("::1", valid("::1").host)
        assertEquals("1::", valid("1::").host)
        assertEquals("::ffff:a01:114", valid("::ffff:10.1.1.20").host)
    }

    @Test
    fun originOmitsSchemeDefaultPort() {
        assertEquals("http://10.1.1.20", valid("10.1.1.20:80").origin)
        assertEquals("https://10.1.1.20", valid("https://10.1.1.20:443").origin)
        assertEquals("https://10.1.1.20:80", valid("https://10.1.1.20:80").origin)
        assertEquals("http://10.1.1.20:8080", valid("10.1.1.20").origin)
    }

    @Test
    fun emptyInput() {
        assertEquals(ParseError.EMPTY, invalid(""))
        assertEquals(ParseError.EMPTY, invalid("   "))
        assertEquals(ParseError.EMPTY, invalid("http://"))
    }

    @Test
    fun unsupportedScheme() {
        assertEquals(ParseError.UNSUPPORTED_SCHEME, invalid("ftp://10.1.1.20"))
        assertEquals(ParseError.UNSUPPORTED_SCHEME, invalid("ws://10.1.1.20"))
    }

    @Test
    fun invalidPorts() {
        for (input in listOf("10.1.1.20:", "10.1.1.20:0", "10.1.1.20:65536", "10.1.1.20:abc",
            "10.1.1.20:-1", "10.1.1.20:+80", "[fe80::1]:99999")) {
            assertEquals(ParseError.INVALID_PORT, invalid(input), input)
        }
        assertEquals(65535, valid("10.1.1.20:65535").port)
        assertEquals(1, valid("10.1.1.20:1").port)
    }

    @Test
    fun invalidIpv4() {
        for (input in listOf("10.1.1", "10.1.1.256", "10.1.1.20.5", "010.1.1.20", "10..1.20", "1.2.3.4.")) {
            assertEquals(ParseError.INVALID_HOST, invalid(input), input)
        }
        assertEquals("0.0.0.0", valid("0.0.0.0").host)
    }

    @Test
    fun invalidIpv6() {
        for (input in listOf("fe80:::1", "1::2::3", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7", "12345::1",
            "fe80::g", "fe80::1%wlan0", "[fe80::1", "[fe80::1]x", "[10.1.1.20]", "1:2:3:4:5:6:7::8:9",
            "::1.2.3.4:5", "fe80::+1")) {
            assertEquals(ParseError.INVALID_HOST, invalid(input), input)
        }
    }

    @Test
    fun invalidHostnames() {
        for (input in listOf("stage_pi", "-stage.local", "stage-.local", "a..b", "user@10.1.1.20",
            "stage pi", "${"a".repeat(64)}.local")) {
            assertEquals(ParseError.INVALID_HOST, invalid(input), input)
        }
    }

    @Test
    fun labelForLists() {
        assertEquals("10.1.1.20", valid("10.1.1.20").label)
        assertEquals("10.1.1.20:9000", valid("10.1.1.20:9000").label)
        assertEquals("https://stage-pi.local", valid("https://Stage-Pi.local").label)
        assertEquals("[fe80::1]", valid("fe80::1").label)
        assertEquals("https://[fe80::1]:443", valid("https://[fe80::1]:443").label)
    }

    @Test
    fun fromPartsAcceptsOnlyValidCanonicalParts() {
        assertEquals(ServerAddress(false, "10.1.1.20", 8080), ServerAddress.fromParts(false, "10.1.1.20", 8080))
        assertEquals(ServerAddress(true, "fe80::1", 9000), ServerAddress.fromParts(true, "fe80::1", 9000))
        assertEquals(null, ServerAddress.fromParts(false, "10.1.1.256", 8080))
        assertEquals(null, ServerAddress.fromParts(false, "Stage.local", 8080)) // not canonical
        assertEquals(null, ServerAddress.fromParts(false, "FE80::1", 8080))
        assertEquals(null, ServerAddress.fromParts(false, "[fe80::1]", 8080))
        assertEquals(null, ServerAddress.fromParts(false, "10.1.1.20", 0))
        assertEquals(null, ServerAddress.fromParts(false, "", 8080))
    }

    @Test
    fun constructorRejectsBadPort() {
        assertFailsWith<IllegalArgumentException> { ServerAddress(false, "10.1.1.20", 0) }
        assertFailsWith<IllegalArgumentException> { ServerAddress(false, "10.1.1.20", 70000) }
    }
}
