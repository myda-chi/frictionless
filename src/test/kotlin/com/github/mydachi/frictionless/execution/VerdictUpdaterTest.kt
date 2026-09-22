package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangeSource
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef
import com.github.mydachi.frictionless.model.Verdict
import com.github.mydachi.frictionless.model.VerdictCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class VerdictUpdaterTest {

    private fun method(id: String, vararg tests: TestRef) = ChangedMethod(
        id = id,
        displayName = "$id()",
        filePath = "T.java",
        line = 1,
        verdict = Verdict(Bucket.UNVERIFIED, VerdictCounts()),
        reachingTests = tests.toList(),
    )

    private val shared = TestRef("demo.SharedTest", "covers")
    private val only = TestRef("demo.OnlyTest", "covers")

    private val changeSet = ChangeSet(
        ChangeSource.WorkingTree,
        listOf(method("a", shared), method("b", shared, only), method("c")),
    )

    @Test
    fun `the tests to run are de-duplicated across methods`() {
        assertEquals(
            listOf("demo.SharedTest.covers", "demo.OnlyTest.covers"),
            VerdictUpdater.testsToRun(changeSet).map { it.displayName },
        )
    }

    @Test
    fun `one result re-buckets every method that test reaches`() {
        val updated = VerdictUpdater.apply(changeSet, TestResult(shared, TestOutcome.PASSED))

        assertEquals(Bucket.PROVEN, updated.methods[0].verdict.bucket)
        // b still has an unrun test, but one pass is enough to have executed it.
        assertEquals(Bucket.PROVEN, updated.methods[1].verdict.bucket)
        // c has no reaching tests at all and is untouched by any result.
        assertEquals(Bucket.UNVERIFIED, updated.methods[2].verdict.bucket)
    }

    @Test
    fun `a failure moves the method to behaviour changed`() {
        val updated = VerdictUpdater.apply(changeSet, TestResult(shared, TestOutcome.FAILED, "expected 3 but was 4"))
        assertEquals(Bucket.BEHAVIOUR_CHANGED, updated.methods[0].verdict.bucket)
        assertEquals(1, updated.methods[0].verdict.counts.failed)
    }

    @Test
    fun `results arriving one at a time end up where a batch would`() {
        val streamed = listOf(TestResult(shared, TestOutcome.PASSED), TestResult(only, TestOutcome.FAILED))
            .fold(changeSet) { acc, result -> VerdictUpdater.apply(acc, result) }
        val batched = VerdictUpdater.apply(changeSet, listOf(TestResult(shared, TestOutcome.PASSED), TestResult(only, TestOutcome.FAILED)))

        assertEquals(batched.methods.map { it.verdict.bucket }, streamed.methods.map { it.verdict.bucket })
        assertEquals(Bucket.BEHAVIOUR_CHANGED, streamed.methods[1].verdict.bucket)
    }

    @Test
    fun `a runner reporting a simple class name still matches the qualified one from PSI`() {
        // E2 reads class names off the runner's location URLs; A3 gets them from PSI. They are the
        // same test, and failing to match would leave a passing test invisible.
        val updated = VerdictUpdater.apply(changeSet, TestResult(TestRef("SharedTest", "covers"), TestOutcome.PASSED))
        assertEquals(Bucket.PROVEN, updated.methods[0].verdict.bucket)
    }
}
