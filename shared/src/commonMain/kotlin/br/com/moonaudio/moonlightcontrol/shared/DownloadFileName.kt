package br.com.moonaudio.moonlightcontrol.shared

/**
 * File name for a download (show export, BRIEF §4): from `Content-Disposition` — preferring the
 * UTF-8 `filename*` form, since show names may have accents — else the URL's last path segment,
 * else [fallback]. The result is safe as a file name (no path separators or control characters).
 */
object DownloadFileName {

    fun from(contentDisposition: String?, url: String?, fallback: String = "download"): String {
        val header = contentDisposition.orEmpty()
        val candidates = listOfNotNull(
            extendedFilename(header),
            plainFilename(header),
            url?.let(::lastPathSegment),
        )
        return candidates.map(::sanitize).firstOrNull { it.isNotEmpty() } ?: fallback
    }

    /** `filename*=UTF-8''Show%20com%20acento%C3%A9.mlcshow` (RFC 6266 / RFC 5987). */
    private fun extendedFilename(header: String): String? {
        val value = parameter(header, "filename*") ?: return null
        val parts = value.split('\'', limit = 3)
        if (parts.size != 3) return null
        val charset = parts[0].lowercase()
        if (charset != "utf-8" && charset != "us-ascii" && charset != "iso-8859-1") return null
        val bytes = percentDecode(parts[2]) ?: return null
        return if (charset == "utf-8") bytes.decodeToString() else bytes.map { (it.toInt() and 0xFF).toChar() }.joinToString("")
    }

    private fun plainFilename(header: String): String? = parameter(header, "filename")

    /** Value of `name=value` / `name="value"` in a header, case-insensitive name. */
    private fun parameter(header: String, name: String): String? {
        var i = 0
        while (i < header.length) {
            // Skip to the next parameter.
            val semicolon = header.indexOf(';', i)
            if (semicolon < 0) return null
            i = semicolon + 1
            while (i < header.length && header[i] == ' ') i++
            val eq = header.indexOf('=', i)
            if (eq < 0) return null
            val key = header.substring(i, eq).trim()
            var j = eq + 1
            while (j < header.length && header[j] == ' ') j++
            val value: String
            if (j < header.length && header[j] == '"') {
                val sb = StringBuilder()
                j++
                while (j < header.length && header[j] != '"') {
                    if (header[j] == '\\' && j + 1 < header.length) j++
                    sb.append(header[j])
                    j++
                }
                value = sb.toString()
                i = j + 1
            } else {
                val end = header.indexOf(';', j).let { if (it < 0) header.length else it }
                value = header.substring(j, end).trim()
                i = end
            }
            if (key.equals(name, ignoreCase = true)) return value
        }
        return null
    }

    private fun lastPathSegment(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?').substringAfter("://", url)
        val segment = path.substringAfter('/', "").substringAfterLast('/')
        if (segment.isEmpty()) return null
        return percentDecode(segment)?.decodeToString()
    }

    private fun percentDecode(text: String): ByteArray? {
        val out = ArrayList<Byte>(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%') {
                if (i + 2 >= text.length) return null
                val byte = text.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                out += byte.toByte()
                i += 3
            } else {
                val end = text.indexOf('%', i).let { if (it < 0) text.length else it }
                text.substring(i, end).encodeToByteArray().forEach { out += it }
                i = end
            }
        }
        return out.toByteArray()
    }

    /** Keeps the name only: no directories, no control or reserved characters, no leading dots. */
    fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base
            .map { c -> if (c.code < 0x20 || c.code == 0x7F || c in "<>:\"|?*") '_' else c }
            .joinToString("")
            .trim()
            .trimStart('.')
        return cleaned.take(MAX_LENGTH)
    }

    private const val MAX_LENGTH = 120
}
