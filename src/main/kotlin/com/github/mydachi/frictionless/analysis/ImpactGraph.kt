package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.CallSite
import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangeSource
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.TestRef
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager

/**
 * Deliverable A3: the impact graph — which tests genuinely reach each changed method.
 *
 * **This is the product.** An empty `reachingTests` is a valid, meaningful answer: it means nothing in
 * the repo executes that method, which is exactly what the ledger exists to show.
 *
 * Breadth-first up the call graph from each changed method, via `ReferencesSearch`, stopping at the
 * test source root. "Up" is the direction that matters: a changed method's *callers* are what decide
 * whether anything exercises it.
 *
 * Three semantics worth stating, because they decide whether the demo tells the truth:
 *
 *  1. **A test is a method in a test source root *and* a test entry point.** The root is the gate the
 *     specification names; the annotation (or JUnit 3 naming) is what separates a real test from a
 *     *helper* that merely happens to live under `src/test`. Without the second half, a helper would be
 *     reported as a test the runner cannot run.
 *  2. **Traversal passes *through* helpers and stops *at* tests.** A test that reaches the changed
 *     method via a private helper is a normal shape, so a non-test method under the test root is
 *     walked through rather than reported. Nothing calls a test, so stopping there loses nothing.
 *  3. **Dispatch-aware callers.** A call through a supertype can land on the changed method, so the
 *     call sites of the method *and of the super methods it overrides* count as touching it. Overrides
 *     are deliberately **not** included: a call to an override does not execute the base method, and
 *     counting it would report "covered" for code nothing runs — the one error this tool must never
 *     make.
 *
 * "Method" here means a Java `PsiMethod` or a Kotlin `KtNamedFunction`, via [Methods] — the plugin is
 * written in Kotlin, so a Java-only analyser could not run on itself.
 */
object ImpactGraph {

    /**
     * The tests that reach a method, plus its direct call sites.
     *
     * `UNVERIFIED` stays the bucket: assigning a real bucket is A4's job. The counts, however, are A3's
     * answer and are filled in here rather than left at zero, so A4 has the numbers it needs.
     */
    /**
     * Runs in **smart mode**. `ReferencesSearch` needs the indexes: during indexing it either throws
     * or quietly finds nothing, and "quietly finds nothing" is indistinguishable from the honest
     * answer that no test reaches the method — the ledger would report a whole project as unverified
     * and be believed. Waiting for indexes is the difference between an empty answer and a wrong one.
     */
    fun populate(project: Project, methods: List<ChangedMethod>): List<ChangedMethod> {
        val enriched = DumbService.getInstance(project).runReadActionInSmartMode<List<ChangedMethod>> {
            methods.map { enrich(project, it) }
        }
        val withTests = enriched.count { it.reachingTests.isNotEmpty() }
        thisLogger().info(
            "Impact graph: ${enriched.size} changed method(s), $withTests with at least one reaching test",
        )
        if (enriched.isNotEmpty() && withTests == 0) {
            // Possible and meaningful in a repo with no tests, but in one that has them it means the
            // search found nothing — worth being loud about rather than rendering a confident ledger.
            thisLogger().warn(
                "No changed method has a reaching test. If this project does have tests, the call-graph " +
                    "search found none: check that the test roots are marked as test sources.",
            )
        }
        return enriched
    }

    /** The whole A1 → A2 → A3 pipeline in the shape the ledger consumes. */
    fun changeSet(project: Project, source: ChangeSource, changedFiles: List<ChangedFile>): ChangeSet =
        ChangeSet(source, populate(project, MethodLevelDelta.changedMethods(project, changedFiles)))

    private fun enrich(project: Project, changed: ChangedMethod): ChangedMethod {
        // A removed method has no pointer: nothing in the working tree left to analyse (A2 does not
        // point at the base tree, which is an in-memory PSI file). It keeps its empty answer, which is
        // honest — the method is gone, so no test can reach it any more.
        val root = changed.pointer?.element?.takeIf(Methods::isMethod) ?: return changed

        val callSites = callSitesOf(project, root)
        val tests = reachingTests(project, root)

        return changed.copy(
            callSites = callSites,
            reachingTests = tests,
            verdict = changed.verdict.copy(
                counts = changed.verdict.counts.copy(
                    reachingTests = tests.size,
                    callSites = callSites.size,
                ),
            ),
        )
    }

    /** Who calls this method directly — the blast radius a developer would want to see. */
    private fun callSitesOf(project: Project, method: PsiElement): List<CallSite> {
        val sites = LinkedHashMap<String, CallSite>()
        for (reference in CallGraph.callersOf(project, method)) {
            val element = reference.element
            val caller = Methods.enclosing(element)
            val line = MethodLevelDelta.lineOf(element.containingFile, element)
            val path = CallGraph.pathOf(element)
            val key = "$path:$line"
            sites.getOrPut(key) {
                CallSite(
                    displayName = caller?.let { MethodLevelDelta.displayName(it) } ?: element.text,
                    filePath = path,
                    line = line,
                    pointer = SmartPointerManager.getInstance(project)
                        .createSmartPsiElementPointer(element),
                )
            }
        }
        return sites.values.toList()
    }

    /** The tests that reach this method, however many hops away they are. */
    private fun reachingTests(project: Project, root: PsiElement): List<TestRef> {
        val found = LinkedHashMap<String, TestRef>()
        val visited = HashSet<PsiElement>()
        val queue = ArrayDeque<PsiElement>()
        queue += root

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            // The visited set is also the termination guard: a cycle in the call graph (A calls B calls
            // A) would otherwise loop forever.
            if (!visited.add(current)) continue

            for (reference in CallGraph.callersOf(project, current)) {
                val caller = Methods.enclosing(reference.element) ?: continue
                if (isReachingTest(project, caller)) {
                    val test = testRef(project, caller)
                    found.putIfAbsent(test.displayName, test)
                } else {
                    queue += caller
                }
            }
        }
        return found.values.toList()
    }

    /**
     * A runnable test method in a test source root — the root is the gate the specification names, the
     * entry-point check is what separates a real test from a helper living under `src/test`.
     */
    private fun isReachingTest(project: Project, method: PsiElement): Boolean {
        val file = method.containingFile?.virtualFile ?: return false
        val inTestRoot = ProjectFileIndex.getInstance(project)
            .getContainingSourceRootType(file)?.isForTests == true
        return inTestRoot && Methods.isTestEntryPoint(method)
    }

    private fun testRef(project: Project, method: PsiElement): TestRef = TestRef(
        className = Methods.qualifiedOwner(method) ?: method.containingFile.name,
        methodName = Methods.nameOf(method),
        pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(method),
    )
}
