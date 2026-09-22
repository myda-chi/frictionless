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
    fun `reads the framework from the repository's own tests`() {
        // The first version hard-coded JUnit 4 and landed in a Jupiter project: the placeholder did
        // not compile, so it broke the build of the project it was meant to help.
        val jupiter = listOf("import org.junit.jupiter.api.Test;\n\nclass AccountTest { }")
        val junit4 = listOf("import org.junit.Test;\nimport org.junit.Assert;\n\npublic class AccountTest { }")

        assertTrue(TemplateTest.usesJUnit5(jupiter))
        assertTrue(!TemplateTest.usesJUnit5(junit4))
        // Nothing to read: assume the modern one rather than guessing at random.
        assertTrue(TemplateTest.usesJUnit5(emptyList()))
    }

    @Test
    fun `uses the right annotations for each framework`() {
        val jupiter = TemplateTest.forMethod(method, "io.wio.transfer", kotlin = false, junit5 = true)
        assertTrue(jupiter.source.contains("import org.junit.jupiter.api.Disabled;"))
        assertTrue(jupiter.source.contains("import org.junit.jupiter.api.Test;"))
        assertTrue(jupiter.source.contains("@Disabled("))

        val four = TemplateTest.forMethod(method, "io.wio.transfer", kotlin = false, junit5 = false)
        assertTrue(four.source.contains("import org.junit.Ignore;"))
        assertTrue(four.source.contains("@Ignore("))
    }

    @Test
    fun `is disabled, so it can never report the method as proven`() {
        // The whole reason the fallback is safe: a disabled test is reported as skipped, and a
        // skipped test is not a pass (issue #55), so the method correctly stays Unverified.
        val java = TemplateTest.forMethod(method, "io.wio.transfer", kotlin = false)
        assertTrue(java.source.contains("@Disabled"))
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
