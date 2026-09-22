package com.github.mydachi.frictionless.analysis

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.vcsUtil.VcsUtil
import java.io.File

/**
 * The base side of a change, holding the content A1 carries out of the VCS [com.intellij.openapi.vcs.changes.Change]
 * and A2 parses as the base `PsiFile`.
 *
 * The platform's own `SimpleContentRevision` lives in `intellij.platform.vcs.impl.jar`, which is not
 * on a plugin's compile classpath — so this is five lines we own, shared by the tests that need a base
 * revision rather than each declaring its own.
 */
internal class BaseRevision(
    private val content: String,
    // Under /tmp on purpose: the test framework forbids VFS access outside a fixed set of roots, and
    // /dev/null is not one of them. Nothing under test reads this path — only `content`, and A2 diffs
    // the content. Every `getFilePath` overload that does not take a `VirtualFile` is deprecated in
    // 2025.2, and a deleted file has no `VirtualFile` to hand it, so the deprecation is suppressed
    // here rather than routed around. This is a test double, not production plumbing.
    @Suppress("DEPRECATION")
    private val file: FilePath = VcsUtil.getFilePath(File("/tmp/frictionless-baseline.java")),
) : ContentRevision {
    override fun getContent(): String = content
    override fun getFile(): FilePath = file
    override fun getRevisionNumber(): VcsRevisionNumber = VcsRevisionNumber.NULL
}
