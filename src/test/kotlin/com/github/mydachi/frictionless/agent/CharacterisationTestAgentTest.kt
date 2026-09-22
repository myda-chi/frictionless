package com.github.mydachi.frictionless.agent

import com.github.mydachi.frictionless.FrictionlessFixtures
import com.github.mydachi.frictionless.execution.TestResult
import com.github.mydachi.frictionless.execution.TestRunner
import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The loop is the valuable part of G3, so it is tested without a key, a network, or Koog.
 */
class CharacterisationTestAgentTest {

    private val context = TestContext(
        method = FrictionlessFixtures.unverified(),
        methodSource = "BigDecimal applyFxMargin(BigDecimal amount) { return amount.multiply(MARGIN); }",
    )

    private class FakeLlm(
        private val responses: List<String>,
        override val isConfigured: Boolean = true,
    ) : LlmClient {
        val prompts = mutableListOf<String>()
        private var index = 0
        override fun complete(systemPrompt: String, userPrompt: String): String {
            prompts += userPrompt
            return responses[index++.coerceAtMost(responses.lastIndex)]
        }
    }

    private class FakeRunner(private val outcomes: List<TestOutcome>, private val output: String = "error: cannot find symbol") : TestRunner {
        var runs = 0
        override fun run(tests: List<TestRef>): List<TestResult> {
            val outcome = outcomes[runs.coerceAtMost(outcomes.lastIndex)]
            runs++
            return tests.map { TestResult(it, outcome, if (outcome == TestOutcome.PASSED) "" else output) }
        }
    }

    private object AcceptingWriter : TestWriter {
        override fun write(test: GeneratedTest) = true
    }

    private fun source(name: String) = "package io.wio.transfer;\nclass $name { void t() {} }"

    @Test
    fun `pins on the first pass`() {
        val runner = FakeRunner(listOf(TestOutcome.PASSED))
        val result = CharacterisationTestAgent(FakeLlm(listOf(source("FxMarginTest"))), runner, AcceptingWriter).pin(context)

        val pinned = result as CharacterisationTestAgent.Result.Pinned
        assertEquals("FxMarginTest", pinned.test.className)
        assertEquals("io.wio.transfer", pinned.test.packageName)
        assertEquals(1, pinned.attempts)
        assertEquals(1, runner.runs)
    }

    @Test
    fun `feeds the failure back and pins on the retry`() {
        val llm = FakeLlm(listOf(source("Broken"), source("Fixed")))
        val runner = FakeRunner(listOf(TestOutcome.FAILED, TestOutcome.PASSED))

        val result = CharacterisationTestAgent(llm, runner, AcceptingWriter).pin(context)

        assertEquals(2, (result as CharacterisationTestAgent.Result.Pinned).attempts)
        // The second prompt must carry the previous source and the build's own words, or the model
        // is guessing again rather than fixing.
        assertTrue(llm.prompts[1].contains("Broken"))
        assertTrue(llm.prompts[1].contains("cannot find symbol"))
    }

    @Test
    fun `gives up after three attempts rather than spinning`() {
        val runner = FakeRunner(listOf(TestOutcome.FAILED))
        val result = CharacterisationTestAgent(FakeLlm(listOf(source("StillBroken"))), runner, AcceptingWriter).pin(context)

        val gaveUp = result as CharacterisationTestAgent.Result.GaveUp
        assertEquals(CharacterisationTestAgent.MAX_ATTEMPTS, gaveUp.attempts)
        assertEquals(3, runner.runs)
        assertTrue(gaveUp.lastFailure.contains("cannot find symbol"))
    }

    @Test
    fun `never calls the model without a key`() {
        val llm = FakeLlm(listOf(source("Never")), isConfigured = false)
        val runner = FakeRunner(listOf(TestOutcome.PASSED))

        val result = CharacterisationTestAgent(llm, runner, AcceptingWriter).pin(context)

        assertTrue(result is CharacterisationTestAgent.Result.Unavailable)
        assertEquals(0, llm.prompts.size)
        assertEquals(0, runner.runs)
    }

    @Test
    fun `a run that never passes cannot be reported as pinned`() {
        val runner = FakeRunner(listOf(TestOutcome.ERROR))
        val result = CharacterisationTestAgent(FakeLlm(listOf(source("T"))), runner, AcceptingWriter).pin(context)
        assertTrue("the runner decides, not the model", result is CharacterisationTestAgent.Result.GaveUp)
    }

    @Test
    fun `strips the markdown fence models add anyway`() {
        val fenced = "```java\npackage io.wio;\nclass FencedTest { }\n```"
        val parsed = GeneratedTest.parse(fenced)!!
        assertEquals("FencedTest", parsed.className)
        assertEquals("io.wio", parsed.packageName)
        assertTrue(!parsed.source.contains("```"))
    }

    @Test
    fun `summarises a compiler failure as a short spoken line`() {
        val agent = CharacterisationTestAgent(FakeLlm(emptyList()), FakeRunner(emptyList()), AcceptingWriter)
        assertEquals("didn't compile — fixing", agent.summarise("error: cannot find symbol"))
        assertEquals("assertion failed — fixing", agent.summarise("expected:<3> but was:<4>"))
    }
}
