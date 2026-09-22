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
}
