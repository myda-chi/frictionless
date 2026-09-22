package com.github.mydachi.frictionless.navigation

import com.github.mydachi.frictionless.model.CallSite
import com.github.mydachi.frictionless.model.ChangedMethod
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Paths

/**
 * Deliverable U3: open the thing a ledger row points at.
 *
 * Prefers the PSI pointer when Analysis supplied one, and falls back to path plus line so a row is
 * navigable even before pointers exist.
 */
object Navigator {

    /**
     * Resolving a [com.intellij.psi.SmartPsiElementPointer] and asking an element for its file are
     * both PSI reads, and they happen *before* any editor is opened — so the whole entry point is
     * wrapped, not merely the editor call inside it. Wrapping only the inner call is what left the
     * tour dying on `SmartPsiElementPointerImpl.getElement`.
     */
    fun open(project: Project, method: ChangedMethod): Editor? = WriteIntentReadAction.compute<Editor?> {
        // Pointer first, then the file analysis already resolved. `resolve` is NOT in this chain:
        // it hits the VFS, which the platform classes as a slow operation and forbids on the EDT —
        // and a read action does not make a slow operation allowed.
        val file = method.pointer?.element?.containingFile?.virtualFile ?: method.virtualFile
        file?.let { openIn(project, it, method.line) }
    }

    /**
     * A call site knows its element, so its file comes from the pointer rather than a VFS lookup.
     */
    fun open(project: Project, callSite: CallSite): Editor? = WriteIntentReadAction.compute<Editor?> {
        callSite.pointer?.element?.containingFile?.virtualFile?.let { openIn(project, it, callSite.line) }
    }

    /**
     * Opening an editor moves the caret, and the caret model asserts read access — which the EDT no
     * longer grants implicitly.
     */
    fun open(project: Project, file: VirtualFile, line: Int): Editor? =
        WriteIntentReadAction.compute<Editor?> { openIn(project, file, line) }

    /**
     * Caller already holds the read action.
     *
     * The descriptor deliberately carries no line. `OpenFileDescriptor(project, file, line, column)`
     * builds a lazy range marker, which reaches for the file's code style and therefore its PSI —
     * another slow operation on the EDT. Opening the file and then moving the caret ourselves gets
     * to the same place without it.
     */
    private fun openIn(project: Project, file: VirtualFile, line: Int): Editor? {
        val editor = FileEditorManager.getInstance(project)
            .openTextEditor(OpenFileDescriptor(project, file), true)
            ?: return null

        val target = (line - 1).coerceIn(0, (editor.document.lineCount - 1).coerceAtLeast(0))
        editor.caretModel.moveToOffset(editor.document.getLineStartOffset(target))
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        return editor
    }

    /**
     * Whether [file] is the file [path] names, without touching the VFS.
     *
     * [resolve] is a slow operation and must not run on the EDT; this answers the same question by
     * comparing paths, since analysis paths are project-relative and the editor's are absolute.
     */
    fun samePath(project: Project, file: VirtualFile, path: String): Boolean {
        if (file.path == path) return true
        val base = project.basePath ?: return false
        return file.path == "$base/$path"
    }

    /**
     * Fixtures and early Analysis output carry repo-relative paths; real output may be absolute.
     *
     * **Off the EDT only** — this hits the VFS, which the platform classes as a slow operation.
     */
    fun resolve(project: Project, path: String): VirtualFile? {
        val fs = LocalFileSystem.getInstance()
        fs.findFileByPath(path)?.let { return it }
        val base = project.basePath ?: return null
        return fs.findFileByPath(Paths.get(base, path).toString())
    }
}
