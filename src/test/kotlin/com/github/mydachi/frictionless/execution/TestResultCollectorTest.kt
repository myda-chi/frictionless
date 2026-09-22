package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
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
 * every `TestStateInfo.Magnitude` to a [TestOutcome] without ever guessing [TestOutcome.PASSED].
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
    fun `passed, complete and skipped all map to PASSED`() {
        assertEquals(TestOutcome.PASSED, testOutcomeFromMagnitude(Magnitude.PASSED_INDEX))
        assertEquals(TestOutcome.PASSED, testOutcomeFromMagnitude(Magnitude.COMPLETE_INDEX))
        assertEquals(TestOutcome.PASSED, testOutcomeFromMagnitude(Magnitude.SKIPPED_INDEX))
    }

    @Test
    fun `an assertion failure maps to FAILED`() {
        assertEquals(TestOutcome.FAILED, testOutcomeFromMagnitude(Magnitude.FAILED_INDEX))
    }

    @Test
    fun `anything else - error, ignored, terminated, not run - maps to ERROR, never a guessed pass`() {
        assertEquals(TestOutcome.ERROR, testOutcomeFromMagnitude(Magnitude.ERROR_INDEX))
        assertEquals(TestOutcome.ERROR, testOutcomeFromMagnitude(Magnitude.IGNORED_INDEX))
        assertEquals(TestOutcome.ERROR, testOutcomeFromMagnitude(Magnitude.TERMINATED_INDEX))
        assertEquals(TestOutcome.ERROR, testOutcomeFromMagnitude(Magnitude.NOT_RUN_INDEX))
    }
}
