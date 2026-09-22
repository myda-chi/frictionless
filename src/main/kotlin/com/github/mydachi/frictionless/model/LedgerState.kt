package com.github.mydachi.frictionless.model

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import java.util.concurrent.CopyOnWriteArrayList

/**
 * What the ledger is currently showing (deliverable S2).
 *
 * There is deliberately no state that renders made-up rows. If analysis fails we show [Failed], never
 * a fixture — a silent fall back to fake data is how a demo dies on stage.
 */
sealed interface LedgerState {
    data object Empty : LedgerState
    data class Running(val what: String) : LedgerState
    data class Ready(val changeSet: ChangeSet) : LedgerState
    data class Failed(val message: String) : LedgerState
}

/**
 * The single place the ledger's current state lives. The Analysis and Execution tracks push into it;
 * the UI track listens.
 */
@Service(Service.Level.PROJECT)
class LedgerModel(@Suppress("unused") private val project: Project) {

    @Volatile
    var state: LedgerState = LedgerState.Empty
        private set

    @Volatile
    var source: ChangeSource = ChangeSource.WorkingTree

    private val listeners = CopyOnWriteArrayList<(LedgerState) -> Unit>()

    fun addListener(listener: (LedgerState) -> Unit) {
        listeners += listener
        listener(state)
    }

    fun removeListener(listener: (LedgerState) -> Unit) {
        listeners -= listener
    }

    fun update(newState: LedgerState) {
        state = newState
        listeners.forEach { it(newState) }
    }

    fun counts(): Map<Bucket, Int> {
        val ready = state as? LedgerState.Ready ?: return emptyMap()
        return ready.changeSet.methods.groupingBy { it.verdict.bucket }.eachCount()
    }

    companion object {
        @JvmField
        val TOPIC: Topic<Runnable> = Topic.create("Receipts ledger changed", Runnable::class.java)
    }
}
