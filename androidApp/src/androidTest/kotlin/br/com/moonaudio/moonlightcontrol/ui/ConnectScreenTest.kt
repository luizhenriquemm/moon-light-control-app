package br.com.moonaudio.moonlightcontrol.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.moonaudio.moonlightcontrol.shared.DiscoveredServer
import br.com.moonaudio.moonlightcontrol.shared.RecentServer
import br.com.moonaudio.moonlightcontrol.shared.ServerAddress
import br.com.moonaudio.moonlightcontrol.shared.ServerHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectScreenTest {

    @get:Rule
    val compose = createComposeRule()

    // The error text lives inside the text field, which merges its children's semantics:
    // look for it in the unmerged tree.
    private fun errorNode() = compose.onNodeWithTag(ConnectScreenTags.ERROR, useUnmergedTree = true)

    private fun assertErrorShown() {
        errorNode().assertIsDisplayed()
    }

    @Test
    fun validAddressIsOpened() {
        val opened = mutableListOf<ServerAddress>()
        compose.setContent { MoonLightTheme { ConnectScreen(onOpen = { opened += it }) } }

        compose.onNodeWithTag(ConnectScreenTags.ADDRESS).performTextInput("10.1.1.20")
        compose.onNodeWithTag(ConnectScreenTags.OPEN).performClick()

        assertEquals(listOf("http://10.1.1.20:8080/"), opened.map { it.rootUrl })
        errorNode().assertDoesNotExist()
    }

    @Test
    fun invalidAddressShowsErrorAndDoesNotOpen() {
        val opened = mutableListOf<ServerAddress>()
        compose.setContent { MoonLightTheme { ConnectScreen(onOpen = { opened += it }) } }

        compose.onNodeWithTag(ConnectScreenTags.ADDRESS).performTextInput("10.1.1.256")
        compose.onNodeWithTag(ConnectScreenTags.OPEN).performClick()

        assertErrorShown()
        assertTrue(opened.isEmpty())
    }

    @Test
    fun editingClearsTheError() {
        compose.setContent { MoonLightTheme { ConnectScreen(onOpen = {}) } }

        compose.onNodeWithTag(ConnectScreenTags.OPEN).performClick()
        assertErrorShown()

        compose.onNodeWithTag(ConnectScreenTags.ADDRESS).performTextInput("1")
        errorNode().assertDoesNotExist()
    }

    private val now = 1_700_000_000_000
    private val recents = listOf(
        RecentServer(ServerAddress(false, "10.1.1.200"), now - 120_000),
        RecentServer(ServerAddress(false, "stage-pi.local", 9000), now - 86_400_000),
    )

    @Test
    fun noRecentServersNoSection() {
        compose.setContent { MoonLightTheme { ConnectScreen(onOpen = {}) } }
        compose.onNodeWithTag(ConnectScreenTags.RECENT_TITLE).assertDoesNotExist()
    }

    @Test
    fun recentServersAreListedInOrder() {
        compose.setContent {
            MoonLightTheme { ConnectScreen(onOpen = {}, recentServers = recents, now = now) }
        }
        compose.onNodeWithTag(ConnectScreenTags.RECENT_TITLE).assertIsDisplayed()
        val first = compose.onNodeWithTag(ConnectScreenTags.recent(recents[0].address))
        val second = compose.onNodeWithTag(ConnectScreenTags.recent(recents[1].address))
        first.assertIsDisplayed()
        second.assertIsDisplayed()
        compose.onNodeWithText("10.1.1.200").assertIsDisplayed()
        compose.onNodeWithText("stage-pi.local:9000").assertIsDisplayed()
        assertTrue(
            first.fetchSemanticsNode().boundsInRoot.top < second.fetchSemanticsNode().boundsInRoot.top,
        )
    }

    @Test
    fun tappingARecentServerOpensIt() {
        val opened = mutableListOf<ServerAddress>()
        compose.setContent {
            MoonLightTheme { ConnectScreen(onOpen = { opened += it }, recentServers = recents, now = now) }
        }
        compose.onNodeWithText("stage-pi.local:9000").performClick()
        assertEquals(listOf(recents[1].address), opened)
    }

    @Test
    fun removeButtonRemovesOnlyThatServer() {
        val opened = mutableListOf<ServerAddress>()
        val removed = mutableListOf<ServerAddress>()
        compose.setContent {
            MoonLightTheme {
                ConnectScreen(
                    onOpen = { opened += it },
                    recentServers = recents,
                    onRemoveRecent = { removed += it },
                    now = now,
                )
            }
        }
        compose.onNodeWithTag(ConnectScreenTags.recentRemove(recents[0].address)).performClick()
        assertEquals(listOf(recents[0].address), removed)
        assertTrue("removing must not open the server", opened.isEmpty())
    }

    @Test
    fun findServersButtonStartsTheSearch() {
        var clicks = 0
        compose.setContent { MoonLightTheme { ConnectScreen(onOpen = {}, onFindServers = { clicks++ }) } }
        compose.onNodeWithTag(ConnectScreenTags.DISCOVER).performScrollTo().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun searchingShowsProgressAndDisablesTheButton() {
        compose.setContent {
            MoonLightTheme {
                ConnectScreen(
                    onOpen = {},
                    discovery = DiscoveryUi(DiscoveryUi.Status.SCANNING, ranges = listOf("10.1.1.0/24")),
                )
            }
        }
        compose.onNodeWithTag(ConnectScreenTags.DISCOVER).assertIsNotEnabled()
        compose.onNodeWithTag(ConnectScreenTags.DISCOVER_PROGRESS).assertExists()
        compose.onNodeWithText("10.1.1.0/24", substring = true).assertExists()
    }

    @Test
    fun foundServerIsListedAndOpensOnTap() {
        val server = DiscoveredServer(ServerAddress(false, "10.1.1.200"), ServerHealth("4b9dade", "4b9dade"))
        val opened = mutableListOf<ServerAddress>()
        compose.setContent {
            MoonLightTheme {
                ConnectScreen(
                    onOpen = { opened += it },
                    discovery = DiscoveryUi(DiscoveryUi.Status.DONE, found = listOf(server)),
                )
            }
        }
        compose.onNodeWithText("4b9dade", substring = true).assertExists()
        compose.onNodeWithTag(ConnectScreenTags.DISCOVER_NONE).assertDoesNotExist()
        compose.onNodeWithTag(ConnectScreenTags.found(server.address)).performScrollTo().performClick()
        assertEquals(listOf(server.address), opened)
    }

    @Test
    fun searchWithoutResultsSaysSo() {
        compose.setContent {
            MoonLightTheme { ConnectScreen(onOpen = {}, discovery = DiscoveryUi(DiscoveryUi.Status.DONE)) }
        }
        compose.onNodeWithTag(ConnectScreenTags.DISCOVER_NONE).assertExists()
    }

    @Test
    fun noLocalNetworkSaysSo() {
        compose.setContent {
            MoonLightTheme { ConnectScreen(onOpen = {}, discovery = DiscoveryUi(DiscoveryUi.Status.NO_NETWORK)) }
        }
        compose.onNodeWithTag(ConnectScreenTags.DISCOVER_NO_NETWORK).assertExists()
    }

    @Test
    fun localNetworkDeniedMessageIsShown() {
        compose.setContent {
            MoonLightTheme { ConnectScreen(onOpen = {}, localNetworkDenied = true) }
        }
        compose.onNodeWithTag(ConnectScreenTags.LOCAL_NETWORK_DENIED).assertIsDisplayed()
    }
}
