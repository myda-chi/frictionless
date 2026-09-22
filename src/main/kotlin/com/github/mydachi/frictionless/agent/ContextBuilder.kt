package com.github.mydachi.frictionless.agent

import com.github.mydachi.frictionless.analysis.Methods
import com.github.mydachi.frictionless.model.ChangedMethod
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * Deliverable G2, against a real project: the repository's own context for the prompt.
 *
 * Three things the model cannot guess — the method as written, the arguments its real callers pass,
 * and how this team writes tests. Everything is read under a read action and truncated, because a
 * prompt that carries an entire file is mostly noise.
 */
class ContextBuilder(private val project: Project) {

    fun build(method: ChangedMethod): TestContext = ReadAction.compute<TestContext, RuntimeException> {
        TestContext(
            method = method,
            methodSource = sourceOf(method),
            callSiteSnippets = callSiteSnippets(method),
            conventionExamples = conventionExamples(),
            testPackage = testPackageFor(method),
        )
    }

    private fun sourceOf(method: ChangedMethod): String =
        method.pointer?.element?.text?.take(MAX_METHOD_CHARS) ?: method.displayName

    /** The line each caller calls it on, which is where plausible argument values come from. */
    private fun callSiteSnippets(method: ChangedMethod): List<String> =
        method.callSites.take(MAX_CALL_SITES).mapNotNull { site ->
            site.pointer?.element?.parent?.text?.take(MAX_SNIPPET_CHARS)
        }

    /**
     * Two existing tests, so the model matches the framework, naming and assertion style already in
     * use rather than inventing its own. The largest files are skipped — a 900-line integration test
     * is a bad example and an expensive one.
     */
    private fun conventionExamples(): List<String> {
        val scope = GlobalSearchScope.projectScope(project)
        val names = FilenameIndex.getAllFilenames(project).filter { it.endsWith("Test.java") || it.endsWith("Test.kt") }
        return names.asSequence()
            .flatMap { FilenameIndex.getVirtualFilesByName(it, scope).asSequence() }
            .filter { it.length in 1..MAX_EXAMPLE_BYTES }
            .mapNotNull { PsiManager.getInstance(project).findFile(it) }
            .take(MAX_EXAMPLES)
            .map { it.text.take(MAX_EXAMPLE_CHARS) }
            .toList()
    }

    /** The package the method lives in — the generated test belongs beside it, not at the root. */
    private fun testPackageFor(method: ChangedMethod): String {
        val file: PsiFile? = method.pointer?.element?.containingFile
        (file as? PsiJavaFile)?.packageName?.takeIf { it.isNotEmpty() }?.let { return it }
        return file?.text
            ?.lineSequence()
            ?.firstOrNull { it.trimStart().startsWith("package ") }
            ?.trim()
            ?.removePrefix("package ")
            ?.removeSuffix(";")
            ?.trim()
            .orEmpty()
    }

    private companion object {
        const val MAX_METHOD_CHARS = 4000
        const val MAX_CALL_SITES = 3
        const val MAX_SNIPPET_CHARS = 400
        const val MAX_EXAMPLES = 2
        const val MAX_EXAMPLE_CHARS = 3000
        const val MAX_EXAMPLE_BYTES = 20_000
    }
}
