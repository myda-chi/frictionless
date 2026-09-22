package com.github.mydachi.frictionless.execution

import com.github.mydachi.frictionless.model.TestRef
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.testframework.TestSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * E1 acceptance: a `JUnitConfiguration` is built programmatically from a list of test methods.
 *
 * **Coverage boundary, stated plainly.** [JUnitRunConfigurationBuilder.build] only ever writes string
 * fields (`TEST_OBJECT`, the pattern set, the search scope) - it never resolves a `PsiClass`, so this
 * is testable headlessly with fixture-only classes that do not need to exist on disk. Actually
 * launching a run ([JUnitRunConfigurationBuilder.launch]) starts a real JVM process against compiled
 * test classes, which this suite does not attempt; that path is verified by running the plugin
 * against a real repo, not by this test.
 */
class JUnitRunConfigurationBuilderTest : BasePlatformTestCase() {

    private val fooTest = TestRef(className = "com.example.FooTest", methodName = "bar")
    private val bazTest = TestRef(className = "com.example.BazTest", methodName = "qux")

    fun testRejectsAnEmptyTestList() {
        assertThrows(IllegalArgumentException::class.java) {
            JUnitRunConfigurationBuilder.build(project, emptyList())
        }
    }

    fun testBuildsAPatternConfigurationCoveringEveryTest() {
        val settings = JUnitRunConfigurationBuilder.build(project, listOf(fooTest, bazTest))

        val configuration = settings.configuration as JUnitConfiguration
        val data = configuration.persistentData

        assertEquals(JUnitConfiguration.TEST_PATTERN, data.TEST_OBJECT)
        assertEquals(
            setOf("com.example.FooTest,bar", "com.example.BazTest,qux"),
            data.patterns,
        )
    }

    fun testScopeIsWholeProjectSoReachingTestsInOtherModulesAreNotDropped() {
        val settings = JUnitRunConfigurationBuilder.build(project, listOf(fooTest))
        val configuration = settings.configuration as JUnitConfiguration

        assertEquals(TestSearchScope.WHOLE_PROJECT, configuration.testSearchScope)
    }

    fun testPatternIsClassCommaMethod() {
        assertEquals("com.example.FooTest,bar", JUnitRunConfigurationBuilder.pattern(fooTest))
    }

    fun testConfigurationNameSummarisesMultipleTests() {
        assertEquals(
            "Frictionless: com.example.FooTest.bar",
            JUnitRunConfigurationBuilder.configurationName(listOf(fooTest)),
        )
        assertEquals(
            "Frictionless: 2 tests",
            JUnitRunConfigurationBuilder.configurationName(listOf(fooTest, bazTest)),
        )
    }
}
