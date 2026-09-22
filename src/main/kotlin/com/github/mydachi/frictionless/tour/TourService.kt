package com.github.mydachi.frictionless.tour

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.github.mydachi.frictionless.narration.Identifiers
import com.github.mydachi.frictionless.narration.NarrationService
import com.github.mydachi.frictionless.navigation.Navigator
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import javax.swing.Timer

/**
 * Deliverable U6: Autopilot Review.
 *
 * Presses Play and the IDE reviews itself — opens each interesting method, dims everything around
 * it, speaks one short verdict, and moves on. Cancellable at any point, and cancelling silences the
 * narration immediately so nothing talks over the presenter afterwards.
 *
 * It visits the five most interesting stops, not every changed method. Riskiest first: unverified,
 * then behaviour changed, then proven.
 */
@Service(Service.Level.PROJECT)
class TourService(private val project: Project) {

    /**
     * How long a stop is held *after* its line has finished being spoken. The total time per stop is
     * the sentence plus this, so it stays in step with the voice on any machine.
     */
    var dwellMillis: Int = 1200

    private var timer: Timer? = null
    private var stops: List<ChangedMethod> = emptyList()

    @Volatile
    private var cancelled = false
    private var dim: MutableList<Pair<Editor, RangeHighlighter>> = mutableListOf()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(Stop?) -> Unit>()

    data class Stop(val index: Int, val total: Int, val method: ChangedMethod)

    val isRunning: Boolean get() = !cancelled && timer?.isRunning == true

    /**
     * Registers [listener] for as long as [parent] lives.
     *
     * Taking a [Disposable] rather than offering an unregister call is the platform idiom, and it is
     * what stops a closed tool window from going on receiving stops for ever: this is a project
     * service, so it outlives every panel that listens to it (issue #63). Without it, reopening the
     * tool window a few times meant each dead panel still handled every stop and painted into
     * disposed components.
     */
    fun addListener(parent: Disposable, listener: (Stop?) -> Unit) {
        listeners += listener
        Disposer.register(parent) { listeners -= listener }
    }

    fun start() {
        stop()
        val stops = pickStops()
        if (stops.isEmpty()) return

        this.stops = stops
        cancelled = false
        thisLogger().info("Tour starting: ${stops.size} stop(s) — ${stops.joinToString { it.displayName }}")
        project.service<NarrationService>().say(opening(stops.size))
        schedule(SETTLE_MILLIS) { runStop(0) }
    }

    /**
     * One stop: move, then tell the UI, then let it land, then speak.
     *
     * The order is the whole fix. The editor and the ledger row change together and *before* the
     * voice starts, and a short settle keeps the highlight from arriving mid-sentence. The tour used
     * to advance on a fixed timer while narration queued up behind it, so by the second stop the
     * voice was describing a method the screen had already left.
     */
    private fun runStop(index: Int) {
        if (cancelled) return
        val stops = this.stops
        if (index >= stops.size) {
            project.service<NarrationService>().say(closing())
            whenQuiet { stop() }
            return
        }

        val method = stops[index]
        try {
            show(method)
            listeners.forEach { it(Stop(index, stops.size, method)) }
        } catch (e: Exception) {
            thisLogger().warn("Tour stopped at stop ${index + 1} of ${stops.size}", e)
            stop()
            return
        }

        schedule(SETTLE_MILLIS) {
            val line = spokenFor(method)
            thisLogger().info("Tour stop ${index + 1}/${stops.size}: ${method.displayName} — \"$line\"")
            project.service<NarrationService>().say(line)
            // Advance when the line has actually finished, not when a guessed duration has elapsed.
            whenQuiet { schedule(dwellMillis) { runStop(index + 1) } }
        }
    }

    /**
     * Polls until nothing is speaking, then runs [action]. Silent machines fall straight through.
     *
     * Bounded by [MAX_WAIT_MILLIS]. Waiting on the voice is what keeps the narration in step with
     * the highlight, but a tour that waits *for ever* because something never reported itself
     * finished is worse than one that runs slightly ahead — on stage it just looks frozen. After the
     * cap it gives up on the line and carries on, and says so in the log.
     */
    private fun whenQuiet(waitedMillis: Int = 0, action: () -> Unit) {
        if (cancelled) return
        when {
            !project.service<NarrationService>().isSpeaking -> action()

            waitedMillis >= MAX_WAIT_MILLIS -> {
                thisLogger().warn(
                    "Narration did not finish within ${MAX_WAIT_MILLIS}ms; continuing the tour without waiting",
                )
                action()
            }

            else -> schedule(POLL_MILLIS) { whenQuiet(waitedMillis + POLL_MILLIS, action) }
        }
    }

    private fun schedule(delayMillis: Int, action: () -> Unit) {
        timer?.stop()
        timer = Timer(delayMillis) { if (!cancelled) action() }.apply { isRepeats = false; start() }
    }

    fun stop() {
        cancelled = true
        timer?.stop()
        timer = null
        clearDim()
        project.service<NarrationService>().stop()
        listeners.forEach { it(null) }
    }

    /** Riskiest first, capped at five. Spec §3.5. */
    internal fun pickStops(): List<ChangedMethod> {
        val ready = project.service<LedgerModel>().state as? LedgerState.Ready ?: return emptyList()
        val rank = mapOf(Bucket.UNVERIFIED to 0, Bucket.BEHAVIOUR_CHANGED to 1, Bucket.PROVEN to 2)
        return ready.changeSet.methods
            .sortedBy { rank[it.verdict.bucket] ?: 3 }
            .take(5)
    }

    /**
     * One read action around the whole stop. Opening the file, moving the caret, scrolling and
     * painting the spotlight are all model reads, and the EDT does not grant read access implicitly
     * — doing them piecemeal is what made the tour die two stops in.
     */
    private fun show(method: ChangedMethod) = WriteIntentReadAction.run<RuntimeException> {
        clearDim()
        // Navigator positions the caret and scrolls; this only needs the line for the spotlight.
        val editor = Navigator.open(project, method)
        if (editor == null) {
            // The one silent path through a stop. A stale PSI pointer plus a missing VirtualFile
            // leaves nothing to open and nothing in the log, which from the outside is
            // indistinguishable from the tour having stopped here.
            thisLogger().warn(
                "Could not open ${method.displayName} (${method.filePath}); " +
                    "pointerResolved=${method.pointer?.element != null}, file=${method.virtualFile != null}",
            )
            return@run
        }
        val line = (method.line - 1).coerceIn(0, (editor.document.lineCount - 1).coerceAtLeast(0))
        spotlight(editor, line)
    }

    /** Everything outside the stop is dimmed, so the eye goes where the verdict is. */
    private fun spotlight(editor: Editor, line: Int) {
        val document = editor.document
        val from = document.getLineStartOffset((line - 2).coerceAtLeast(0))
        val to = document.getLineEndOffset((line + 6).coerceAtMost(document.lineCount - 1))
        val dimmed = TextAttributes().apply { foregroundColor = JBColor.namedColor("Label.disabledForeground", JBColor.GRAY) }

        if (from > 0) add(editor, 0, from, dimmed)
        if (to < document.textLength) add(editor, to, document.textLength, dimmed)
    }

    private fun add(editor: Editor, start: Int, end: Int, attributes: TextAttributes) {
        val highlighter = editor.markupModel.addRangeHighlighter(
            start, end, HighlighterLayer.LAST, attributes, HighlighterTargetArea.EXACT_RANGE,
        )
        dim += editor to highlighter
    }

    private fun clearDim() {
        dim.forEach { (editor, highlighter) -> editor.markupModel.removeHighlighter(highlighter) }
        dim.clear()
    }

    private fun opening(stops: Int): String = "$stops stops. Reviewing."

    private fun closing(): String {
        val counts = project.service<LedgerModel>().counts()
        val proven = counts[Bucket.PROVEN] ?: 0
        val unverified = counts[Bucket.UNVERIFIED] ?: 0
        return "$proven proven. $unverified unverified."
    }

    /** "Checking apply F X margin." then the verdict's own spoken line. */
    private fun spokenFor(method: ChangedMethod): String =
        "Checking ${Identifiers.humanise(method.displayName)}. ${method.verdict.spoken()}"

    private companion object {
        /** Lets the editor and the ledger row settle before the voice starts. */
        const val SETTLE_MILLIS = 400

        /** How often to check whether the current line has finished. */
        const val POLL_MILLIS = 120

        /** Longest the tour will wait on one line before moving on regardless. */
        const val MAX_WAIT_MILLIS = 8000
    }
}
