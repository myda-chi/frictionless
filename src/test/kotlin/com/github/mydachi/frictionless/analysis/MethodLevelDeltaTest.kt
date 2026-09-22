package com.github.mydachi.frictionless.analysis

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangeSource
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * A2 acceptance: base blob and head parsed as `PsiFile`s, compared at method level, emitting
 * `ChangedMethod`s, with added / removed / modified all handled.
 */
class MethodLevelDeltaTest : BasePlatformTestCase() {

    fun testAddedRemovedModifiedAndUnchangedAreSeparated() {
        val head = javaFile(
            """
            package demo;
            public class Service {
                public int keep() { return 1; }
                public int change() { return 20; }
                public int added() { return 4; }
            }
            """.trimIndent(),
        )
        val base = """
            package demo;
            public class Service {
                public int keep() { return 1; }
                public int change() { return 2; }
                public int remove() { return 3; }
            }
        """.trimIndent()

        val reported = MethodLevelDelta.delta(project, changed(head, base))
        val byChange = reported.groupBy { it.change }

        assertEquals(
            listOf("Service.added()"),
            byChange[MethodChange.ADDED].orEmpty().map { it.method.displayName },
        )
        assertEquals(
            listOf("Service.remove()"),
            byChange[MethodChange.REMOVED].orEmpty().map { it.method.displayName },
        )
        assertEquals(
            listOf("Service.change()"),
            byChange[MethodChange.MODIFIED].orEmpty().map { it.method.displayName },
        )
        assertTrue(
            "keep() is identical on both sides and must not be reported",
            reported.none { it.method.displayName == "Service.keep()" },
        )
    }

    fun testNewFileReportsEveryMethodAsAdded() {
        val head = javaFile("package demo;\npublic class Fresh {\n    public int one() { return 1; }\n}\n")

        // A1 gives a brand-new file no base revision to diff against.
        val deltas = MethodLevelDelta.delta(project, changed(head, base = null, kind = ChangedFileKind.ADDED))

        assertEquals(listOf(MethodChange.ADDED), deltas.map { it.change }.distinct())
        assertEquals(listOf("Fresh.one()"), deltas.map { it.method.displayName })
    }

    fun testDeletedFileReportsEveryMethodAsRemoved() {
        val base = "package demo;\npublic class Gone {\n    public int one() { return 1; }\n    public int two() { return 2; }\n}\n"
        val deleted = ChangedFile(
            path = "src/main/java/demo/Gone.java",
            kind = ChangedFileKind.DELETED,
            status = null,
            file = null, // nothing on disk left to parse — the path drives the file type
            baseRevision = BaseRevision(base),
        )

        val deltas = MethodLevelDelta.delta(project, deleted)

        assertEquals(listOf(MethodChange.REMOVED, MethodChange.REMOVED), deltas.map { it.change })
        assertEquals(setOf("Gone.one()", "Gone.two()"), deltas.map { it.method.displayName }.toSet())
        assertTrue("a removed method has nothing left to navigate to", deltas.all { it.method.pointer == null })
    }

    fun testOverloadsAreDistinctMethods() {
        val head = javaFile(
            """
            package demo;
            public class Over {
                public int pick(int a) { return a; }
                public int pick(String a) { return a.length(); }
                public int pick(int a, int b) { return a + b; }
            }
            """.trimIndent(),
        )
        val base = "package demo;\npublic class Over {\n    public int pick(int a) { return a; }\n}\n"

        val added = MethodLevelDelta.delta(project, changed(head, base))
            .filter { it.change == MethodChange.ADDED }

        // The signature, not the name: both overloads are named pick and they must stay distinct.
        // The owner is part of the signature (issue #58), so two same-named methods in different
        // classes in one file cannot collide and silently drop one.
        assertEquals(
            setOf("Over.pick(String)", "Over.pick(int, int)"),
            added.map { it.method.id.substringAfterLast('#') }.toSet(),
        )
    }

    fun testSignatureMatchingKeepsARenamedParameterPaired() {
        // A renamed parameter is still the same method: it must not read as a removal plus an
        // addition. The declaration text changed, so it is correctly reported as modified.
        val head = javaFile("package demo;\npublic class P {\n    public int go(int renamed) { return 1; }\n}\n")
        val base = "package demo;\npublic class P {\n    public int go(int original) { return 1; }\n}\n"

        val deltas = MethodLevelDelta.delta(project, changed(head, base))

        assertEquals(MethodChange.MODIFIED, deltas.single().change)
    }

    fun testLineNumbersAreOneBasedAndPerSide() {
        val head = javaFile(
            """
            package demo;
            public class Lines {
                public int first() { return 1; }
                public int second() { return 20; }
            }
            """.trimIndent(),
        )
        // Same two methods on both sides, so the only delta is second()'s new body.
        val base = """
            package demo;
            public class Lines {
                public int first() { return 1; }
                public int second() { return 2; }
            }
        """.trimIndent()

        val delta = MethodLevelDelta.delta(project, changed(head, base)).single()

        assertEquals(MethodChange.MODIFIED, delta.change)
        assertEquals("Lines.second()", delta.method.displayName)
        assertEquals(4, delta.method.line) // the 1-based line in the head file
        assertEquals("src/main/java/demo/Lines.java", delta.method.filePath)
    }

    fun testEmittedMethodsCarryTheEmptyVerdictAndAnIdEncodingTheKind() {
        val head = javaFile("package demo;\npublic class V {\n    public int got() { return 1; }\n}\n")

        val only = MethodLevelDelta.delta(project, changed(head, base = null, kind = ChangedFileKind.ADDED)).single()

        // A3/A4 have not run, so nothing is claimed yet. UNVERIFIED is the contract's only
        // not-yet-decided value, and the counts are empty rather than invented.
        assertEquals(Bucket.UNVERIFIED, only.method.verdict.bucket)
        assertEquals(0, only.method.verdict.counts.reachingTests)
        assertEquals(0, only.method.verdict.counts.callSites)
        assertNotNull("an added method is navigable in the working tree", only.method.pointer)
        assertTrue(only.method.id.startsWith("added:"))
    }

    fun testChangedMethodsPipelineFeedsTheFrozenChangeSet() {
        val head = javaFile("package demo;\npublic class Pipe {\n    public int a() { return 1; }\n}\n")

        val changeSet = MethodLevelDelta.changeSet(
            project,
            ChangeSource.WorkingTree,
            listOf(changed(head, base = null, kind = ChangedFileKind.ADDED)),
        )

        assertEquals(ChangeSource.WorkingTree, changeSet.source)
        assertEquals(1, changeSet.methods.size)
        assertEquals("Pipe.a()", changeSet.methods.single().displayName)
    }

    fun testSignatureUsesParameterTypesNotParameterNames() {
        val psi = javaFile("package demo;\npublic class S {\n    public void go(int a, String b) { }\n}\n")
        val method = MethodLevelDelta.methodsOf(PsiManager.getInstance(project).findFile(psi)!!).values.single()

        val signature = MethodLevelDelta.signature(method)

        // Parameter types in order, and never the parameter names — that is what keeps a rename from
        // reading as a delete plus an add. Asserted structurally rather than against a canonical text,
        // which varies with whether the fixture has an SDK configured.
        assertTrue(signature, signature.startsWith("S.go("))
        assertTrue(signature, signature.contains("int"))
        assertTrue(signature, signature.contains("String"))
        assertFalse("parameter names leaked into the signature: $signature", signature.contains(" a") || signature.contains(" b"))
    }

    fun testKotlinFunctionsAreComparedToo() {
        // Issue #56: the analyser collected PsiMethod only, so a Kotlin file contributed nothing and
        // the plugin could not be run on itself.
        val head = kotlinFile(
            "package demo\n\nclass Fx {\n    fun applyMargin(amount: Int): Int = amount * 2\n" +
                "    fun untouched(): Int = 1\n}\n",
        )
        val base = "package demo\n\nclass Fx {\n    fun applyMargin(amount: Int): Int = amount\n" +
            "    fun untouched(): Int = 1\n}\n"

        val deltas = MethodLevelDelta.delta(project, changed(head, base))

        assertEquals(1, deltas.size)
        assertEquals(MethodChange.MODIFIED, deltas.single().change)
        assertEquals("Fx.applyMargin()", deltas.single().method.displayName)
    }

    fun testKotlinTopLevelFunctionHasNoOwnerInItsName() {
        val head = kotlinFile("package demo\n\nfun helper(): Int = 2\n")
        val base = "package demo\n\nfun helper(): Int = 1\n"

        val deltas = MethodLevelDelta.delta(project, changed(head, base))

        assertEquals("helper()", deltas.single().method.displayName)
    }

    private var kotlinFileCounter = 0

    private fun kotlinFile(text: String): VirtualFile =
        myFixture.addFileToProject("src/main/kotlin/demo/Fixture${kotlinFileCounter++}.kt", text).virtualFile

    private fun javaFile(text: String): VirtualFile =
        myFixture.addFileToProject("src/main/java/demo/${nameOf(text)}", text).virtualFile

    /** The project file name is incidental; a single name keeps `addFileToProject` from colliding. */
    private fun nameOf(text: String) = text.lineSequence()
        .first { it.startsWith("public class ") }
        .removePrefix("public class ")
        .substringBefore(" {")
        .trim() + ".java"

    private fun changed(head: VirtualFile, base: String?, kind: ChangedFileKind = ChangedFileKind.MODIFIED) =
        ChangedFile(
            path = "src/main/java/demo/${head.name}",
            kind = kind,
            status = null,
            file = head,
            baseRevision = base?.let { BaseRevision(it) },
        )
}
