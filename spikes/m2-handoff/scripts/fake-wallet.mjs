#!/usr/bin/env node
// spikes/m2-handoff/scripts/fake-wallet.mjs — headless stand-in for the phone.
// Plays the AV app's role in the captured same-device flow so the full
// roundtrip is provable on this machine without the Pixel:
//   POST /ui/presentations (as the page would) -> follow request_uri ->
//   GET /wallet/request.jwt/{id} -> build a zkagent/1 presentation ->
//   POST /wallet/direct_post (form-encoded, per OpenID4VP direct_post) ->
//   poll GET /ui/presentations/{transactionId} for the verdict.
//
// Tiers:
//   --tier A (default) — mode A: bare evidence set (D27)
//   --tier B           — mode B: SYNTHETIC zktag (no chip in this spike) +
//                        sig-ed25519/1 evidence signed with the DEV-ONLY
//                        attester key (D30 default mode-B delivery)
// Modes:
//   valid    (default) — honest client; expects allowed:true
//   tamper   — (tier A) edits challenge.expires_at after fetching the request;
//              the D20 HMAC seal breaks => nonce_forged; expects allowed !== true
//   expired  — (tier A) short-TTL challenge, waited out => challenge_expired
//   wrongkey — (tier B) signs with a fresh keypair under the pinned key_id
//              => plug's valid:false path (sig_invalid); expects allowed !== true
//   missing  — (tier B) sends evidence: [] on a tier-B challenge
//              => whatever chiproof's contract says (observed: evidence_required_missing)
//
// Usage: node scripts/fake-wallet.mjs [--base http://127.0.0.1:8787]
//          [--tier A|B] [--mode valid|tamper|expired|wrongkey|missing]

import { createPrivateKey, createPublicKey, generateKeyPairSync, sign as edSign } from 'node:crypto';
import { sigMessage, SIG_ED25519_KEY } from '../sig-ed25519-plug.mjs';
import { DEV_ATTESTER } from '../dev-attester-key.mjs';
import { verifyJws, REQUEST_OBJECT_TYP } from '../jws.mjs';
import { DEV_REQUEST_SIGNER } from '../dev-request-signer-key.mjs';

const args = process.argv.slice(2);
function arg(name, dflt) {
  const i = args.indexOf(`--${name}`);
  return i >= 0 && args[i + 1] ? args[i + 1] : dflt;
}
const BASE = arg('base', 'http://127.0.0.1:8787');
const TIER = arg('tier', 'A');
const MODE = arg('mode', 'valid');
if (!['A', 'B'].includes(TIER)) { console.error(`unknown --tier ${TIER}`); process.exit(2); }
const MODES = TIER === 'A' ? ['valid', 'tamper', 'expired'] : ['valid', 'wrongkey', 'missing'];
if (!MODES.includes(MODE)) {
  console.error(`unknown --mode ${MODE} for tier ${TIER} (valid: ${MODES.join('|')})`); process.exit(2);
}

// SYNTHETIC zktag: this spike has no chip and no scanner app — a real mode-B
// client derives the zktag from the document number (D9). Clearly labeled so
// no transcript ever reads as chip-backed.
const SYNTHETIC_ZKTAG = 'SYNTHETIC-DEV-ZKTAG-no-chip-in-this-spike';

// The wallet-side attester key. DEV-ONLY (see dev-attester-key.mjs); a real
// attester generates and holds its own private key (D30).
const SCOPE_DOMAIN = process.env.SCOPE_DOMAIN ?? 'm2-handoff.test';

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const log = (step, obj) => console.log(`\n== ${step} ==\n${typeof obj === 'string' ? obj : JSON.stringify(obj, null, 2)}`);

// 1. The page's role: create the transaction.
const createBody = { ...(TIER === 'B' ? { mode: 'B' } : {}), ...(MODE === 'expired' ? { ttlMs: 80 } : {}) };
const createRes = await fetch(`${BASE}/ui/presentations`, {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify(createBody),
});
const tx = await createRes.json();
log(`1. POST /ui/presentations -> ${createRes.status}`, tx);

// 2. The app's role: the app link carries request_uri; fetch the request by reference.
const linkUrl = new URL(tx.app_link.replace(/^av:\/\//, 'https://av-scheme-host/'));
const requestUri = linkUrl.searchParams.get('request_uri');
if (requestUri !== tx.request_uri) {
  console.error('app link request_uri does not match request_uri'); process.exit(2);
}
const reqRes = await fetch(requestUri);
const jws = await reqRes.text();
log(`2. GET ${new URL(requestUri).pathname} -> ${reqRes.status} (${reqRes.headers.get('content-type')})`, `${jws.slice(0, 72)}… (compact JWS, ${jws.length} chars)`);

// Verify the ES256-signed request object (JAR) against the pinned request-
// signer pubkey BEFORE trusting anything in it — a wallet acting on an
// unverified request can be pointed at an attacker's response_uri. The pin is
// the DEV-ONLY spike key by default (see dev-request-signer-key.mjs).
const signerPub = createPublicKey(process.env.REQUEST_SIGNER_PUBKEY_PEM ?? DEV_REQUEST_SIGNER.publicKeyPem);
const verified = verifyJws(jws, signerPub);
if (!verified.valid || verified.header.typ !== REQUEST_OBJECT_TYP) {
  console.error(`REFUSED: request object JWS verification failed (${verified.valid ? 'typ_mismatch' : verified.reason}) — not POSTing direct_post`);
  process.exit(3);
}
log('2a. request JWS verified', { alg: verified.header.alg, typ: verified.header.typ, kid: verified.header.kid });
const requestObject = verified.payload;

// 3. Build the presentation from the request's chiproof challenge.
const challenge = requestObject.zkagent.challenge;
if (MODE === 'tamper') {
  challenge.expires_at += 60_000; // breaks the D20 HMAC seal -> nonce_forged
  log('3a. TAMPER', 'challenge.expires_at bumped +60s after minting');
}
if (MODE === 'expired') {
  log('3a. EXPIRE', 'waiting 200ms for the 80ms challenge to lapse');
  await sleep(200);
}
// D28 note: current_date / max_scan_age coarsening is a CLIENT-side concern
// and would happen right here, before the presentation is built. Neither the
// bare tier-A flow nor the sig-ed25519/1 tier-B flow carries scan-dated
// evidence, so there is nothing to coarsen; a real client presenting
// scan-backed evidence coarsens current_date at this point, never on the verifier.
const claim = { over_threshold: true, threshold: challenge.threshold };
const presentation = { spec: 'zkagent/1', tier: TIER, claim, challenge, evidence: [] };

if (TIER === 'B') {
  presentation.zktag = SYNTHETIC_ZKTAG;
  if (MODE === 'missing') {
    log('3a. MISSING', 'tier-B challenge answered with evidence: [] (sig-ed25519/1 required by the verifier)');
  } else {
    let privateKey = createPrivateKey(DEV_ATTESTER.privateKeyPem);
    let keyId = DEV_ATTESTER.key_id;
    if (MODE === 'wrongkey') {
      privateKey = generateKeyPairSync('ed25519').privateKey; // NOT the pinned key
      log('3a. WRONGKEY', `signing with a freshly generated keypair under pinned key_id "${keyId}"`);
    }
    const sig = edSign(null, sigMessage(claim, challenge.nonce, SCOPE_DOMAIN, presentation.zktag), privateKey).toString('base64');
    presentation.evidence = [{ type: 'sig-ed25519', version: 1, data: { key_id: keyId, sig } }];
  }
}
log('3. presentation (vp_token payload)', presentation);

// 4. POST the response back — form-encoded, per OpenID4VP direct_post.
const vpToken = Buffer.from(JSON.stringify(presentation)).toString('base64url');
const dpRes = await fetch(requestObject.response_uri, {
  method: 'POST',
  headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ state: requestObject.state, vp_token: vpToken }).toString(),
});
log(`4. POST /wallet/direct_post -> ${dpRes.status}`, await dpRes.json());

// 5. The page's role again: poll for the verdict.
let verdict = null;
for (let i = 0; i < 25; i++) {
  const pollRes = await fetch(`${BASE}/ui/presentations/${tx.transactionId}`);
  const status = await pollRes.json();
  if (status.status === 'done') { verdict = status.verdict; log(`5. GET /ui/presentations/${tx.transactionId} -> ${pollRes.status}`, status); break; }
  await sleep(200);
}
if (!verdict) { console.error('poll never completed'); process.exit(2); }

// Invariant check (chiproof M1 spec §3): {ok:false, allowed:false} must not exist.
if (verdict.ok === false && verdict.allowed !== null) {
  console.error('INVARIANT VIOLATION: ok:false without allowed:null'); process.exit(2);
}
const expectedAllowed = MODE === 'valid';
const pass = expectedAllowed ? verdict.allowed === true : verdict.allowed !== true;
console.log(`\nRESULT tier=${TIER} mode=${MODE}: allowed=${verdict.allowed} reason=${verdict.reason} -> ${pass ? 'AS EXPECTED' : 'UNEXPECTED'}`);
process.exit(pass ? 0 : 1);
