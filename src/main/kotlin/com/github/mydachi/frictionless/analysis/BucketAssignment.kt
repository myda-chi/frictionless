package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.TestOutcome

/**
 * Deliverable A4: which bucket a changed method lands in.
 *
 * Two passes, because the answer arrives in two stages:
 *
 *  - [beforeTests] runs the moment A3 finishes, with no test execution at all. It can already say
 *    **Unverified** with certainty — nothing reaches the method, so nothing can be reached by running
 *    anything. Methods that *do* have reaching tests are left Unverified too, because "a test exists"
 *    is not the same claim as "a test passed", and until E1/E2 have run we cannot make the second one.
 *  - [afterTests] runs once results are in and promotes to **Proven** or **Behaviour changed**.
 *
 * The asymmetry is deliberate. Unverified is safe to assert early; Proven never is.
 */
object BucketAssignment {

    fun beforeTests(changeSet: ChangeSet): ChangeSet =
        changeSet.copy(methods = changeSet.methods.map(::beforeTests))

    fun beforeTests(method: ChangedMethod): ChangedMethod = method.copy(
        verdict = method.verdict.copy(
            bucket = Bucket.UNVERIFIED,
            counts = method.verdict.counts.copy(
                reachingTests = method.reachingTests.size,
                callSites = method.callSites.size,
            ),
        ),
    )

    /**
     * The bucket a method has earned once its reaching tests have actually run.
     *
     * **A skipped test is not a pass** (issue #55). `@Ignore`d, `assumeTrue`-skipped or otherwise
     * not-executed tests are counted as not run: a method whose only reaching test was skipped stays
     * **Unverified**, because nothing executed it. Reporting that as Proven would be the exact
     * failure this product exists to catch.
     */
    fun afterTests(method: ChangedMethod): ChangedMethod {
        val outcomes = method.reachingTests.map { it.outcome }
        val passed = outcomes.count { it == TestOutcome.PASSED }
        val failed = outcomes.count { it == TestOutcome.FAILED || it == TestOutcome.ERROR }

        val bucket = when {
            failed > 0 -> Bucket.BEHAVIOUR_CHANGED
            passed > 0 -> Bucket.PROVEN
            else -> Bucket.UNVERIFIED
        }

        return method.copy(
            verdict = method.verdict.copy(
                bucket = bucket,
                counts = method.verdict.counts.copy(
                    reachingTests = method.reachingTests.size,
                    passed = passed,
                    failed = failed,
                    callSites = method.callSites.size,
                ),
            ),
        )
    }

    fun afterTests(changeSet: ChangeSet): ChangeSet =
        changeSet.copy(methods = changeSet.methods.map(::afterTests))
}
