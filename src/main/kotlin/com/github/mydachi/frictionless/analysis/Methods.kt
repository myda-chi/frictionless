package com.github.mydachi.frictionless.analysis

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

/**
 * One vocabulary for "a method", whether it is a Java `PsiMethod` or a Kotlin `KtNamedFunction`.
 *
 * A2 and A3 were written against `PsiMethod`, which is Java PSI: a Kotlin file parses into
 * `KtNamedFunction` nodes and contributed **zero** changed methods (issue #56). Since the plugin
 * itself is Kotlin, we could not even run it on ourselves.
 *
 * Everything here is deliberately resolution-free — names and source text, no type inference and no
 * light-class round trip — except [searchTargets], where the light methods are exactly what makes
 * `ReferencesSearch` find Java callers of a Kotlin function.
 */
object Methods {

    /** Every method or function declared in [psiFile], in either language. */
    fun declarationsIn(psiFile: PsiFile): List<PsiElement> = buildList {
        addAll(PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java))
        addAll(PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java))
    }

    fun isMethod(element: PsiElement): Boolean = element is PsiMethod || element is KtNamedFunction

    fun nameOf(element: PsiElement): String = when (element) {
        is PsiMethod -> element.name
        is KtNamedFunction -> element.name.orEmpty()
        else -> ""
    }

    /**
     * `Owner.name(paramTypes)` — the identity two revisions of a file are matched on.
     *
     * The owner is part of the key on purpose (issue #58): without it, two same-named methods in one
     * file collide and one silently disappears from the ledger. Parameter *types*, not names, because
     * renaming a parameter is not a signature change and overloading is.
     *
     * Kotlin types are compared as written rather than resolved. Both sides of the comparison are the
     * same file at two revisions, so the spelling is consistent — and resolving types against an
     * in-memory base-revision file is not reliable anyway.
     */
    fun signature(element: PsiElement): String {
        val owner = ownerName(element)?.plus(".").orEmpty()
        return "$owner${nameOf(element)}(${parameterTypes(element).joinToString(", ")})"
    }

    /** `Class.method()`, or `method()` for a Kotlin top-level function. */
    fun displayName(element: PsiElement): String {
        val owner = ownerName(element)
        return if (owner.isNullOrEmpty()) "${nameOf(element)}()" else "$owner.${nameOf(element)}()"
    }

    fun ownerName(element: PsiElement): String? = when (element) {
        is PsiMethod -> PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.name
        is KtNamedFunction -> element.containingClassOrObject?.name
        else -> null
    }

    /** Fully qualified owner, for the `TestRef` the JUnit runner needs. */
    fun qualifiedOwner(element: PsiElement): String? = when (element) {
        is PsiMethod -> PsiTreeUtil.getParentOfType(element, PsiClass::class.java)?.qualifiedName
        is KtNamedFunction -> element.containingClassOrObject?.fqName?.asString()
        else -> null
    }

    fun parameterTypes(element: PsiElement): List<String> = when (element) {
        is PsiMethod -> element.parameterList.parameters.map { it.type.canonicalText }
        is KtNamedFunction -> element.valueParameters.map { it.typeReference?.text ?: "?" }
        else -> emptyList()
    }

    /**
     * What `ReferencesSearch` should be pointed at to find everything that can dispatch to [element].
     *
     * A Kotlin function is also searched through its light methods, which is how a Java caller of a
     * Kotlin function is found at all. Java super methods are included because a call through a
     * supertype can land here; overrides are deliberately excluded — see the dispatch note on
     * [ImpactGraph].
     */
    fun searchTargets(element: PsiElement): List<PsiElement> = when (element) {
        is PsiMethod -> buildList {
            add(element)
            addAll(element.findSuperMethods())
        }
        is KtNamedFunction -> buildList {
            add(element)
            addAll(runCatching { element.toLightMethods() }.getOrDefault(emptyList()))
        }
        else -> listOf(element)
    }

    /** The method or function [element] sits inside, in either language. */
    fun enclosing(element: PsiElement): PsiElement? =
        PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            ?: PsiTreeUtil.getParentOfType(element, KtNamedFunction::class.java)

    /**
     * A runnable test, as opposed to a helper that merely lives under the test root.
     *
     * Java annotations are matched by qualified name. Kotlin annotation entries are matched on their
     * short name: resolving them needs the whole module set up, and `@Test` is not ambiguous in
     * practice.
     */
    fun isTestEntryPoint(element: PsiElement): Boolean = when (element) {
        is PsiMethod -> isJavaTest(element)
        is KtNamedFunction -> element.annotationEntries.any { entry ->
            entry.shortName?.asString() in TEST_ANNOTATION_SHORT_NAMES
        }
        else -> false
    }

    private fun isJavaTest(method: PsiMethod): Boolean {
        if (method.annotations.any { it.qualifiedName in TEST_ANNOTATIONS }) return true
        // JUnit 3 had no annotation: subclass TestCase and prefix the name.
        val owner = method.containingClass ?: return false
        return owner.supers.any { it.qualifiedName == JUNIT3_TEST_CASE } && method.name.startsWith("test")
    }

    private const val JUNIT3_TEST_CASE = "junit.framework.TestCase"

    private val TEST_ANNOTATIONS = setOf(
        "org.junit.Test",
        "org.junit.jupiter.api.Test",
        "org.junit.jupiter.api.RepeatedTest",
        "org.junit.jupiter.api.TestFactory",
        "org.junit.jupiter.params.ParameterizedTest",
        "kotlin.test.Test",
    )

    private val TEST_ANNOTATION_SHORT_NAMES =
        setOf("Test", "RepeatedTest", "TestFactory", "ParameterizedTest")
}
