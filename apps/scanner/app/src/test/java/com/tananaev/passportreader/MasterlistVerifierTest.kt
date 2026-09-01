package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security

/**
 * §6.2 item 7 — CMS integrity + two-bucket rule, run against the REAL
 * bundled `assets/DE_ML.ml` (copied to test/resources so a plain JVM test
 * can read it — same bytes the app ships, see README.md).
 *
 * NOTE: this exercises the CMS-parse/signature/chain half of item 7 fully
 * (all in pure-Java BouncyCastle code). It does NOT exercise the "well-
 * formed but lacking the presenting issuer" allowed:false branch — that
 * needs a real document's signer certificate (`excludeAnchorFor`), which
 * only exists after an on-device chip read; see the device-run checklist
 * in the conformance report for that half.
 */
class MasterlistVerifierTest {

    init {
        // BouncyCastleProvider must be registered for JcaSimpleSignerInfoVerifierBuilder("BC").
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    private fun resource(name: String): ByteArray =
        MasterlistVerifierTest::class.java.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes() }

    @Test
    fun `full CMS SignedData verifies and the two-bucket load succeeds`() {
        val ml = resource("DE_ML.ml")
        val root = resource("csca-germany-root.der")
        val result = MasterlistVerifier.load(ml, root)
        assertTrue("expected Success, got $result", result is MasterlistVerifier.LoadResult.Success)
        val ok = result as MasterlistVerifier.LoadResult.Success
        assertTrue("declared/parsed counts must be consistent", ok.masterList.consistent)
        assertTrue("expected a non-trivial CSCA count, got ${ok.masterList.certsParsed}", ok.masterList.certsParsed > 100)
        println("MasterlistVerifierTest: real DE_ML.ml certsParsed=${ok.masterList.certsParsed}")
    }

    @Test
    fun `a half-truncated CMS file is refused as an integrity failure, never a pass`() {
        val ml = resource("DE_ML.ml")
        val root = resource("csca-germany-root.der")
        val half = ml.copyOf(ml.size / 2)
        val result = MasterlistVerifier.load(half, root)
        assertTrue("a truncated file must be Failure (ok:false), never Success", result is MasterlistVerifier.LoadResult.Failure)
    }

    @Test
    fun `a single flipped byte in the CMS signature bytes breaks verification`() {
        val ml = resource("DE_ML.ml").copyOf()
        val root = resource("csca-germany-root.der")
        // Flip a byte roughly 3/4 through the file — inside the signerInfos'
        // signature material for this specific capture (encapContentInfo
        // dominates the first ~90% of the file; signerInfos trail it).
        val idx = (ml.size * 0.95).toInt()
        ml[idx] = (ml[idx].toInt() xor 0xFF).toByte()
        val result = MasterlistVerifier.load(ml, root)
        assertTrue("a corrupted trailing region must not silently verify — got $result", result is MasterlistVerifier.LoadResult.Failure)
    }

    @Test
    fun `pinned root byte-mismatch refuses even a structurally valid CMS file`() {
        val ml = resource("DE_ML.ml")
        val wrongRoot = resource("DE_ML.ml").copyOfRange(0, 1200) // garbage, not a valid cert
        val result = MasterlistVerifier.load(ml, wrongRoot)
        assertTrue(result is MasterlistVerifier.LoadResult.Failure)
        assertEquals(
            "pinned csca-germany root asset unparsable",
            (result as MasterlistVerifier.LoadResult.Failure).reason.substringBefore(":"),
        )
    }
}
