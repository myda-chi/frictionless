package com.github.mydachi.frictionless.narration

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deliverable U7: speak a verdict line, if this machine can.
 *
 * Provider is detected once, in priority order, and the choice is logged — a tour that runs silently
 * should be diagnosable in ten seconds rather than looking like a broken feature. If none is present
 * the tour runs silently and the on-screen line carries the message: narration is decoration, never
 * a dependency.
 *
 * Identifiers reach this class straight from source code, so arguments are always passed as a list.
 * Nothing is ever interpolated into a shell.
 */
@Service(Service.Level.PROJECT)
class NarrationService(@Suppress("unused") private val project: Project) {

    /** How a detected binary is invoked. `%s` is never used — the text is a separate argument. */
    private data class Provider(val binary: String, val args: (String) -> List<String>)

    /**
     * Every provider here speaks to the audio device on its own. `piper` deliberately is not one:
     * it writes raw PCM to stdout and needs a `--model` voice file, so invoking it the way this
     * class invokes everything else produced **no sound at all** — and being first in the list, it
     * was chosen ahead of providers that work (issue #62). Silent narration that looks like it ran
     * is worse than no narration, so it is gone rather than half-wired. Adding it back means
     * shipping a voice model and piping stdout to a player, which is a feature, not a list entry.
     */
    private val providers = listOf(
        Provider("espeak-ng") { text -> listOf("-s", "150", "-v", "en-us", text) },
        Provider("spd-say") { text -> listOf("-w", text) },
        Provider("say") { text -> listOf(text) },
    )

    private val queue = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Frictionless narration").apply { isDaemon = true }
    }

    @Volatile
    private var current: Process? = null

    /**
     * Bumped by [stop]. A queued utterance checks this against the generation it was submitted
     * under and drops itself if [stop] has since moved on - otherwise a Stop only kills the
     * utterance speaking *right now*, and everything already queued still fires afterwards
     * (issue #61), which is exactly the "narration talks over the presenter" risk specification.md
     * §8.1 calls out.
     */
    private val generation = AtomicInteger(0)

    val provider: String? by lazy {
        val found = providers.firstOrNull { PathEnvironmentVariableUtil.findInPath(it.binary) != null }?.binary
        if (found != null) {
            thisLogger().info("Narration will use '$found'")
        } else {
            thisLogger().info(
                "No text-to-speech binary on PATH (looked for ${providers.joinToString { it.binary }}); " +
                    "the tour will run silently",
            )
        }
        found
    }

    val isAvailable: Boolean get() = provider != null

    /** Queued so two lines never overlap. Returns immediately. */
    fun say(text: String) {
        val chosen = providers.firstOrNull { it.binary == provider } ?: return
        val submittedGeneration = generation.get()
        queue.submit {
            if (generation.get() != submittedGeneration) return@submit
            try {
                val command = GeneralCommandLine(chosen.binary).withParameters(chosen.args(text))
                val process = command.createProcess()
                current = process
                process.waitFor()
            } catch (e: Exception) {
                thisLogger().info("Narration failed, continuing silently", e)
            } finally {
                current = null
            }
        }
    }

    /**
     * Called when the tour is cancelled, so nothing talks over the presenter afterwards: kills
     * whatever is speaking right now, and drops everything still queued behind it.
     */
    fun stop() {
        generation.incrementAndGet()
        current?.destroyForcibly()
        current = null
    }
}
