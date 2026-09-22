package com.github.mydachi.frictionless.analysis

import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vfs.VirtualFile

/**
 * Deliverable A1: one file that differs from the revision the current checkout is based on.
 *
 * This is the file-level half of the change set, and it is deliberately **not** part of the frozen
 * contracts in `model/Contracts.kt`. A file can be changed without any method changing (a comment,
 * an import, a formatting pass), and A2 is what collapses those away. Keeping the file list out of
 * [com.github.mydachi.frictionless.model.ChangeSet] means A5's branch source has to produce the same
 * file list without either track touching a type the other three already build against.
 */
data class ChangedFile(
    /** Project-relative when the file is inside the project, the platform's absolute path otherwise. */
    val path: String,

    val kind: ChangedFileKind,

    /** The IDE's own status for the file, for the ledger's colour. Null when the VCS did not say. */
    val status: FileStatus?,

    /** The file in the working tree. Null for a deletion — there is nothing on disk left to parse. */
    val file: VirtualFile?,

    /**
     * The revision the working tree is compared against. A2 parses this as the base `PsiFile`;
     * null means there is no base (a file the developer has just created).
     */
    val baseRevision: ContentRevision?,
)

/** How a file differs. Mirrors the buckets A2 has to handle: added, removed and modified. */
enum class ChangedFileKind {
    MODIFIED,
    ADDED,
    DELETED,
    MOVED;

    companion object {
        /**
         * VCS implementations differ in how much they fill in: [Change.getType] is the precise answer
         * and [FileStatus] is the fallback. Both being unknown is a modification, never a drop — a
         * file the change list manager lists is in the change set.
         */
        fun of(type: Change.Type?, status: FileStatus?): ChangedFileKind = when (type) {
            Change.Type.NEW -> ADDED
            Change.Type.DELETED -> DELETED
            Change.Type.MOVED -> MOVED
            Change.Type.MODIFICATION -> MODIFIED
            null -> when (status) {
                FileStatus.ADDED -> ADDED
                FileStatus.DELETED, FileStatus.DELETED_FROM_FS -> DELETED
                else -> MODIFIED
            }
        }
    }
}
