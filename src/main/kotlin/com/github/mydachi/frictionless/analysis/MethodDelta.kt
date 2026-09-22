package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.ChangedMethod

/** How a method differs between the base revision and the working tree. */
enum class MethodChange {
    ADDED,
    REMOVED,
    MODIFIED,
}

/**
 * A changed method plus the way it changed.
 *
 * The frozen [ChangedMethod] cannot express this: it has no change-kind field, and `Contracts.kt` is
 * frozen. The kind therefore rides in `ChangedMethod.id` as a stable prefix, and callers who need it
 * structurally take it from here. If A4 or the ledger ever branches on it, the contract gains a `kind`
 * field rather than anyone parsing ids.
 */
data class MethodDelta(
    val change: MethodChange,
    val method: ChangedMethod,
)
