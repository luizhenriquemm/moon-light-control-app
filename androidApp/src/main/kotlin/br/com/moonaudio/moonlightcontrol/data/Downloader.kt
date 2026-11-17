package br.com.moonaudio.moonlightcontrol.data

import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * Downloads a file the web UI offers (show export, BRIEF §5) with the page's login cookie and user
 * agent, and writes it to [output]. For HTTPS with a self-signed certificate, [pinnedSha256] is the
 * fingerprint the user accepted: only that exact certificate is trusted. Returns the byte count.
 */
suspend fun download(
    url: String,
    cookie: String?,
    userAgent: String?,
    pinnedSha256: String?,
    output: OutputStream,
): Long = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        if (connection is HttpsURLConnection && pinnedSha256 != null) {
            connection.sslSocketFactory = pinnedSocketFactory(pinnedSha256)
            // The certificate itself is pinned, so its names need not match the address.
            connection.setHostnameVerifier { _, _ -> true }
        }
        connection.connectTimeout = 5_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        if (!cookie.isNullOrEmpty()) connection.setRequestProperty("Cookie", cookie)
        if (!userAgent.isNullOrEmpty()) connection.setRequestProperty("User-Agent", userAgent)
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status")
        connection.inputStream.use { it.copyTo(output) }
    } finally {
        connection.disconnect()
    }
}

/**
 * TLS that accepts exactly one certificate, identified by its SHA-256 fingerprint (the self-signed
 * certificate the user accepted, BRIEF §5). Stricter than the system trust store for this server,
 * not looser: every other certificate is rejected.
 */
@SuppressLint("CustomX509TrustManager") // Pinning: rejects everything but the accepted certificate.
fun pinnedSocketFactory(pinnedSha256: String): SSLSocketFactory {
    val pin = pinnedSha256.lowercase()
    val trustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            throw CertificateException("client certificates are not used")

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            val leaf = chain.firstOrNull() ?: throw CertificateException("no certificate")
            if (sha256Hex(leaf.encoded) != pin) throw CertificateException("certificate does not match the trusted one")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    return SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory
}
