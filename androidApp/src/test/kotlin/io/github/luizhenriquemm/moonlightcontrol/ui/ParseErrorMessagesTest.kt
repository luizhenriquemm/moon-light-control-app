package io.github.luizhenriquemm.moonlightcontrol.ui

import io.github.luizhenriquemm.moonlightcontrol.shared.ParseError
import org.junit.Assert.assertEquals
import org.junit.Test

class ParseErrorMessagesTest {

    @Test
    fun everyErrorHasItsOwnMessage() {
        val messages = ParseError.entries.map { it.messageRes() }
        assertEquals("each ParseError needs a distinct message", messages.size, messages.toSet().size)
    }
}
