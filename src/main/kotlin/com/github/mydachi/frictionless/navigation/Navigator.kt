package com.github.mydachi.frictionless.navigation

import com.github.mydachi.frictionless.model.CallSite
import com.github.mydachi.frictionless.model.ChangedMethod
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

    fun open(project: Project, file: VirtualFile, line: Int): Editor? =
        FileEditorManager.getInstance(project)
            .openTextEditor(OpenFileDescriptor(project, file, (line - 1).coerceAtLeast(0), 0), true)

    /** Fixtures and early Analysis output carry repo-relative paths; real output may be absolute. */
    fun resolve(project: Project, path: String): VirtualFile? {
        val fs = LocalFileSystem.getInstance()
        fs.findFileByPath(path)?.let { return it }
        val base = project.basePath ?: return null
        return fs.findFileByPath(Paths.get(base, path).toString())
    }
}
