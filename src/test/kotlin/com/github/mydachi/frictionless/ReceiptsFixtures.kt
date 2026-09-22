package com.github.mydachi.frictionless

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.CallSite
import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangeSource
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef
import com.github.mydachi.frictionless.model.Verdict
import com.github.mydachi.frictionless.model.VerdictCounts

/**
 * Deliverable S3: one changed method per bucket, so the UI and Agent tracks can build before the
 * analyser is real.
 *
 * Two rules, and they are the whole reason this is safe:
 *
 *  1. **Test scope only.** This file lives in `src/test`, so production code structurally cannot
 *     reach it. There is no toggle, no setting, and no `if (methods.isEmpty()) useFixture()`.
 *  2. **Retired at hour 5.** The moment A2 lands, every track moves to real changed methods with
 *     empty verdicts. Real but incomplete data beats fake complete data.
 */
object ReceiptsFixtures {

    fun proven() = ChangedMethod(
        id = "fixture:proven",
        displayName = "TransferService.submit()",
        filePath = "src/main/java/io/wio/transfer/TransferService.java",
        line = 84,
        verdict = Verdict(
            Bucket.PROVEN,
            VerdictCounts(reachingTests = 4, passed = 4, callSites = 2, durationMs = 300),
        ),
        callSites = listOf(
            CallSite("TransferController.submit()", "src/main/java/io/wio/transfer/TransferController.java", 41),
            CallSite("RetryScheduler.retry()", "src/main/java/io/wio/transfer/RetryScheduler.java", 27),
        ),
        reachingTests = listOf(
            TestRef("TransferServiceTest", "submits", TestOutcome.PASSED),
            TestRef("TransferServiceTest", "rejectsBelowMinimum", TestOutcome.PASSED),
            TestRef("TransferServiceTest", "retriesOnTimeout", TestOutcome.PASSED),
            TestRef("TransferFlowIT", "endToEnd", TestOutcome.PASSED),
        ),
    )

    fun behaviourChanged() = ChangedMethod(
        id = "fixture:changed",
        displayName = "LimitsService.check()",
        filePath = "src/main/java/io/wio/limits/LimitsService.java",
        line = 52,
        verdict = Verdict(
            Bucket.BEHAVIOUR_CHANGED,
            VerdictCounts(reachingTests = 3, passed = 1, failed = 2, callSites = 1, durationMs = 900),
        ),
        reachingTests = listOf(
            TestRef("LimitsServiceTest", "allowsUnderDailyCap", TestOutcome.PASSED),
            TestRef("LimitsServiceTest", "blocksOverDailyCap", TestOutcome.FAILED),
            TestRef("LimitsServiceTest", "countsPendingTransfers", TestOutcome.FAILED),
        ),
    )

    fun unverified() = ChangedMethod(
        id = "fixture:unverified",
        displayName = "TransferService.applyFxMargin()",
        filePath = "src/main/java/io/wio/transfer/TransferService.java",
        line = 131,
        verdict = Verdict(Bucket.UNVERIFIED, VerdictCounts(callSites = 3)),
        callSites = listOf(
            CallSite("TransferService.submit()", "src/main/java/io/wio/transfer/TransferService.java", 92),
            CallSite("QuoteMapper.toQuote()", "src/main/java/io/wio/quote/QuoteMapper.java", 18),
            CallSite("ReceiptBuilder.build()", "src/main/java/io/wio/receipt/ReceiptBuilder.java", 60),
        ),
    )

    fun changeSet(source: ChangeSource = ChangeSource.WorkingTree) =
        ChangeSet(source, listOf(proven(), behaviourChanged(), unverified()))
}
