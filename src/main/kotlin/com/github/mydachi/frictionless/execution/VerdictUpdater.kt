package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.analysis.BucketAssignment
import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.TestRef

/**
 * Deliverable E3: fold streaming test results back into the ledger.
 *
 * E2 reports one [TestResult] at a time as the run progresses, and the Autopilot tour repaints as
 * each lands — so this applies results incrementally rather than waiting for the whole run.
 *
 * Matching is on class plus method name: the `TestRef`s A3 produced come from PSI, the ones E2
 * produces come from the runner's location URLs, and they are equal by value but not by identity.
 */
object VerdictUpdater {

    /** Every test in the change set, de-duplicated — the set E1 should be asked to run. */
    fun testsToRun(changeSet: ChangeSet): List<TestRef> =
        changeSet.methods
            .flatMap { it.reachingTests }
            .distinctBy { it.displayName }

    /** One result in; every method that test reaches is re-bucketed. */
    fun apply(changeSet: ChangeSet, result: TestResult): ChangeSet =
        changeSet.copy(methods = changeSet.methods.map { method -> apply(method, result) })

    fun apply(changeSet: ChangeSet, results: List<TestResult>): ChangeSet =
        results.fold(changeSet, ::apply)

    private fun apply(method: ChangedMethod, result: TestResult): ChangedMethod {
        if (method.reachingTests.none { it.matches(result.test) }) return method

        val updated = method.copy(
            reachingTests = method.reachingTests.map { test ->
                if (test.matches(result.test)) test.copy(outcome = result.outcome) else test
            },
        )
        return BucketAssignment.afterTests(updated)
    }

    /**
     * The runner reports a simple class name where PSI gave a qualified one (and the other way round
     * for a Kotlin top-level test), so the comparison falls back to the simple name rather than
     * silently failing to match and leaving a passing test invisible.
     */
    private fun TestRef.matches(other: TestRef): Boolean {
        if (methodName != other.methodName) return false
        if (className == other.className) return true
        return className.substringAfterLast('.') == other.className.substringAfterLast('.')
    }
}
