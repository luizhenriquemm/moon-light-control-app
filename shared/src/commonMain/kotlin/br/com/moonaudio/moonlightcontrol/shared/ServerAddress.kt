package br.com.moonaudio.moonlightcontrol.shared

/** Port the MoonLightControl server listens on unless configured otherwise. */
const val DEFAULT_SERVER_PORT: Int = 8080

/**
 * Where a MoonLightControl server is reached.
 *
 * [host] is always canonical, as a browser would serialize it: hostnames in lower case, IPv4 as a
 * dotted quad, IPv6 in compressed lower-case form **without brackets**. Keeping it canonical lets
 * [origin] match the origin the WebView computes for the page (needed by the app bridge, BRIEF §6).
 */
data class ServerAddress(
    val https: Boolean,
    val host: String,
    val port: Int = DEFAULT_SERVER_PORT,
) {
    init {
        require(port in 1..65535) { "port out of range: $port" }
    }

    val scheme: String get() = if (https) "https" else "http"

    /** Web origin, e.g. `http://10.1.1.20:8080` or `http://[fe80::1]:8080` (no trailing slash). */
    val origin: String
        get() {
            val hostPart = if (':' in host) "[$host]" else host
            val defaultPort = if (https) 443 else 80
            return if (port == defaultPort) "$scheme://$hostPart" else "$scheme://$hostPart:$port"
        }

    /** The web UI's address: the server root. */
    val rootUrl: String get() = "$origin/"

    /**
     * Short text for lists: `10.1.1.20`, `stage-pi.local:9000`, `https://[fe80::1]`. The default
     * port (8080) and `http://` are left out.
     */
    val label: String
        get() {
            val hostPart = if (':' in host) "[$host]" else host
            val portPart = if (port == DEFAULT_SERVER_PORT) "" else ":$port"
            val schemePart = if (https) "https://" else ""
            return "$schemePart$hostPart$portPart"
        }

    companion object {
        /**
         * Builds an address from stored parts, or null when they are not valid and canonical
         * (e.g. a corrupted or hand-edited file).
         */
        fun fromParts(https: Boolean, host: String, port: Int): ServerAddress? {
            if (port !in 1..65535 || canonicalHost(host) != host) return null
            return ServerAddress(https, host, port)
        }

        /**
         * Parses what a user typed: `10.1.1.20`, `10.1.1.20:9000`, `stage-pi.local`, `fe80::1`,
         * `[fe80::1]:9000`, `https://10.1.1.20`, … Surrounding spaces are ignored, the port
         * defaults to [DEFAULT_SERVER_PORT] (also with an explicit scheme), the scheme to `http`, and
         * any path, query or fragment is dropped (the app always opens the server root).
         */
        fun parse(input: String): ParseResult {
            var rest = input.trim()
            if (rest.isEmpty()) return ParseResult.Invalid(ParseError.EMPTY)

            var https = false
            val schemeEnd = rest.indexOf("://")
            if (schemeEnd >= 0) {
                https = when (rest.substring(0, schemeEnd).lowercase()) {
                    "http" -> false
                    "https" -> true
                    else -> return ParseResult.Invalid(ParseError.UNSUPPORTED_SCHEME)
                }
                rest = rest.substring(schemeEnd + 3)
            }

            // Drop path, query and fragment.
            val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
            if (authorityEnd >= 0) rest = rest.substring(0, authorityEnd)
            if (rest.isEmpty()) return ParseResult.Invalid(ParseError.EMPTY)
            if ('@' in rest) return ParseResult.Invalid(ParseError.INVALID_HOST)

            val hostText: String
            val portText: String?
            when {
                rest.startsWith("[") -> {
                    val close = rest.indexOf(']')
                    if (close < 0) return ParseResult.Invalid(ParseError.INVALID_HOST)
                    hostText = rest.substring(1, close)
                    val after = rest.substring(close + 1)
                    portText = when {
                        after.isEmpty() -> null
                        after.startsWith(":") -> after.substring(1)
                        else -> return ParseResult.Invalid(ParseError.INVALID_HOST)
                    }
                    if (':' !in hostText) return ParseResult.Invalid(ParseError.INVALID_HOST)
                }
                rest.count { it == ':' } > 1 -> { // bare IPv6, no port possible
                    hostText = rest
                    portText = null
                }
                else -> {
                    val colon = rest.indexOf(':')
                    hostText = if (colon >= 0) rest.substring(0, colon) else rest
                    portText = if (colon >= 0) rest.substring(colon + 1) else null
                }
            }

            val port = if (portText == null) {
                DEFAULT_SERVER_PORT
            } else {
                parsePort(portText) ?: return ParseResult.Invalid(ParseError.INVALID_PORT)
            }

            val host = canonicalHost(hostText) ?: return ParseResult.Invalid(ParseError.INVALID_HOST)
            return ParseResult.Valid(ServerAddress(https, host, port))
        }

        private fun parsePort(text: String): Int? {
            if (text.isEmpty() || text.length > 5 || !text.all { it in '0'..'9' }) return null
            return text.toInt().takeIf { it in 1..65535 }
        }

        private fun canonicalHost(text: String): String? = when {
            ':' in text -> Ipv6.canonical(text)
            text.isNotEmpty() && text.all { it in '0'..'9' || it == '.' } -> Ipv4.canonical(text)
            else -> Hostname.canonical(text)
        }
    }
}

sealed interface ParseResult {
    data class Valid(val address: ServerAddress) : ParseResult
    data class Invalid(val error: ParseError) : ParseResult
}

enum class ParseError {
    EMPTY,
    UNSUPPORTED_SCHEME,
    INVALID_HOST,
    INVALID_PORT,
}

internal object Ipv4 {
    /** Dotted quad only. Leading zeros are refused: browsers would read `010` as octal. */
    fun canonical(text: String): String? = parseOctets(text)?.joinToString(".")

    fun parseOctets(text: String): List<Int>? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        return parts.map { part ->
            if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' }) return null
            if (part.length > 1 && part[0] == '0') return null
            part.toInt().takeIf { it <= 255 } ?: return null
        }
    }
}

internal object Ipv6 {
    /**
     * Parses an IPv6 address (`::` compression and a trailing dotted IPv4 allowed; zone IDs such as
     * `%wlan0` are refused) and serializes it like a browser does: lower case, no leading zeros,
     * the longest run of two or more zero groups compressed to `::`.
     */
    fun canonical(text: String): String? {
        val groups = parse(text) ?: return null
        return format(groups)
    }

    private fun parse(text: String): IntArray? {
        if (text.isEmpty() || '%' in text) return null
        val doubleColon = text.indexOf("::")
        if (doubleColon >= 0 && text.indexOf("::", doubleColon + 1) >= 0) return null

        val head: List<String>
        val tail: List<String>
        if (doubleColon >= 0) {
            val left = text.substring(0, doubleColon)
            val right = text.substring(doubleColon + 2)
            head = if (left.isEmpty()) emptyList() else left.split(':')
            tail = if (right.isEmpty()) emptyList() else right.split(':')
        } else {
            head = text.split(':')
            tail = emptyList()
        }

        val headGroups = parseGroups(head, allowIpv4Last = tail.isEmpty() && doubleColon < 0)
            ?: return null
        val tailGroups = parseGroups(tail, allowIpv4Last = true) ?: return null
        val total = headGroups.size + tailGroups.size
        return when {
            doubleColon < 0 && total == 8 -> (headGroups + tailGroups).toIntArray()
            doubleColon >= 0 && total <= 7 ->
                (headGroups + List(8 - total) { 0 } + tailGroups).toIntArray()
            else -> null
        }
    }

    private fun parseGroups(parts: List<String>, allowIpv4Last: Boolean): List<Int>? {
        val groups = mutableListOf<Int>()
        parts.forEachIndexed { index, part ->
            if (allowIpv4Last && index == parts.lastIndex && '.' in part) {
                val octets = Ipv4.parseOctets(part) ?: return null
                groups += (octets[0] shl 8) or octets[1]
                groups += (octets[2] shl 8) or octets[3]
            } else {
                if (part.isEmpty() || part.length > 4 || !part.all(::isHexDigit)) return null
                groups += part.toInt(16)
            }
        }
        return groups
    }

    private fun isHexDigit(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun format(groups: IntArray): String {
        // Longest run of zero groups (length >= 2); the first one wins a tie.
        var bestStart = -1
        var bestLength = 0
        var index = 0
        while (index < groups.size) {
            if (groups[index] == 0) {
                val start = index
                while (index < groups.size && groups[index] == 0) index++
                if (index - start > bestLength) {
                    bestStart = start
                    bestLength = index - start
                }
            } else {
                index++
            }
        }
        if (bestLength < 2) return groups.joinToString(":") { it.toString(16) }

        val left = groups.copyOfRange(0, bestStart).joinToString(":") { it.toString(16) }
        val right = groups.copyOfRange(bestStart + bestLength, groups.size)
            .joinToString(":") { it.toString(16) }
        return "$left::$right"
    }
}

internal object Hostname {
    /** DNS names (`stage-pi.local`, `localhost`): letters, digits and hyphens, case-insensitive. */
    fun canonical(text: String): String? {
        val name = text.removeSuffix(".").lowercase()
        if (name.isEmpty() || name.length > 253) return null
        val labels = name.split('.')
        val valid = labels.all { label ->
            label.isNotEmpty() && label.length <= 63 &&
                label.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } &&
                !label.startsWith('-') && !label.endsWith('-')
        }
        // An all-numeric last label is a malformed IPv4 address (e.g. "10.1.1"), not a name.
        if (!valid || labels.last().all { it in '0'..'9' }) return null
        return name
    }
}
