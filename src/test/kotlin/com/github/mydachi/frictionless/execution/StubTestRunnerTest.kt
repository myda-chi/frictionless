package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * E0 acceptance: a `TestRunner` stub the Agent track can call before the real JUnit-backed runner
 * (E1-E3) exists.
 */
class StubTestRunnerTest {

    private val fooTest = TestRef(className = "FooTest", methodName = "bar")
    private val bazTest = TestRef(className = "BazTest", methodName = "qux")

    @Test
    fun `defaults every test to FAILED rather than a fabricated pass`() {
        val results = StubTestRunner().run(listOf(fooTest))

        assertEquals(TestOutcome.FAILED, results.single().outcome)
    }

    @Test
    fun `reports a canned outcome keyed by test display name`() {
        val passing = TestResult(test = fooTest, outcome = TestOutcome.PASSED)
        val runner = StubTestRunner(canned = mapOf(fooTest.displayName to passing))

        val results = runner.run(listOf(fooTest))

        assertEquals(passing, results.single())
    }

    @Test
    fun `runs every test in the order given, defaulting the ones with no canned result`() {
        val passing = TestResult(test = fooTest, outcome = TestOutcome.PASSED)
        val runner = StubTestRunner(canned = mapOf(fooTest.displayName to passing))

        val results = runner.run(listOf(fooTest, bazTest))

        assertEquals(listOf(TestOutcome.PASSED, TestOutcome.FAILED), results.map { it.outcome })
        assertEquals(listOf(fooTest, bazTest), results.map { it.test })
    }

    @Test
    fun `a TestResult cannot report NOT_RUN`() {
        assertThrows(IllegalArgumentException::class.java) {
            TestResult(test = fooTest, outcome = TestOutcome.NOT_RUN)
        }
    }
}
