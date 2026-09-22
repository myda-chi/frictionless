package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestOutcome
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.github.mydachi.frictionless.model.TestRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * E2 acceptance: parsing and outcome-mapping for streamed per-test results.
 *
 * **Coverage boundary, stated plainly.** [TestResultCollector.collector] and [toTestResult] read from
 * a live `SMTestProxy` tree that only exists during a real JUnit run, which this suite does not spin
 * up. What is pinned here is the two pure decisions that determine correctness once real events
 * arrive: parsing the JUnit runtime's `java:test://` location URL back into a [TestRef], and mapping
 * a finished test to a [TestOutcome] without ever guessing [TestOutcome.PASSED].
 */
class TestResultCollectorTest {

    @Test
    fun `parses a location url into class and method`() {
        val ref = testRefFromLocationUrl("java:test://com.example.FooTest/bar")
        assertEquals(TestRef(className = "com.example.FooTest", methodName = "bar"), ref)
    }

    @Test
    fun `rejects a url with no method segment`() {
        assertNull(testRefFromLocationUrl("java:test://com.example.FooTest"))
        assertNull(testRefFromLocationUrl("java:test://com.example.FooTest/"))
    }

    @Test
    fun `rejects a null or unrecognised url`() {
        assertNull(testRefFromLocationUrl(null))
        assertNull(testRefFromLocationUrl("not-a-location-url"))
    }

    @Test
    fun `a skipped test is reported as skipped, never as a pass`() {
        // Issue #55, at the layer that was still leaking it: the mapping used to collapse SKIPPED in
        // with PASSED, so an @Ignore'd test could carry its method all the way to Proven. Nothing
        // executed, so nothing is proven — SKIPPED counts as neither a pass nor a failure and the
        // method stays Unverified.
        assertEquals(TestOutcome.SKIPPED, testOutcomeOf(proxy(ignored = true)))
    }

    @Test
    fun `ignored wins over passed, because the platform calls a skipped test not-failed`() {
        assertEquals(TestOutcome.SKIPPED, testOutcomeOf(proxy(ignored = true, passed = true)))
    }

    @Test
    fun `a passing test maps to PASSED`() {
        assertEquals(TestOutcome.PASSED, testOutcomeOf(proxy(passed = true)))
    }

    @Test
    fun `a defect maps to FAILED`() {
        assertEquals(TestOutcome.FAILED, testOutcomeOf(proxy(defect = true)))
    }

    @Test
    fun `anything else is an ERROR, never a guessed pass`() {
        assertEquals(TestOutcome.ERROR, testOutcomeOf(proxy()))
    }

    private fun proxy(passed: Boolean = false, ignored: Boolean = false, defect: Boolean = false) =
        object : SMTestProxy("t", false, null) {
            override fun isPassed() = passed
            override fun isIgnored() = ignored
            override fun isDefect() = defect
        }
}
