package com.github.mydachi.frictionless.agent

import com.github.mydachi.frictionless.model.ChangedMethod

/**
 * Deliverable G4: the test we can write without a model.
 *
 * When the model call fails — no key, no network, the venue Wi-Fi at the wrong moment — the agent
 * still places a real file in the right package, so the gap is recorded in the codebase rather than
 * lost when the IDE closes.
 *
 * **It is deliberately disabled, and that is a correctness decision, not politeness.** An empty test
 * that passes would promote its method to Proven, and Proven is the one claim this product must
 * never make falsely. A disabled test is reported as skipped, and a skipped test is not a pass
 * (issue #55) — so the method correctly stays Unverified, with a named, navigable placeholder
 * sitting next to it.
 *
 * **It has to compile.** A placeholder that does not is worse than none: it breaks the build of the
 * project it was meant to help, which is why the framework is read from the repository's own tests
 * rather than assumed.
 */
object TemplateTest {

    fun forMethod(
        method: ChangedMethod,
        packageName: String,
        kotlin: Boolean,
        junit5: Boolean = true,
    ): GeneratedTest {
        val className = classNameFor(method)
        val source = when {
            kotlin -> kotlinSource(method, packageName, className, junit5)
            else -> javaSource(method, packageName, className, junit5)
        }
        return GeneratedTest(className = className, packageName = packageName, source = source)
    }

    /**
     * Which JUnit the repository actually uses, read from the tests it already has.
     *
     * Guessing here writes a file that does not compile: the first version hard-coded JUnit 4's
     * `org.junit.Ignore` and landed in a Jupiter project, so the placeholder broke the build of the
     * project it was supposed to be helping. JUnit 5 is the default only when there is nothing to
     * read.
     */
    fun usesJUnit5(conventionExamples: List<String>): Boolean {
        val joined = conventionExamples.joinToString("\n")
        if (joined.contains("org.junit.jupiter")) return true
        if (joined.contains("org.junit.Test") || joined.contains("org.junit.Assert")) return false
        return true
    }

    internal fun classNameFor(method: ChangedMethod): String {
        val member = method.displayName.substringAfterLast('.').removeSuffix("()")
        return member.replaceFirstChar { it.uppercase() } + "CharacterisationTest"
    }

    private fun javaSource(
        method: ChangedMethod,
        packageName: String,
        className: String,
        junit5: Boolean,
    ): String = buildString {
        if (packageName.isNotEmpty()) appendLine("package $packageName;").appendLine()
        if (junit5) {
            appendLine("import org.junit.jupiter.api.Disabled;")
            appendLine("import org.junit.jupiter.api.Test;")
        } else {
            appendLine("import org.junit.Ignore;")
            appendLine("import org.junit.Test;")
        }
        appendLine()
        appendLine("/** Behaviour of ${method.displayName} is not pinned yet. */")
        appendLine("public class $className {")
        appendLine()
        appendLine("    @${disabled(junit5)}(\"TODO: pin the current behaviour of ${method.displayName}\")")
        appendLine("    @Test")
        appendLine("    public void pinsCurrentBehaviour() {")
        appendLine("        // ${method.filePath}:${method.line}")
        appendLine("    }")
        appendLine("}")
    }

    private fun kotlinSource(
        method: ChangedMethod,
        packageName: String,
        className: String,
        junit5: Boolean,
    ): String = buildString {
        if (packageName.isNotEmpty()) appendLine("package $packageName").appendLine()
        if (junit5) {
            appendLine("import org.junit.jupiter.api.Disabled")
            appendLine("import org.junit.jupiter.api.Test")
        } else {
            appendLine("import org.junit.Ignore")
            appendLine("import org.junit.Test")
        }
        appendLine()
        appendLine("/** Behaviour of ${method.displayName} is not pinned yet. */")
        appendLine("class $className {")
        appendLine()
        appendLine("    @${disabled(junit5)}(\"TODO: pin the current behaviour of ${method.displayName}\")")
        appendLine("    @Test")
        appendLine("    fun `pins current behaviour`() {")
        appendLine("        // ${method.filePath}:${method.line}")
        appendLine("    }")
        appendLine("}")
    }

    private fun disabled(junit5: Boolean) = if (junit5) "Disabled" else "Ignore"
}
