package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.MyBundle
import com.github.mydachi.frictionless.execution.JUnitRunConfigurationBuilder
import com.github.mydachi.frictionless.execution.TestResultCollector
import com.github.mydachi.frictionless.execution.VerdictUpdater
import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangeSource
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

/**
 * What the Run button does: A1 → A2 → A3 → A4, then the impacted tests, then E3.
 *
 * Every track had landed and nothing connected them — Run still reported "not implemented" on top of
 * a working analyser. This is that wire.
 *
 * Two ordering decisions matter:
 *
 *  1. **The ledger is published before any test runs.** The call-graph answer is the valuable half
 *     and it is available in seconds; waiting for a test run to show it would make the tool feel slow
 *     and would show nothing at all in a repo with no tests.
 *  2. **Results are applied as they stream in.** E2 reports per test, so each one re-buckets the
 *     methods it reaches and the gutter marks flip live — which is what the tour is built to show.
 */
@Service(Service.Level.PROJECT)
class AnalysisService(private val project: Project) {

    fun run() {
        val model = project.service<LedgerModel>()
        model.update(LedgerState.Running(MyBundle["analysis.running"]))

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, MyBundle["analysis.title"], true) {
            override fun run(indicator: ProgressIndicator) {
                val analysed = try {
                    analyse(model.source, indicator)
                } catch (e: Exception) {
                    thisLogger().warn("Analysis failed", e)
                    model.update(LedgerState.Failed(e.message ?: e::class.java.simpleName))
                    return
                }

                model.update(LedgerState.Ready(analysed))
                runImpactedTests(analysed)
            }
        })
    }

    /**
     * The read-only half. An empty change set is a real answer — a clean checkout — and is published
     * as an empty ledger rather than an error.
     */
    private fun analyse(source: ChangeSource, indicator: ProgressIndicator): ChangeSet {
        indicator.text = MyBundle["analysis.step.files"]
        val files = providerFor(source).changedFiles(project)

        indicator.text = MyBundle["analysis.step.methods"]
        val methods = MethodLevelDelta.changedMethods(project, files)

        indicator.text = MyBundle["analysis.step.impact"]
        val impacted = ImpactGraph.populate(project, methods)

        return BucketAssignment.beforeTests(ChangeSet(source, impacted))
    }

    /**
     * Launches exactly the tests that reach a changed method, and folds each result back as it
     * arrives. No reaching tests means nothing to run — the ledger already says Unverified, which is
     * the whole point, so there is nothing to wait for.
     */
    private fun runImpactedTests(changeSet: ChangeSet) {
        val tests = VerdictUpdater.testsToRun(changeSet)
        if (tests.isEmpty()) return

        val model = project.service<LedgerModel>()
        ApplicationManager.getApplication().invokeLater {
            try {
                val settings = JUnitRunConfigurationBuilder.build(project, tests)
                val collector = TestResultCollector.collector(
                    project = project,
                    parentDisposable = project,
                    onResult = { result ->
                        val current = (model.state as? LedgerState.Ready)?.changeSet ?: return@collector
                        model.update(LedgerState.Ready(VerdictUpdater.apply(current, result)))
                    },
                )
                JUnitRunConfigurationBuilder.launch(project, settings, collector)
            } catch (e: Exception) {
                // The ledger keeps the call-graph answer it already has: losing "4 tests reach this"
                // because the runner would not start is worse than losing the pass/fail on top of it.
                thisLogger().warn("Could not launch impacted tests", e)
            }
        }
    }

    private fun providerFor(source: ChangeSource): ChangeSetProvider = when (source) {
        is ChangeSource.WorkingTree -> WorkingTreeChangeSetProvider
        is ChangeSource.Branch -> BranchChangeSetProvider(source.base, source.head)
    }
}
