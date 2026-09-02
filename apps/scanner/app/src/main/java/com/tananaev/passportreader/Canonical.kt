package com.tananaev.passportreader

/**
 * §6.2 item 9 — a Kotlin port of chiproof's `canonicalize()`
 * (`packages/chiproof/src/canonical.js`), reproduced exactly enough to
 * byte-match it for the claim shapes this app actually signs (D11:
 * `{ over_threshold: Boolean, threshold: Int }`). NOT a general JSON
 * canonicalizer — same restriction chiproof's own doc comment states:
 * integers only (no floats), object keys sorted by plain UTF-16 code-unit
 * order, no insignificant whitespace, arrays keep given order.
 *
 * This is read-only reuse of a published algorithm, not a modification to
 * chiproof itself (item 11: "no change to chiproof beyond what the app
 * needs" — this file changes nothing in packages/chiproof).
 */
object Canonical {

    /**
     * @param value one of: null, Boolean, Int/Long (safe-integer range),
     *   String, List<Any?>, Map<String, Any?> (with String keys)
     * @throws IllegalArgumentException on a float, non-finite number, or an
     *   unsupported value type — mirrors canonical.js's TypeError-on-float
     *   discipline: a signing canonicalizer must never guess.
     */
    fun canonicalize(value: Any?): String = stringify(value)

    private fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is String -> quote(value)
        is Int -> value.toString()
        is Long -> {
            require(value in -9007199254740991L..9007199254740991L) {
                "canonical JSON: integer out of safe range: $value"
            }
            value.toString()
        }
        is List<*> -> "[" + value.joinToString(",") { stringify(it) } + "]"
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            val map = value as Map<String, Any?>
            val keys = map.keys.sorted() // plain lexicographic == UTF-16 code-unit order for BMP keys used here
            "{" + keys.joinToString(",") { k -> "${quote(k)}:${stringify(map[k])}" } + "}"
        }
        else -> throw IllegalArgumentException("canonical JSON: unsupported value type ${value::class.java}")
    }

    /** Matches JS `JSON.stringify` string escaping for the ASCII-range
     * content this app's claims/challenges actually carry (booleans,
     * thresholds, domain strings, nonces) — control chars, backslash and
     * quote escaped; everything else passed through verbatim, same as
     * JSON.stringify's default (non-ASCII is NOT escaped by JSON.stringify). */
    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\u0008' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c.code < 0x20) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
