package com.github.mydachi.frictionless.agent

/** A test the model produced, parsed into the pieces the writer and the runner need. */
data class GeneratedTest(
    val className: String,
    val packageName: String,
    val source: String,
) {
    val qualifiedName: String get() = if (packageName.isEmpty()) className else "$packageName.$className"

    companion object {

        private val FENCE = Regex("^\\s*```[a-zA-Z]*\\s*\\n|\\n\\s*```\\s*$")
        private val PACKAGE = Regex("^\\s*package\\s+([\\w.]+)", RegexOption.MULTILINE)
        private val CLASS = Regex("(?:class|object)\\s+(\\w+)")

        /**
         * Models fence their output no matter how firmly you ask them not to, so strip it rather
         * than failing the attempt over formatting.
         */
        fun parse(raw: String): GeneratedTest? {
            val source = raw.replace(FENCE, "").trim()
            if (source.isEmpty()) return null
            val className = CLASS.find(source)?.groupValues?.get(1) ?: return null
            return GeneratedTest(
                className = className,
                packageName = PACKAGE.find(source)?.groupValues?.get(1).orEmpty(),
                source = source,
            )
        }
    }
}

/** Where a generated test ends up. The real placement is G4; the loop only needs the seam. */
interface TestWriter {
    /** Returns true when the test is on disk and runnable. */
    fun write(test: GeneratedTest): Boolean
}
