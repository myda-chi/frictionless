package com.github.mydachi.frictionless

import com.github.mydachi.frictionless.model.Bucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the verdict copy to specification.md §3.6.
 *
 * This test exists so the on-screen text and the narration can never drift apart: both come from
 * [com.github.mydachi.frictionless.model.Verdict], and changing one without the other fails here.
 */
class VerdictCopyTest {

    @Test
    fun `proven reads and speaks as specified`() {
        val verdict = ReceiptsFixtures.proven().verdict
        assertEquals("✓ 4 tests reached it · passed in 0.3s", verdict.display())
        assertEquals("Proven. Four tests cover this.", verdict.spoken())
    }

    @Test
    fun `behaviour changed reads and speaks as specified`() {
        val verdict = ReceiptsFixtures.behaviourChanged().verdict
        assertEquals("⚠ 2 reaching tests red — click to open", verdict.display())
        assertEquals("Behaviour changed. Two tests failing.", verdict.spoken())
    }

    @Test
    fun `unverified reads and speaks as specified`() {
        val verdict = ReceiptsFixtures.unverified().verdict
        assertEquals("✗ no test reaches this code · 3 call sites", verdict.display())
        assertEquals("Nothing tests this.", verdict.spoken())
    }

    @Test
    fun `every spoken line stays under six words`() {
        Bucket.entries.forEach { bucket ->
            val spoken = ReceiptsFixtures.changeSet().methods
                .first { it.verdict.bucket == bucket }
                .verdict.spoken()
            val words = spoken.trim().split(Regex("\\s+")).size
            assertTrue("$bucket spoke $words words: \"$spoken\"", words < 6)
        }
    }
}
