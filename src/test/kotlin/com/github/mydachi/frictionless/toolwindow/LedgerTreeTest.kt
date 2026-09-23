package com.github.mydachi.frictionless.toolwindow

import com.github.mydachi.frictionless.FrictionlessFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.swing.SwingUtilities

/**
 * The selection behaviour the Autopilot tour depends on: as the editor moves to a stop, the ledger
 * row for that method highlights, so the panel and the editor tell the same story.
 *
 * Everything runs on the EDT because the platform's `Tree` asserts it — which is also why the panel
 * drives these calls from inside `invokeLater`.
 */
class LedgerTreeTest {

    private fun onEdt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)

    @Test
    fun `every bucket is expanded, so no method is hidden`() = onEdt {
        val tree = LedgerTree { }

        tree.show(FrictionlessFixtures.changeSet())

        // Three buckets and the three methods under them. The expand loop used to read rowCount once,
        // so expanding the first bucket pushed the rest down and only that one ever opened - the
        // ledger showed three coloured circles with nothing beneath them.
        assertEquals(6, tree.rowCount)
    }

    @Test
    fun `selects the row for a method the tour has moved to`() = onEdt {
        val tree = LedgerTree { }
        tree.show(FrictionlessFixtures.changeSet())

        tree.select(FrictionlessFixtures.unverified())

        assertEquals(FrictionlessFixtures.unverified().id, tree.selected()?.id)
    }

    @Test
    fun `selecting a method that is not in the ledger leaves the selection alone`() = onEdt {
        val tree = LedgerTree { }
        tree.show(FrictionlessFixtures.changeSet())

        tree.select(FrictionlessFixtures.unverified().copy(id = "not-in-this-ledger"))

        assertNull(tree.selected())
    }
}
