package com.tananaev.passportreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX pass (D57 exit criterion 2 / findings.md #5) — pins [LifecycleFence]'s
 * two states and, most importantly, that two independent instances never
 * share state (the singleton trap this class exists specifically to avoid
 * — see its class doc for why a companion object/`object` was rejected).
 */
class LifecycleFenceTest {

    @Test
    fun `a fresh fence passes`() {
        val fence = LifecycleFence()
        assertTrue(fence.passes())
    }

    @Test
    fun `a retired fence does not pass`() {
        val fence = LifecycleFence()
        fence.retire()
        assertFalse(fence.passes())
    }

    @Test
    fun `retire is idempotent`() {
        val fence = LifecycleFence()
        fence.retire()
        fence.retire()
        assertFalse(fence.passes())
    }

    @Test
    fun `two independent instances do not affect each other`() {
        val first = LifecycleFence()
        val second = LifecycleFence()

        first.retire()

        assertFalse(first.passes())
        assertTrue(second.passes())
    }
}
