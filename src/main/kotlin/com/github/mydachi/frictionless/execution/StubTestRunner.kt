package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef

/**
 * Deliverable E0: canned results so the Agent track has something to call before E1 exists.
 *
 * Every test defaults to [TestOutcome.FAILED] rather than a fabricated pass — a characterisation
 * test the agent has just written should not sail through against pretend success. That keeps G3's
 * write -> run -> read failure -> fix loop exercised honestly against the stub, the same shape it
 * will run in for real once G5 swaps this out for the JUnit-backed runner. Pass [canned], keyed by
 * [TestRef.displayName], to script a specific test's outcome (e.g. "fails twice, then passes" by
 * calling [run] with a fresh runner each attempt).
 */
class StubTestRunner(
    private val canned: Map<String, TestResult> = emptyMap(),
) : TestRunner {

    override fun run(tests: List<TestRef>): List<TestResult> = tests.map { test ->
        canned[test.displayName] ?: TestResult(
            test = test,
            outcome = TestOutcome.FAILED,
            output = "stub runner: no canned result for '${test.displayName}', reporting FAILED",
        )
    }
}
