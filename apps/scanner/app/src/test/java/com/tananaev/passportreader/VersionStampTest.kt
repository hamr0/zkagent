package com.tananaev.passportreader

import org.junit.Assert.assertEquals
import org.junit.Test

/** §6.2 item 24 (D70(c)) — literal-expectation tests for [VersionStamp],
 *  pinned against hardcoded strings, never against [VersionStamp] itself. */
class VersionStampTest {

    @Test
    fun `format combines versionName and sha with the v-prefix and parens shape`() {
        assertEquals("v0.2.0 (abc1234)", VersionStamp.format("0.2.0", "abc1234"))
    }

    @Test
    fun `format shows the dirty suffix verbatim, unmodified`() {
        assertEquals("v0.2.0 (abc1234-dirty)", VersionStamp.format("0.2.0", "abc1234-dirty"))
    }

    @Test
    fun `format shows the no-git fallback verbatim`() {
        assertEquals("v0.2.0 (nogit)", VersionStamp.format("0.2.0", "nogit"))
    }
}
