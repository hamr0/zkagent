package com.tananaev.passportreader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * D112 (finding #38) — the arrival step: every tier-C refusal decidable without the chip is a [TierCArrival.Plan.Refuse]
 * the moment the request is known, the age map is never written, and the Activity wires the step in before the request
 * becomes the verified (Verify-able) one. Hosts are synthetic; expected causes and strings are written out literally.
 */
class TierCArrivalTest {

    private val pinned = setOf("kyc.example")

    private fun dob(vararg ages: Int): JSONObject {
        val a = JSONArray()
        for (age in ages) a.put(JSONObject().put("name", "dob_at_least").put("age", age))
        return JSONObject().put("zkagent", JSONObject().put("tier", "C").put("challenge", JSONObject().put("predicates", a)))
    }

    private fun expiry(): JSONObject = JSONObject().put(
        "zkagent",
        JSONObject().put("tier", "C").put("challenge", JSONObject().put("predicates", JSONArray().put(JSONObject().put("name", "expiry_beyond").put("months", 6)))),
    )

    private fun plan(
        req: JSONObject,
        host: String? = "kyc.example",
        operator: Boolean = true,
        holder: Boolean = true,
        locks: Map<String, Int> = emptyMap(),
    ) = TierCArrival.plan(req, operator, host, pinned, holder, locks)

    private fun refusalOf(p: TierCArrival.Plan) = (p as TierCArrival.Plan.Refuse).refusal

    @Test
    fun `every chip-free refusal cause is a Refuse on arrival, each beside its admitted pair`() {
        assertTrue(plan(expiry()) is TierCArrival.Plan.Proceed) // pass pair
        assertEquals(TierCAdmission.Refusal.OPERATOR_SWITCH_OFF, refusalOf(plan(expiry(), operator = false)).refusal)
        assertEquals(TierCAdmission.Refusal.NOT_PINNED, refusalOf(plan(expiry(), host = "127.0.0.1")).refusal)
        assertEquals(TierCAdmission.Refusal.HOLDER_SETTING_OFF, refusalOf(plan(expiry(), holder = false)).refusal)
        val offList = refusalOf(plan(dob(19)))
        assertEquals(TierCAdmission.Refusal.REQUEST_NOT_ANSWERABLE, offList.refusal)
        assertEquals("boundary_off_list", offList.reason)
        assertEquals(TierCAdmission.Refusal.AGE_NOT_LOCKED_AGE, refusalOf(plan(dob(21), locks = mapOf("kyc.example" to 18))).refusal)
        assertEquals(TierCAdmission.Refusal.AGE_CONFLICT_IN_REQUEST, refusalOf(plan(dob(18, 21))).refusal)
    }

    @Test
    fun `a tier A, B or absent request is not this step's business`() {
        for (tier in listOf("A", "B", "X")) {
            val req = JSONObject().put("zkagent", JSONObject().put("tier", tier))
            assertEquals(TierCArrival.Plan.NotTierC, plan(req, operator = false, holder = false, host = null))
        }
        assertEquals(TierCArrival.Plan.NotTierC, plan(JSONObject()))
    }

    @Test
    fun `an unpinned host is refused on arrival without the holder or the request shape mattering`() {
        var refused = 0
        for (req in listOf(expiry(), dob(18), dob(18, 21), dob(19))) {
            assertEquals(TierCAdmission.Refusal.NOT_PINNED, refusalOf(plan(req, host = "localhost", holder = false)).refusal)
            refused++
        }
        assertEquals(4, refused)
    }

    @Test
    fun `arrival never writes the age map, on refusal or on admission with a lock to record`() {
        val locks = HashMap<String, Int>()
        locks["kyc.example"] = 18
        locks["other.example"] = 21
        val before = HashMap(locks)
        plan(dob(21), locks = locks)            // refuse: conflicts with the lock
        plan(dob(18, 21), locks = locks)        // refuse: conflict in request
        plan(expiry(), operator = false, locks = locks)
        plan(dob(18), locks = locks)            // admit
        assertEquals(before, locks)
        val fresh = HashMap<String, Int>()
        val admit = plan(dob(16), locks = fresh) as TierCArrival.Plan.Proceed
        assertEquals(16, admit.admission.ageToLock) // the lock the READ would write later ...
        assertTrue(fresh.isEmpty())                  // ... is not written by arrival
    }

    @Test
    fun `MainActivity runs the arrival step before the request becomes the verified one, and refuses with the notice path`() {
        val src = File("src/main/java/com/tananaev/passportreader/MainActivity.kt").readText()
        val call = src.indexOf("TierCArrival.plan(")
        val verifiedWrite = src.indexOf("verifiedRequest = outcome.request")
        assertTrue("TierCArrival.plan must be called from MainActivity", call > 0)
        assertTrue("arrival must run before verifiedRequest is set", verifiedWrite > call)
        val between = src.substring(call, verifiedWrite)
        assertTrue(between.contains("TierCArrival.Plan.Refuse"))
        assertTrue(between.contains("refuseTierC(outcome.request, arrival.refusal, onArrival = true)"))
        assertTrue("the arrival refusal returns before the request is stored", between.contains("return"))
    }
}
