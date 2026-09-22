package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef

/**
 * Deliverable E0: the seam between "something needs a test run" and "how it actually got run".
 *
 * This exists so the Agent track (G1-G5) can start in hour 2 against [StubTestRunner] instead of
 * waiting on the real JUnit-backed runner (E1-E3, due hour 13) — deliverables.md calls this one of
 * only two true blockers, alongside the frozen contracts. G3's "run the test" tool calls
 * [TestRunner.run] without knowing which implementation answers it; G5 swaps the stub for the real
 * runner with no other change required.
 *
 * The trust guarantee in specification.md §3.4 lives here: the agent *proposes* a test, but only a
 * [TestRunner] decides whether it passed. A hallucinated pass is structurally impossible as long as
 * every implementation reports what actually happened rather than what was hoped for.
 */
interface TestRunner {

    /**
     * Runs [tests] and reports what happened, one [TestResult] per test, in the same order.
     *
     * Whether this runs tests one at a time or all at once, and whether it blocks or streams, is the
     * implementation's business — the real runner (E1/E2) streams per-test as `SMTRunnerEventsListener`
     * events arrive; [StubTestRunner] just answers immediately with canned data.
     */
    fun run(tests: List<TestRef>): List<TestResult>
}

/**
 * What a single test run produced.
 *
 * [output] is what the agent (G3) reads to fix a failing or non-compiling test — the compiler error
 * or the failed assertion, not just a boolean. It is empty when [outcome] is [TestOutcome.PASSED].
 */
data class TestResult(
    val test: TestRef,
    val outcome: TestOutcome,
    val output: String = "",
    val durationMs: Long = 0,
) {
    init {
        require(outcome != TestOutcome.NOT_RUN) { "a TestResult always reports a run that already happened" }
    }
}
