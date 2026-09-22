package com.github.mydachi.frictionless.agent

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Deliverable G4: put the generated test where the build will actually compile it.
 *
 * Source root first, package directory second. Getting either wrong means the run configuration
 * finds nothing and the agent reports a failure that is really our filing mistake.
 */
class ProjectTestWriter(
    private val project: Project,
    /** Kotlin or Java, taken from the file the changed method lives in rather than guessed. */
    private val kotlin: Boolean,
) : TestWriter {

    override fun write(test: GeneratedTest): Boolean = try {
        WriteCommandAction.writeCommandAction(project).compute<Boolean, Exception> {
            val root = testSourceRoot() ?: return@compute false
            val directory = packageDirectory(root, test.packageName) ?: return@compute false
            val name = "${test.className}.${if (kotlin) "kt" else "java"}"

            val existing = directory.findChild(name)
            val file = existing ?: directory.createChildData(this, name)
            VfsUtil.saveText(file, test.source)
            true
        }
    } catch (e: Exception) {
        thisLogger().warn("Could not write ${test.qualifiedName}", e)
        false
    }

    /**
     * A test source root, preferring one whose path mentions the generated test's language, so a
     * Kotlin test lands in `src/test/kotlin` rather than `src/test/java` in a project that has both.
     */
    internal fun testSourceRoot(): VirtualFile? {
        val roots = ProjectRootManager.getInstance(project).contentSourceRoots
            .filter { it.path.contains("/test") }
        if (roots.isEmpty()) return null
        val wanted = if (kotlin) "kotlin" else "java"
        return roots.firstOrNull { it.path.endsWith(wanted) } ?: roots.first()
    }

    private fun packageDirectory(root: VirtualFile, packageName: String): VirtualFile? {
        if (packageName.isEmpty()) return root
        return WriteAction.compute<VirtualFile?, Exception> {
            VfsUtil.createDirectoryIfMissing(root, packageName.replace('.', '/'))
        }
    }
}
