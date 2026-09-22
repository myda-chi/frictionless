package com.github.mydachi.frictionless.analysis

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch

/**
 * Call-graph primitives shared by A3 (does anything reach the change?) and A6 (what is it live
 * under?). One implementation, because if the two disagreed about a call-graph edge the detail panel
 * would contradict the ledger.
 *
 * What counts as *the same method* — the super-method expansion that makes the search dispatch-aware,
 * and the light methods that make a Java caller of a Kotlin function findable — belongs to
 * [Methods.searchTargets] and is deliberately not restated here. This file only composes that with the
 * search itself.
 */
internal object CallGraph {

    /** Everything that can dispatch to [element], in either language. */
    fun callersOf(project: Project, element: PsiElement): List<PsiReference> {
        val scope = GlobalSearchScope.projectScope(project)
        return Methods.searchTargets(element)
            .flatMap { ReferencesSearch.search(it, scope, false).findAll() }
            .distinct()
    }

    /** A displayable path for [element] — its file on disk, or its name when the file is not local. */
    fun pathOf(element: PsiElement): String =
        element.containingFile.virtualFile?.path ?: element.containingFile.name
}
