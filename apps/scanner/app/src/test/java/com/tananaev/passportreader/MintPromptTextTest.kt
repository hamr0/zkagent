package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding #11 (`.claude/remember/findings.md`) — the biometric prompt showed
 * no origin/site, so a user could not tell whose request they were
 * authorizing. [MintPromptText.titleFor] is the extracted, pure decision:
 * `MainActivity.promptAndMint`'s `BiometricPrompt.PromptInfo.Builder` is
 * itself unassertable in this suite (a stub under
 * `unitTests.isReturnDefaultValues = true`), so the title text this fix
 * puts in front of the user is pinned here instead — same pattern as
 * [PaneVisibilityTest] pinning [PaneVisibility.choosePane].
 */
class MintPromptTextTest {

    @Test
    fun `title names the verified site`() {
        val title = MintPromptText.titleFor("example.com:8443")
        assertTrue("expected the site in the title, got: $title", title.contains("example.com:8443"))
    }

    @Test
    fun `blank site falls back to the no-handoff label`() {
        assertEquals(MintPromptText.titleFor(""), MintPromptText.titleFor(null))
        assertTrue(MintPromptText.titleFor("").contains(MintPromptText.NO_HANDOFF_FALLBACK))
    }

    @Test
    fun `null site falls back to the no-handoff label`() {
        assertTrue(MintPromptText.titleFor(null).contains(MintPromptText.NO_HANDOFF_FALLBACK))
    }
}
