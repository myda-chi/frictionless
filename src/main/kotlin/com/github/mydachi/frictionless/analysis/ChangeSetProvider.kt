package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangeSource
import com.intellij.openapi.project.Project

/**
 * Deliverable A1: where a change set comes from.
 *
 * The seam exists so branch mode (A5) is a second implementation rather than an `if` inside the
 * analyser — specification.md §4.2 asks for exactly that: "Everything downstream is identical: both
 * modes emit the same `ChangeSet`". [WorkingTreeChangeSetProvider] is the working-tree side.
 */
interface ChangeSetProvider {

    val source: ChangeSource

    /**
     * The files that differ from the revision this checkout is based on, sorted by path so two runs
     * over an unchanged tree produce identical output.
     *
     * An empty list is a valid answer: a clean checkout has no change set. It is not an error, and
     * nothing downstream should substitute made-up data for it (specification.md §3.7).
     */
    fun changedFiles(project: Project): List<ChangedFile>

    /**
     * The same thing wrapped as the frozen [ChangeSet] everything downstream consumes.
     *
     * A1 fills [ChangeSet.source] and the file list; [ChangeSet.methods] stays empty because
     * method-level comparison is A2's deliverable. Nothing hands this to the ledger yet — a ledger
     * showing zero methods because the analyser has not run is worse than the honest "not
     * implemented" the Run action reports today.
     */
    fun changeSet(project: Project): ChangeSet = ChangeSet(source, emptyList())
}
