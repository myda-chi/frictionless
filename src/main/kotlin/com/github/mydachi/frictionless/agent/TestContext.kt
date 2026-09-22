package com.github.mydachi.frictionless.agent

import com.github.mydachi.frictionless.model.ChangedMethod

/**
 * Deliverable G2: everything the model needs to write a test that fits *this* repository.
 *
 * The brief asks what makes a test generator more than "call the API with the function body". This
 * is the answer: the method's real source, the arguments its actual callers pass, and two tests the
 * team already wrote, so the output matches local convention instead of a generic tutorial.
 */
data class TestContext(
    val method: ChangedMethod,
    val methodSource: String,
    /** Snippets from real call sites — where the plausible argument values come from. */
    val callSiteSnippets: List<String> = emptyList(),
    /** Existing tests, used as convention examples: naming, framework, assertion style, fixtures. */
    val conventionExamples: List<String> = emptyList(),
    val testPackage: String = "",
    val previousAttempt: PreviousAttempt? = null,
) {

    /** What the last attempt produced, so the next one is a fix rather than a fresh guess. */
    data class PreviousAttempt(val source: String, val failure: String)

    fun userPrompt(): String = buildString {
        appendLine("Write a characterisation test that pins the CURRENT behaviour of this method.")
        appendLine()
        appendLine("Method: ${method.displayName}  (${method.filePath}:${method.line})")
        appendLine("```")
        appendLine(methodSource.trim())
        appendLine("```")

        if (callSiteSnippets.isNotEmpty()) {
            appendLine()
            appendLine("Real call sites — use argument values consistent with these:")
            callSiteSnippets.forEach { appendLine("```\n${it.trim()}\n```") }
        }

        if (conventionExamples.isNotEmpty()) {
            appendLine()
            appendLine("Existing tests in this repository. Match their framework, naming and assertion style:")
            conventionExamples.forEach { appendLine("```\n${it.trim()}\n```") }
        }

        if (testPackage.isNotEmpty()) {
            appendLine()
            appendLine("Put the test in package `$testPackage`.")
        }

        previousAttempt?.let { attempt ->
            appendLine()
            appendLine("Your previous attempt did not pass. Fix it — do not start over.")
            appendLine("Previous test:")
            appendLine("```\n${attempt.source.trim()}\n```")
            appendLine("What the build reported:")
            appendLine("```\n${attempt.failure.trim().take(MAX_FAILURE_CHARS)}\n```")
        }
    }

    companion object {
        /** Compiler output can be enormous; the first part carries the actual cause. */
        const val MAX_FAILURE_CHARS = 4000

        val SYSTEM_PROMPT = """
            You write characterisation tests for an existing codebase.

            A characterisation test pins behaviour as it is TODAY. It is not a specification and not a
            bug report. If the current behaviour looks wrong, still pin it — the test documents reality
            so a future change cannot alter it silently.

            Rules:
            - Match the repository's existing test framework, naming and assertion style exactly.
            - Assert concrete observable values. Never assert that something merely does not throw.
            - No mocking framework unless the examples already use one.
            - Output ONLY the test file source. No prose, no explanation, no markdown fence.
        """.trimIndent()
    }
}
