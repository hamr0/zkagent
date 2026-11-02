package com.tananaev.passportreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Finding #55 — a link captured while an outcome dialog is open must survive the dialog's OK. */
class OutcomeDismissScopeTest {

    private class Handoff(val tx: String)

    @Test
    fun `same handoff pending at show and at dismissal is the dialog's own, released`() {
        val h = Handoff("a")
        assertTrue(OutcomeDismissScope.ownsHandoff(h, h))
    }

    @Test
    fun `no handoff at show and none at dismissal (local scan) is released as before`() {
        assertTrue(OutcomeDismissScope.ownsHandoff(null, null))
    }

    @Test
    fun `a link captured while the dialog was open is not the dialog's, even with equal content`() {
        val shown = Handoff("same")
        val reopened = Handoff("same")
        assertFalse(OutcomeDismissScope.ownsHandoff(shown, reopened))
    }

    @Test
    fun `a link captured during a local-scan dialog survives`() {
        assertFalse(OutcomeDismissScope.ownsHandoff(null, Handoff("new")))
    }

    // Weak by nature (source text); the device re-check is the real proof.
    @Test
    fun `wiring - the OK handler releases the handoff only through OutcomeDismissScope`() {
        val src = listOf("src/main/java", "app/src/main/java")
            .map { File(it, "com/tananaev/passportreader/MainActivity.kt") }.first { it.exists() }.readText()
        val fn = src.substringAfter("private fun showBlockingOutcomeDialog(")
        val body = fn.substringBefore("\n    /**")
        val snapAt = body.indexOf("val handoffWhenShown = pendingHandoff")
        val showAt = body.indexOf("AlertDialog.Builder(this)")
        val guardAt = body.indexOf("OutcomeDismissScope.ownsHandoff(handoffWhenShown, pendingHandoff)")
        val releaseAt = body.indexOf("releasePendingHandoff()")
        assertTrue("snapshot taken before the dialog is built", snapAt in 0 until showAt)
        assertTrue("guard precedes the release inside the handler", guardAt > showAt && releaseAt > guardAt)
    }
}
