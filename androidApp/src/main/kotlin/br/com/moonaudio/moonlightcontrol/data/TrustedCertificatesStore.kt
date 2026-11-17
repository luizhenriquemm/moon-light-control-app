package br.com.moonaudio.moonlightcontrol.data

import android.content.Context
import android.net.http.SslCertificate
import android.os.Build
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import br.com.moonaudio.moonlightcontrol.shared.TrustedCertificates
import kotlinx.coroutines.flow.Flow
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Certificates the user accepted (trust on first use), per server origin. */
class TrustedCertificatesStore(private val dataStore: DataStore<Map<String, String>>) {
    val certificates: Flow<Map<String, String>> = dataStore.data

    suspend fun trust(origin: String, fingerprint: String) {
        dataStore.updateData { TrustedCertificates.trust(it, origin, fingerprint) }
    }
}

object TrustedCertificatesSerializer : Serializer<Map<String, String>> {
    override val defaultValue: Map<String, String> = emptyMap()

    override suspend fun readFrom(input: InputStream): Map<String, String> =
        TrustedCertificates.decode(input.readBytes().decodeToString())
            ?: throw CorruptionException("Unreadable trusted certificates file")

    override suspend fun writeTo(t: Map<String, String>, output: OutputStream) {
        output.write(TrustedCertificates.encode(t).encodeToByteArray())
    }
}

/** A corrupted file forgets the accepted certificates: the user is asked again, never trusted blindly. */
val Context.trustedCertificatesDataStore: DataStore<Map<String, String>> by dataStore(
    fileName = "trusted_certificates.json",
    serializer = TrustedCertificatesSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyMap() },
)

/** SHA-256 of the certificate's DER encoding, as 64 lower-case hex digits; null if unavailable. */
fun SslCertificate.sha256(): String? {
    val x509 = toX509() ?: return null
    return sha256Hex(x509.encoded)
}

fun sha256Hex(der: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }

private fun SslCertificate.toX509(): X509Certificate? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        x509Certificate
    } else {
        // Before API 29 the DER bytes are only reachable through the saved state.
        SslCertificate.saveState(this).getByteArray("x509-certificate")?.let { der ->
            runCatching {
                CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
            }.getOrNull()
        }
    }
