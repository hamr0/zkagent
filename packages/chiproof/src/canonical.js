// SPDX-License-Identifier: Apache-2.0
/**
 * Canonical JSON — a JCS-like (RFC 8785-flavoured) serialization used only so two
 * parties signing/verifying the same object produce identical bytes. This is NOT a
 * full RFC 8785 implementation: it exists to canonicalize the small, flat-ish
 * objects this package signs (challenges), not arbitrary JSON from the wild.
 *
 * Differences from the full spec:
 *   - Numbers MUST be safe integers (or passed as strings) — no floats, no
 *     NaN/Infinity, no exponents. RFC 8785's ECMAScript number serialization is
 *     exactly the kind of subtlety ("what does -0 look like? what about 1e21?")
 *     a signing canonicalizer should not depend on: floats are rejected outright
 *     rather than serialized ambiguously.
 *   - `undefined`, functions, and symbols throw rather than being silently
 *     dropped or coerced (a dropped field is a dropped commitment when signing).
 *
 * Everything else follows RFC 8785's spirit: object keys sorted recursively
 * (plain `Array.prototype.sort()`, UTF-16 code unit order), no insignificant
 * whitespace, arrays keep their given order.
 */
import { createHash } from 'node:crypto';

/**
 * @param {unknown} value
 * @returns {string} canonical JSON text
 * @throws {TypeError} on a float, non-finite number, or unsupported value type
 */
export function canonicalize(value) {
  return stringify(value);
}

function stringify(value) {
  if (value === null) return 'null';
  const t = typeof value;
  if (t === 'boolean') return value ? 'true' : 'false';
  if (t === 'string') return JSON.stringify(value);
  if (t === 'number') return stringifyNumber(value);
  if (Array.isArray(value)) return `[${value.map(stringify).join(',')}]`;
  if (t === 'object') {
    const keys = Object.keys(value).sort();
    return `{${keys.map((k) => `${JSON.stringify(k)}:${stringify(value[k])}`).join(',')}}`;
  }
  throw new TypeError(`canonical JSON: unsupported value type "${t}"`);
}

function stringifyNumber(n) {
  if (!Number.isInteger(n)) {
    throw new TypeError(`canonical JSON: numbers must be integers or strings, got ${n}`);
  }
  if (!Number.isSafeInteger(n)) {
    throw new TypeError(`canonical JSON: integer out of safe range: ${n}`);
  }
  return String(n);
}

/**
 * sha256(canonicalize(value)).
 *
 * @param {unknown} value
 * @returns {Buffer}
 * @throws {TypeError} propagated from canonicalize() on an unsignable value
 */
export function sha256(value) {
  return createHash('sha256').update(canonicalize(value), 'utf8').digest();
}
