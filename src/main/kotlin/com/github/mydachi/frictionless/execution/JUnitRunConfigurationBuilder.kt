package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestRef
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit.JUnitConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.testframework.TestSearchScope
import com.intellij.openapi.project.Project

/**
 * Deliverable E1: builds a `JUnitConfiguration` from a list of test methods and launches it.
 *
 * Specification.md §3.2 requires running exactly the tests that reach a changed method - which
 * routinely spans several classes at once. JUnit's "Pattern" test object is the one test type built
 * for that: it accepts a set of `"FullyQualifiedClass,methodName"` entries instead of a single class,
 * so one configuration covers the whole reaching set in one launch rather than one run per class.
 *
 * This is deliberately a thin wrapper: it builds and starts the run, nothing more. Collecting
 * results as they stream in is E2's job, and turning those results into ledger verdicts is E3's -
 * both listen to the [ExecutionEnvironment] this hands back rather than duplicating how it was built.
 */
object JUnitRunConfigurationBuilder {

    /**
     * Builds a run configuration covering every test in [tests] as a single JUnit "Pattern" run.
     *
     * Scope is forced to [TestSearchScope.WHOLE_PROJECT]: the reaching tests for one changed method
     * can live in a different module than the method itself, and a narrower default scope would
     * silently drop them - the same "empty list is fine, a wrong list is not" principle as A3.
     */
    fun build(project: Project, tests: List<TestRef>): RunnerAndConfigurationSettings {
        require(tests.isNotEmpty()) { "cannot build a run configuration for zero tests" }

        val runManager = RunManager.getInstance(project)
        val factory = JUnitConfigurationType.getInstance().configurationFactories.first()
        val settings = runManager.createConfiguration(configurationName(tests), factory)

        val configuration = settings.configuration as JUnitConfiguration
        configuration.persistentData.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN
        configuration.persistentData.setPatterns(LinkedHashSet(tests.map { pattern(it) }))
        configuration.setSearchScope(TestSearchScope.WHOLE_PROJECT)

        return settings
    }

    /**
     * Starts [settings] running. Returns the [ExecutionEnvironment] immediately - this does not wait
     * for the run to finish; E2 attaches an `SMTRunnerEventsListener` to observe it as it goes.
     */
    fun launch(project: Project, settings: RunnerAndConfigurationSettings): ExecutionEnvironment {
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val environment = ExecutionEnvironmentBuilder.create(executor, settings).build()
        ExecutionManager.getInstance(project).restartRunProfile(environment)
        return environment
    }

    /** `"ClassName,methodName"` - the exact shape `JUnitConfiguration.bePatternConfiguration` emits. */
    internal fun pattern(test: TestRef): String = "${test.className},${test.methodName}"

    internal fun configurationName(tests: List<TestRef>): String = when (tests.size) {
        1 -> "Frictionless: ${tests.single().displayName}"
        else -> "Frictionless: ${tests.size} tests"
    }
}
