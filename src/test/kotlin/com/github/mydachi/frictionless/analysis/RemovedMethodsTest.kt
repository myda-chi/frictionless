package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.Verdict
import com.github.mydachi.frictionless.model.VerdictCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A deleted method has no behaviour left to verify. Reporting it as Unverified is a false alarm, and
 * because Unverified sorts first it crowded the tour out entirely: a branch comparison that deleted
 * a file filled every stop with methods the tour could not open.
 */
class RemovedMethodsTest {

    private fun method(id: String, removed: Boolean) = ChangedMethod(
        id = id,
        displayName = "$id()",
        filePath = "src/main/java/com/example/bank/RefundService.java",
        line = 1,
        verdict = Verdict(Bucket.UNVERIFIED, VerdictCounts()),
        removed = removed,
        // A deletion has neither: nothing in the working tree, nothing on disk.
        pointer = null,
        virtualFile = null,
    )

    @Test
    fun `a deleted method is marked as such`() {
        assertTrue(method("refund", removed = true).removed)
        assertTrue(!method("refund", removed = false).removed)
    }

    @Test
    fun `the ledger's view excludes deletions`() {
        val all = listOf(method("refund", removed = true), method("partialRefund", removed = false))

        assertEquals(listOf("partialRefund"), all.filterNot { it.removed }.map { it.id })
    }
}
