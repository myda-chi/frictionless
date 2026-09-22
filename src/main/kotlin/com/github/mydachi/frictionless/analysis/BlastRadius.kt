package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.CallSite
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/**
 * Deliverable A6: the blast radius of a changed method, for the detail panel (spec §3.3).
 *
 * A developer looking at a changed method wants to know what a mistake in it would cost. That is two
 * directions plus the way in:
 *
 *  - [callSites] — who calls it. Owned by A3 and passed through here, not recomputed.
 *  - [callees] — what it calls. What this method depends on.
 *  - [endpoints] — the live entry points it is reachable under, however many hops up.
 *
 * Deliberately **not** part of `ChangedMethod`. That contract is frozen and shared by four tracks, and
 * blast radius is a detail-panel concern that only the caller of [BlastRadiusResolver] needs. Keeping
 * it beside the contract rather than inside it means no cross-track agreement is required to add it.
 */
data class BlastRadius(
    val callSites: List<CallSite>,
    val callees: List<CallSite>,
    val endpoints: List<Endpoint>,
) {
    val isEmpty: Boolean get() = callSites.isEmpty() && callees.isEmpty() && endpoints.isEmpty()
}

/** An entry point the world can start from, and which the changed method is live under. */
data class Endpoint(
    val kind: EndpointKind,
    val site: CallSite,
)

/**
 * How an entry point is entered. Tests are included because a test is genuinely a live entry point —
 * it is what makes the code run — and it is the one the tool can act on.
 */
enum class EndpointKind(val label: String) {
    /** `public static void main(String[])`. */
    MAIN("main"),

    /** A JetBrains platform action handler (`AnAction.actionPerformed`). */
    ACTION("action"),

    /** An HTTP handler, by annotation (Spring, JAX-RS). */
    HTTP("http"),

    /** A JUnit/Kotlin test method. */
    TEST("test"),
}

/** A method somewhere else, named and navigable. Shared by [Endpoint] and [BlastRadius.callees]. */
internal fun callSiteFor(
    name: String,
    path: String,
    line: Int,
    pointer: SmartPsiElementPointer<PsiElement>?,
): CallSite = CallSite(displayName = name, filePath = path, line = line, pointer = pointer)
