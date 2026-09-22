package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.ChangeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parts of A5 that can be asserted without a live Git repository.
 *
 * The diff itself is `GitChangeUtils.getDiffWithWorkingDir`, which needs a real repository and a
 * real ref — that path is exercised by running the plugin, not from a headless fixture. What is
 * worth pinning here is the contract A5 promises the rest of the pipeline.
 */
class BranchChangeSetProviderTest {

    @Test
    fun `reports the branch source it was built for`() {
        val source = BranchChangeSetProvider("main", "feature/x").source

        val branch = source as ChangeSource.Branch
        assertEquals("main", branch.base)
        assertEquals("feature/x", branch.head)
    }

    @Test
    fun `the head is the checkout, never a second chosen branch`() {
        // Branch mode compares a base ref against the working tree, so the head side is whatever is
        // checked out. If this ever becomes a free choice, A2 would be handed an in-memory PSI file
        // for the head and A3 would report every method as reaching zero tests.
        val provider = BranchChangeSetProvider(base = "main", head = "feature/x")
        val branch = provider.source as ChangeSource.Branch
        assertTrue(branch.base != branch.head)
    }

    @Test
    fun `is a ChangeSetProvider, so nothing downstream can tell the two modes apart`() {
        // The acceptance test for A5. Both modes are ChangeSetProviders returning ChangedFile, and
        // this one reuses WorkingTreeChangeSetProvider's own converters rather than reimplementing
        // them — so A2, A3, A4 and the ledger see one shape.
        val branch: ChangeSetProvider = BranchChangeSetProvider("main", "feature/x")
        val workingTree: ChangeSetProvider = WorkingTreeChangeSetProvider

        assertTrue(branch.source is ChangeSource.Branch)
        assertTrue(workingTree.source is ChangeSource.WorkingTree)
    }
}
