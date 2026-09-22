package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef
import com.github.mydachi.frictionless.model.Verdict
import com.github.mydachi.frictionless.model.VerdictCounts
import org.junit.Assert.assertEquals
import org.junit.Test

class BucketAssignmentTest {

    private fun method(vararg outcomes: TestOutcome) = ChangedMethod(
        id = "m",
        displayName = "T.m()",
        filePath = "T.java",
        line = 1,
        verdict = Verdict(Bucket.UNVERIFIED, VerdictCounts()),
        reachingTests = outcomes.mapIndexed { i, outcome -> TestRef("TTest", "t$i", outcome) },
    )

    @Test
    fun `no reaching tests is unverified, before and after running anything`() {
        assertEquals(Bucket.UNVERIFIED, BucketAssignment.beforeTests(method()).verdict.bucket)
        assertEquals(Bucket.UNVERIFIED, BucketAssignment.afterTests(method()).verdict.bucket)
    }

    @Test
    fun `having tests is not yet proven, before they run`() {
        // A test existing is not a test passing. Proven is only ever asserted after execution.
        val before = BucketAssignment.beforeTests(method(TestOutcome.NOT_RUN, TestOutcome.NOT_RUN))
        assertEquals(Bucket.UNVERIFIED, before.verdict.bucket)
        assertEquals(2, before.verdict.counts.reachingTests)
    }

    @Test
    fun `all passing is proven`() {
        val after = BucketAssignment.afterTests(method(TestOutcome.PASSED, TestOutcome.PASSED))
        assertEquals(Bucket.PROVEN, after.verdict.bucket)
        assertEquals(2, after.verdict.counts.passed)
    }

    @Test
    fun `any failure outranks the passes`() {
        val after = BucketAssignment.afterTests(method(TestOutcome.PASSED, TestOutcome.FAILED))
        assertEquals(Bucket.BEHAVIOUR_CHANGED, after.verdict.bucket)
        assertEquals(1, after.verdict.counts.failed)
    }

    @Test
    fun `an error counts as a failure, not as a pass`() {
        assertEquals(Bucket.BEHAVIOUR_CHANGED, BucketAssignment.afterTests(method(TestOutcome.ERROR)).verdict.bucket)
    }

    @Test
    fun `a method whose only test never ran is unverified, not proven`() {
        // Issue #55. The whole product is the claim that Proven means something executed this code.
        val after = BucketAssignment.afterTests(method(TestOutcome.NOT_RUN))
        assertEquals(Bucket.UNVERIFIED, after.verdict.bucket)
        assertEquals(0, after.verdict.counts.passed)
    }
}
