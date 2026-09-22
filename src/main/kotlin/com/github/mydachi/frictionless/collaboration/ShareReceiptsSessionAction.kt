package com.github.mydachi.frictionless.collaboration

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.DataFlavor

private const val CODE_WITH_ME_PLUGIN_ID = "com.jetbrains.codeWithMe"
private const val NOTIFICATION_GROUP_ID = "Receipts"

private val ID_MARKERS = listOf("codewithme", "cwm", "rdserver")
private val START_NEGATIVE_MARKERS =
    listOf("stop", "join", "settings", "config", "leave", "disable", "enable", "toggle")
private val START_POSITIVE_MARKERS = listOf("share", "start")

/**
 * Code With Me has no public API to start a session or read its invite link, and its internal
 * action IDs are undocumented and not guaranteed stable across versions. Rather than hardcode a
 * guessed ID that silently breaks on the next release, this discovers the right actions at
 * runtime by matching on their id/text, and fails with a visible notification instead of
 * pretending to succeed if nothing matches.
 */
class ShareReceiptsSessionAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        if (!isCodeWithMeEnabled()) {
            notify(
                project,
                "Code With Me is not installed or enabled. Install it from Settings | Plugins to share a Receipts session.",
                NotificationType.WARNING,
            )
            return
        }

        val actionManager = ActionManager.getInstance()
        val candidates = codeWithMeActionCandidates(actionManager)

        val startId = candidates.entries.firstOrNull { (id, text) -> looksLikeStartAction(id, text) }?.key
        if (startId == null) {
            notify(
                project,
                "Couldn't find the Code With Me \"start session\" action - it may have moved in a newer version.",
                NotificationType.ERROR,
            )
            return
        }
        performAction(actionManager.getAction(startId), e)

        val copyLinkId = candidates.entries.firstOrNull { (id, text) -> looksLikeCopyLinkAction(id, text) }?.key
        if (copyLinkId == null) {
            notify(
                project,
                "Code With Me session started, but the invite-link action couldn't be found. Copy the link manually from the Code With Me widget.",
                NotificationType.WARNING,
            )
            return
        }
        performAction(actionManager.getAction(copyLinkId), e)

        val link = readClipboardLink()
        val message = if (link != null) {
            "Code With Me session started. Invite link copied to clipboard:\n$link"
        } else {
            "Code With Me session started and the invite link was copied to the clipboard."
        }
        notify(project, message, NotificationType.INFORMATION)
    }

    private fun performAction(action: AnAction?, source: AnActionEvent) {
        if (action == null) return
        val event = AnActionEvent.createFromAnAction(action, source.inputEvent, source.place, source.dataContext)
        ActionUtil.performAction(action, event)
    }

    private fun isCodeWithMeEnabled(): Boolean {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(CODE_WITH_ME_PLUGIN_ID)) ?: return false
        return plugin.isEnabled
    }

    private fun readClipboardLink(): String? {
        val contents = CopyPasteManager.getInstance().contents ?: return null
        if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) return null
        val text = contents.getTransferData(DataFlavor.stringFlavor) as? String ?: return null
        return text.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun notify(project: Project, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(message, type)
            .notify(project)
    }
}

internal fun codeWithMeActionCandidates(actionManager: ActionManager): Map<String, String?> =
    actionManager.getActionIdList("")
        .filter { id -> ID_MARKERS.any { marker -> id.contains(marker, ignoreCase = true) } }
        .associateWith { id -> actionManager.getAction(id)?.templateText }

internal fun looksLikeStartAction(id: String, text: String?): Boolean {
    val lowerId = id.lowercase()
    val lowerText = text.orEmpty().lowercase()
    if (START_NEGATIVE_MARKERS.any { lowerId.contains(it) || lowerText.contains(it) }) return false
    return START_POSITIVE_MARKERS.any { lowerId.contains(it) || lowerText.contains(it) }
}

internal fun looksLikeCopyLinkAction(id: String, text: String?): Boolean {
    val lowerId = id.lowercase()
    val lowerText = text.orEmpty().lowercase()
    val hasCopy = lowerId.contains("copy") || lowerText.contains("copy")
    val hasLink = lowerId.contains("link") || lowerText.contains("link") || lowerText.contains("invit")
    return hasCopy && hasLink
}
