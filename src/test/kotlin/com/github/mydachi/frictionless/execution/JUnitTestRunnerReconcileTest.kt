package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * G5's reconciliation: what was asked for, answered with what actually came back.
 *
 * Launching a real JUnit run needs a live project, so what is pinned here is the part that decides
 * whether the agent is told the truth.
 */
class JUnitTestRunnerReconcileTest {

    private fun result(className: String, methodName: String, outcome: TestOutcome) =
        TestResult(TestRef(className, methodName), outcome)

    @Test
    fun `a requested test the run never mentioned is an error, never a pass`() {
        val requested = listOf(TestRef("demo.FooTest", "bar"))

        val reconciled = JUnitTestRunner.reconcile(requested, emptyList())

        assertEquals(TestOutcome.ERROR, reconciled.single().outcome)
    }

    @Test
    fun `a whole-class request passes only if every test in it passed`() {
        val requested = listOf(TestRef.wholeClass("demo.FxTest"))
        val reported = listOf(
            result("demo.FxTest", "a", TestOutcome.PASSED),
            result("demo.FxTest", "b", TestOutcome.FAILED),
        )

        assertEquals(TestOutcome.FAILED, JUnitTestRunner.reconcile(requested, reported).single().outcome)
    }

    @Test
    fun `a whole-class request passes when all of them did`() {
        val requested = listOf(TestRef.wholeClass("demo.FxTest"))
        val reported = listOf(
            result("demo.FxTest", "a", TestOutcome.PASSED),
            result("demo.FxTest", "b", TestOutcome.PASSED),
        )

        assertEquals(TestOutcome.PASSED, JUnitTestRunner.reconcile(requested, reported).single().outcome)
    }

    @Test
    fun `the runner's simple class name still matches a qualified request`() {
        val requested = listOf(TestRef("demo.FxTest", "a"))
        val reported = listOf(result("FxTest", "a", TestOutcome.PASSED))

        assertEquals(TestOutcome.PASSED, JUnitTestRunner.reconcile(requested, reported).single().outcome)
    }

}
