package com.github.mydachi.frictionless.navigation

import com.github.mydachi.frictionless.model.CallSite
import com.github.mydachi.frictionless.model.ChangedMethod
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.Editor
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

    fun open(project: Project, method: ChangedMethod): Editor? {
        method.pointer?.element?.let { element ->
            val file = element.containingFile?.virtualFile
            if (file != null) return open(project, file, method.line)
        }
        return resolve(project, method.filePath)?.let { open(project, it, method.line) }
    }

    fun open(project: Project, callSite: CallSite): Editor? =
        resolve(project, callSite.filePath)?.let { open(project, it, callSite.line) }

    /**
     * Opening an editor moves the caret, and the caret model asserts read access — which the EDT no
     * longer grants implicitly. Without this the Autopilot tour dies on its first stop with "Read
     * access is allowed from inside read-action only", which is what made the tour speak one line
     * and then stop.
     */
    fun open(project: Project, file: VirtualFile, line: Int): Editor? =
        WriteIntentReadAction.compute<Editor?> {
            FileEditorManager.getInstance(project)
                .openTextEditor(OpenFileDescriptor(project, file, (line - 1).coerceAtLeast(0), 0), true)
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
