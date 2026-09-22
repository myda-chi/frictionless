package com.github.mydachi.frictionless.agent

import com.github.mydachi.frictionless.execution.TestRunner
import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef

/**
 * Deliverable G3: write, run, read the failure, fix, repeat.
 *
 * This is the loop the specification's trust guarantee rests on. The model *proposes* a test; the
 * [TestRunner] *disposes*. A hallucinated pass is structurally impossible, because "passed" is
 * decided by an actual run and nothing else.
 *
 * Capped at [MAX_ATTEMPTS]. On stage an agent that spins is worse than one that admits defeat, so
 * the cap is hard and the failure is reported honestly rather than dressed up.
 */
class CharacterisationTestAgent(
    private val llm: LlmClient,
    private val runner: TestRunner,
    private val writer: TestWriter,
) {

    sealed interface Result {
        /** The test exists, was run, and passed. The only outcome that pins anything. */
        data class Pinned(val test: GeneratedTest, val attempts: Int) : Result

        /** Ran out of attempts. [lastFailure] is what the build actually said. */
        data class GaveUp(val attempts: Int, val lastFailure: String) : Result

        /** Could not even start — no key configured, or the model returned nothing usable. */
        data class Unavailable(val reason: String) : Result
    }

    fun pin(context: TestContext, onProgress: (String) -> Unit = {}): Result {
        if (!llm.isConfigured) return Result.Unavailable(NO_KEY)

        var attempt = context
        var lastFailure = ""

        repeat(MAX_ATTEMPTS) { index ->
            onProgress(if (index == 0) "generating characterisation test…" else "attempt ${index + 1} of $MAX_ATTEMPTS")

            val raw = try {
                llm.complete(TestContext.SYSTEM_PROMPT, attempt.userPrompt())
            } catch (e: Exception) {
                return Result.Unavailable(e.message ?: "the model call failed")
            }

            val generated = GeneratedTest.parse(raw)
                ?: return Result.Unavailable("the model returned no test class")

            if (!writer.write(generated)) {
                lastFailure = "could not write ${generated.qualifiedName} to disk"
                return@repeat
            }

            val results = runner.run(listOf(testRef(generated)))
            val outcome = results.firstOrNull()

            if (outcome?.outcome == TestOutcome.PASSED) {
                return Result.Pinned(generated, index + 1)
            }

            lastFailure = outcome?.output.orEmpty().ifEmpty { "the test did not pass and reported nothing" }
            onProgress(summarise(lastFailure))
            attempt = attempt.copy(
                previousAttempt = TestContext.PreviousAttempt(generated.source, lastFailure),
            )
        }

        return Result.GaveUp(MAX_ATTEMPTS, lastFailure)
    }

    /** The whole generated class is the unit of work, so the method name is the class itself. */
    private fun testRef(test: GeneratedTest) = TestRef(className = test.qualifiedName, methodName = "*")

    /** One short line for the ledger — "Didn't compile. Fixing." reads better than a stack trace. */
    internal fun summarise(failure: String): String = when {
        failure.contains("error:", ignoreCase = true) ||
            failure.contains("cannot find symbol", ignoreCase = true) ||
            failure.contains("unresolved", ignoreCase = true) -> "didn't compile — fixing"
        failure.isBlank() -> "no output — retrying"
        else -> "assertion failed — fixing"
    }

    companion object {
        /** Spec §3.4. Hard cap: three attempts, then report honestly and stop. */
        const val MAX_ATTEMPTS = 3
        const val NO_KEY = "no OPENAI_API_KEY configured — set it and try again"
    }
}
