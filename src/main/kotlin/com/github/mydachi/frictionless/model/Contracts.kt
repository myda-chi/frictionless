package com.github.mydachi.frictionless.model

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/**
 * Frozen shared contracts (deliverable S2). Every track builds against these types and nothing else.
 *
 * Changing anything in this file means telling all four tracks. See specification.md §4.3.
 */

/** What we are reviewing. The only thing that differs between review modes. */
sealed interface ChangeSource {
    /** HEAD to uncommitted working-tree changes. */
    data object WorkingTree : ChangeSource

    /** A branch compared against a base ref, typically `main`. */
    data class Branch(val base: String, val head: String) : ChangeSource
}

/** The output of the Analysis track and the input to everything else. */
data class ChangeSet(
    val source: ChangeSource,
    val methods: List<ChangedMethod>,
)

/**
 * One changed method.
 *
 * Display fields are plain data so the ledger can paint a row without a read action; [pointer] is
 * how we navigate to it and is null in test fixtures.
 */
data class ChangedMethod(
    val id: String,
    val displayName: String,
    val filePath: String,
    val line: Int,
    val verdict: Verdict,
    val callSites: List<CallSite> = emptyList(),
    val reachingTests: List<TestRef> = emptyList(),
    val pointer: SmartPsiElementPointer<PsiElement>? = null,
)

data class CallSite(
    val displayName: String,
    val filePath: String,
    val line: Int,
    val pointer: SmartPsiElementPointer<PsiElement>? = null,
)

/** A test that reaches a changed method. Class and method names are what the JUnit runner needs. */
data class TestRef(
    val className: String,
    /** Empty means the whole class — see [wholeClass]. */
    val methodName: String,
    val outcome: TestOutcome = TestOutcome.NOT_RUN,
    val pointer: SmartPsiElementPointer<PsiElement>? = null,
) {
    val displayName: String get() = if (methodName.isEmpty()) className else "$className.$methodName"

    /** Whether this names a whole class rather than one method inside it. */
    val isWholeClass: Boolean get() = methodName.isEmpty()

    companion object {
        /**
         * Every test in a class.
         *
         * The agent generates a test *class* and has no method name to give until the model has
         * written one, so it needs a way to say "run all of it". It used to say `methodName = "*"`,
         * which JUnit cannot run — the pattern `Class,*` is neither a class nor a class-and-method
         * (issue #57). An empty method name is the honest representation, and the run-configuration
         * builder renders it as the bare class name.
         */
        fun wholeClass(className: String) = TestRef(className = className, methodName = "")
    }
}

enum class TestOutcome { NOT_RUN, PASSED, FAILED, ERROR }

enum class Bucket {
    /** Covered by tests that reach it, and those tests pass. */
    PROVEN,

    /** Covered, and a reaching test is red right now. We do not run a pre-change baseline. */
    BEHAVIOUR_CHANGED,

    /** Changed, and nothing in the repo executes it. This is the product. */
    UNVERIFIED,
}

data class VerdictCounts(
    val reachingTests: Int = 0,
    val passed: Int = 0,
    val failed: Int = 0,
    val callSites: Int = 0,
    val durationMs: Long = 0,
)

/**
 * A method's standing, and the single source of truth for how it is worded.
 *
 * [display] and [spoken] live together so the ledger and the narration can never contradict each
 * other mid-demo. Copy is specified in specification.md §3.6 and pinned by VerdictCopyTest.
 */
data class Verdict(
    val bucket: Bucket,
    val counts: VerdictCounts = VerdictCounts(),
) {
    /** On-screen text. Carries the detail. */
    fun display(): String = when (bucket) {
        Bucket.PROVEN ->
            "✓ ${counts.reachingTests} ${tests(counts.reachingTests)} reached it · passed in ${seconds()}"
        Bucket.BEHAVIOUR_CHANGED ->
            "⚠ ${counts.failed} reaching ${tests(counts.failed)} red — click to open"
        Bucket.UNVERIFIED ->
            "✗ no test reaches this code · ${counts.callSites} ${sites(counts.callSites)}"
    }

    /** Narration text. Under six words, always. */
    fun spoken(): String = when (bucket) {
        Bucket.PROVEN -> "Proven. ${spell(counts.reachingTests)} ${tests(counts.reachingTests)} cover this."
        Bucket.BEHAVIOUR_CHANGED -> "Behaviour changed. ${spell(counts.failed)} ${tests(counts.failed)} failing."
        Bucket.UNVERIFIED -> "Nothing tests this."
    }

    private fun seconds(): String = "%.1fs".format(counts.durationMs / 1000.0)

    private fun tests(n: Int) = if (n == 1) "test" else "tests"

    private fun sites(n: Int) = if (n == 1) "call site" else "call sites"

    private companion object {
        val WORDS = listOf(
            "zero", "one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten",
        )

        fun spell(n: Int): String = WORDS.getOrNull(n)?.replaceFirstChar { it.uppercase() } ?: n.toString()
    }
}
