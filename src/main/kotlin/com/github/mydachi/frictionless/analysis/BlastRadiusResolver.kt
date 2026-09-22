package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.CallSite
import com.github.mydachi.frictionless.model.ChangedMethod
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * Deliverable A6: resolve a changed method's blast radius for the detail panel (spec §3.3).
 *
 * Call sites arrive already resolved from A3 rather than being recomputed here — one owner for "who
 * calls this", so the two features cannot drift. This adds the two directions A3 does not carry: what
 * the method calls, and the live entry points it is reachable under.
 *
 * **Entry points are heuristic.** There is no registry of "live" code, so they are recognised by
 * shape: `main`, a platform action handler, an HTTP handler by annotation, a test. That is a real
 * limitation, not a hidden one — a codebase entered some other way (a message listener, a scheduled
 * task) reports no endpoint, which reads as "nothing enters here" rather than "I did not check".
 */
object BlastRadiusResolver {

    /**
     * Above this many methods visited while climbing, the walk gives up on that method.
     *
     * The climb is transitive, so without a bound a method called from a widely-used utility would
     * walk most of the repository. On hitting the cap the endpoints found so far are returned: a
     * partial honest answer beats a hung EDT.
     */
    private const val MAX_TRAVERSAL_NODES = 2_000

    fun resolve(project: Project, changed: ChangedMethod): BlastRadius =
        ReadAction.compute<BlastRadius, RuntimeException> { blastRadius(project, changed) }

    /** Keyed by [ChangedMethod.id] so the detail panel can look a method up directly. */
    fun resolve(project: Project, methods: List<ChangedMethod>): Map<String, BlastRadius> =
        ReadAction.compute<Map<String, BlastRadius>, RuntimeException> {
            methods.associate { it.id to blastRadius(project, it) }
        }

    private fun blastRadius(project: Project, changed: ChangedMethod): BlastRadius {
        val callSites = changed.callSites
        // A method with no pointer is one A2 removed: nothing in the working tree left to read. The
        // honest answer is empty, and `Methods.isMethod` keeps this true for Kotlin functions too.
        val method = changed.pointer?.element?.takeIf(Methods::isMethod)
            ?: return BlastRadius(callSites, emptyList(), emptyList())

        return BlastRadius(
            callSites = callSites,
            callees = calleesOf(project, method),
            endpoints = endpointsOf(project, method),
        )
    }

    /**
     * What the method calls: every resolved method invocation and constructor call in its body.
     *
     * Sorted by location so the panel's order is stable rather than whatever PSI traversal happened to
     * emit. Unresolvable calls (a library not on the project's classpath) are simply absent — the panel
     * shows what can be navigated to.
     *
     * **Java only.** A Kotlin function's calls are `KtCallExpression` nodes needing their own resolution
     * pass, so a Kotlin method reports no callees today rather than a wrong set. The endpoints walk below
     * *does* cross languages, because [Methods.searchTargets] resolves through light methods.
     */
    internal fun calleesOf(project: Project, element: PsiElement): List<CallSite> {
        val body = (element as? PsiMethod)?.body ?: return emptyList()
        val manager = SmartPointerManager.getInstance(project)

        return buildList {
            PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java)
                .forEach { add(it.resolveMethod()) }
            PsiTreeUtil.findChildrenOfType(body, PsiNewExpression::class.java)
                .forEach { add(it.resolveConstructor()) }
        }
            .filterNotNull()
            .distinctBy { "${CallGraph.pathOf(it)}#${Methods.signature(it)}" }
            .sortedWith(compareBy({ CallGraph.pathOf(it) }, { MethodLevelDelta.lineOf(it.containingFile, it) }))
            .map {
                callSiteFor(
                    name = Methods.displayName(it),
                    path = CallGraph.pathOf(it),
                    line = MethodLevelDelta.lineOf(it.containingFile, it),
                    pointer = manager.createSmartPsiElementPointer(it),
                )
            }
    }

    /**
     * The live entry points above [element], however many hops up.
     *
     * Breadth-first, and **an entry point is a root: it is recorded and the climb stops there**.
     * Nothing meaningful calls `main`, and it bounds the walk on a method reachable from everywhere.
     * The visited set is also the termination guard for a cycle in the call graph.
     */
    internal fun endpointsOf(project: Project, element: PsiElement): List<Endpoint> {
        val found = LinkedHashMap<String, Endpoint>()
        val visited = HashSet<PsiElement>()
        val queue = ArrayDeque<PsiElement>()
        val manager = SmartPointerManager.getInstance(project)
        queue += element
        var budget = MAX_TRAVERSAL_NODES

        while (queue.isNotEmpty() && budget-- > 0) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue

            val kind = endpointKind(current)
            if (kind != null) {
                val name = Methods.displayName(current)
                found.putIfAbsent("$kind:$name", Endpoint(
                    kind = kind,
                    site = callSiteFor(
                        name = name,
                        path = CallGraph.pathOf(current),
                        line = MethodLevelDelta.lineOf(current.containingFile, current),
                        pointer = manager.createSmartPsiElementPointer(current),
                    ),
                ))
                continue
            }

            CallGraph.callersOf(project, current)
                .mapNotNull { Methods.enclosing(it.element) }
                .filter { it !in visited }
                .forEach { queue += it }
        }
        return found.values.toList()
    }

    /**
     * `main`, an action handler and an HTTP handler are Java shapes, so they are recognised on
     * `PsiMethod`. A test is recognised in either language by [Methods.isTestEntryPoint].
     */
    private fun endpointKind(element: PsiElement): EndpointKind? {
        val javaShape = (element as? PsiMethod)?.let { method ->
            when {
                isMain(method) -> EndpointKind.MAIN
                isActionHandler(method) -> EndpointKind.ACTION
                isHttpHandler(method) -> EndpointKind.HTTP
                else -> null
            }
        }
        return javaShape ?: EndpointKind.TEST.takeIf { Methods.isTestEntryPoint(element) }
    }

    /** `public static void main(String[])`. The array component is checked, so a missing SDK is fine. */
    private fun isMain(method: PsiMethod): Boolean {
        if (method.name != MAIN) return false
        if (!method.hasModifierProperty(PsiModifier.STATIC)) return false
        if (method.parameterList.parametersCount != 1) return false
        val array = method.parameterList.parameters[0].type as? PsiArrayType ?: return false
        return array.componentType.canonicalText.endsWith("String")
    }

    /** A platform action handler: `actionPerformed`, declared in an `AnAction`, or overriding one's. */
    private fun isActionHandler(method: PsiMethod): Boolean {
        if (method.name != ACTION_PERFORMED) return false
        val owner = method.containingClass ?: return false
        return method.findSuperMethods().any { it.containingClass?.qualifiedName == AN_ACTION } ||
            owner.supers.any { it.qualifiedName == AN_ACTION }
    }

    /**
     * An HTTP handler, by annotation.
     *
     * A class-level mapping (Spring's `@RequestMapping`, JAX-RS's `@Path`) applies to the class's
     * public methods, which is how those frameworks treat it — so the owner is consulted too.
     */
    private fun isHttpHandler(method: PsiMethod): Boolean {
        if (method.annotations.any { it.qualifiedName in HTTP_ANNOTATIONS }) return true
        val owner: PsiClass = method.containingClass ?: return false
        val mappedAtClassLevel = owner.annotations.any { it.qualifiedName in HTTP_ANNOTATIONS }
        return mappedAtClassLevel && method.hasModifierProperty(PsiModifier.PUBLIC)
    }

    private const val MAIN = "main"
    private const val ACTION_PERFORMED = "actionPerformed"
    private const val AN_ACTION = "com.intellij.openapi.actionSystem.AnAction"

    private val HTTP_ANNOTATIONS = setOf(
        "org.springframework.web.bind.annotation.RequestMapping",
        "org.springframework.web.bind.annotation.GetMapping",
        "org.springframework.web.bind.annotation.PostMapping",
        "org.springframework.web.bind.annotation.PutMapping",
        "org.springframework.web.bind.annotation.DeleteMapping",
        "org.springframework.web.bind.annotation.PatchMapping",
        "javax.ws.rs.GET",
        "javax.ws.rs.POST",
        "javax.ws.rs.PUT",
        "javax.ws.rs.DELETE",
        "javax.ws.rs.Path",
        "jakarta.ws.rs.GET",
        "jakarta.ws.rs.POST",
        "jakarta.ws.rs.PUT",
        "jakarta.ws.rs.DELETE",
        "jakarta.ws.rs.Path",
    )
}
