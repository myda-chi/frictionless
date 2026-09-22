package com.github.mydachi.frictionless.editor

import com.github.mydachi.frictionless.analysis.MethodLevelDelta
import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangeSource
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.github.mydachi.frictionless.navigation.Navigator
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/**
 * Deliverable U4 acceptance: gutter marks reach **every** open editor, not just the focused one (#60).
 *
 * Files are written to real disk under the project base path rather than added through the fixture.
 * That is load-bearing, not incidental: `Navigator.resolve` goes through `LocalFileSystem`, and a
 * `temp:///` fixture path resolves to nothing — so `paint` would return before marking anything and
 * every assertion below would pass while proving nothing. Verified: `Navigator.resolve("src/Real.java")`
 * finds a real file under the base path; `LocalFileSystem.findFileByPath("<temp path>")` is null.
 *
 * The marks themselves are document-level (highlighters and inlays), not painted pixels, so this needs
 * no display — the rendering is what `runIde` is for, not the logic.
 */
class VerdictMarkupTest : BasePlatformTestCase() {

    private val markup get() = project.service<VerdictMarkup>()

    override fun setUp() {
        super.setUp()
        // The project's services can outlive a single test method, so start from a known state rather
        // than whatever the previous test left in LedgerModel — otherwise `refresh()` marks the new
        // editor off a stale ChangeSet and the result depends on test order.
        project.service<LedgerModel>().update(LedgerState.Empty)
    }

    // --- the reported bug ------------------------------------------------------------------------

    fun testMarksLandOnEveryOpenEditorNotJustTheSelectedOne() {
        val editorA = openInEditor(realFile("src/A.java", TARGET_AT_LINE_2))
        val editorB = openInEditor(realFile("src/B.java", TARGET_AT_LINE_2))
        // B is the selected editor by now, which is what the old code collapsed everything down to.
        publish(method("src/A.java", 2), method("src/B.java", 2))

        markup.refresh()

        assertEquals("A must be marked even though B has focus", listOf(1), markedLines(editorA))
        assertEquals("B must be marked", listOf(1), markedLines(editorB))
    }

    fun testEveryOpenEditorIsCollectedNotJustTheSelectedOne() {
        // Guards the selection itself, so a future collapse back to selectedTextEditor fails here too.
        openInEditor(realFile("src/A.java", TARGET_AT_LINE_2))
        openInEditor(realFile("src/B.java", TARGET_AT_LINE_2))

        assertEquals(2, markup.openEditors().size)
    }

    // --- the other half of the issue: files opened *after* the verdict ---------------------------

    fun testAFileOpenedAfterTheVerdictLandsIsMarked() {
        publish(method("src/A.java", 2))

        val editorA = openInEditor(realFile("src/A.java", TARGET_AT_LINE_2))

        assertEquals(listOf(1), markedLines(editorA))
    }

    fun testAFileOpenedBeforeAnyVerdictIsMarkedByRefresh() {
        val editorA = openInEditor(realFile("src/A.java", TARGET_AT_LINE_2))

        publish(method("src/A.java", 2))
        markup.refresh()

        assertEquals(listOf(1), markedLines(editorA))
    }

    // --- drawing is idempotent and confined to the right file -----------------------------------

    fun testMarksAreNotDuplicatedWhenRefreshRunsTwice() {
        val editorA = openInEditor(realFile("src/A.java", TARGET_AT_LINE_2))
        publish(method("src/A.java", 2))

        markup.refresh()
        markup.refresh()

        assertEquals(listOf(1), markedLines(editorA))
    }

    fun testAMethodForAFileThatIsNotOpenMarksNothing() {
        val editorA = openInEditor(realFile("src/A.java", TARGET_AT_LINE_2))
        realFile("src/B.java", TARGET_AT_LINE_2)

        publish(method("src/B.java", 2))
        markup.refresh()

        assertEmpty(markedLines(editorA))
    }

    fun testNothingIsMarkedWithoutAReadyState() {
        val editorA = openInEditor(realFile("src/A.java", TARGET_AT_LINE_2))

        markup.refresh() // LedgerState is Empty

        assertEmpty(markedLines(editorA))
    }

    // --- the end-of-line badge ------------------------------------------------------------------

    fun testOnlyUnverifiedMethodsGetTheEndOfLineBadge() {
        val editorA = openInEditor(realFile("src/A.java", TARGET_AT_LINE_2))

        publish(method("src/A.java", 2, Bucket.UNVERIFIED))
        markup.refresh()
        assertFalse(
            "unverified code should say so on the line",
            editorA.inlayModel.getAfterLineEndElementsForLogicalLine(1).isEmpty(),
        )

        publish(method("src/A.java", 2, Bucket.PROVEN))
        markup.refresh()
        assertTrue(
            "a proven method should carry no badge",
            editorA.inlayModel.getAfterLineEndElementsForLogicalLine(1).isEmpty(),
        )
    }

    // --- closing an editor must not poison a later refresh --------------------------------------

    fun testClosingAnEditorDoesNotBreakALaterRefresh() {
        val editorA = openInEditor(realFile("src/A.java", TARGET_AT_LINE_2))
        val fileB = realFile("src/B.java", TARGET_AT_LINE_2)
        openInEditor(fileB)
        publish(method("src/A.java", 2), method("src/B.java", 2))
        markup.refresh()

        FileEditorManager.getInstance(project).closeFile(fileB)
        markup.refresh() // must not touch the disposed editor

        assertEquals(listOf(1), markedLines(editorA))
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun publish(vararg methods: ChangedMethod) {
        project.service<LedgerModel>().update(
            LedgerState.Ready(ChangeSet(ChangeSource.WorkingTree, methods.toList())),
        )
    }

    /** A verdict for [path] at 1-based [line]; [id] is unique per path so several can coexist. */
    private fun method(path: String, line: Int, bucket: Bucket = Bucket.UNVERIFIED) = ChangedMethod(
        id = "$path#$line",
        displayName = "Target.target()",
        filePath = path,
        line = line,
        verdict = MethodLevelDelta.emptyVerdict.copy(bucket = bucket),
    )

    /** The 0-based lines carrying a gutter icon, in document order. */
    private fun markedLines(editor: Editor): List<Int> =
        editor.markupModel.allHighlighters
            .filter { it.gutterIconRenderer != null }
            .map { editor.document.getLineNumber(it.startOffset) }
            .sorted()

    /** A real file on disk, so `Navigator.resolve` can find it. Returns its [VirtualFile]. */
    private fun realFile(relative: String, text: String): VirtualFile {
        val file = File(project.basePath, relative)
        file.parentFile.mkdirs()
        file.writeText(text)
        return requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)) {
            "could not get a VirtualFile for $file"
        }
    }

    private fun openInEditor(file: VirtualFile): Editor {
        Navigator.open(project, file, 1)
        return FileEditorManager.getInstance(project).getEditors(file)
            .filterIsInstance<TextEditor>()
            .first()
            .editor
    }

    private companion object {
        const val TARGET_AT_LINE_2 = "class Holder {\n    void target() {}\n}\n"
    }
}
