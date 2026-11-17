package br.com.moonaudio.moonlightcontrol.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TrustedCertificatesTest {

    private val a = "ab".repeat(32)
    private val b = "cd".repeat(32)
    private val origin = "https://10.1.1.20:8080"

    @Test
    fun verdicts() {
        val trusted = TrustedCertificates.trust(emptyMap(), origin, a)
        assertEquals(TrustedCertificates.Verdict.TRUSTED, TrustedCertificates.verdict(trusted, origin, a))
        assertEquals(TrustedCertificates.Verdict.TRUSTED, TrustedCertificates.verdict(trusted, origin, a.uppercase()))
        assertEquals(
            TrustedCertificates.Verdict.TRUSTED,
            TrustedCertificates.verdict(trusted, origin, TrustedCertificates.formatFingerprint(ByteArray(32) { 0xAB.toByte() })),
        )
        assertEquals(TrustedCertificates.Verdict.CHANGED, TrustedCertificates.verdict(trusted, origin, b))
        assertEquals(TrustedCertificates.Verdict.CHANGED, TrustedCertificates.verdict(trusted, origin, "garbage"))
        assertEquals(TrustedCertificates.Verdict.UNKNOWN, TrustedCertificates.verdict(trusted, "https://10.1.1.21:8080", a))
        assertEquals(TrustedCertificates.Verdict.UNKNOWN, TrustedCertificates.verdict(emptyMap(), origin, a))
    }

    @Test
    fun trustReplacesAndNormalizes() {
        var trusted = TrustedCertificates.trust(emptyMap(), origin, a)
        trusted = TrustedCertificates.trust(trusted, origin, TrustedCertificates.formatFingerprint(ByteArray(32) { 0xCD.toByte() }))
        assertEquals(mapOf(origin to b), trusted)
    }

    @Test
    fun formatting() {
        val bytes = ByteArray(32) { it.toByte() }
        val text = TrustedCertificates.formatFingerprint(bytes)
        assertEquals("00:01:02:03", text.take(11))
        assertEquals(32 * 3 - 1, text.length)
        assertEquals(bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }, TrustedCertificates.normalize(text))
    }

    @Test
    fun normalizeRejectsNonFingerprints() {
        assertFailsWith<IllegalArgumentException> { TrustedCertificates.normalize("abc") }
        assertFailsWith<IllegalArgumentException> { TrustedCertificates.normalize("zz".repeat(32)) }
    }

    @Test
    fun encodeDecode() {
        val trusted = mapOf(origin to a, "https://[fe80::1]:8080" to b)
        assertEquals(trusted, TrustedCertificates.decode(TrustedCertificates.encode(trusted)))
        assertEquals(emptyMap(), TrustedCertificates.decode(""))
        assertNull(TrustedCertificates.decode("{nope"))
        assertEquals(
            mapOf(origin to a),
            TrustedCertificates.decode("""{"version":1,"certificates":{"$origin":"${a.uppercase()}","x":"bad"},"new":1}"""),
        )
    }
}
