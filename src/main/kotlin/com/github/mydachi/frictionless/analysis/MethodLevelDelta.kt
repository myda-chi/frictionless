package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangeSource
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.Verdict
import com.github.mydachi.frictionless.model.VerdictCounts
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * Deliverable A2: the base revision and the working tree, diffed at method level.
 *
 * Both sides are parsed as [PsiFile]s — the base from the content A1 carried out of the [com.intellij.openapi.vcs.changes.Change],
 * the head from the real file — and their methods are matched by signature. Added, removed and
 * modified are all reported; an unchanged method is not.
 *
 * **Signature, not position.** Matching on name plus parameter types is what makes `move this method
 * down the file` a non-event and `add an overload` two distinct methods. Line numbers ride along for
 * display but are never the identity.
 *
 * **Every method is handed over with an empty verdict.** `Verdict(UNVERIFIED, VerdictCounts())` is the
 * placeholder the S3 note means by "real methods with empty verdicts": whether anything reaches a
 * method is A3's answer and which bucket it lands in is A4's, and neither has run yet. A4 overwrites
 * this before anything renders — see the note on [emptyVerdict].
 */
object MethodLevelDelta {

    /**
     * The verdict a method carries before A3 and A4 have looked at it.
     *
     * `UNVERIFIED` is the only representable "not yet decided" state in the frozen contract, since
     * [Bucket] has no PENDING member and `Contracts.kt` is frozen. It is a placeholder, not a claim:
     * today it renders as "no test reaches this code", which is A4's conclusion to reach, not A2's to
     * assert. If anything renders a method before A4 runs, the honest fix is a PENDING bucket added to
     * the frozen contract by agreement — **not** a silent defaulting here.
     */
    val emptyVerdict: Verdict = Verdict(Bucket.UNVERIFIED, VerdictCounts())

    /**
     * The methods that differ between [changedFile]'s base revision and its working-tree file.
     *
     * Runs its own read action: PSI access is a read action everywhere in the platform, and pushing
     * that onto every caller is how you get a `ReadAccessException` on stage.
     */
    fun delta(project: Project, changedFile: ChangedFile): List<MethodDelta> =
        ReadAction.compute<List<MethodDelta>, RuntimeException> {
            compare(project, changedFile)
        }

    fun changedMethods(project: Project, changedFile: ChangedFile): List<ChangedMethod> =
        delta(project, changedFile).map { it.method }

    fun changedMethods(project: Project, changedFiles: List<ChangedFile>): List<ChangedMethod> =
        changedFiles.flatMap { changedMethods(project, it) }

    /** The whole A1 → A2 pipeline, in the shape every other track consumes. */
    fun changeSet(project: Project, source: ChangeSource, changedFiles: List<ChangedFile>): ChangeSet =
        ChangeSet(source, changedMethods(project, changedFiles))

    private fun compare(project: Project, changedFile: ChangedFile): List<MethodDelta> {
        val headFile = changedFile.file?.let { PsiManager.getInstance(project).findFile(it) }
        // A revision that cannot produce its content leaves the base empty, which reports the file's
        // methods as added — the same shape as a new file. Nothing is invented to fill the gap.
        val baseFile = changedFile.baseRevision?.content?.let { content ->
            psiFileFor(project, fileNameOf(changedFile), content, changedFile.file?.fileType)
        }

        val head = headFile?.let { methodsOf(it) }.orEmpty()
        val base = baseFile?.let { methodsOf(it) }.orEmpty()

        val added = head.keys - base.keys
        val removed = base.keys - head.keys
        val modified = (head.keys intersect base.keys).filter { key ->
            normalize(head.getValue(key).text) != normalize(base.getValue(key).text)
        }

        return buildList {
            added.forEach { key -> add(emit(project, MethodChange.ADDED, head.getValue(key), headFile!!, changedFile.path)) }
            removed.forEach { key -> add(emit(project, MethodChange.REMOVED, base.getValue(key), baseFile!!, changedFile.path)) }
            modified.forEach { key -> add(emit(project, MethodChange.MODIFIED, head.getValue(key), headFile!!, changedFile.path)) }
        }
    }

    private fun emit(
        project: Project,
        change: MethodChange,
        method: PsiMethod,
        psiFile: PsiFile,
        path: String,
    ): MethodDelta {
        val id = "${change.name.lowercase()}:$path#${signature(method)}"
        val method_: ChangedMethod = ChangedMethod(
            id = id,
            displayName = displayName(method),
            filePath = path,
            line = lineOf(psiFile, method),
            verdict = emptyVerdict,
            // Call sites and reaching tests are A3/A6. A removed method has nothing in the working
            // tree to navigate to, so it gets no pointer rather than a dangling one.
            pointer = if (change == MethodChange.REMOVED) {
                null
            } else {
                SmartPointerManager.getInstance(project).createSmartPsiElementPointer(method)
            },
        )
        return MethodDelta(change, method_)
    }

    /**
     * `Class.method(paramTypes)`. Parameter types, not names: renaming a parameter is not a change to
     * the method's signature, and overloading is.
     */
    internal fun signature(method: PsiMethod): String =
        "${method.name}(${method.parameterList.parameters.joinToString(", ") { it.type.canonicalText }})"

    internal fun methodsOf(psiFile: PsiFile): Map<String, PsiMethod> =
        PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java).associateBy { signature(it) }

    internal fun displayName(method: PsiMethod): String {
        val owner = PsiTreeUtil.getParentOfType(method, PsiClass::class.java)?.name
        return if (owner.isNullOrEmpty()) "${method.name}()" else "$owner.${method.name}()"
    }

    /**
     * 1-based line of [element], counted off the file's own text.
     *
     * Counting newlines rather than asking a `Document` is deliberate: the base side is an in-memory
     * PSI file with no committed document, so a `Document`-based line number would work for one side
     * and be null for the other.
     */
    internal fun lineOf(psiFile: PsiFile, element: PsiElement): Int =
        psiFile.text.take(element.textOffset).count { it == '\n' } + 1

    /** Formatting is not a behaviour change; only the text's content is compared. */
    private fun normalize(text: String) = text.replace(WHITESPACE, " ").trim()

    private val WHITESPACE = Regex("\\s+")

    /**
     * A deleted file has no [com.intellij.openapi.vfs.VirtualFile] to read a name or type from, so both
     * fall back to the path and the file-type registry.
     */
    private fun fileNameOf(changedFile: ChangedFile) =
        changedFile.file?.name ?: changedFile.path.substringAfterLast('/')

    /**
     * `null` when the platform cannot parse the text — a binary type, or a language with no parser
     * installed. That is an honest empty result, not an error: no methods found, nothing reported.
     */
    private fun psiFileFor(project: Project, name: String, text: String, fileType: FileType?): PsiFile? =
        runCatching {
            val type = fileType ?: FileTypeManager.getInstance().getFileTypeByFileName(name)
            PsiFileFactory.getInstance(project).createFileFromText(name, type, text)
        }.getOrNull()
}
