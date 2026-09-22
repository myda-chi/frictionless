package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestOutcome
import com.github.mydachi.frictionless.model.TestRef
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Deliverable G5: the real runner, built from E1's configuration builder and E2's result collector.
 *
 * [StubTestRunner] and this are interchangeable by construction — that was the point of E0's seam,
 * and the agent's loop needed no change to move across.
 *
 * **This blocks**, because [TestRunner.run] is synchronous and the agent's loop is a sequence of
 * "write, run, read what happened". It must therefore never be called on the EDT: the run has to be
 * *launched* on the EDT, and a caller blocking there would deadlock against its own launch. The
 * agent already runs on a background thread.
 */
class JUnitTestRunner(
    private val project: Project,
    /** A run that never reports back must not hang the agent for ever. */
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) : TestRunner {

    override fun run(tests: List<TestRef>): List<TestResult> {
        if (tests.isEmpty()) return emptyList()
        check(!ApplicationManager.getApplication().isDispatchThread) {
            "JUnitTestRunner.run blocks and must not be called on the EDT"
        }

        val collected = ConcurrentLinkedQueue<TestResult>()
        val finished = CountDownLatch(1)
        val lifetime = Disposer.newDisposable("Frictionless test run")

        try {
            launch(tests, collected, finished, lifetime)
            if (!finished.await(timeoutSeconds, TimeUnit.SECONDS)) {
                return tests.map { timedOut(it) }
            }
        } catch (e: Exception) {
            thisLogger().warn("Could not run ${tests.size} test(s)", e)
            return tests.map { failedToStart(it, e) }
        } finally {
            Disposer.dispose(lifetime)
        }

        return reconcile(tests, collected.toList())
    }

    private fun launch(
        tests: List<TestRef>,
        collected: ConcurrentLinkedQueue<TestResult>,
        finished: CountDownLatch,
        lifetime: Disposable,
    ) = ApplicationManager.getApplication().invokeAndWait {
        val settings = JUnitRunConfigurationBuilder.build(project, tests)
        val collector = TestResultCollector.collector(
            project = project,
            parentDisposable = lifetime,
            onResult = { collected += it },
            onRunFinished = { finished.countDown() },
        )
        JUnitRunConfigurationBuilder.launch(project, settings, collector)
    }

    private fun timedOut(test: TestRef) = TestResult(
        test = test,
        outcome = TestOutcome.ERROR,
        output = "the test run did not finish within ${timeoutSeconds}s",
    )

    private fun failedToStart(test: TestRef, e: Exception) = TestResult(
        test = test,
        outcome = TestOutcome.ERROR,
        output = "the test run could not be started: ${e.message ?: e::class.java.simpleName}",
    )

    companion object {
        const val DEFAULT_TIMEOUT_SECONDS = 120L

    /**
         * What was asked for, answered with what came back.
         *
         * A requested test the run never reported is [TestOutcome.ERROR], never a pass: "the runner said
         * nothing about it" and "it passed" are different facts, and only one of them is safe to act on.
         * A whole-class request matches on class name, since the run reports the individual methods.
         */
            internal fun reconcile(requested: List<TestRef>, reported: List<TestResult>): List<TestResult> =
            requested.map { want ->
                val matches = reported.filter { it.test.matchesRequest(want) }
                when {
                    matches.isEmpty() -> TestResult(
                        test = want,
                        outcome = TestOutcome.ERROR,
                        output = "the run reported no result for ${want.displayName}",
                    )
                    // A class passes only if every test in it did.
                    else -> matches.firstOrNull { it.outcome != TestOutcome.PASSED } ?: matches.first()
                }
            }

        private fun TestRef.matchesRequest(request: TestRef): Boolean {
            val sameClass = className == request.className ||
                className.substringAfterLast('.') == request.className.substringAfterLast('.')
            if (!sameClass) return false
            return request.isWholeClass || methodName == request.methodName
        }
    }
}
