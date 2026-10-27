package com.tananaev.passportreader

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D113 (b), findings #39/#41 — the well-known request-signer key fetch.
 * Part 1 drives [RequestTrust.fetchWellKnownKey] through its injected seam; part 2 drives the PRODUCTION
 * [RequestTrust.httpGetNoRedirect] against real loopback servers to prove redirects are not followed.
 */
class WellKnownKeyTest {

    // A real P-256 public JWK (the dev request-signer's public key; public material only).
    private val goodJwk =
        """{"kty":"EC","crv":"P-256","kid":"k1","x":"C0-YHKxkZ8_Fffp7q17BPd1KRuwxNue4bvANNKBMv0Q","y":"tXB_f0WJ6bf48Ax6ijOZXPCI3xOZlBA7gW5kK0XdZts"}"""

    private val sockets = mutableListOf<java.net.ServerSocket>()

    @After
    fun stop() { sockets.forEach { it.close() } }

    // ------------------------------------------------------------ seam

    @Test
    fun `200 with a good key resolves`() {
        var asked: String? = null
        val key = RequestTrust.fetchWellKnownKey("https://site.example:443") { url ->
            asked = url
            RequestTrust.WellKnownResponse(200, goodJwk)
        }
        assertEquals("https://site.example:443/.well-known/zkagent-verifier", asked)
        assertNotNull(key)
        assertEquals("k1", key!!.kid)
        assertFalse(key.isDev)
    }

    @Test
    fun `200 with a JWKS wrapper resolves`() {
        val key = RequestTrust.fetchWellKnownKey("https://site.example:443") {
            RequestTrust.WellKnownResponse(200, """{"keys":[$goodJwk]}""")
        }
        assertNotNull(key)
    }

    @Test
    fun `3xx to the same host is refused`() {
        for (status in listOf(301, 302, 303, 307, 308)) {
            val key = RequestTrust.fetchWellKnownKey("https://site.example:443") {
                // Even if a (buggy) transport handed back a good body, a 3xx status must not resolve.
                RequestTrust.WellKnownResponse(status, goodJwk)
            }
            assertNull("status $status", key)
        }
    }

    @Test
    fun `3xx to another host is refused`() {
        val key = RequestTrust.fetchWellKnownKey("https://site.example:443") {
            RequestTrust.WellKnownResponse(302, null)
        }
        assertNull(key)
    }

    @Test
    fun `non-200 statuses are refused`() {
        for (status in listOf(400, 404, 500)) {
            val key = RequestTrust.fetchWellKnownKey("https://site.example:443") {
                RequestTrust.WellKnownResponse(status, goodJwk)
            }
            assertNull("status $status", key)
        }
    }

    @Test
    fun `2xx with an unusable body is refused (not a key, empty keys, wrong curve, throwing transport)`() {
        assertNull(RequestTrust.fetchWellKnownKey("https://s.example:443") { RequestTrust.WellKnownResponse(200, "not json") })
        assertNull(RequestTrust.fetchWellKnownKey("https://s.example:443") { RequestTrust.WellKnownResponse(200, """{"keys":[]}""") })
        assertNull(RequestTrust.fetchWellKnownKey("https://s.example:443") { RequestTrust.WellKnownResponse(200, goodJwk.replace("P-256", "P-384")) })
        assertNull(RequestTrust.fetchWellKnownKey("https://s.example:443") { throw java.io.IOException("boom") })
    }

    // ------------------------------------------------ real transport

    /** Minimal raw-socket HTTP server (the JDK httpserver module is not visible to this Kotlin build). [handler]
     * gets the request path and returns (status, extra header lines, body). */
    private class Srv(val port: Int, val hits: AtomicInteger, val socket: java.net.ServerSocket)

    private fun serve(handler: (String) -> Triple<Int, List<String>, String>): Srv {
        val ss = java.net.ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        val hits = AtomicInteger()
        val t = Thread {
            while (!ss.isClosed) {
                try {
                    ss.accept().use { c ->
                        val reader = c.getInputStream().bufferedReader()
                        val path = reader.readLine().split(" ")[1]
                        while (reader.readLine().let { it != null && it.isNotEmpty() }) { /* drain headers */ }
                        hits.incrementAndGet()
                        val (status, headers, body) = handler(path)
                        val head = "HTTP/1.1 $status X\r\n" + headers.joinToString("") { "$it\r\n" } +
                            "Content-Length: ${body.toByteArray().size}\r\nConnection: close\r\n\r\n"
                        c.getOutputStream().apply { write(head.toByteArray()); write(body.toByteArray()); flush() }
                    }
                } catch (e: Exception) { /* closed */ }
            }
        }
        t.isDaemon = true
        t.start()
        sockets += ss
        return Srv(ss.localPort, hits, ss)
    }

    @Test
    fun `production transport - a 200 is read and resolves`() {
        val s = serve { Triple(200, emptyList(), goodJwk) }
        val key = RequestTrust.fetchWellKnownKey("http://127.0.0.1:${s.port}")
        assertNotNull(key)
        assertEquals("k1", key!!.kid)
    }

    @Test
    fun `production transport - a same-host redirect is NOT followed`() {
        val s = serve { path ->
            if (path == "/.well-known/zkagent-verifier") Triple(302, listOf("Location: /elsewhere"), "")
            else Triple(200, emptyList(), goodJwk)
        }
        val key = RequestTrust.fetchWellKnownKey("http://127.0.0.1:${s.port}")
        assertNull(key)
        assertEquals("only the original GET may reach the server; the redirect target must not be requested", 1, s.hits.get())
    }

    @Test
    fun `production transport - a cross-host redirect is NOT followed`() {
        val target = serve { Triple(200, emptyList(), goodJwk) }
        val origin = serve { Triple(307, listOf("Location: http://localhost:${target.port}/.well-known/zkagent-verifier"), "") }
        val key = RequestTrust.fetchWellKnownKey("http://127.0.0.1:${origin.port}")
        assertNull(key)
        assertEquals(1, origin.hits.get())
        assertEquals("the redirect target must never be contacted", 0, target.hits.get())
    }

    @Test
    fun `production transport - a 404 is refused`() {
        val s = serve { Triple(404, emptyList(), "") }
        assertNull(RequestTrust.fetchWellKnownKey("http://127.0.0.1:${s.port}"))
        assertTrue(s.hits.get() > 0)
    }
}
