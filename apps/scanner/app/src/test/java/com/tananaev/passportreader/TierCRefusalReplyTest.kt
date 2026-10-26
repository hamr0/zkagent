package com.tananaev.passportreader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * D110 — the one generic refusal. Three proofs, as the project's guard rule asks: a truth table over the decision
 * ([TierCRefusalReply.plan]: verified, operator switch, exact-hostname pin, plus the holder switch NOT being an input),
 * the exact wire body, and a REAL local HTTP server receiving the POST through the production transport.
 * Every host, state and URL here is synthetic.
 */
class TierCRefusalReplyTest {

    private val origin = "http://kyc.example:8787"
    private val pinned = setOf("kyc.example")

    private fun verified(
        responseUri: String? = "$origin/wallet/direct_post",
        state: String? = "req-123",
        at: String = origin,
    ): RequestTrust.VerifiedRequest {
        val json = JSONObject().put("zkagent", JSONObject().put("tier", "C"))
        if (responseUri != null) json.put("response_uri", responseUri)
        if (state != null) json.put("state", state)
        return RequestTrust.VerifiedRequest(json, at)
    }

    private fun plan(
        v: RequestTrust.VerifiedRequest? = verified(),
        operator: Boolean = true,
        pins: Set<String> = pinned,
    ) = TierCRefusalReply.plan(v, operator, pins)

    // ---------------------------------------------------------------- the decision: who is told

    @Test
    fun `a verified request from a pinned host with the operator switch on is told - the passing control`() {
        val p = plan()
        assertNotNull(p)
        assertEquals("$origin/wallet/direct_post", p!!.responseUri)
        assertEquals("req-123", p.state)
    }

    @Test
    fun `no verified request means no notice, however pinned and enabled the build is`() {
        assertNull(plan(v = null))
    }

    @Test
    fun `the operator switch off means no notice`() {
        assertNull(plan(operator = false))
        assertNotNull(plan(operator = true)) // paired control
    }

    @Test
    fun `a host that is not on the pin list gets no notice - exact hostname only`() {
        assertNull(plan(pins = emptySet()))
        assertNull(plan(pins = setOf("other.example")))
        assertNull(plan(pins = setOf("example"))) // a suffix is not a pin
        assertNull(plan(v = verified(at = "http://sub.kyc.example:8787", responseUri = "http://sub.kyc.example:8787/x"))) // a subdomain is not the pin
        assertNull(plan(v = verified(at = "http://not-an-origin", responseUri = "http://not-an-origin/x")))
        assertNotNull(plan(pins = setOf("kyc.example", "other.example"))) // paired control
    }

    @Test
    fun `the holder's own switch is not an input - OFF passes the gates, so the site is still told`() {
        val request = JSONObject().put(
            "zkagent",
            JSONObject().put("tier", "C").put(
                "challenge",
                JSONObject().put("predicates", org.json.JSONArray().put(JSONObject().put("name", "expiry_beyond").put("months", 3))),
            ),
        )
        // the pre-read decision refuses because the HOLDER setting is off ...
        val d = TierCAdmission.decide(
            operatorEnabled = true, hostname = "kyc.example", pinned = pinned, holderAllowsAdditional = false,
            requestJson = request, lockedAge = null,
        ) as TierCAdmission.Decision.Refuse
        assertEquals(TierCAdmission.Refusal.HOLDER_SETTING_OFF, d.refusal)
        // ... and the refusal notice is still planned (it is decided by verified + operator + pin alone)
        assertNotNull(plan())
        // while the two causes that fail the gates themselves are NOT told
        for (cause in listOf(false to pinned, true to emptySet<String>())) {
            val gate = TierCAdmission.decide(cause.first, "kyc.example", cause.second, true, request, null) as TierCAdmission.Decision.Refuse
            assertTrue(gate.refusal == TierCAdmission.Refusal.OPERATOR_SWITCH_OFF || gate.refusal == TierCAdmission.Refusal.NOT_PINNED)
            assertNull(plan(operator = cause.first, pins = cause.second))
        }
    }

    @Test
    fun `a request without a response_uri, or whose response_uri is on another origin, gets no notice`() {
        assertNull(plan(v = verified(responseUri = null)))
        assertNull(plan(v = verified(responseUri = "")))
        assertNull(plan(v = verified(responseUri = "http://evil.example/wallet/direct_post")))
        assertNull(plan(v = verified(responseUri = "http://kyc.example:9999/wallet/direct_post"))) // other port = other origin
        assertNull(plan(v = verified(responseUri = "not a url")))
    }

    @Test
    fun `state is optional - absent or empty state plans no state`() {
        assertNull(plan(v = verified(state = null))!!.state)
        assertNull(plan(v = verified(state = ""))!!.state)
    }

    // ---------------------------------------------------------------- the wire body

    @Test
    fun `the body is exactly error=access_denied plus the encoded state, and nothing else`() {
        assertEquals("error=access_denied&state=req-123", TierCRefusalReply.formBody("req-123"))
        assertEquals("error=access_denied", TierCRefusalReply.formBody(null))
        assertEquals("error=access_denied&state=a+b%26c%3Dd", TierCRefusalReply.formBody("a b&c=d"))
        for (body in listOf(TierCRefusalReply.formBody("s"), TierCRefusalReply.formBody(null))) {
            assertFalse(body.contains("description"))
            assertFalse(body.contains("vp_token"))
            assertEquals(setOf("error", "state").containsAll(body.split("&").map { it.substringBefore("=") }.toSet()), true)
        }
    }

    // ---------------------------------------------------------------- a real server receives it through the production transport

    private class Seen(val method: String, val contentType: String?, val body: String)

    /** A real HTTP/1.1 server on a loopback socket (the Android unit-test classpath has no com.sun HttpServer): reads
     * each request's line, headers and Content-Length body, records it, answers [status]. */
    private fun withServer(status: Int, block: (String, CopyOnWriteArrayList<Seen>) -> Unit) {
        val seen = CopyOnWriteArrayList<Seen>()
        val listener = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        val worker = Thread {
            try {
                while (true) {
                    listener.accept().use { sock ->
                        val input = sock.getInputStream().buffered()
                        fun line(): String {
                            val sb = StringBuilder()
                            while (true) {
                                val c = input.read()
                                if (c < 0 || c == '\n'.code) break
                                if (c != '\r'.code) sb.append(c.toChar())
                            }
                            return sb.toString()
                        }
                        val requestLine = line()
                        var length = 0
                        var ctype: String? = null
                        while (true) {
                            val h = line()
                            if (h.isEmpty()) break
                            val name = h.substringBefore(":").trim().lowercase()
                            val value = h.substringAfter(":").trim()
                            if (name == "content-length") length = value.toInt()
                            if (name == "content-type") ctype = value
                        }
                        val body = ByteArray(length).also { buf -> var off = 0; while (off < length) { val n = input.read(buf, off, length - off); if (n < 0) break; off += n } }
                        seen.add(Seen(requestLine.substringBefore(" "), ctype, body.toString(Charsets.UTF_8)))
                        val reply = "{\"accepted\":true}"
                        sock.getOutputStream().write(
                            ("HTTP/1.1 $status X\r\ncontent-type: application/json\r\ncontent-length: ${reply.length}\r\nconnection: close\r\n\r\n$reply").toByteArray(),
                        )
                    }
                }
            } catch (e: java.io.IOException) {
                // listener closed: the test is over
            }
        }
        worker.isDaemon = true
        worker.start()
        try {
            block("http://127.0.0.1:${listener.localPort}/wallet/direct_post", seen)
        } finally {
            listener.close()
        }
    }

    @Test
    fun `one POST reaches a real server with exactly the generic body, and a 409 is a status, not a retry`() {
        for (status in listOf(200, 409, 500)) {
            withServer(status) { url, seen ->
                val out = TierCRefusalReply.send(TierCRefusalReply.Plan(url, "req-123"))
                assertEquals(TierCRefusalReply.Outcome.Sent(status), out)
                assertEquals("exactly one attempt, no retry (status $status)", 1, seen.size)
                assertEquals("POST", seen[0].method)
                assertEquals("application/x-www-form-urlencoded", seen[0].contentType)
                assertEquals("error=access_denied&state=req-123", seen[0].body)
            }
        }
    }

    @Test
    fun `the body is the same whatever the cause - nothing about a reason can reach the server`() {
        withServer(200) { url, seen ->
            // every refusal path funnels through plan() and send(), neither of which takes a reason
            repeat(3) { TierCRefusalReply.send(TierCRefusalReply.Plan(url, "same-state")) }
            assertEquals(3, seen.size)
            assertEquals(1, seen.map { it.body }.toSet().size)
        }
    }

    @Test
    fun `an unreachable server is a logged failure, never a throw and never a retry`() {
        // a port nobody listens on: bind then stop to get a free port number
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).let { val p = it.localPort; it.close(); p }
        var attempts = 0
        val out = TierCRefusalReply.send(TierCRefusalReply.Plan("http://127.0.0.1:$port/x", null)) { uri, body ->
            attempts += 1
            TierCRefusalReply.postForm(uri, body)
        }
        assertTrue(out is TierCRefusalReply.Outcome.Failed)
        assertEquals(1, attempts)
        // a transport that throws anything at all is contained
        assertTrue(TierCRefusalReply.send(TierCRefusalReply.Plan("http://x", null)) { _, _ -> throw IllegalStateException("boom") } is TierCRefusalReply.Outcome.Failed)
        // a malformed URI is contained too
        assertTrue(TierCRefusalReply.send(TierCRefusalReply.Plan("not a url", null)) is TierCRefusalReply.Outcome.Failed)
    }
}
