package com.github.mydachi.frictionless.agent

import com.github.mydachi.frictionless.MyBundle
import com.github.mydachi.frictionless.execution.JUnitTestRunner
import com.github.mydachi.frictionless.execution.VerdictUpdater
import com.github.mydachi.frictionless.model.ChangedMethod
import com.github.mydachi.frictionless.model.LedgerModel
import com.github.mydachi.frictionless.model.LedgerState
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJavaFile

/**
 * Pin behaviour: the red-to-green step.
 *
 * Wires G1–G3's loop to G4's writer and G5's runner. Nothing in [CharacterisationTestAgent] changed
 * to get here — swapping the stub for the real runner was a constructor argument, which is what E0's
 * seam was for.
 *
 * The whole thing runs on a background thread because [JUnitTestRunner] blocks, and it reports what
 * actually happened: a test that could not be made to pass says so rather than being dressed up.
 */
@Service(Service.Level.PROJECT)
class PinBehaviourService(private val project: Project) {

    fun pin(method: ChangedMethod) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, MyBundle["pin.title"], true) {
            override fun run(indicator: ProgressIndicator) {
                val kotlin = isKotlin(method)
                val context = ContextBuilder(project).build(method)

                val agent = CharacterisationTestAgent(
                    llm = KoogLlmClient(),
                    runner = JUnitTestRunner(project),
                    writer = ProjectTestWriter(project, kotlin),
                )

                when (val result = agent.pin(context) { step -> indicator.text = step }) {
                    is CharacterisationTestAgent.Result.Pinned -> {
                        rerunLedgerFor(method)
                        notify(MyBundle["pin.pinned", result.test.className, result.attempts], NotificationType.INFORMATION)
                    }

                    is CharacterisationTestAgent.Result.GaveUp ->
                        notify(MyBundle["pin.gaveUp", result.attempts], NotificationType.WARNING)

                    is CharacterisationTestAgent.Result.Unavailable -> {
                        // No model available. Still leave something real behind: a disabled
                        // placeholder in the right package, which cannot fake a pass.
                        val template = TemplateTest.forMethod(
                            method = method,
                            packageName = context.testPackage,
                            kotlin = kotlin,
                            // Read from the repository's own tests, not assumed: a placeholder that
                            // does not compile breaks the build it was meant to help.
                            junit5 = TemplateTest.usesJUnit5(context.conventionExamples),
                        )
                        val written = ProjectTestWriter(project, kotlin).write(template)
                        notify(
                            if (written) MyBundle["pin.template", template.className, result.reason]
                            else MyBundle["pin.unavailable", result.reason],
                            NotificationType.WARNING,
                        )
                    }
                }
            }
        })
    }

    /** The generated test is real now, so the ledger's answer for this method is re-derived. */
    private fun rerunLedgerFor(method: ChangedMethod) {
        val model = project.service<LedgerModel>()
        val current = (model.state as? LedgerState.Ready)?.changeSet ?: return
        val tests = VerdictUpdater.testsToRun(current)
        if (tests.isEmpty()) return
        val results = JUnitTestRunner(project).run(tests)
        model.update(LedgerState.Ready(VerdictUpdater.apply(current, results)))
    }

    private fun isKotlin(method: ChangedMethod): Boolean {
        if (method.filePath.endsWith(".kt")) return true
        if (method.filePath.endsWith(".java")) return false
        return ReadAction.compute<Boolean, RuntimeException> {
            method.pointer?.element?.containingFile !is PsiJavaFile
        }
    }

    private fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Frictionless")
            .createNotification(message, type)
            .notify(project)
    }
}
