package com.github.mydachi.frictionless.actions

import com.github.mydachi.frictionless.MyBundle
import com.github.mydachi.frictionless.model.ChangeSource
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.JComponent

/**
 * Deliverable S1: the four toolbar actions.
 *
 * Run, Play and Share are wired to the tool window but not yet implemented — each says which issue
 * owns it rather than pretending to work. None of them registers a default keyboard shortcut.
 */

private fun notYet(project: Project, what: String, issue: String) {
    project.service<LedgerModel>().update(LedgerState.Failed(MyBundle["action.notImplemented", what, issue]))
}

/** Source picker — working tree or a branch compared against a base. Branch mode is A5 + U5. */
class SourcePickerAction : ComboBoxAction(), DumbAware {

    override fun createPopupActionGroup(button: JComponent, context: com.intellij.openapi.actionSystem.DataContext) =
        DefaultActionGroup(SelectWorkingTreeAction(), SelectBranchAction())

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        e.presentation.text = when (val source = project.service<LedgerModel>().source) {
            is ChangeSource.WorkingTree -> MyBundle["source.workingTree"]
            is ChangeSource.Branch -> MyBundle["source.branch", source.head, source.base]
        }
    }

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}

class SelectWorkingTreeAction : AnAction(MyBundle["source.workingTree"]), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<LedgerModel>().source = ChangeSource.WorkingTree
    }
}

class SelectBranchAction : AnAction(MyBundle["source.pickBranch"]), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        JBPopupFactory.getInstance()
            .createMessage(MyBundle["action.notImplemented", MyBundle["source.pickBranch"], "#29"])
            .showInFocusCenter()
    }
}

/** Run the analysis over the selected change set. Owned by A1–A4. */
class RunReceiptsAction : AnAction(MyBundle["action.run"], null, AllIcons.Actions.Execute), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        notYet(project, MyBundle["action.run"], "#15")
    }
}

/** Autopilot tour. Owned by U6. */
class PlayTourAction : AnAction(MyBundle["action.play"], null, AllIcons.Debugger.ThreadRunning), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        notYet(project, MyBundle["action.play"], "#30")
    }
}

/** Code With Me session sharing. Owned by C1. */
class ShareSessionAction : AnAction(MyBundle["action.share"], null, AllIcons.Toolwindows.ToolWindowProfiler), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        notYet(project, MyBundle["action.share"], "#38")
    }
}
