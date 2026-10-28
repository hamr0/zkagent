package com.tananaev.passportreader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * finding #21 (2026-09-03), item 4's fallback: since this worktree cannot
 * drive the real Kotlin path from a JVM test harness against a live
 * `spikes/m2-handoff` server, this pins the EXACT JSON
 * [HandoffClient.buildPresentation] emits for the bare tier-A path
 * [MainActivity.presentBareAOnBackground] calls
 * (`buildPresentation("A", claim, challenge, null, emptyList())`) — no
 * `zktag` key, no `evidence` entries, `claim` carrying exactly
 * `over_threshold`/`threshold`. This shape was ALSO proven end-to-end
 * against a live `spikes/m2-handoff` instance
 * (`spikes/m2-handoff/verify-mode-a-bare.mjs`, hand-mirroring these same
 * rules) — that run is the stronger proof; this test is the fast, always-on
 * regression guard for the same contract, run every `./gradlew test`.
 */
class HandoffClientTest {

    private fun challenge() = JSONObject().apply {
        put("nonce", "test-nonce")
        put("tier", "A")
        put("threshold", 18)
        put("issued_at", 1_725_000_000_000L)
        put("expires_at", 1_725_000_600_000L)
    }

    @Test
    fun `bare tier-A presentation carries no zktag key and empty evidence`() {
        val claim = mapOf("over_threshold" to true, "threshold" to 18)
        val presentation = HandoffClient.buildPresentation("A", claim, challenge(), null, emptyList())

        assertEquals("zkagent/1", presentation.getString("spec"))
        assertEquals("A", presentation.getString("tier"))
        assertFalse("tier-A presentation must never carry a zktag key (D27, chiproof zktag_forbidden_at_tier_a)", presentation.has("zktag"))
        assertTrue(presentation.has("evidence"))
        assertEquals(0, presentation.getJSONArray("evidence").length())

        val claimObj = presentation.getJSONObject("claim")
        assertEquals(2, claimObj.length())
        assertEquals(true, claimObj.getBoolean("over_threshold"))
        assertEquals(18, claimObj.getInt("threshold"))
    }

    @Test
    fun `bare tier-A presentation's claim reflects an honest under-threshold answer`() {
        val claim = mapOf("over_threshold" to false, "threshold" to 21)
        val presentation = HandoffClient.buildPresentation("A", claim, challenge(), null, emptyList())

        val claimObj = presentation.getJSONObject("claim")
        assertEquals(false, claimObj.getBoolean("over_threshold"))
        assertEquals(21, claimObj.getInt("threshold"))
        assertFalse(presentation.has("zktag"))
        assertEquals(0, presentation.getJSONArray("evidence").length())
    }

    @Test
    fun `bare tier-A presentation carries the SAME challenge object untouched`() {
        val ch = challenge()
        val presentation = HandoffClient.buildPresentation("A", mapOf("over_threshold" to true, "threshold" to 18), ch, null, emptyList())
        assertEquals(ch.toString(), presentation.getJSONObject("challenge").toString())
    }

    // ---------------------------------------------------------------- finding #52: a redirect on the answer post

    @Test
    fun `a 301, 302 or 303 on the answer post is returned as that status, not followed, and maps to Rejected`() {
        for (status in listOf(301, 302, 303, 307)) {
            RedirectServer(status).use { srv ->
                val result = HandoffClient.postForm(srv.postUrl, "vp_token=x")
                assertEquals("status $status", status, result.httpStatus)
                assertEquals("", result.body) // a 3xx with no body: the null error stream reads as empty, no throw
                assertEquals(1, srv.postHits())
                assertEquals("the landing page got no request ($status)", 0, srv.landingHits())
                // MainActivity maps `httpStatus in 200..299` to Accepted and everything else to Rejected
                assertFalse(result.httpStatus in 200..299)
                assertEquals(VerifierRefusal.Outcome.Refused("HTTP $status"), VerifierRefusal.classify(result.httpStatus, result.body))
                assertEquals("Verifier refused: HTTP $status", VerifierRefusal.dialogMessage(VerifierRefusal.classify(result.httpStatus, result.body)) ?: "Verifier refused: HTTP ${result.httpStatus}")
            }
        }
    }

    @Test
    fun `a 302 that carries a body is read, not thrown on`() {
        RedirectServer(302, "{\"error\":\"moved\"}").use { srv ->
            val result = HandoffClient.postForm(srv.postUrl, "vp_token=x")
            assertEquals(302, result.httpStatus)
            assertEquals("{\"error\":\"moved\"}", result.body)
            assertEquals(0, srv.landingHits())
        }
    }

    // ---------------------------------------------------------------- finding #52: a redirect on the request_uri GET

    @Test
    fun `a 301, 302, 303 or 307 on the request_uri GET is a failed fetch with that status and the landing host is never contacted`() {
        for (status in listOf(301, 302, 303, 307)) {
            RedirectServer(status).use { srv ->
                val e = try {
                    HandoffClient.fetchRequestRaw(srv.postUrl)
                    null
                } catch (e: HandoffClient.HandoffHttpException) {
                    e
                }
                assertTrue("status $status must throw", e != null)
                assertEquals(status, e!!.httpStatus)
                assertEquals(1, srv.seen.count { it.method == "GET" && it.path == "/post" })
                assertEquals("the landing host got no request ($status)", 0, srv.landingHits())
                // RequestTrust.verifyHandoff turns this into the existing refusal
                assertTrue(e.message!!.startsWith("GET http://127.0.0.1:"))
            }
        }
    }
}
