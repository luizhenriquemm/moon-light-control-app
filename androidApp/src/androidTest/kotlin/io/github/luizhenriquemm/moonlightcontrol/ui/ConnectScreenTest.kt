package io.github.luizhenriquemm.moonlightcontrol.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.luizhenriquemm.moonlightcontrol.shared.ServerAddress
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

    @Test
    fun localNetworkDeniedMessageIsShown() {
        compose.setContent {
            MoonLightTheme { ConnectScreen(onOpen = {}, localNetworkDenied = true) }
        }
        compose.onNodeWithTag(ConnectScreenTags.LOCAL_NETWORK_DENIED).assertIsDisplayed()
    }
}
