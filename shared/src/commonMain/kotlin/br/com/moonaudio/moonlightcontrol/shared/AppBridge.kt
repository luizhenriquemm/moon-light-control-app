package br.com.moonaudio.moonlightcontrol.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

/**
 * App ↔ web UI bridge, protocol 1 (BRIEF §6; server side: `web/src/lib/appBridge.ts`).
 * Messages are JSON strings `{"v": <protocol>, "type": "…", …}`. Unknown types, extra fields and any
 * `v` ≥ 1 are accepted, so the app and the server can be updated in any order.
 */
object AppBridge {
    /** Name of the object injected into the page (`window.MoonLightApp`). */
    const val OBJECT_NAME: String = "MoonLightApp"
    const val PROTOCOL: Int = 1

    /** Features announced in `hello`: the web UI shows the matching options only. */
    val FEATURES: List<String> = listOf("logout", "switchHost")

    /** Messages the app reacts to. */
    sealed interface Incoming {
        /** The page loaded; [serverVersion] is null when its health request failed. */
        data class Ready(val serverVersion: String?) : Incoming

        /** Explicit logout (server already cleared the cookie): back to the start screen. */
        data object Logout : Incoming

        /** "Change server" (login kept): back to the start screen. */
        data object SwitchHost : Incoming
    }

    /** Parses a message from the page; null for anything that is not a known protocol message. */
    fun parse(message: String?): Incoming? {
        if (message == null) return null
        val obj: JsonObject = try {
            Json.parseToJsonElement(message).jsonObject
        } catch (e: Exception) { // not JSON, or not an object
            return null
        }
        val v = (obj["v"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return null
        if (v < PROTOCOL) return null
        val type = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return when (type) {
            "ready" -> Incoming.Ready(
                (obj["serverVersion"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull,
            )
            "logout" -> Incoming.Logout
            "switchHost" -> Incoming.SwitchHost
            else -> null
        }
    }

    /** The reply to every `ready`. */
    fun hello(appVersion: String): String = JsonObject(
        mapOf(
            "v" to JsonPrimitive(PROTOCOL),
            "type" to JsonPrimitive("hello"),
            "app" to JsonPrimitive("android"),
            "appVersion" to JsonPrimitive(appVersion),
            "features" to JsonArray(FEATURES.map { JsonPrimitive(it) }),
        ),
    ).toString()
}
