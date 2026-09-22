package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.ChangeSource
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import git4idea.changes.GitChangeUtils
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Deliverable A5: the branch side of the change source.
 *
 * **What "branch mode" compares.** The base ref against the **current working tree** — so the
 * workflow is "check out the branch you are reviewing, then compare it to `main`". That is how a
 * reviewer actually works locally, and it is the only shape where the rest of the pipeline keeps
 * working: A2 parses the head side as real files on disk, and A3's `ReferencesSearch` and the
 * ledger's navigation both need real PSI. Comparing two arbitrary refs would hand A2 an in-memory
 * PSI file for the head, whose elements no reference search can find — every method would come back
 * with zero reaching tests, and the ledger would confidently report a repository full of untested
 * code. Being unable to review a branch you have not checked out is a much smaller cost.
 *
 * **Nothing downstream changes.** This emits the same [ChangedFile] list as
 * [WorkingTreeChangeSetProvider] — it reuses that class's own converters rather than reimplementing
 * them — so A2, A3, A4 and the ledger cannot tell which mode produced their input. That was the
 * acceptance test for this deliverable.
 */
class BranchChangeSetProvider(
    private val base: String,
    private val head: String,
) : ChangeSetProvider {

    override val source: ChangeSource = ChangeSource.Branch(base, head)

    /**
     * Throws [com.intellij.openapi.vcs.VcsException] when the base ref does not resolve. That is
     * deliberate: a typo'd or deleted base must surface as a failed analysis, never as an empty
     * change set that reads like "this branch changes nothing".
     */
    override fun changedFiles(project: Project): List<ChangedFile> {
        if (project.isDisposed) return emptyList()
        val repository = repositoryFor(project) ?: return emptyList()

        val diff = GitChangeUtils.getDiffWithWorkingDir(project, repository.root, base, null, false)

        val tracked = diff.map { WorkingTreeChangeSetProvider.toChangedFile(project, it) }
        // Uncommitted new files are part of what this branch is proposing too, and they are the most
        // likely home of an untested change — the same reasoning A1 gives.
        val untracked = ChangeListManager.getInstance(project).unversionedFilesPaths
            .mapNotNull { WorkingTreeChangeSetProvider.toAddedFile(project, it) }

        return WorkingTreeChangeSetProvider.merge(tracked, untracked)
    }

    companion object {

        fun repositoryFor(project: Project): GitRepository? =
            GitRepositoryManager.getInstance(project).repositories.firstOrNull()

        /** Local branch names, for the picker. */
        fun localBranches(project: Project): List<String> =
            repositoryFor(project)?.branches?.localBranches?.map { it.name }?.sorted().orEmpty()

        /** What the current checkout is called, which is always the head side of the comparison. */
        fun currentBranch(project: Project): String? =
            repositoryFor(project)?.currentBranchName

        /**
         * The base to offer first: the branch the current one tracks, else `main`, else `master`,
         * else anything that is not the current branch.
         */
        fun defaultBase(project: Project): String? {
            val repository = repositoryFor(project) ?: return null
            val current = repository.currentBranchName
            repository.currentBranch
                ?.findTrackedBranch(repository)
                ?.nameForRemoteOperations
                ?.let { return it }

            val locals = repository.branches.localBranches.map { it.name }
            return listOf("main", "master").firstOrNull { it in locals && it != current }
                ?: locals.firstOrNull { it != current }
        }
    }
}
