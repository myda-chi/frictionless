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
 */
object TemplateTest {

    fun forMethod(method: ChangedMethod, packageName: String, kotlin: Boolean): GeneratedTest {
        val className = classNameFor(method)
        return GeneratedTest(
            className = className,
            packageName = packageName,
            source = if (kotlin) kotlinSource(method, packageName, className) else javaSource(method, packageName, className),
        )
    }

    internal fun classNameFor(method: ChangedMethod): String {
        val member = method.displayName.substringAfterLast('.').removeSuffix("()")
        return member.replaceFirstChar { it.uppercase() } + "CharacterisationTest"
    }

    private fun javaSource(method: ChangedMethod, packageName: String, className: String): String = buildString {
        if (packageName.isNotEmpty()) appendLine("package $packageName;").appendLine()
        appendLine("import org.junit.Ignore;")
        appendLine("import org.junit.Test;")
        appendLine()
        appendLine("/** Behaviour of ${method.displayName} is not pinned yet. */")
        appendLine("public class $className {")
        appendLine()
        appendLine("    @Ignore(\"TODO: pin the current behaviour of ${method.displayName}\")")
        appendLine("    @Test")
        appendLine("    public void pinsCurrentBehaviour() {")
        appendLine("        // ${method.filePath}:${method.line}")
        appendLine("    }")
        appendLine("}")
    }

    private fun kotlinSource(method: ChangedMethod, packageName: String, className: String): String = buildString {
        if (packageName.isNotEmpty()) appendLine("package $packageName").appendLine()
        appendLine("import org.junit.Ignore")
        appendLine("import org.junit.Test")
        appendLine()
        appendLine("/** Behaviour of ${method.displayName} is not pinned yet. */")
        appendLine("class $className {")
        appendLine()
        appendLine("    @Ignore(\"TODO: pin the current behaviour of ${method.displayName}\")")
        appendLine("    @Test")
        appendLine("    fun `pins current behaviour`() {")
        appendLine("        // ${method.filePath}:${method.line}")
        appendLine("    }")
        appendLine("}")
    }
}
