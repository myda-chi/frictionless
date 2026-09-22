package com.github.mydachi.frictionless.agent

import com.github.mydachi.frictionless.FrictionlessFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateTestTest {

    private val method = FrictionlessFixtures.unverified()

    @Test
    fun `is named after the method it stands in for`() {
        assertEquals("ApplyFxMarginCharacterisationTest", TemplateTest.classNameFor(method))
    }

    @Test
    fun `is disabled, so it can never report the method as proven`() {
        // The whole reason the fallback is safe: a disabled test is reported as skipped, and a
        // skipped test is not a pass (issue #55), so the method correctly stays Unverified.
        val java = TemplateTest.forMethod(method, "io.wio.transfer", kotlin = false)
        assertTrue(java.source.contains("@Ignore"))
        assertTrue(java.source.contains("@Test"))
    }

    @Test
    fun `lands in the right package, in the right language`() {
        val java = TemplateTest.forMethod(method, "io.wio.transfer", kotlin = false)
        assertTrue(java.source.startsWith("package io.wio.transfer;"))

        val kotlin = TemplateTest.forMethod(method, "io.wio.transfer", kotlin = true)
        assertTrue(kotlin.source.startsWith("package io.wio.transfer\n"))
        assertTrue(kotlin.source.contains("fun `pins current behaviour`()"))
    }

    @Test
    fun `points back at the method it is standing in for`() {
        val generated = TemplateTest.forMethod(method, "io.wio.transfer", kotlin = false)
        assertTrue(generated.source.contains(method.displayName))
        assertTrue(generated.source.contains("${method.filePath}:${method.line}"))
    }
}
