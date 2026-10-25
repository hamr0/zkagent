package com.tananaev.passportreader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M3b chunk 1 — the whole pre-read decision (items 1, 2, 7, 9, 13) as a truth table. Hosts are synthetic. The age
 * lock is passed in as `lockedAge` exactly the way the Activity reads it from the shared per-hostname map, and the
 * exemption list is passed explicitly because the reference build's own list is empty (item 9 pass criteria (a)).
 */
class TierCAdmissionTest {

    private val pinned = setOf("kyc.example")
    private val exempt = setOf("multi.example")
    private val pinnedAndExempt = setOf("kyc.example", "multi.example")

    private fun dob(vararg ages: Int): JSONObject {
        val a = JSONArray()
        for (age in ages) a.put(JSONObject().put("name", "dob_at_least").put("age", age))
        return JSONObject().put("zkagent", JSONObject().put("tier", "C").put("predicates", a))
    }

    private fun other(): JSONObject = JSONObject().put(
        "zkagent",
        JSONObject().put("tier", "C").put("predicates", JSONArray().put(JSONObject().put("name", "expiry_beyond").put("months", 6))),
    )

    private fun decide(
        req: JSONObject,
        host: String? = "kyc.example",
        operator: Boolean = true,
        holder: Boolean = true,
        locked: Int? = null,
        pins: Set<String> = pinned,
        exemptHosts: Set<String> = exempt,
    ) = TierCAdmission.decide(operator, host, pins, holder, req, locked, exemptHosts)

    private fun refusal(d: TierCAdmission.Decision) = (d as TierCAdmission.Decision.Refuse).refusal

    // ---------------------------------------------------------------- the three gates, in order

    @Test
    fun `holder switch off refuses every tier-C request before any read, beside the paired on case`() {
        assertTrue(decide(other()) is TierCAdmission.Decision.Admit) // pass pair
        var refused = 0
        for (req in listOf(other(), dob(18), dob(18, 21))) {
            assertEquals(TierCAdmission.Refusal.HOLDER_SETTING_OFF, refusal(decide(req, holder = false)))
            refused++
        }
        assertEquals(3, refused)
    }

    @Test
    fun `operator switch off, then an unpinned host, then the holder switch - in that order`() {
        assertEquals(TierCAdmission.Refusal.OPERATOR_SWITCH_OFF, refusal(decide(other(), operator = false, holder = false)))
        assertEquals(TierCAdmission.Refusal.NOT_PINNED, refusal(decide(other(), host = "other.example", holder = false)))
        assertEquals(TierCAdmission.Refusal.NOT_PINNED, refusal(decide(other(), host = null)))
        assertEquals(TierCAdmission.Refusal.NOT_PINNED, refusal(decide(other(), host = "sub.kyc.example"))) // exact hostname only
        assertEquals(TierCAdmission.Refusal.HOLDER_SETTING_OFF, refusal(decide(other(), holder = false)))
        assertTrue(decide(other(), host = "KYC.example".lowercase()) is TierCAdmission.Decision.Admit)
    }

    @Test
    fun `an unparseable request is refused whole with the parse reason, after the gates pass`() {
        val bad = JSONObject().put("zkagent", JSONObject().put("tier", "C").put("predicates", JSONArray().put(JSONObject().put("name", "dob_at_least").put("age", 19))))
        val d = decide(bad) as TierCAdmission.Decision.Refuse
        assertEquals(TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE, d.refusal)
        assertEquals("boundary_off_list", d.reason)
    }

    // ---------------------------------------------------------------- the shared age lock (item 9 pass criteria (a))

    @Test
    fun `tier B at 18 locked, then tier-C DOB 18 admitted, then tier-C DOB 21 refused whole`() {
        val at18 = decide(dob(18), locked = 18) as TierCAdmission.Decision.Admit
        assertNull(at18.ageToLock) // already locked: nothing new to write
        assertEquals(TierCAdmission.Refusal.AGE_NOT_LOCKED_AGE, refusal(decide(dob(21), locked = 18)))
    }

    @Test
    fun `tier-C DOB 21 asked first sets the age to write, then tier A at 18 is refused by the same map`() {
        val first = decide(dob(21), locked = null) as TierCAdmission.Decision.Admit
        assertEquals(21, first.ageToLock)
        // the caller writes 21 into originThresholdLocks; tier A/B then reads that same map:
        val lockedAfter = first.ageToLock
        val tierAB = ThresholdPolicy.evaluate("kyc.example", 18, lockedAfter)
        assertTrue(tierAB is ThresholdPolicy.Decision.RefuseDifferentThreshold)
        // pass pair: the same age is answered
        assertTrue(ThresholdPolicy.evaluate("kyc.example", 21, lockedAfter) is ThresholdPolicy.Decision.Admit)
    }

    @Test
    fun `two different DOB boundaries in one request are refused whole for a non-exempt site`() {
        assertEquals(TierCAdmission.Refusal.AGE_CONFLICT_IN_REQUEST, refusal(decide(dob(18, 21), locked = null)))
        assertEquals(TierCAdmission.Refusal.AGE_CONFLICT_IN_REQUEST, refusal(decide(dob(18, 21), locked = 18)))
        // the same boundary twice is one boundary
        val twice = decide(dob(18, 18), locked = null) as TierCAdmission.Decision.Admit
        assertEquals(18, twice.ageToLock)
    }

    @Test
    fun `a refused request writes no lock - the decision of a refusal carries no age`() {
        var refusals = 0
        for (d in listOf(
            decide(dob(21), locked = 18),
            decide(dob(18, 21), locked = null),
            decide(dob(18), holder = false),
            decide(dob(18), host = "other.example"),
            decide(dob(18), operator = false),
        )) {
            assertTrue(d is TierCAdmission.Decision.Refuse) // a Refuse has no ageToLock member to write
            refusals++
        }
        assertEquals(5, refusals)
    }

    @Test
    fun `exempt multi-threshold host - several ages across requests and inside one request, and no lock is ever written`() {
        val ex = "multi.example"
        val a = decide(dob(18), host = ex, pins = pinnedAndExempt) as TierCAdmission.Decision.Admit
        assertNull(a.ageToLock)
        val b = decide(dob(21), host = ex, locked = 18, pins = pinnedAndExempt) as TierCAdmission.Decision.Admit // tier B at 18, then DOB 21
        assertNull(b.ageToLock)
        val c = decide(dob(18, 21, 60), host = ex, locked = 18, pins = pinnedAndExempt) as TierCAdmission.Decision.Admit
        assertNull(c.ageToLock)
        assertEquals(3, TierCRequest.dobAges(c.predicates).size)
    }

    @Test
    fun `non-vacuity pair - the same sequences from a host NOT in the list are refused at the second age`() {
        assertTrue(exempt.isNotEmpty()) // the exemption list holds an entry, so the exempt rows above prove something
        var refusedUnnamed = 0
        // identical to the exempt rows, but from the pinned, non-exempt host
        val one = decide(dob(21), host = "kyc.example", locked = 18)
        assertEquals(TierCAdmission.Refusal.AGE_NOT_LOCKED_AGE, refusal(one)); refusedUnnamed++
        val two = decide(dob(18, 21, 60), host = "kyc.example", locked = 18)
        assertEquals(TierCAdmission.Refusal.AGE_CONFLICT_IN_REQUEST, refusal(two)); refusedUnnamed++
        assertTrue(refusedUnnamed > 0)
        // and the exemption is keyed on the list: emptying it turns the exempt host's second age into a refusal
        val noList = decide(dob(21), host = "multi.example", locked = 18, pins = pinnedAndExempt, exemptHosts = emptySet())
        assertEquals(TierCAdmission.Refusal.AGE_NOT_LOCKED_AGE, refusal(noList))
    }

    @Test
    fun `a request with no DOB predicate never touches the lock`() {
        val d = decide(other(), locked = 18) as TierCAdmission.Decision.Admit
        assertNull(d.ageToLock)
        assertEquals(1, d.predicates.size)
    }
}
