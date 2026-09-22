package com.github.mydachi.frictionless.toolwindow

import com.github.mydachi.frictionless.MyBundle
import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.BoxLayout
import javax.swing.JComponent

/**
 * Deliverable S1: toolbar plus the surface the ledger paints on.
 *
 * The rows themselves are U2's job — this owns the frame, the counts header and the three states a
 * developer can actually hit (nothing run yet, running, failed).
 */
class FrictionlessPanel(project: Project, parent: Disposable) : SimpleToolWindowPanel(true, true) {

    private val model = project.service<LedgerModel>()
    private val counts = JBLabel()
    private val body = JBPanel<JBPanel<*>>().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    private val listener: (LedgerState) -> Unit = { state ->
        ApplicationManager.getApplication().invokeLater { render(state) }
    }

    init {
        toolbar = buildToolbar()

        val content = JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(counts.apply { border = JBUI.Borders.emptyBottom(8) }, BorderLayout.NORTH)
            add(body, BorderLayout.CENTER)
        }
        setContent(content)

        model.addListener(listener)
        Disposer.register(parent) { model.removeListener(listener) }
    }

    private fun buildToolbar(): JComponent {
        val group = ActionManager.getInstance().getAction("Frictionless.Toolbar") as DefaultActionGroup
        val toolbar: ActionToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_TITLE, group, true)
        toolbar.targetComponent = this
        return toolbar.component
    }

    private fun render(state: LedgerState) {
        body.removeAll()
        when (state) {
            is LedgerState.Empty -> {
                counts.text = MyBundle["ledger.counts.none"]
                body.add(message(MyBundle["ledger.empty"]))
            }

            is LedgerState.Running -> {
                counts.text = MyBundle["ledger.counts.none"]
                body.add(message(state.what))
            }

            is LedgerState.Failed -> {
                counts.text = MyBundle["ledger.counts.none"]
                body.add(message(MyBundle["ledger.failed", state.message]))
            }

            is LedgerState.Ready -> {
                val byBucket = model.counts()
                counts.text = MyBundle[
                    "ledger.counts",
                    state.changeSet.methods.size,
                    byBucket[Bucket.PROVEN] ?: 0,
                    byBucket[Bucket.BEHAVIOUR_CHANGED] ?: 0,
                    byBucket[Bucket.UNVERIFIED] ?: 0,
                ]
                // Rows are U2. Until then, one line per method so the pipeline is visible end to end.
                state.changeSet.methods.forEach { method ->
                    body.add(JBLabel("${method.displayName}  —  ${method.verdict.display()}"))
                }
            }
        }
        body.revalidate()
        body.repaint()
    }

    private fun message(text: String) = JBLabel(text).apply { foreground = UIUtil.getInactiveTextColor() }
}
