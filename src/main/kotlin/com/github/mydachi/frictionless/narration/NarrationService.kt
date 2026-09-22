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
 * Provider is detected once, in priority order. If none is present the tour runs silently and the
 * on-screen line carries the message — narration is decoration, never a dependency.
 *
 * Identifiers reach this class straight from source code, so arguments are always passed as a list.
 * Nothing is ever interpolated into a shell.
 */
@Service(Service.Level.PROJECT)
class NarrationService(@Suppress("unused") private val project: Project) {

    /** How a detected binary is invoked. `%s` is never used — the text is a separate argument. */
    private data class Provider(val binary: String, val args: (String) -> List<String>)

    private val providers = listOf(
        Provider("piper") { listOf("--output-raw") },              // text arrives on stdin
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
        providers.firstOrNull { PathEnvironmentVariableUtil.findInPath(it.binary) != null }?.binary
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
                if (chosen.binary == "piper") {
                    process.outputStream.use { it.write(text.toByteArray()); it.flush() }
                }
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
