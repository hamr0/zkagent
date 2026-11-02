package com.tananaev.passportreader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D121 shared truth table. The SAME list (same inputs, same expectation, same order) is pinned on the site side in
 * packages/chiproof/tests/integration/tier-c.test.js (NAME_WORD_TABLE), so chiproof and the app cannot drift on which
 * names are left with no word. `true` = at least one word is left = the request is accepted. Every input is written with
 * explicit escapes so the code points are unambiguous. Expectations were derived from reading TierCNameMatch.kt
 * (APOSTROPHES, isSeparator, Char.isWhitespace, NFC, mark stripping), not from any chiproof constant.
 */
class TierCNameWordsTest {

    private val table: List<Pair<String, Boolean>> = listOf(
        "'" to false, "’" to false, "‘" to false, "ʼ" to false, "`" to false, "´" to false,
        "-" to false, "‐" to false, "‑" to false, "‒" to false, "–" to false,
        "\u2014" to false, "\u2015" to false, "\u2212" to false, "\u2E3A" to false, "\u2E3B" to false,
        "\uFE58" to false, "\uFE63" to false, "\uFF0D" to false,
        "\u301C" to true, "\u3030" to true, "~" to true,
        "<" to false, "<<" to false, "" to false, "   " to false, "\t\n\r" to false,
        " " to false, "　" to false, " " to false, "\u001F" to false,
        "﻿" to true,
        "́" to false, "-́" to false,
        "≮" to true,
        "'-<  ’" to false,
        "O'Neil" to true, "O’Neil" to true, "Jean-Pierre" to true, "Jean–Pierre" to true, "Jean\u2014Pierre" to true, "Jean\u2212Pierre" to true, "  Anna  " to true,
        "Müller" to true, "ß" to true, "'A'" to true, "A" to true, "😀" to true,
    )

    private fun request(given: String, surname: String): JSONObject {
        val p = JSONObject().put("name", "name_match").put("given_names", given).put("surname", surname)
        val c = JSONObject().put("predicates", JSONArray().put(p))
        return JSONObject().put("zkagent", JSONObject().put("spec", "zkagent/1").put("tier", "C").put("challenge", c))
    }

    @Test
    fun `a name field with no word left is refused whole as name_fields_malformed, any other is accepted (D121 table)`() {
        var accepted = 0
        var refused = 0
        for ((input, expectWord) in table) {
            assertEquals("siteWords " + input.map { it.code }, expectWord, TierCNameMatch.siteWords(input).isNotEmpty())
            for (req in listOf(request(input, "Examplesson"), request("Testa", input))) {
                val parsed = TierCRequest.parse(req)
                if (expectWord) {
                    assertTrue("accepted " + input.map { it.code }, parsed is TierCRequest.Parse.Ok)
                } else {
                    assertEquals("refused " + input.map { it.code }, TierCRequest.Parse.Refuse("name_fields_malformed"), parsed)
                }
            }
            if (expectWord) accepted++ else refused++
        }
        assertTrue(accepted > 10 && refused > 10) // non-vacuous: the table both includes and excludes
    }

    @Test
    fun `apostrophes are dropped and hyphens and dashes split words (D121)`() {
        assertEquals(1, TierCNameMatch.siteWords("O'Neil").size)
        assertEquals(5, TierCNameMatch.siteWords("O'Neil")[0].size)
        assertEquals(2, TierCNameMatch.siteWords("Jean-Pierre").size)
        assertEquals(2, TierCNameMatch.siteWords("Jean–Pierre").size)
        assertEquals(2, TierCNameMatch.siteWords("Jean\u2014Pierre").size)
        assertEquals(2, TierCNameMatch.siteWords("Jean\u2212Pierre").size)
        assertEquals(0, TierCNameMatch.siteWords("\u2014").size)
        assertEquals(1, TierCNameMatch.siteWords("\u301C").size) // a wave dash is not a hyphen
    }

    @Test
    fun `a site name with a dash or minus sign matches the document name written with the MRZ filler (D121)`() {
        fun fields(given: String, sur: String) =
            TierCFields.fromRaw("270126", "850101", "NLD", "SPE123456", sur, given, TierCFields.MrzForm.TD1)
        for (sep in listOf("-", "\u2013", "\u2014", "\u2212")) {
            assertEquals("sep " + sep.map { it.code }, Answer.True,
                TierCNameMatch.matches("Jean${sep}Pierre", "De Vries", fields("JEAN<PIERRE", "DE<VRIES")))
        }
        // control: a different name still does not match
        assertEquals(Answer.False, TierCNameMatch.matches("Jean\u2014Paul", "De Vries", fields("JEAN<PIERRE", "DE<VRIES")))
    }
}
