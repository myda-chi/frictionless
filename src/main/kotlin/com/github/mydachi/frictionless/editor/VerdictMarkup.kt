package com.github.mydachi.frictionless.editor

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.github.mydachi.frictionless.navigation.Navigator
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.Icon

/**
 * Deliverable U4: gutter icons and end-of-line badges for every changed method.
 *
 * Driven straight off [LedgerModel] rather than a PSI-based LineMarkerProvider, so it works with
 * path-and-line data and updates the moment a verdict arrives.
 */
@Service(Service.Level.PROJECT)
class VerdictMarkup(private val project: Project) {

    private val highlighters = mutableListOf<Pair<Editor, RangeHighlighter>>()
    private val inlays = mutableListOf<Inlay<*>>()

    fun refresh() {
        clear()
        val ready = project.service<LedgerModel>().state as? LedgerState.Ready ?: return
        // Every open text editor, not the selected one repeated (issue #60): the lambda used to
        // ignore its parameter, so a split view left the unfocused pane unmarked during the tour.
        val open = FileEditorManager.getInstance(project).allEditors
            .filterIsInstance<TextEditor>()
            .map { it.editor }
            .distinct()
        ready.changeSet.methods.forEach { method -> open.forEach { editor -> paint(editor, method) } }
    }

    fun clear() {
        highlighters.forEach { (editor, highlighter) -> editor.markupModel.removeHighlighter(highlighter) }
        highlighters.clear()
        inlays.forEach { it.dispose() }
        inlays.clear()
    }

    private fun paint(editor: Editor, method: ChangedMethod) {
        val file = Navigator.resolve(project, method.filePath) ?: return
        if (editor.virtualFile != file) return
        val line = (method.line - 1).coerceIn(0, (editor.document.lineCount - 1).coerceAtLeast(0))

        val highlighter = editor.markupModel.addLineHighlighter(null, line, HighlighterLayer.ADDITIONAL_SYNTAX)
        highlighter.gutterIconRenderer = VerdictGutterIcon(method)
        highlighters += editor to highlighter

        if (method.verdict.bucket == Bucket.UNVERIFIED) {
            val offset = editor.document.getLineEndOffset(line)
            editor.inlayModel.addAfterLineEndElement(offset, false, BadgeRenderer("  no test reaches this code"))
                ?.let { inlays += it }
        }
    }

    private class VerdictGutterIcon(private val method: ChangedMethod) : GutterIconRenderer() {
        override fun getIcon(): Icon = when (method.verdict.bucket) {
            Bucket.PROVEN -> AllIcons.RunConfigurations.TestPassed
            Bucket.BEHAVIOUR_CHANGED -> AllIcons.RunConfigurations.TestFailed
            Bucket.UNVERIFIED -> AllIcons.General.Warning
        }

        override fun getTooltipText(): String = "${method.displayName} — ${method.verdict.display()}"
        override fun equals(other: Any?) = other is VerdictGutterIcon && other.method.id == method.id
        override fun hashCode() = method.id.hashCode()
    }

    private class BadgeRenderer(private val text: String) : EditorCustomElementRenderer {
        override fun calcWidthInPixels(inlay: Inlay<*>): Int =
            inlay.editor.contentComponent.getFontMetrics(UIUtil.getLabelFont()).stringWidth(text)

        override fun paint(inlay: Inlay<*>, g: Graphics, target: Rectangle, textAttributes: com.intellij.openapi.editor.markup.TextAttributes) {
            g.font = UIUtil.getLabelFont()
            g.color = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
            g.drawString(text, target.x, target.y + inlay.editor.ascent)
        }
    }

}
