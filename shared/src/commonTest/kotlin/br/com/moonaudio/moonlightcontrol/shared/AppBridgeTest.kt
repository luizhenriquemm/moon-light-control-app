package br.com.moonaudio.moonlightcontrol.shared

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppBridgeTest {

    @Test
    fun readyWithAndWithoutServerVersion() {
        assertEquals(
            AppBridge.Incoming.Ready("4b9dade"),
            AppBridge.parse("""{"v":1,"type":"ready","serverVersion":"4b9dade"}"""),
        )
        assertEquals(AppBridge.Incoming.Ready(null), AppBridge.parse("""{"v":1,"type":"ready"}"""))
        assertEquals(AppBridge.Incoming.Ready(null), AppBridge.parse("""{"v":1,"type":"ready","serverVersion":7}"""))
    }

    @Test
    fun logoutAndSwitchHost() {
        assertEquals(AppBridge.Incoming.Logout, AppBridge.parse("""{"v":1,"type":"logout"}"""))
        assertEquals(AppBridge.Incoming.SwitchHost, AppBridge.parse("""{"v":1,"type":"switchHost"}"""))
    }

    @Test
    fun newerProtocolAndExtraFieldsAreAccepted() {
        assertEquals(AppBridge.Incoming.Logout, AppBridge.parse("""{"v":2,"type":"logout","reason":"everywhere"}"""))
        assertEquals(AppBridge.Incoming.SwitchHost, AppBridge.parse("""{"type":"switchHost","v":1.5,"x":[1]}"""))
    }

    @Test
    fun invalidMessagesAreIgnored() {
        for (message in listOf(
            null,
            "",
            "logout",
            "[]",
            "\"logout\"",
            """{"type":"logout"}""",            // no v
            """{"v":0,"type":"logout"}""",      // v < 1
            """{"v":"1","type":"logout"}""",    // v not a number
            """{"v":1}""",                      // no type
            """{"v":1,"type":1}""",             // type not a string
            """{"v":1,"type":"hello"}""",       // the app's own message
            """{"v":1,"type":"selfDestruct"}""", // unknown type
        )) {
            assertNull(AppBridge.parse(message), message)
        }
    }

    @Test
    fun helloAnnouncesTheFeatures() {
        val hello = Json.parseToJsonElement(AppBridge.hello("0.1.0")).jsonObject
        assertEquals("1", hello["v"]!!.jsonPrimitive.content)
        assertEquals("hello", hello["type"]!!.jsonPrimitive.content)
        assertEquals("android", hello["app"]!!.jsonPrimitive.content)
        assertEquals("0.1.0", hello["appVersion"]!!.jsonPrimitive.content)
        assertEquals(listOf("logout", "switchHost"), hello["features"]!!.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun helloEscapesTheVersion() {
        val hello = Json.parseToJsonElement(AppBridge.hello("1.0 \"beta\"")).jsonObject
        assertEquals("1.0 \"beta\"", hello["appVersion"]!!.jsonPrimitive.content)
    }
}
