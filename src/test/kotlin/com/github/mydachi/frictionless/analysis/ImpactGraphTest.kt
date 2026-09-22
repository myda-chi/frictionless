package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.ChangedMethod
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A3 acceptance: BFS via `ReferencesSearch` / `OverridingMethodsSearch` up the call graph, stopping at
 * the test source root, populates `reachingTests`.
 *
 * **Setup is load-bearing.** The platform fixture's default source root is the temp directory itself,
 * not `src/main/java`. Package names are computed from the source root, so with the default the class
 * `demo.Prod` is really `src.main.java.demo.Prod`, cross-file Java resolution returns `null`, and every
 * reference search here would silently find zero — a green suite proving nothing. The roots are
 * therefore declared explicitly in [setUp], and `dropResolveCaches` clears what was resolved before.
 */
class ImpactGraphTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir(MAIN_ROOT), false)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir(TEST_ROOT), true)
        // The fixture project has no junit on its classpath, so `@Test` would stay unresolved and
        // `qualifiedName` would report the bare simple name `Test`. Stubbing the annotation at its real
        // FQN lets the production check — which matches on the qualified name, deliberately, so that an
        // unrelated `@Test` cannot claim coverage — be exercised as written.
        myFixture.addFileToProject("$TEST_ROOT/org/junit/Test.java", "package org.junit;\npublic @interface Test {}\n")
        PsiManager.getInstance(project).dropResolveCaches()
    }

    fun testDirectCallSiteIsReported() {
        prod("demo/Service.java", "package demo;\npublic class Service { public void work() {} }\n")
        prod("demo/Caller.java", "package demo;\npublic class Caller { public void drive() { new Service().work(); } }\n")

        val enriched = analyse(productionMethod("demo/Service.java", "work")).single()

        assertEquals(1, enriched.callSites.size)
        assertEquals("Caller.drive()", enriched.callSites.single().displayName)
        assertEquals(2, enriched.callSites.single().line)
    }

    fun testTestThatReachesThroughAProductionChainIsFound() {
        prod("demo/Deep.java", "package demo;\npublic class Deep { public void target() {} }\n")
        prod("demo/Mid.java", "package demo;\npublic class Mid { public void middle() { new Deep().target(); } }\n")
        prod("demo/Top.java", "package demo;\npublic class Top { public void top() { new Mid().middle(); } }\n")
        test("demo/DeepTest.java", testBody("DeepTest", "should reach the target", "new Top().top();"))

        val enriched = analyse(productionMethod("demo/Deep.java", "target")).single()

        assertEquals(listOf("demo.DeepTest.shouldReachTheTarget"), enriched.reachingTests.map { it.displayName })
    }

    fun testMethodNothingCallsHasNoReachingTests() {
        prod("demo/Orphan.java", "package demo;\npublic class Orphan { public void nothingCallsThis() {} }\n")

        val enriched = analyse(productionMethod("demo/Orphan.java", "nothingCallsThis")).single()

        // The meaningful answer: nothing in the repo executes it. This is the product.
        assertEmpty(enriched.reachingTests)
        assertEmpty(enriched.callSites)
        assertEquals(0, enriched.verdict.counts.reachingTests)
    }

    fun testATestHelperIsNotReportedInPlaceOfTheTest() {
        prod("demo/Subject.java", "package demo;\npublic class Subject { public void subject() {} }\n")
        test(
            "demo/SubjectTest.java",
            """
            package demo;
            import org.junit.Test;
            public class SubjectTest {
                private void helper() { new Subject().subject(); }
                @Test
                public void shouldExerciseIt() { helper(); }
            }
            """.trimIndent(),
        )

        val enriched = analyse(productionMethod("demo/Subject.java", "subject")).single()

        // The helper lives under the test root and genuinely reaches it, but it is not a runnable test,
        // so the traversal must walk through it and report the annotated method instead.
        assertEquals(listOf("demo.SubjectTest.shouldExerciseIt"), enriched.reachingTests.map { it.displayName })
    }

    fun testACallThroughASupertypeReachesTheOverridingChangedMethod() {
        prod(
            "demo/Shape.java",
            "package demo;\npublic class Shape { public void draw() {} }\n",
        )
        prod(
            "demo/Circle.java",
            "package demo;\npublic class Circle extends Shape { @Override public void draw() {} }\n",
        )
        // The call is written against the supertype, so it statically resolves to Shape.draw but
        // dispatches to Circle.draw at runtime. Circle.draw is the changed method.
        test("demo/CircleTest.java", testBody("CircleTest", "draws through the supertype", "Shape s = new Circle();\ns.draw();"))

        val enriched = analyse(productionMethod("demo/Circle.java", "draw")).single()

        assertEquals(listOf("demo.CircleTest.drawsThroughTheSupertype"), enriched.reachingTests.map { it.displayName })
    }

    fun testACallToAnOverrideDoesNotCountAsReachingTheChangedBaseMethod() {
        prod("demo/Shape.java", "package demo;\npublic class Shape { public void draw() {} }\n")
        prod("demo/Circle.java", "package demo;\npublic class Circle extends Shape { @Override public void draw() {} }\n")
        // Resolves to Circle.draw, which does not execute Shape.draw.
        test("demo/ShapeTest.java", testBody("ShapeTest", "draws a circle", "new Circle().draw();"))

        val enriched = analyse(productionMethod("demo/Shape.java", "draw")).single()

        // The base method is NOT covered by this test and must not be reported as if it were. Claiming
        // coverage here is the one error that would make the ledger lie.
        assertEmpty(enriched.reachingTests)
    }

    fun testATestOutsideTheTestSourceRootIsNotCounted() {
        prod("demo/Prod.java", "package demo;\npublic class Prod { public void run() {} }\n")
        // Annotated as a test, but it lives in the production root: the specification gates tests on the
        // test source root, so this is not one.
        prod(
            "demo/Mislabelled.java",
            "package demo;\nimport org.junit.Test;\npublic class Mislabelled { @Test public void looksLikeATest() { new Prod().run(); } }\n",
        )

        val enriched = analyse(productionMethod("demo/Prod.java", "run")).single()

        assertEmpty(enriched.reachingTests)
    }

    fun testACyclicCallGraphTerminatesAndStillFindsTheTest() {
        prod("demo/A.java", "package demo;\npublic class A { public void a() { new B().b(); } }\n")
        prod("demo/B.java", "package demo;\npublic class B { public void b() { new A().a(); } }\n")
        test("demo/ATest.java", testBody("ATest", "calls into the cycle", "new A().a();"))

        val enriched = analyse(productionMethod("demo/A.java", "a")).single()

        assertEquals(listOf("demo.ATest.callsIntoTheCycle"), enriched.reachingTests.map { it.displayName })
    }

    fun testAMethodWithNoPointerIsPassedThroughUntouched() {
        // What A2 emits for a removed method: nothing in the working tree left to analyse.
        val removed = ChangedMethod(
            id = "removed:demo/Gone.java#gone()",
            displayName = "Gone.gone()",
            filePath = "demo/Gone.java",
            line = 3,
            verdict = MethodLevelDelta.emptyVerdict,
        )

        val enriched = analyse(removed).single()

        assertEquals(removed, enriched)
    }

    fun testThePipelineFillsTheCountsA4WillDecideOn() {
        prod("demo/Widget.java", "package demo;\npublic class Widget { public void spin() {} }\n")
        prod("demo/Machine.java", "package demo;\npublic class Machine { public void start() { new Widget().spin(); } }\n")
        test("demo/WidgetTest.java", testBody("WidgetTest", "spins a widget", "new Widget().spin();"))

        val enriched = analyse(productionMethod("demo/Widget.java", "spin")).single()

        assertEquals(1, enriched.verdict.counts.reachingTests)
        assertEquals(2, enriched.verdict.counts.callSites) // Machine.start() and the test itself
        // A4's decision, not A3's: the bucket is still the placeholder.
        assertEquals(MethodLevelDelta.emptyVerdict.bucket, enriched.verdict.bucket)
    }

    // --- helpers ---------------------------------------------------------------------------------

    private fun analyse(vararg methods: ChangedMethod): List<ChangedMethod> =
        ImpactGraph.populate(project, methods.toList()).also {
            PsiManager.getInstance(project).dropResolveCaches()
        }

    /** One `@Test` method with `body` as its content. */
    private fun testBody(className: String, methodName: String, body: String) =
        "package demo;\nimport org.junit.Test;\npublic class $className {\n    @Test\n    public void ${camel(methodName)}() {\n        $body\n    }\n}\n"

    /** A valid Java identifier from a sentence — non-identifier characters are dropped, not spliced in. */
    private fun camel(sentence: String): String {
        val words = sentence.split(" ")
            .map { word -> word.filter(Char::isLetterOrDigit) }
            .filter { it.isNotEmpty() }
        return words.first().replaceFirstChar(Char::lowercase) +
            words.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercase) }
    }

    private fun prod(path: String, text: String) {
        myFixture.addFileToProject("$MAIN_ROOT/$path", text)
        PsiManager.getInstance(project).dropResolveCaches()
    }

    private fun test(path: String, text: String) {
        myFixture.addFileToProject("$TEST_ROOT/$path", text)
        PsiManager.getInstance(project).dropResolveCaches()
    }

    private fun productionMethod(path: String, name: String): ChangedMethod {
        val file = myFixture.findFileInTempDir("$MAIN_ROOT/$path")
        return changedMethod(file, name)
    }

    private fun changedMethod(file: VirtualFile, name: String): ChangedMethod {
        val psi = PsiManager.getInstance(project).findFile(file)!!
        val method: PsiMethod = PsiTreeUtil.findChildrenOfType(psi, PsiMethod::class.java)
            .first { it.name == name }
        return ChangedMethod(
            id = "modified:$name",
            displayName = name,
            filePath = file.name,
            line = MethodLevelDelta.lineOf(psi, method),
            verdict = MethodLevelDelta.emptyVerdict,
            pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(method),
        )
    }

    private companion object {
        const val MAIN_ROOT = "src/main/java"
        const val TEST_ROOT = "src/test/java"
    }
}
