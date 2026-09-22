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
 * A6 acceptance: call sites and callees are resolved per method for the detail panel (spec §3.3).
 *
 * The source roots are declared explicitly in [setUp] for the reason A3 documents: the fixture's
 * default root is the temp directory, so package FQNs come out wrong and every PSI search silently
 * finds nothing. See the `intellij-platform-plugins` skill, pitfall 9.
 */
class BlastRadiusResolverTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir(MAIN_ROOT), false)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir(TEST_ROOT), true)

        // The fixture project has no junit or platform SDK on its classpath, so these would stay
        // unresolved and the production checks — which match on qualified names, deliberately, so an
        // unrelated `@Test` or a class that merely *looks* like an action cannot claim to be one —
        // would never fire. Stubbing them at their real FQNs exercises those checks as written. This is
        // a test double, not a production assumption.
        stub("$TEST_ROOT/org/junit/Test.java", "package org.junit;\npublic @interface Test {}\n")
        stub(
            "$MAIN_ROOT/com/intellij/openapi/actionSystem/AnAction.java",
            "package com.intellij.openapi.actionSystem;\npublic abstract class AnAction { public abstract void actionPerformed(AnActionEvent e); }\n",
        )
        stub(
            "$MAIN_ROOT/com/intellij/openapi/actionSystem/AnActionEvent.java",
            "package com.intellij.openapi.actionSystem;\npublic class AnActionEvent {}\n",
        )
        stub("$MAIN_ROOT/$SPRING_PATH/GetMapping.java", "package $SPRING_PKG;\npublic @interface GetMapping { String value() default \"\"; }\n")
        stub("$MAIN_ROOT/$SPRING_PATH/RequestMapping.java", "package $SPRING_PKG;\npublic @interface RequestMapping { String value() default \"\"; }\n")
        PsiManager.getInstance(project).dropResolveCaches()
    }

    private fun stub(path: String, text: String) {
        myFixture.addFileToProject(path, text)
    }

    // --- callees: what the method calls ---------------------------------------------------------

    fun testCalleesAreResolvedForAMethodThatCallsOthers() {
        prod("demo/Dep.java", "package demo;\npublic class Dep { public void helper() {} }\n")
        prod("demo/Subject.java", "package demo;\npublic class Subject {\n    public void subject() {\n        new Dep().helper();\n    }\n}\n")

        val radius = radiusOf("demo/Subject.java", "subject")

        assertEquals(listOf("Dep.helper()"), radius.callees.map { it.displayName })
        assertEquals(2, radius.callees.single().line)
    }

    fun testConstructorCallsCountAsCallees() {
        prod("demo/Thing.java", "package demo;\npublic class Thing { public Thing() {} }\n")
        prod("demo/Maker.java", "package demo;\npublic class Maker {\n    public void make() {\n        new Thing();\n    }\n}\n")

        val radius = radiusOf("demo/Maker.java", "make")

        assertEquals(listOf("Thing.Thing()"), radius.callees.map { it.displayName })
    }

    fun testRepeatedCallsToOneMethodAreReportedOnce() {
        prod("demo/Once.java", "package demo;\npublic class Once { public void target() {} }\n")
        prod(
            "demo/Repeater.java",
            "package demo;\npublic class Repeater {\n    public void go() {\n        new Once().target();\n        new Once().target();\n        new Once().target();\n    }\n}\n",
        )

        val radius = radiusOf("demo/Repeater.java", "go")

        assertEquals(1, radius.callees.count { it.displayName == "Once.target()" })
    }

    fun testAMethodThatCallsNothingHasNoCallees() {
        prod("demo/Leaf.java", "package demo;\npublic class Leaf { public void leaf() {} }\n")

        assertEmpty(radiusOf("demo/Leaf.java", "leaf").callees)
    }

    fun testCalleesAreSortedByLocationForStableDisplay() {
        prod("demo/A.java", "package demo;\npublic class A { public void a() {} }\n")
        prod("demo/Z.java", "package demo;\npublic class Z { public void z() {} }\n")
        prod("demo/S.java", "package demo;\npublic class S {\n    public void s() {\n        new Z().z();\n        new A().a();\n    }\n}\n")

        // Called Z then A in source order; sorted output is by file, so A precedes Z.
        assertEquals(listOf("A.a()", "Z.z()"), radiusOf("demo/S.java", "s").callees.map { it.displayName })
    }

    // --- endpoints: where the method is live ----------------------------------------------------

    fun testMainIsFoundAsAnEndpointAboveTheMethod() {
        prod("demo/App.java", "package demo;\npublic class App {\n    public static void main(String[] args) {\n        new Worker().work();\n    }\n}\n")
        prod("demo/Worker.java", "package demo;\npublic class Worker { public void work() {} }\n")

        val radius = radiusOf("demo/Worker.java", "work")

        assertEquals(listOf(EndpointKind.MAIN), radius.endpoints.map { it.kind })
        assertEquals("App.main()", radius.endpoints.single().site.displayName)
    }

    fun testActionHandlerIsFoundAsAnEndpoint() {
        prod(
            "demo/Share.java",
            "package demo;\nimport com.intellij.openapi.actionSystem.AnAction;\nimport com.intellij.openapi.actionSystem.AnActionEvent;\npublic class Share extends AnAction {\n    @Override public void actionPerformed(AnActionEvent e) {\n        new Session().start();\n    }\n}\n",
        )
        prod("demo/Session.java", "package demo;\npublic class Session { public void start() {} }\n")

        val radius = radiusOf("demo/Session.java", "start")

        assertEquals(listOf(EndpointKind.ACTION), radius.endpoints.map { it.kind })
    }

    fun testHttpHandlerIsFoundByAnnotation() {
        prod(
            "demo/Controller.java",
            "package demo;\nimport org.springframework.web.bind.annotation.GetMapping;\npublic class Controller {\n    @GetMapping(\"/x\")\n    public String handle() {\n        return new Service().load();\n    }\n}\n",
        )
        prod("demo/Service.java", "package demo;\npublic class Service { public String load() { return \"\"; } }\n")

        val radius = radiusOf("demo/Service.java", "load")

        assertEquals(listOf(EndpointKind.HTTP), radius.endpoints.map { it.kind })
    }

    fun testAClassLevelMappingMakesItsPublicMethodsEndpoints() {
        prod("demo/Repo.java", "package demo;\npublic class Repo { public String get() { return \"\"; } }\n")
        prod(
            "demo/Handlers.java",
            "package demo;\nimport org.springframework.web.bind.annotation.RequestMapping;\n@RequestMapping(\"/api\")\npublic class Handlers {\n    public String list() { return new Repo().get(); }\n    private String hidden() { return new Repo().get(); }\n}\n",
        )

        val radius = radiusOf("demo/Repo.java", "get")

        // The class-level mapping covers the public methods, which is how Spring treats it — and not
        // the private one.
        assertEquals(listOf("Handlers.list()"), radius.endpoints.map { it.site.displayName })
    }

    fun testATestReachingTheMethodIsAnEndpoint() {
        prod("demo/Target.java", "package demo;\npublic class Target { public void go() {} }\n")
        test(
            "demo/TargetTest.java",
            "package demo;\nimport org.junit.Test;\npublic class TargetTest {\n    @Test public void shouldGo() { new Target().go(); }\n}\n",
        )

        val radius = radiusOf("demo/Target.java", "go")

        assertEquals(listOf(EndpointKind.TEST), radius.endpoints.map { it.kind })
        // displayName is the owning class's simple name, not its FQN.
        assertEquals("TargetTest.shouldGo()", radius.endpoints.single().site.displayName)
    }

    fun testTheNearestEndpointIsFoundThroughAnIntermediateCaller() {
        prod("demo/Leaf2.java", "package demo;\npublic class Leaf2 { public void leaf() {} }\n")
        prod("demo/Mid2.java", "package demo;\npublic class Mid2 { public void mid() { new Leaf2().leaf(); } }\n")
        prod(
            "demo/Main2.java",
            "package demo;\npublic class Main2 {\n    public static void main(String[] args) { new Mid2().mid(); }\n}\n",
        )

        val radius = radiusOf("demo/Leaf2.java", "leaf")

        assertEquals(listOf(EndpointKind.MAIN), radius.endpoints.map { it.kind })
        assertEquals("Main2.main()", radius.endpoints.single().site.displayName)
    }

    fun testAMethodNothingEntersHasNoEndpoints() {
        prod("demo/Float.java", "package demo;\npublic class Float { public void floats() {} }\n")

        assertEmpty(radiusOf("demo/Float.java", "floats").endpoints)
    }

    fun testACyclicCallGraphTerminatesWithoutAnEndpoint() {
        prod("demo/C1.java", "package demo;\npublic class C1 { public void one() { new C2().two(); } }\n")
        prod("demo/C2.java", "package demo;\npublic class C2 { public void two() { new C1().one(); } }\n")

        // Would spin forever without the visited set.
        assertEmpty(radiusOf("demo/C1.java", "one").endpoints)
    }

    fun testTheClimbStopsAtAnEntryPointRatherThanGoingAboveIt() {
        prod("demo/Upper.java", "package demo;\npublic class Upper { public static void main(String[] args) { new Mid3().mid(); } }\n")
        prod("demo/Mid3.java", "package demo;\npublic class Mid3 { public void mid() { new Low3().low(); } }\n")
        prod("demo/Low3.java", "package demo;\npublic class Low3 { public void low() {} }\n")

        val radius = radiusOf("demo/Low3.java", "low")

        // main is a root: recorded once, with nothing above it walked.
        assertEquals(1, radius.endpoints.size)
        assertEquals(EndpointKind.MAIN, radius.endpoints.single().kind)
    }

    fun testRemovedMethodWithNoPointerGivesAnEmptyRadius() {
        prod("demo/X.java", "package demo;\npublic class X { public void x() {} }\n")
        val method = productionMethod("demo/X.java", "x")
        val removed = method.copy(pointer = null)

        val radius = BlastRadiusResolver.resolve(project, removed)

        assertTrue(radius.isEmpty)
    }

    // --- the panel's lookup shape ---------------------------------------------------------------

    fun testResolveByListIsKeyedById() {
        prod("demo/K.java", "package demo;\npublic class K { public void k() { new Dep4().d(); } }\n")
        prod("demo/Dep4.java", "package demo;\npublic class Dep4 { public void d() {} }\n")
        val method = productionMethod("demo/K.java", "k")

        val byId = BlastRadiusResolver.resolve(project, listOf(method))

        assertEquals(setOf(method.id), byId.keys)
        assertEquals(listOf("Dep4.d()"), byId.getValue(method.id).callees.map { it.displayName })
    }

    fun testEveryEndpointKindHasADisplayLabel() {
        // The panel renders these, so a new kind without a label would show an enum name on screen.
        assertEquals(listOf("main", "action", "http", "test"), EndpointKind.entries.map { it.label })
    }

    fun testCallSitesComeFromA3RatherThanBeingRecomputed() {
        prod("demo/Cal.java", "package demo;\npublic class Cal { public void target() {} }\n")
        prod("demo/User.java", "package demo;\npublic class User { public void use() { new Cal().target(); } }\n")
        val analysed = ImpactGraph.populate(project, listOf(productionMethod("demo/Cal.java", "target"))).single()

        val radius = BlastRadiusResolver.resolve(project, analysed)

        assertEquals(analysed.callSites, radius.callSites)
    }

    // --- helpers --------------------------------------------------------------------------------

    private fun radiusOf(path: String, name: String): BlastRadius =
        BlastRadiusResolver.resolve(project, productionMethod(path, name))

    private fun prod(path: String, text: String) {
        myFixture.addFileToProject("$MAIN_ROOT/$path", text)
        PsiManager.getInstance(project).dropResolveCaches()
    }

    private fun test(path: String, text: String) {
        myFixture.addFileToProject("$TEST_ROOT/$path", text)
        PsiManager.getInstance(project).dropResolveCaches()
    }

    private fun productionMethod(path: String, name: String): ChangedMethod {
        val file: VirtualFile = myFixture.findFileInTempDir("$MAIN_ROOT/$path") ?: myFixture.findFileInTempDir("$TEST_ROOT/$path")!!
        val psi = PsiManager.getInstance(project).findFile(file)!!
        val method: PsiMethod = PsiTreeUtil.findChildrenOfType(psi, PsiMethod::class.java).first { it.name == name }
        return ChangedMethod(
            id = "modified:$path#$name",
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

        /** Dotted, for a `package` declaration — not the same thing as the directory path below. */
        const val SPRING_PKG = "org.springframework.web.bind.annotation"
        const val SPRING_PATH = "org/springframework/web/bind/annotation"
    }
}
