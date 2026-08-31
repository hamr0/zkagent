// spikes/m2-handoff/jws.mjs — minimal compact-JWS ES256 sign/verify, stdlib only.
// ES256 = ECDSA over P-256 with SHA-256, signature as raw R||S (ieee-p1363),
// per RFC 7518 §3.4. Used for the OpenID4VP request object (JAR, RFC 9101),
// matching the EU reference verifier's sign-by-default (M2-CAPTURE Finding 1).
// No npm dependency — node:crypto only.
import { sign as ecSign, verify as ecVerify } from 'node:crypto';

const b64u = (buf) => Buffer.from(buf).toString('base64url');

// typ per the OpenID4VP/JAR request-object convention (RFC 9101 §10.8).
export const REQUEST_OBJECT_TYP = 'oauth-authz-req+jwt';

/** Compact JWS over `payload` (claims = exactly the request-object JSON). */
export function signJws(payload, privateKey, kid) {
  const header = { alg: 'ES256', typ: REQUEST_OBJECT_TYP, ...(kid ? { kid } : {}) };
  const signingInput = `${b64u(JSON.stringify(header))}.${b64u(JSON.stringify(payload))}`;
  const sig = ecSign('sha256', Buffer.from(signingInput, 'utf8'), { key: privateKey, dsaEncoding: 'ieee-p1363' });
  return `${signingInput}.${b64u(sig)}`;
}

/**
 * Never throws. => {valid:true, header, payload} | {valid:false, reason}.
 * Pins alg to ES256 — an attacker-chosen alg (e.g. none/HS256) is refused.
 */
export function verifyJws(token, publicKey) {
  try {
    if (typeof token !== 'string') return { valid: false, reason: 'not_a_string' };
    const parts = token.split('.');
    if (parts.length !== 3) return { valid: false, reason: 'not_compact_jws' };
    const [h, p, s] = parts;
    const header = JSON.parse(Buffer.from(h, 'base64url').toString('utf8'));
    if (header.alg !== 'ES256') return { valid: false, reason: 'alg_mismatch' };
    const sig = Buffer.from(s, 'base64url');
    if (sig.length !== 64) return { valid: false, reason: 'signature_malformed' };
    const good = ecVerify('sha256', Buffer.from(`${h}.${p}`, 'utf8'), { key: publicKey, dsaEncoding: 'ieee-p1363' }, sig);
    if (!good) return { valid: false, reason: 'signature_invalid' };
    return { valid: true, header, payload: JSON.parse(Buffer.from(p, 'base64url').toString('utf8')) };
  } catch {
    return { valid: false, reason: 'jws_malformed' };
  }
}
