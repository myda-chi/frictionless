package com.github.mydachi.frictionless.toolwindow

import com.github.mydachi.frictionless.MyBundle
import com.github.mydachi.frictionless.editor.VerdictMarkup
import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.github.mydachi.frictionless.navigation.Navigator
import com.github.mydachi.frictionless.tour.TourService
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
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent

/**
 * Deliverables U1 and U2: the toolbar, the counts header, the ledger and the blast-radius detail.
 *
 * Three states a developer can actually hit are rendered honestly — nothing analysed yet, running,
 * and failed. There is no state that paints invented rows.
 */
class FrictionlessPanel(private val project: Project, parent: Disposable) : SimpleToolWindowPanel(true, true) {

    private val model = project.service<LedgerModel>()
    private val counts = JBLabel()
    private val tree = LedgerTree { method -> Navigator.open(project, method) }
    private val blastRadius = BlastRadiusPanel(project)
    private val message = JBLabel().apply { foreground = UIUtil.getInactiveTextColor() }

    private val cards = CardLayout()
    private val content = JBPanel<JBPanel<*>>(cards)

    private val listener: (LedgerState) -> Unit = { state ->
        ApplicationManager.getApplication().invokeLater { render(state) }
    }

    init {
        toolbar = buildToolbar()

        val ledger = OnePixelSplitter(true, 0.6f).apply {
            firstComponent = JBScrollPane(tree)
            secondComponent = blastRadius
        }
        content.add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(12)
            add(message, BorderLayout.NORTH)
        }, MESSAGE)
        content.add(ledger, LEDGER)

        setContent(JBPanel<JBPanel<*>>(BorderLayout()).apply {
            add(header(), BorderLayout.NORTH)
            add(content, BorderLayout.CENTER)
        })

        tree.addTreeSelectionListener { blastRadius.show(tree.selected()) }

        model.addListener(listener)
        // The ledger row follows the tour: as the editor moves to a stop, its row highlights, so the
        // panel and the editor tell the same story on a projector. The call here used to be
        // `tree.selected()`, which read the selection and discarded it (issue #64).
        project.service<TourService>().addListener(parent) { stop ->
            ApplicationManager.getApplication().invokeLater {
                if (stop == null) {
                    // Tour over: the header goes back to the totals it shows at rest.
                    showCounts()
                } else {
                    tree.select(stop.method)
                    blastRadius.show(stop.method)
                    counts.text = MyBundle[
                        "tour.stop",
                        stop.index + 1,
                        stop.total,
                        stop.method.displayName,
                        stop.method.verdict.display(),
                    ]
                }
            }
        }
        Disposer.register(parent) {
            model.removeListener(listener)
            project.service<VerdictMarkup>().clear()
        }
    }

    private fun header() = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0),
            JBUI.Borders.empty(6, 10),
        )
        add(counts.apply { font = font.deriveFont(font.size2D + 1f) }, BorderLayout.WEST)
    }

    private fun buildToolbar(): JComponent {
        val group = ActionManager.getInstance().getAction("Frictionless.Toolbar") as DefaultActionGroup
        val toolbar: ActionToolbar = ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLWINDOW_TITLE, group, true)
        toolbar.targetComponent = this
        return toolbar.component
    }

    /** The header at rest: what the whole change set adds up to. */
    private fun showCounts() {
        val ready = model.state as? LedgerState.Ready ?: return
        val byBucket = model.counts()
        counts.text = MyBundle[
            "ledger.counts",
            ready.changeSet.methods.size,
            byBucket[Bucket.PROVEN] ?: 0,
            byBucket[Bucket.BEHAVIOUR_CHANGED] ?: 0,
            byBucket[Bucket.UNVERIFIED] ?: 0,
        ]
    }

    private fun render(state: LedgerState) {
        when (state) {
            is LedgerState.Empty -> {
                counts.text = MyBundle["ledger.counts.none"]
                message.text = MyBundle["ledger.empty"]
                cards.show(content, MESSAGE)
            }

            is LedgerState.Running -> {
                counts.text = MyBundle["ledger.counts.none"]
                message.text = state.what
                cards.show(content, MESSAGE)
            }

            is LedgerState.Failed -> {
                counts.text = MyBundle["ledger.counts.none"]
                message.text = MyBundle["ledger.failed", state.message]
                cards.show(content, MESSAGE)
            }

            is LedgerState.Ready -> {
                showCounts()
                tree.show(state.changeSet)
                blastRadius.show(null)
                cards.show(content, LEDGER)
                project.service<VerdictMarkup>().refresh()
            }
        }
    }

    private companion object {
        const val MESSAGE = "message"
        const val LEDGER = "ledger"
    }
}
