package com.github.mydachi.frictionless.actions

import com.github.mydachi.frictionless.MyBundle
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
import git4idea.repo.GitRepositoryManager
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
            is ChangeSource.Branch -> MyBundle["source.branch", source.head, source.base]
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
 * Deliverable U5: a searchable popup over the repository's branches. The last base is remembered,
 * so switching branches repeatedly costs one click.
 */
class SelectBranchAction : AnAction(MyBundle["source.pickBranch"]), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val model = project.service<LedgerModel>()
        val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
        if (repository == null) {
            model.update(LedgerState.Failed(MyBundle["source.noRepository"]))
            return
        }

        val base = model.lastBase ?: defaultBase(repository.branches.localBranches.map { it.name })
        val branches = repository.branches.localBranches.map { it.name }.sorted()

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(branches)
            .setTitle(MyBundle["source.pickBranch.title"])
            .setNamerForFiltering { it }
            .setItemChosenCallback { head ->
                model.lastBase = base
                model.source = ChangeSource.Branch(base = base, head = head)
            }
            .createPopup()
            .showInFocusCenter()
    }

    private fun defaultBase(branches: List<String>): String =
        branches.firstOrNull { it == "main" } ?: branches.firstOrNull { it == "master" } ?: "main"

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

/** Run the analysis over the selected change set. Owned by A1–A4 (#15). */
class RunAnalysisAction : AnAction(MyBundle["action.run"], null, AllIcons.Actions.Execute), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        notYet(project, MyBundle["action.run"], "#15")
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
