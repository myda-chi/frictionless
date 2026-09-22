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
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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

    /** Everything drawn on one editor, so it can all be taken back off again. */
    private class Marks(val file: VirtualFile) {
        val highlighters = mutableListOf<RangeHighlighter>()
        val inlays = mutableListOf<Inlay<*>>()
        val methodIds = mutableSetOf<String>()
    }

    private val painted = LinkedHashMap<Editor, Marks>()

    init {
        // A file opened *after* the verdict landed would otherwise show no marks until the next
        // refresh — and for a tour that has already stopped, there is no next refresh. The subscription
        // shares this service's lifetime, so it needs no parent disposable.
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    val ready = readyState() ?: return
                    source.getEditors(file).filterIsInstance<TextEditor>()
                        .forEach { paintAll(it.editor, ready.changeSet.methods) }
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    // The editor is being disposed and takes its markup with it. Forgetting the
                    // bookkeeping here — without calling into the dying editor — is what stops a later
                    // [clear] from touching a disposed one.
                    painted.filterValues { it.file == file }.keys.forEach { painted.remove(it) }
                }
            },
        )
    }

    fun refresh() {
        clear()
        val ready = readyState() ?: return
        openEditors().forEach { paintAll(it, ready.changeSet.methods) }
    }

    fun clear() {
        painted.forEach { (editor, marks) ->
            marks.highlighters.forEach { editor.markupModel.removeHighlighter(it) }
            marks.inlays.forEach { it.dispose() }
        }
        painted.clear()
    }

    /**
     * Every open text editor — one entry per split or tab (#60), not merely the focused one.
     *
     * `allEditors` also carries non-text editors (a diff view, the UI designer), which is why this
     * filters by type rather than taking the array as it comes.
     */
    internal fun openEditors(): List<Editor> =
        FileEditorManager.getInstance(project).allEditors
            .filterIsInstance<TextEditor>()
            .map { it.editor }

    private fun readyState(): LedgerState.Ready? =
        project.service<LedgerModel>().state as? LedgerState.Ready

    private fun paintAll(editor: Editor, methods: List<ChangedMethod>) =
        methods.forEach { paint(editor, it) }

    private fun paint(editor: Editor, method: ChangedMethod) {
        val file = Navigator.resolve(project, method.filePath) ?: return
        if (editor.virtualFile != file) return

        val marks = painted.getOrPut(editor) { Marks(file) }
        // `fileOpened` also reports a file that is already open in another split, so drawing happens
        // once per (editor, method) rather than once per event.
        if (!marks.methodIds.add(method.id)) return

        val line = (method.line - 1).coerceIn(0, (editor.document.lineCount - 1).coerceAtLeast(0))

        val highlighter = editor.markupModel.addLineHighlighter(null, line, HighlighterLayer.ADDITIONAL_SYNTAX)
        highlighter.gutterIconRenderer = VerdictGutterIcon(method)
        marks.highlighters += highlighter

        if (method.verdict.bucket == Bucket.UNVERIFIED) {
            val offset = editor.document.getLineEndOffset(line)
            editor.inlayModel.addAfterLineEndElement(offset, false, BadgeRenderer("  no test reaches this code"))
                ?.let { marks.inlays += it }
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
