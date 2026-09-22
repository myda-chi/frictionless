package com.github.mydachi.frictionless.tour

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.github.mydachi.frictionless.narration.Identifiers
import com.github.mydachi.frictionless.narration.NarrationService
import com.github.mydachi.frictionless.navigation.Navigator
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
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

    /** Milliseconds a stop is held. Long enough to read, short enough to keep a demo moving. */
    var dwellMillis: Int = 3200

    private var timer: Timer? = null
    private var dim: MutableList<Pair<Editor, RangeHighlighter>> = mutableListOf()
    private var listeners = mutableListOf<(Stop?) -> Unit>()

    data class Stop(val index: Int, val total: Int, val method: ChangedMethod)

    val isRunning: Boolean get() = timer?.isRunning == true

    fun addListener(listener: (Stop?) -> Unit) {
        listeners += listener
    }

    fun start() {
        stop()
        val stops = pickStops()
        if (stops.isEmpty()) return

        val narration = project.service<NarrationService>()
        narration.say(opening(stops.size))

        var index = 0
        val step = {
            if (index >= stops.size) {
                narration.say(closing())
                stop()
            } else {
                val method = stops[index]
                show(method)
                narration.say(spokenFor(method))
                listeners.forEach { it(Stop(index, stops.size, method)) }
                index++
            }
        }

        step()
        timer = Timer(dwellMillis) { step() }.apply { isRepeats = true; start() }
    }

    fun stop() {
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

    private fun show(method: ChangedMethod) {
        clearDim()
        val editor = Navigator.open(project, method) ?: return
        val line = (method.line - 1).coerceIn(0, (editor.document.lineCount - 1).coerceAtLeast(0))
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(line))
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
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
}
