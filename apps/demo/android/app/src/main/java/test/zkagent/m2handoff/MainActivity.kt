package test.zkagent.m2handoff

// spikes/m2-handoff/android — THROWAWAY M2 handoff spike. Not shipped, no PII,
// no chip reading. This activity plays the exact role scripts/fake-wallet.mjs
// plays headlessly (see spikes/m2-handoff/scripts/fake-wallet.mjs): on receiving
// an av:// invocation it fetches the request-by-reference, VERIFIES the ES256
// request-object JWS against a pinned dev signer pubkey, refuses on a bad
// signature, and otherwise builds a tier-A bare zkagent/1 presentation and
// POSTs it via OpenID4VP direct_post. Tier A only on device (D27).

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class MainActivity : Activity() {

    // Pinned DEV-ONLY request-signer public key (P-256), copied verbatim from
    // spikes/m2-handoff/dev-request-signer-key.mjs. NOT a secret, spike only.
    // The wallet refuses any request object not signed by this key.
    private val PINNED_SIGNER_PEM = """
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEC0+YHKxkZ8/Fffp7q17BPd1KRuwx
        Nue4bvANNKBMv0S1cH9/RYnpt/jwDHqKM5lc8IjfE5mUEDuBbmQrRd1m2w==
        -----END PUBLIC KEY-----
    """.trimIndent()

    private val REQUEST_OBJECT_TYP = "oauth-authz-req+jwt"

    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        out = TextView(this).apply {
            textSize = 15f
            setPadding(40, 80, 40, 40)
            setTextColor(Color.WHITE)
        }
        scroll.addView(out)
        setContentView(scroll)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun render(bg: Int, title: String, body: String) {
        runOnUiThread {
            out.setBackgroundColor(bg)
            out.text = "$title\n\n$body"
        }
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data
        if (data == null || data.scheme != "av") {
            render(Color.parseColor("#37474F"), "m2-handoff spike",
                "Waiting for an av:// invocation from the age-gate page.")
            return
        }
        render(Color.parseColor("#37474F"), "WORKING…", "Received: $data")
        Thread { runFlow(data.toString()) }.start()
    }

    private fun runFlow(link: String) {
        val log = StringBuilder()
        try {
            // 1. Parse request_uri out of the av:// link.
            val uri = android.net.Uri.parse(link)
            val requestUri = uri.getQueryParameter("request_uri")
            val clientId = uri.getQueryParameter("client_id")
            log.append("request_uri = $requestUri\n")
            if (requestUri == null) { refuse(log, "no request_uri in link"); return }

            // 2. Fetch the request-by-reference (compact JWS).
            val jws = httpGet(requestUri)
            log.append("fetched request.jwt (${jws.length} chars)\n")

            // 3. VERIFY the ES256 request object BEFORE trusting anything in it.
            val parts = jws.split(".")
            if (parts.size != 3) { refuse(log, "not_compact_jws"); return }
            val header = JSONObject(String(b64u(parts[0])))
            log.append("JWS header: $header\n")
            if (header.optString("alg") != "ES256") { refuse(log, "alg_mismatch"); return }
            if (header.optString("typ") != REQUEST_OBJECT_TYP) { refuse(log, "typ_mismatch"); return }
            val sig = b64u(parts[2])
            if (sig.size != 64) { refuse(log, "signature_malformed (${sig.size}B, expected 64 P1363)"); return }
            val signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8)
            val ok = verifyEs256(signingInput, sig)
            if (!ok) { refuse(log, "signature_invalid"); return }
            log.append("request JWS verified (ES256, kid=${header.optString("kid")})\n")

            // 4. Build the tier-A bare zkagent/1 presentation from the challenge.
            val payload = JSONObject(String(b64u(parts[1])))
            val zk = payload.getJSONObject("zkagent")
            val challenge = zk.getJSONObject("challenge")
            val responseUri = payload.getString("response_uri")
            val state = payload.getString("state")
            val threshold = challenge.getInt("threshold")

            val claim = JSONObject().put("over_threshold", true).put("threshold", threshold)
            val presentation = JSONObject()
                .put("spec", "zkagent/1")
                .put("tier", "A")
                .put("claim", claim)
                .put("challenge", challenge)
                .put("evidence", org.json.JSONArray())

            val vpToken = Base64.encodeToString(
                presentation.toString().toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            // 5. POST the response — form-encoded, per OpenID4VP direct_post.
            val form = "state=" + URLEncoder.encode(state, "UTF-8") +
                "&vp_token=" + URLEncoder.encode(vpToken, "UTF-8")
            val postResp = httpPostForm(responseUri, form)
            log.append("POST direct_post -> $postResp\n")

            render(Color.parseColor("#1B5E20"), "PRESENTED (tier A, bare)",
                "Verified the request signature, built a bare zkagent/1 " +
                "presentation, and POSTed it to the verifier.\n\n$log")
        } catch (e: Exception) {
            refuse(log, "exception: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun refuse(log: StringBuilder, reason: String) {
        render(Color.parseColor("#B71C1C"), "REFUSED",
            "Did NOT post any response.\nreason = $reason\n\n$log")
    }

    // ---- crypto: ES256 = ECDSA P-256/SHA-256; JWS sig is raw R||S (ieee-p1363),
    // java.security wants DER, so convert. ----
    private fun verifyEs256(signingInput: ByteArray, p1363: ByteArray): Boolean {
        val pub = loadEcPublicKey(PINNED_SIGNER_PEM)
        val der = p1363ToDer(p1363)
        val v = Signature.getInstance("SHA256withECDSA")
        v.initVerify(pub)
        v.update(signingInput)
        return v.verify(der)
    }

    private fun loadEcPublicKey(pem: String): java.security.PublicKey {
        val b64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.decode(b64, Base64.DEFAULT)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
    }

    /** raw 64-byte R||S -> DER SEQUENCE(INTEGER r, INTEGER s). */
    private fun p1363ToDer(sig: ByteArray): ByteArray {
        val r = BigInteger(1, sig.copyOfRange(0, 32))
        val s = BigInteger(1, sig.copyOfRange(32, 64))
        val rb = r.toByteArray()
        val sb = s.toByteArray()
        val len = 2 + rb.size + 2 + sb.size
        val out = java.io.ByteArrayOutputStream()
        out.write(0x30)
        out.write(len)
        out.write(0x02); out.write(rb.size); out.write(rb)
        out.write(0x02); out.write(sb.size); out.write(sb)
        return out.toByteArray()
    }

    private fun b64u(s: String): ByteArray = Base64.decode(s, Base64.URL_SAFE or Base64.NO_PADDING)

    // ---- minimal HTTP ----
    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        try {
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }

    private fun httpPostForm(url: String, form: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        try {
            DataOutputStream(c.outputStream).use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.readText() ?: ""
            return "HTTP $code $body"
        } finally { c.disconnect() }
    }
}
