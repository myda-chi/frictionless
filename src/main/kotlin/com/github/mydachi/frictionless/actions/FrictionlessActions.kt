package com.github.mydachi.frictionless.actions

import com.github.mydachi.frictionless.MyBundle
import com.github.mydachi.frictionless.analysis.AnalysisService
import com.github.mydachi.frictionless.analysis.BranchChangeSetProvider
import com.github.mydachi.frictionless.model.ChangeSource
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.github.mydachi.frictionless.tour.TourService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.JComponent

/**
 * Deliverable U1: the four toolbar actions.
 *
 * Run is owned by the Analysis track (#15) and says so rather than pretending. Play and the source
 * picker are live.
 */

private fun notYet(project: Project, what: String, issue: String) {
    project.service<LedgerModel>().update(LedgerState.Failed(MyBundle["action.notImplemented", what, issue]))
}

/** Deliverable U5: working tree, or a branch compared against a base. */
class SourcePickerAction : ComboBoxAction(), DumbAware {

    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup =
        DefaultActionGroup(SelectWorkingTreeAction(), SelectBranchAction())

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        e.presentation.text = when (val source = project.service<LedgerModel>().source) {
            is ChangeSource.WorkingTree -> MyBundle["source.workingTree"]
            is ChangeSource.Branch -> MyBundle["source.branch", source.base, source.head]
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

class SelectWorkingTreeAction : AnAction(MyBundle["source.workingTree"]), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<LedgerModel>().source = ChangeSource.WorkingTree
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/**
 * Deliverable U5: choose what to compare the current branch **against**.
 *
 * The picker selects the *base*, not the head. Branch mode compares a base ref to the working tree
 * (see [BranchChangeSetProvider]), so the head is always whatever is checked out — offering a choice
 * of head would promise a comparison the analyser cannot honestly make.
 */
class SelectBranchAction : AnAction(MyBundle["source.pickBranch"]), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val model = project.service<LedgerModel>()

        val head = BranchChangeSetProvider.currentBranch(project)
        if (head == null) {
            model.update(LedgerState.Failed(MyBundle["source.noRepository"]))
            return
        }

        val candidates = BranchChangeSetProvider.localBranches(project).filter { it != head }
        if (candidates.isEmpty()) {
            model.update(LedgerState.Failed(MyBundle["source.noOtherBranch"]))
            return
        }

        val preferred = model.lastBase ?: BranchChangeSetProvider.defaultBase(project)
        val ordered = candidates.sortedByDescending { it == preferred }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(ordered)
            .setTitle(MyBundle["source.pickBranch.title", head])
            .setNamerForFiltering { it }
            .setItemChosenCallback { base ->
                model.lastBase = base
                model.source = ChangeSource.Branch(base = base, head = head)
            }
            .createPopup()
            .showInFocusCenter()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/** Runs the analysis over the selected change set: A1 → A2 → A3 → A4, then the impacted tests. */
class RunAnalysisAction : AnAction(MyBundle["action.run"], null, AllIcons.Actions.Execute), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<AnalysisService>().run()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/** Deliverable U6: Autopilot Review. Toggles, so a tour can always be stopped. */
class PlayTourAction : AnAction(MyBundle["action.play"], null, AllIcons.Actions.Execute), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val tour = project.service<TourService>()
        if (tour.isRunning) tour.stop() else tour.start()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val tour = project?.service<TourService>()
        val running = tour?.isRunning == true
        e.presentation.text = if (running) MyBundle["action.play.stop"] else MyBundle["action.play"]
        e.presentation.icon = if (running) AllIcons.Actions.Suspend else AllIcons.Actions.Execute
        e.presentation.isEnabled = project != null &&
            project.service<LedgerModel>().state is LedgerState.Ready
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/** Code With Me session sharing. Owned by C1. Logic lives in [shareCodeWithMeSession]. */
class ShareSessionAction : AnAction(MyBundle["action.share"], null, AllIcons.Toolwindows.ToolWindowProfiler), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        shareCodeWithMeSession(project, e)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
