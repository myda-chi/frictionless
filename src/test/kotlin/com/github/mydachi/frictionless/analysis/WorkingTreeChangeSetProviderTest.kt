package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.ChangeSource
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.vcsUtil.VcsUtil
import java.nio.file.Files

/**
 * A1 acceptance: `ChangeListManager` yields the changed files for the current checkout, wrapped as a
 * change set.
 *
 * **Coverage boundary, stated plainly.** The platform's test fixture runs with no VCS registered, so
 * `ChangeListManager` reports an empty change list even after a document is edited — there is no way
 * to have it report a tracked change headlessly without the Git plugin's own test fixtures, which are
 * not part of the distribution. What is pinned here is therefore (a) the enumeration call itself runs
 * against the real service and answers honestly on a clean checkout, and (b) the mapping from the
 * platform's own [Change] objects, which is exactly the shape `ChangeListManager` hands us, including
 * the base revision A2 parses. The end-to-end "dirty checkout in a live IDE" path is verified by
 * running the plugin on a real git repository, not by this suite.
 */
class WorkingTreeChangeSetProviderTest : BasePlatformTestCase() {

    fun testCleanCheckoutHasNoChangedFiles() {
        assertEmpty(WorkingTreeChangeSetProvider.changedFiles(project))
    }

    fun testChangeSetIsWrappedWithTheWorkingTreeSource() {
        val changeSet = WorkingTreeChangeSetProvider.changeSet(project)

        assertEquals(ChangeSource.WorkingTree, changeSet.source)
        // A1 stops at file level. Methods stay empty until A2 diffs the two PSI trees.
        assertEmpty(changeSet.methods)
    }

    fun testTrackedModificationCarriesTheBaseRevisionForA2() {
        val file = localFile("Base.kt", "class Changed\n")
        val path = VcsUtil.getFilePath(file)

        val changed = WorkingTreeChangeSetProvider.toChangedFile(
            project,
            Change(baseRevision("class Before\n", path), CurrentContentRevision(path), FileStatus.MODIFIED),
        )

        assertEquals(ChangedFileKind.MODIFIED, changed.kind)
        assertEquals(file, changed.file)
        assertEquals(FileStatus.MODIFIED, changed.status)
        assertEquals("class Before\n", changed.baseRevision!!.content)
        assertTrue("expected a path ending in Base.kt, got ${changed.path}", changed.path.endsWith("Base.kt"))
    }

    fun testDeletionKeepsTheBaseRevisionAndDropsTheWorkingTreeFile() {
        val file = localFile("Gone.kt", "class Gone\n")
        val path = VcsUtil.getFilePath(file)
        val change = Change(baseRevision("class Gone\n", path), null, FileStatus.DELETED)

        write { file.delete(this) }
        val changed = WorkingTreeChangeSetProvider.toChangedFile(project, change)

        assertEquals(ChangedFileKind.DELETED, changed.kind)
        assertNull(changed.file)
        assertEquals("class Gone\n", changed.baseRevision!!.content)
    }

    fun testUnversionedFileIsAddedWithNoBaseToCompareAgainst() {
        val file = localFile("BrandNew.kt", "class BrandNew\n")

        val added = WorkingTreeChangeSetProvider.toAddedFile(project, VcsUtil.getFilePath(file))!!

        assertEquals(ChangedFileKind.ADDED, added.kind)
        assertEquals(file, added.file)
        assertNull(added.baseRevision)
        assertNull(added.status)
    }

    fun testDirectoriesAreNotChangedFiles() {
        val dir = myFixture.tempDirFixture.findOrCreateDir("src/main/kotlin/packageDir")

        assertNull(WorkingTreeChangeSetProvider.toAddedFile(project, VcsUtil.getFilePath(dir)))
    }

    fun testChangedFilesAreDeduplicatedAndSortedForStableLedgerOrder() {
        val merged = WorkingTreeChangeSetProvider.merge(
            tracked = listOf(changedFile("b/Two.kt"), changedFile("a/One.kt")),
            untracked = listOf(changedFile("b/Two.kt"), changedFile("c/Three.kt")),
        )

        assertEquals(listOf("a/One.kt", "b/Two.kt", "c/Three.kt"), merged.map { it.path })
    }

    fun testKindMappingFallsBackToFileStatus() {
        assertEquals(ChangedFileKind.ADDED, ChangedFileKind.of(Change.Type.NEW, FileStatus.ADDED))
        assertEquals(ChangedFileKind.ADDED, ChangedFileKind.of(null, FileStatus.ADDED))
        assertEquals(ChangedFileKind.DELETED, ChangedFileKind.of(Change.Type.DELETED, FileStatus.DELETED))
        assertEquals(ChangedFileKind.DELETED, ChangedFileKind.of(null, FileStatus.DELETED_FROM_FS))
        assertEquals(ChangedFileKind.MOVED, ChangedFileKind.of(Change.Type.MOVED, FileStatus.MODIFIED))
        assertEquals(ChangedFileKind.MODIFIED, ChangedFileKind.of(Change.Type.MODIFICATION, FileStatus.MODIFIED))
        assertEquals(ChangedFileKind.MODIFIED, ChangedFileKind.of(null, null))
    }

    private fun baseRevision(content: String, path: FilePath): ContentRevision = BaseRevision(content, path)

    private fun changedFile(path: String) = ChangedFile(path, ChangedFileKind.MODIFIED, null, null, null)

    private fun write(action: () -> Unit) = WriteCommandAction.runWriteCommandAction(project, action)

    /**
     * A file the platform can resolve back to a [VirtualFile]. `VcsUtil` builds its [FilePath] from a
     * path string, and a `temp:///` fixture file never resolves back through it, so tests that need a
     * real `file` use the local file system.
     */
    private fun localFile(name: String, text: String): VirtualFile {
        val directory = Files.createTempDirectory("frictionless-a1").toFile()
        val file = directory.resolve(name)
        file.writeText(text)
        return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file.toPath())!!
    }
}

/** The base side of a change, holding the content A2 parses. */
private class BaseRevision(
    private val content: String,
    private val file: FilePath,
) : ContentRevision {
    override fun getContent(): String = content
    override fun getFile(): FilePath = file
    override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
}
