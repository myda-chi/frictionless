package com.github.mydachi.frictionless.actions

import junit.framework.TestCase

class ShareCodeWithMeSessionTest : TestCase() {

    fun testLooksLikeStartActionMatchesShareOrStart() {
        assertTrue(looksLikeStartAction("CodeWithMe.ShareProject", "Start Code With Me..."))
        assertTrue(looksLikeStartAction("cwm.session.share", null))
    }

    fun testLooksLikeStartActionRejectsUnrelatedLifecycleActions() {
        assertFalse(looksLikeStartAction("CodeWithMe.StopSession", "Stop Sharing"))
        assertFalse(looksLikeStartAction("CodeWithMe.JoinSession", "Join a Session"))
        assertFalse(looksLikeStartAction("CodeWithMe.Settings", "Code With Me Settings"))
    }

    fun testLooksLikeCopyLinkActionRequiresBothCopyAndLink() {
        assertTrue(looksLikeCopyLinkAction("CodeWithMe.CopyInviteLink", null))
        assertTrue(looksLikeCopyLinkAction("cwm.copy", "Copy Invitation Link"))
        assertFalse(looksLikeCopyLinkAction("CodeWithMe.Copy", "Copy Something Unrelated"))
        assertFalse(looksLikeCopyLinkAction("CodeWithMe.OpenLink", "Open Link"))
    }

    fun testLinkIfChangedAcceptsANewLinkThatWasNotThereBefore() {
        assertEquals(
            "https://code-with-me.jetbrains.com/abc123",
            linkIfChanged(before = null, after = "https://code-with-me.jetbrains.com/abc123"),
        )
    }

    fun testLinkIfChangedRejectsAnUnchangedClipboardEvenIfItLooksLikeALink() {
        val staleLink = "https://stackoverflow.com/questions/some-old-thing"
        assertNull(linkIfChanged(before = staleLink, after = staleLink))
    }

    fun testLinkIfChangedRejectsANonUrlEvenIfItChanged() {
        assertNull(linkIfChanged(before = "old text", after = "not a url"))
    }

    fun testLinkIfChangedRejectsWhenClipboardEndsUpEmpty() {
        assertNull(linkIfChanged(before = "https://example.com/old", after = null))
    }
}
