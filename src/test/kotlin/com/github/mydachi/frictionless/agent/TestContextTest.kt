package com.github.mydachi.frictionless.agent

import com.github.mydachi.frictionless.FrictionlessFixtures
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G2 is what makes this more than "call the API with the function body" — so the prompt is asserted
 * to actually carry the repository's own context.
 */
class TestContextTest {

    private val context = TestContext(
        method = FrictionlessFixtures.unverified(),
        methodSource = "BigDecimal applyFxMargin(BigDecimal amount)",
        callSiteSnippets = listOf("applyFxMargin(new BigDecimal(\"250.00\"))"),
        conventionExamples = listOf("class TransferServiceTest { @Test void submits() {} }"),
        testPackage = "io.wio.transfer",
    )

    @Test
    fun `carries the method, real call sites and existing conventions`() {
        val prompt = context.userPrompt()
        assertTrue(prompt.contains("applyFxMargin"))
        assertTrue(prompt.contains("250.00"))
        assertTrue(prompt.contains("TransferServiceTest"))
        assertTrue(prompt.contains("io.wio.transfer"))
    }

    @Test
    fun `asks for current behaviour, not correct behaviour`() {
        assertTrue(context.userPrompt().contains("CURRENT behaviour"))
        assertTrue(TestContext.SYSTEM_PROMPT.contains("pins behaviour as it is TODAY"))
    }

    @Test
    fun `truncates enormous compiler output`() {
        val huge = "x".repeat(TestContext.MAX_FAILURE_CHARS * 3)
        val prompt = context.copy(
            previousAttempt = TestContext.PreviousAttempt("class T {}", huge),
        ).userPrompt()
        assertTrue(prompt.length < huge.length)
        assertTrue(prompt.contains("do not start over"))
    }
}
