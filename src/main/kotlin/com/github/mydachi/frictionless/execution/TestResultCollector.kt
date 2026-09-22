package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project

/**
 * Deliverable E2: streams per-test results as `SMTRunnerEventsListener` reports them, instead of
 * waiting for the whole run to finish and handing back one batch. The Autopilot tour (U6) flips
 * gutter marks live as each verdict lands, so results have to arrive one at a time, not batched.
 *
 * `SMTRunnerEventsListener.TEST_STATUS` is a project-wide message-bus topic - there is no per-run
 * variant. [collector] captures the `SMRootTestProxy` that belongs to *this* run's own console (from
 * the [ProgramRunner.Callback] fired once the run's console exists, wired through
 * [JUnitRunConfigurationBuilder.launch]) and filters every event on identity against it, so a second,
 * unrelated JUnit run started elsewhere in the IDE is not misattributed to this one - the same trick
 * the platform's own Test Runner tab uses to tell concurrent runs apart.
 */
object TestResultCollector {

    /**
     * A [ProgramRunner.Callback] to pass to [JUnitRunConfigurationBuilder.launch]. Once the run's
     * console exists, subscribes to test events for the lifetime of [parentDisposable]: [onResult]
     * fires once per finished test as it happens, [onRunFinished] once when the whole run completes.
     */
    fun collector(
        project: Project,
        parentDisposable: Disposable,
        onResult: (TestResult) -> Unit,
        onRunFinished: () -> Unit = {},
    ): ProgramRunner.Callback = ProgramRunner.Callback { descriptor ->
        val console = descriptor?.executionConsole as? SMTRunnerConsoleView
        if (console != null) {
            val root = console.resultsViewer.testsRootNode
            project.messageBus.connect(parentDisposable)
                .subscribe(SMTRunnerEventsListener.TEST_STATUS, RelayListener(root, onResult, onRunFinished))
        }
    }

    private class RelayListener(
        private val root: SMTestProxy.SMRootTestProxy,
        private val onResult: (TestResult) -> Unit,
        private val onRunFinished: () -> Unit,
    ) : SMTRunnerEventsListener {

        private fun belongsToThisRun(test: SMTestProxy): Boolean {
            var node: SMTestProxy = test
            while (true) {
                val parent = node.parent ?: return node === root
                node = parent
            }
        }

        override fun onTestingStarted(testsRoot: SMTestProxy.SMRootTestProxy) = Unit

        override fun onTestingFinished(testsRoot: SMTestProxy.SMRootTestProxy) {
            if (testsRoot === root) onRunFinished()
        }

        override fun onTestsCountInSuite(count: Int) = Unit
        override fun onTestStarted(test: SMTestProxy) = Unit

        override fun onTestFinished(test: SMTestProxy) {
            if (test.isSuite || !belongsToThisRun(test)) return
            onResult(toTestResult(test))
        }

        override fun onTestFailed(test: SMTestProxy) = Unit
        override fun onTestIgnored(test: SMTestProxy) = Unit
        override fun onSuiteFinished(suite: SMTestProxy) = Unit
        override fun onSuiteStarted(suite: SMTestProxy) = Unit
        override fun onCustomProgressTestsCategory(categoryName: String?, testCount: Int) = Unit
        override fun onCustomProgressTestStarted() = Unit
        override fun onCustomProgressTestFailed() = Unit
        override fun onCustomProgressTestFinished() = Unit
        override fun onSuiteTreeNodeAdded(testProxy: SMTestProxy) = Unit
        override fun onSuiteTreeStarted(suite: SMTestProxy) = Unit
    }
}

/**
 * `"java:test://com.example.FooTest/bar"` -> `TestRef("com.example.FooTest", "bar")` - the exact
 * location-url shape the JUnit runtime's SM protocol emits (`JUnitTestTreeNodeManager`).
 */
internal fun testRefFromLocationUrl(locationUrl: String?): TestRef? {
    val path = locationUrl?.removePrefix("java:test://") ?: return null
    val slash = path.lastIndexOf('/')
    if (slash <= 0 || slash == path.length - 1) return null
    return TestRef(className = path.substring(0, slash), methodName = path.substring(slash + 1))
}

/**
 * SKIPPED/COMPLETE collapse into [TestOutcome.PASSED] the same way [SMTestProxy.isPassed] treats
 * them. Anything that is not an unambiguous pass or assertion failure - ignored, terminated, an
 * exception, an unrecognised future magnitude - is [TestOutcome.ERROR]: this never reports
 * [TestOutcome.PASSED] on a guess.
 */
internal fun testOutcomeFromMagnitude(magnitude: TestStateInfo.Magnitude): TestOutcome = when (magnitude) {
    TestStateInfo.Magnitude.PASSED_INDEX,
    TestStateInfo.Magnitude.COMPLETE_INDEX,
    TestStateInfo.Magnitude.SKIPPED_INDEX,
    -> TestOutcome.PASSED
    TestStateInfo.Magnitude.FAILED_INDEX -> TestOutcome.FAILED
    else -> TestOutcome.ERROR
}

internal fun toTestResult(test: SMTestProxy): TestResult {
    val ref = testRefFromLocationUrl(test.locationUrl) ?: TestRef(className = "", methodName = test.name)
    return TestResult(
        test = ref,
        outcome = testOutcomeFromMagnitude(test.magnitudeInfo),
        output = test.stacktrace ?: test.errorMessage.orEmpty(),
        durationMs = test.duration ?: 0,
    )
}
