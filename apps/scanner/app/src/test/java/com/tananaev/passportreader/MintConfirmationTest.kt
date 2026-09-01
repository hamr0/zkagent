package com.tananaev.passportreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §6.2 item 15 (D43 extension, 2026-09) — pins that ONLY a genuinely
 * accepted delivery (`DeliveryResult.Accepted`, HTTP 2xx) confirms success
 * to the user; every other `DeliveryResult` (Rejected, NoResponseUri,
 * TransportFailed) must NOT — a signed-but-undelivered presentation must
 * never render as a success.
 */
class MintConfirmationTest {

    @Test
    fun `an accepted delivery confirms success`() {
        assertTrue(MintConfirmation.confirmsSuccess(deliveryAccepted = true))
    }

    @Test
    fun `anything other than an accepted delivery does not confirm success`() {
        // Stands in for DeliveryResult.Rejected / NoResponseUri / TransportFailed —
        // all three collapse to deliveryAccepted = false at the call site.
        assertFalse(MintConfirmation.confirmsSuccess(deliveryAccepted = false))
    }
}
