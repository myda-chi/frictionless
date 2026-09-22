package com.github.mydachi.frictionless

import com.github.mydachi.frictionless.narration.Identifiers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Deliverable U7: the fifteen lines that decide whether the tour sounds deliberate or broken.
 */
class IdentifiersTest {

    @Test
    fun `expands the initialisms we actually use`() {
        assertEquals("apply F X margin", Identifiers.humanise("TransferService.applyFxMargin()"))
        assertEquals("find by customer I D", Identifiers.humanise("findByCustomerId"))
        assertEquals("to A P I response", Identifiers.humanise("Mapper.toApiResponse()"))
    }

    @Test
    fun `drops the class and the parentheses`() {
        assertEquals("submit", Identifiers.humanise("TransferService.submit()"))
    }

    @Test
    fun `handles snake case and single words`() {
        assertEquals("apply margin", Identifiers.humanise("apply_margin"))
        assertEquals("check", Identifiers.humanise("check"))
    }

    @Test
    fun `splits camel case without losing characters`() {
        assertEquals(listOf("apply", "Fx", "Margin"), Identifiers.splitCamelCase("applyFxMargin"))
        assertEquals(listOf("submit"), Identifiers.splitCamelCase("submit"))
        assertEquals(emptyList<String>(), Identifiers.splitCamelCase(""))
    }
}
