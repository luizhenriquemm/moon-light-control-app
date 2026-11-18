package br.com.moonaudio.moonlightcontrol.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Self-signed HTTPS, trust on first use (BRIEF §5): the SHA-256 fingerprint of the certificate the
 * user accepted, per server origin. A load proceeds only when the server presents that exact
 * certificate; any other certificate is a loud warning, never a silent accept.
 */
object TrustedCertificates {

    /** What to do with the certificate a server presented. */
    enum class Verdict {
        /** Same certificate as accepted before: proceed. */
        TRUSTED,

        /** Never accepted one for this server: ask the user. */
        UNKNOWN,

        /** A different certificate than the accepted one: warn loudly. */
        CHANGED,
    }

    fun verdict(trusted: Map<String, String>, origin: String, fingerprint: String): Verdict {
        val known = trusted[origin] ?: return Verdict.UNKNOWN
        val presented = runCatching { normalize(fingerprint) }.getOrNull() ?: return Verdict.CHANGED
        return if (known == presented) Verdict.TRUSTED else Verdict.CHANGED
    }

    fun trust(trusted: Map<String, String>, origin: String, fingerprint: String): Map<String, String> =
        trusted + (origin to normalize(fingerprint))

    /** `AB:CD:…` for display: 32 bytes as upper-case hex pairs. */
    fun formatFingerprint(sha256: ByteArray): String =
        sha256.joinToString(":") { b -> (b.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

    /** Fingerprints are stored as 64 lower-case hex digits; throws if [text] is not a SHA-256 one. */
    fun normalize(text: String): String {
        val hex = text.filter { it != ':' && it != ' ' }.lowercase()
        require(hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }) { "not a SHA-256 fingerprint" }
        return hex
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(trusted: Map<String, String>): String =
        json.encodeToString(StoredTrust.serializer(), StoredTrust(version = 1, certificates = trusted))

    /** Null when unreadable; invalid entries are dropped. */
    fun decode(text: String): Map<String, String>? {
        if (text.isBlank()) return emptyMap()
        val stored = try {
            json.decodeFromString(StoredTrust.serializer(), text)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        return stored.certificates.mapNotNull { (origin, fp) ->
            runCatching { origin to normalize(fp) }.getOrNull()
        }.toMap()
    }
}

@Serializable
private data class StoredTrust(val version: Int, val certificates: Map<String, String> = emptyMap())
