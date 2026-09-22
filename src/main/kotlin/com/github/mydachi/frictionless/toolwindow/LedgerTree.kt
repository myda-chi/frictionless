package com.github.mydachi.frictionless.toolwindow

import com.github.mydachi.frictionless.model.Bucket
import com.github.mydachi.frictionless.model.ChangeSet
import com.github.mydachi.frictionless.model.ChangedMethod
import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.treeStructure.Tree
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

/**
 * Deliverable U2: the ledger rows, grouped by bucket, riskiest first.
 *
 * Deliberately shows the empty buckets too — "0 unverified" is information, and a bucket that
 * silently disappears reads as a bug.
 */
class LedgerTree(private val onActivate: (ChangedMethod) -> Unit) : Tree(DefaultTreeModel(DefaultMutableTreeNode())) {

    init {
        isRootVisible = false
        showsRootHandles = true
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = Renderer()

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1) selected()?.let(onActivate)
            }
        })
        // U8: the tree is keyboard-navigable, Enter opens the selected method.
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) selected()?.let(onActivate)
            }
        })
    }

    fun selected(): ChangedMethod? =
        (lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? ChangedMethod

    fun show(changeSet: ChangeSet) {
        val root = DefaultMutableTreeNode()
        val byBucket = changeSet.methods.groupBy { it.verdict.bucket }
        ORDER.forEach { bucket ->
            val methods = byBucket[bucket].orEmpty()
            val node = DefaultMutableTreeNode(BucketNode(bucket, methods.size))
            methods.forEach { node.add(DefaultMutableTreeNode(it)) }
            root.add(node)
        }
        model = DefaultTreeModel(root)
        for (row in 0 until rowCount) expandRow(row)
    }

    data class BucketNode(val bucket: Bucket, val count: Int)

    private class Renderer : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean,
        ) {
            when (val payload = (value as? DefaultMutableTreeNode)?.userObject) {
                is BucketNode -> {
                    icon = iconFor(payload.bucket)
                    append(label(payload.bucket), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    append("  ${payload.count}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                is ChangedMethod -> {
                    icon = iconFor(payload.verdict.bucket)
                    append(payload.displayName)
                    append("   ${payload.verdict.display()}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }

        private fun iconFor(bucket: Bucket) = when (bucket) {
            Bucket.PROVEN -> AllIcons.RunConfigurations.TestPassed
            Bucket.BEHAVIOUR_CHANGED -> AllIcons.RunConfigurations.TestFailed
            Bucket.UNVERIFIED -> AllIcons.General.Warning
        }

        private fun label(bucket: Bucket) = when (bucket) {
            Bucket.PROVEN -> "Proven"
            Bucket.BEHAVIOUR_CHANGED -> "Behaviour changed"
            Bucket.UNVERIFIED -> "Unverified"
        }
    }

    companion object {
        /** Riskiest first — the unverified bucket is the product, so it reads first. */
        private val ORDER = listOf(Bucket.UNVERIFIED, Bucket.BEHAVIOUR_CHANGED, Bucket.PROVEN)
    }
}
