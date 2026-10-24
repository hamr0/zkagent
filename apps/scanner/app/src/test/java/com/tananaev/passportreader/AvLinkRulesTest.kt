package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding #23 guard, proof 1 of 3 (truth table). The pure rules take the
 * scheme/host/query lookups; the test models those with a deliberately
 * simple, independent parser (split on `:`, `?`, `&`, `=`) and literal
 * expected strings. It does NOT exercise android.net.Uri's parsing (a stub
 * under this module's JVM tests) -- that remains proof 3 (device).
 */
class AvLinkRulesTest {

    private fun decide(link: String): AvLinkRules.Decision {
        val schemeEnd = link.indexOf(':')
        val scheme = if (schemeEnd < 0) null else link.substring(0, schemeEnd)
        val rest = if (schemeEnd < 0) "" else link.substring(schemeEnd + 1).removePrefix("//")
        val host = rest.substringBefore('?').substringBefore('/')
        val query = if (rest.contains('?')) rest.substringAfter('?') else ""
        val params = query.split('&').filter { it.isNotEmpty() }
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        return AvLinkRules.decide(scheme, { host }, { params[it] })
    }

    private fun assertMalformed(link: String, reason: AvLinkRules.Reason) {
        val d = decide(link)
        assertTrue("expected Malformed for '$link' but got $d", d is AvLinkRules.Decision.Malformed)
        assertEquals(reason, (d as AvLinkRules.Decision.Malformed).reason)
    }

    @Test
    fun `well-formed av link is accepted with both fields`() {
        val d = decide("av://authorize?client_id=site.example&request_uri=http://127.0.0.1:8787/request/abc")
        assertEquals(AvLinkRules.Decision.Accepted("site.example", "http://127.0.0.1:8787/request/abc"), d)
    }

    @Test
    fun `client_id is optional`() {
        assertEquals(
            AvLinkRules.Decision.Accepted(null, "http://127.0.0.1:8787/r"),
            decide("av://authorize?request_uri=http://127.0.0.1:8787/r"),
        )
    }

    @Test
    fun `request_uri missing is malformed`() {
        assertMalformed("av://authorize?client_id=site.example", AvLinkRules.Reason.MISSING_REQUEST_URI)
        assertMalformed("av://authorize", AvLinkRules.Reason.MISSING_REQUEST_URI)
    }

    @Test
    fun `link truncated right after ampersand (shell ate the rest) is malformed`() {
        // Unquoted `am start ... av://authorize?client_id=x&request_uri=...`: the shell
        // backgrounds at '&' and the intent only carries what precedes it.
        assertMalformed("av://authorize?client_id=site.example&", AvLinkRules.Reason.MISSING_REQUEST_URI)
    }

    @Test
    fun `wrong scheme is malformed`() {
        assertMalformed("http://authorize?request_uri=http://x/r", AvLinkRules.Reason.WRONG_SCHEME)
        assertMalformed("avx://authorize?request_uri=http://x/r", AvLinkRules.Reason.WRONG_SCHEME)
    }

    @Test
    fun `wrong host for av scheme is malformed`() {
        assertMalformed("av://other?request_uri=http://x/r", AvLinkRules.Reason.WRONG_HOST)
    }

    @Test
    fun `empty string is malformed`() {
        assertMalformed("", AvLinkRules.Reason.WRONG_SCHEME)
    }

    @Test
    fun `unknown extra params are tolerated`() {
        assertEquals(
            AvLinkRules.Decision.Accepted("c", "http://x/r"),
            decide("av://authorize?foo=1&client_id=c&bar=2&request_uri=http://x/r"),
        )
    }

    @Test
    fun `https app-link skips the host check and still needs request_uri`() {
        assertEquals(
            AvLinkRules.Decision.Accepted(null, "http://x/r"),
            decide("https://anything.example/path?request_uri=http://x/r"),
        )
        assertMalformed("https://anything.example/path", AvLinkRules.Reason.MISSING_REQUEST_URI)
    }

    @Test
    fun `host and query are not read when the scheme already fails`() {
        var touched = false
        val d = AvLinkRules.decide("ftp", { touched = true; "authorize" }, { touched = true; "x" })
        assertTrue(d is AvLinkRules.Decision.Malformed)
        assertTrue("lazy lookups must not run for a wrong scheme", !touched)
    }

    @Test
    fun `host is not read for https`() {
        var hostTouched = false
        AvLinkRules.decide("https", { hostTouched = true; null }, { "v" })
        assertTrue(!hostTouched)
    }
}
