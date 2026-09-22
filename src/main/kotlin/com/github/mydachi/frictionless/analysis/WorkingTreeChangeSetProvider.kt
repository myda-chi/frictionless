package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.ChangeSource
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile

/**
 * Deliverable A1: `ChangeListManager` yields the changed files for the current checkout.
 *
 * Working-tree mode is the revision the checkout is based on (HEAD) versus what is on disk right now.
 * That is the whole query — `ChangeListManager` already knows the answer, and it is the tracked-change
 * list the Commit dialog shows, so what we review is what a developer would be about to commit.
 *
 * `getAllChanges()` rather than `getAffectedFiles()`: the latter gives paths only, while each [Change]
 * carries the before revision A2 needs to parse the base tree *and* the IDE's own status for the
 * ledger's colour — one call instead of a per-file lookup, and no invalid-`VirtualFile` handling for
 * deletions.
 *
 * Two deliberate choices worth knowing about:
 *
 *  - **Unversioned files are part of the change set.** A file the developer has just created is not in
 *    a change list yet, but it is unambiguously part of what is under review — and it is the most
 *    likely home of an untested change.
 *  - **Nothing here resolves PSI.** A1 answers "which files", A2 answers "which methods inside them".
 *    Doing it in that order is what keeps the A2 diff small enough to land.
 */
object WorkingTreeChangeSetProvider : ChangeSetProvider {

    override val source: ChangeSource = ChangeSource.WorkingTree

    override fun changedFiles(project: Project): List<ChangedFile> {
        if (project.isDisposed) return emptyList()

        val changeListManager = ChangeListManager.getInstance(project)

        val tracked = changeListManager.allChanges.map { toChangedFile(project, it) }
        val untracked = changeListManager.unversionedFilesPaths.mapNotNull { toAddedFile(project, it) }

        return merge(tracked, untracked)
    }

    /**
     * Deduplicate by path and sort, so two runs over an unchanged tree produce identical output and
     * the ledger's row order never depends on VCS iteration order.
     */
    internal fun merge(tracked: List<ChangedFile>, untracked: List<ChangedFile>): List<ChangedFile> =
        (tracked + untracked).distinctBy { it.path }.sortedBy { it.path }

    /** A tracked change: the file, how it differs, and the base revision A2 parses. */
    internal fun toChangedFile(project: Project, change: Change): ChangedFile {
        val file = change.virtualFile
        return ChangedFile(
            path = file?.let { displayPath(project, it) }
                ?: change.afterRevision?.file?.path
                ?: change.beforeRevision?.file?.path
                .orEmpty(),
            kind = ChangedFileKind.of(change.type, change.fileStatus),
            status = change.fileStatus,
            file = file?.takeIf { it.isValid },
            baseRevision = change.beforeRevision,
        )
    }

    /**
     * An unversioned file: new, so there is no base revision to compare against, and no status — the
     * VCS has not classified it yet. Directories are skipped; A2 parses files.
     */
    internal fun toAddedFile(project: Project, path: FilePath): ChangedFile? {
        if (path.isDirectory) return null
        val file = path.virtualFile ?: return null
        return ChangedFile(
            path = displayPath(project, file),
            kind = ChangedFileKind.ADDED,
            status = null,
            file = file,
            baseRevision = null,
        )
    }

    /**
     * Project-relative when the file is inside the project, absolute otherwise.
     *
     * `Project.getBasePath()` rather than `getBaseDir()`: the latter is deprecated in 2025.2, and
     * `ProjectUtil.guessProjectDir` — the documented replacement — is not reachable from the plugin's
     * compile classpath (`ProjectUtil` ships in `lib/app-client.jar`, not on it). A string prefix test
     * answers the same question with no extra dependency, and a file outside the project keeps its
     * absolute path rather than losing it.
     */
    internal fun displayPath(project: Project, file: VirtualFile): String {
        val basePath = project.basePath ?: return file.path
        val prefix = "$basePath/"
        return if (file.path.startsWith(prefix)) file.path.removePrefix(prefix) else file.path
    }
}
