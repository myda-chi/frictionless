package com.github.mydachi.frictionless.toolwindow

import com.github.mydachi.frictionless.MyBundle
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.navigation.Navigator
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.BoxLayout

/**
 * Deliverable U3, detail half: what this method costs if it is wrong.
 *
 * Call sites and reaching tests, both clickable. Populated by A6.
 */
class BlastRadiusPanel(private val project: Project) : JBPanel<BlastRadiusPanel>(BorderLayout()) {

    private val body = JBPanel<JBPanel<*>>().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }

    init {
        border = JBUI.Borders.empty(8)
        add(JBScrollPane(body), BorderLayout.CENTER)
        show(null)
    }

    fun show(method: ChangedMethod?) {
        body.removeAll()
        if (method == null) {
            body.add(hint(MyBundle["blast.none"]))
        } else {
            body.add(heading(method.displayName))
            body.add(hint(method.verdict.display()))

            body.add(heading(MyBundle["blast.callSites", method.callSites.size]))
            if (method.callSites.isEmpty()) body.add(hint(MyBundle["blast.callSites.none"]))
            method.callSites.forEach { site ->
                body.add(link("${site.displayName}  (${site.filePath}:${site.line})") {
                    Navigator.open(project, site)
                })
            }

            body.add(heading(MyBundle["blast.tests", method.reachingTests.size]))
            if (method.reachingTests.isEmpty()) body.add(hint(MyBundle["blast.tests.none"]))
            method.reachingTests.forEach { test ->
                val mark = when (test.outcome) {
                    TestOutcome.PASSED -> "✓"
                    TestOutcome.FAILED, TestOutcome.ERROR -> "✗"
                    TestOutcome.NOT_RUN -> "·"
                }
                body.add(hint("$mark ${test.displayName}"))
            }
        }
        body.revalidate()
        body.repaint()
    }

    private fun heading(text: String) = JBLabel(text).apply {
        border = JBUI.Borders.empty(6, 0, 2, 0)
        font = font.deriveFont(font.style or java.awt.Font.BOLD)
    }

    private fun hint(text: String) = JBLabel(text).apply { foreground = UIUtil.getInactiveTextColor() }

    private fun link(text: String, onClick: () -> Unit) = ActionLink(text) { onClick() }
}
