// apps/demo/server.mjs — M3 demo verifier website (moved from spikes/m2-handoff, D73).
//
// Implements the EU-Blueprint-shaped same-device flow recorded in
// docs/logs/M2-CAPTURE.md, Finding 1 (reference verifier `av-web-verifier-ui`):
//
//   website -> own backend  POST /ui/presentations              (create transaction)
//   QR or app link          carries the request-by-reference URI
//   app -> backend          GET  /wallet/request.jwt/{requestId} (request by reference)
//   app -> backend          POST /wallet/direct_post             (vp_token response in)
//   website -> own backend  GET  /ui/presentations/{transactionId} (poll for verdict)
//
// The browser page never carries the credential; the verdict core is chiproof.
// Mode A (default): tier A, bare evidence set `evidence: []` (D27).
// Mode B (D30, PRD v1.15): tier B, `sig-ed25519/1` REQUIRED as the reference
// default evidence delivery — verified via chiproof's evidence slot with an
// operator-pinned attester pubkey. chiproof's verdict invariants are preserved
// end-to-end: never throw, ok:false => allowed:null.
//
// Known, flagged simplifications vs the reference verifier (see README.md):
//  - (resolved 2026-08-31) /wallet/request.jwt/{id} now serves an ES256-signed
//    request object (JAR, RFC 9101), matching the reference verifier's default.
//  - The DCQL block is carried in the captured *shape*; the credential actually
//    verified is chiproof's `zkagent/1` presentation riding in `zkagent`.
//  - (resolved 2026-08-31, chiproof 0.3.0) ONE chiproof instance, both modes:
//    `evidence.require` now accepts a per-tier `{A?, B?, C?}` object, so a
//    single instance keeps tier A bare while requiring `sig-ed25519/1` at
//    tier B. The former two-instance workaround is gone; see README.md.

import { createServer } from 'node:http';
import { networkInterfaces } from 'node:os';
import { randomBytes, createPublicKey, createPrivateKey } from 'node:crypto';
import { readFileSync } from 'node:fs';
// NEW DEPENDENCY, spike-only — never added to packages/chiproof (D68 part b,
// decisions.md; finding #18). Pure-JS QR encoder; renders app_link_av as a
// scannable image instead of text-only. Pinned exact version in package.json.
import QRCode from 'qrcode';
import {
  createVerifier, realNo, sigEd25519, sigP256,
} from 'chiproof';
import { DEV_ATTESTER } from './dev-attester-key.mjs';
import { DEV_ATTESTER_P256 } from './dev-attester-key-p256.mjs';
import { signJws } from './jws.mjs';
import { DEV_REQUEST_SIGNER } from './dev-request-signer-key.mjs';
// §6.3 item 2/3/9: the demo is its own adopter, its own persistent store —
// chiproof's InMemoryNonceStore/InMemoryAttesterStore are test-only and MUST
// NOT be used once this is a persistent operator-facing run. No in-memory
// fallback exists anywhere below; JsonFileStore's constructor throws
// synchronously on any I/O/parse failure and that is left to propagate to
// process top-level (fail closed, item 9 — see the bottom of this file).
import { JsonFileStore, defaultStorePath } from './store.mjs';

// D31 (2026-09-01): the verifier accepts EITHER attester-sig plug, not one
// fixed one — the device picks whichever its Keystore actually produced (F2:
// Ed25519 is unavailable via AndroidKeyStore on the Pixel 6a). chiproof 0.4.0
// ships both plugs (src/plugs/attester-sig.js); this spike's own local
// sig-ed25519-plug.mjs is now redundant with chiproof's sigEd25519 (same
// preimage, same item shape) and is retired in favor of it.
const SIG_ED25519_KEY = 'sig-ed25519/1';
const SIG_P256_KEY = 'sig-p256/1';

// ---------------------------------------------------------------- config ----
// §6.3 item 7/10: the demo must be reachable from the phone's own browser at
// a stable, non-localhost LAN origin, not just 127.0.0.1 -- BIND_HOST is now
// configurable (default unchanged: loopback-only, matching every prior
// spike run) so an operator can bind 0.0.0.0 (or a specific LAN interface
// address) for a real device run. See startServer below -- this is the ONE
// source of truth for "what host does the listen socket actually bind."
const BIND_HOST = process.env.BIND_HOST ?? '127.0.0.1';
// SCOPE_DOMAIN's default is deliberately a SEPARATE constant, not derived
// from BIND_HOST: BIND_HOST may now be a listen address like `0.0.0.0` that
// no client ever actually connects to, but the scanner signs mode-B
// evidence with scope = the HOST the PHONE'S REQUEST actually arrived on
// (D37) -- for a LAN run that is the operator's LAN IP, which only the
// operator knows and MUST set via SCOPE_DOMAIN explicitly (see README "LAN
// run" section). Unset, this preserves every prior loopback-only default.
const DEFAULT_SCOPE_HOST = '127.0.0.1';
// Real-device finding (2026-09-01): the scanner signs mode-B evidence with
// scope = the HOST of its verified request origin (D37,
// apps/scanner/.../MainActivity.kt:876) -- e.g. `127.0.0.1`, never a port
// and never a scheme. This verifier was hardcoded to an unrelated fixed
// string ('m2-handoff.test'), so every real-device P-256 signature failed
// (`sig_invalid`, not `sig_unknown_key` -- the pinned key resolved fine,
// the scope byte in the signed preimage just didn't match). SCOPE_DOMAIN
// is set ONCE at startup (decision (a)), not per-transaction from the
// request origin, because chiproof's `createVerifier` takes `scopeDomain`
// as fixed, boot-time config (`src/index.js`: `typeof config.scopeDomain
// !== 'string'` throws) -- there is no per-call override in `verify()`, so
// a literal per-transaction derivation (b) would mean re-`createVerifier`ing
// per request, which is not what a spike (or fixed-origin deployment)
// needs. Good enough for this single-origin spike; a multi-origin
// deployment would need one verifier instance PER origin, not a per-call
// scope. §6.3 item 7/10 (LAN run): SCOPE_DOMAIN no longer defaults to
// BIND_HOST (BIND_HOST may now be a non-routable listen address like
// `0.0.0.0`) -- an operator running on the LAN MUST set SCOPE_DOMAIN to
// their own machine's actual LAN IP explicitly (see README "LAN run"
// section), the same way `SCOPE_DOMAIN=127.0.0.1` was already the
// documented override for anyone running this behind a real hostname or
// TLS terminator.
//
// Escalated, not decided (PRD-level, D37): scope is HOST ONLY here, same as
// the scanner -- but D37's origin-CONSISTENCY check (verifying the request
// object's own origin before trusting it, D34) uses the FULL
// scheme+host+port origin (`origin(req)` below). That's a deliberate,
// different granularity for two different jobs (a stable per-site
// pseudonym scope vs. an exact same-request-object check), not an
// oversight -- but it should be a written PRD decision, not an implicit
// one. Recommendation (orchestrator, not owner-decided): keep scope
// host-only, keep the consistency check on the full origin. Flagging for
// owner confirmation; did not edit the PRD file myself.
const SCOPE_DOMAIN = process.env.SCOPE_DOMAIN ?? DEFAULT_SCOPE_HOST;
// Spike-only dev secret. A real deployment supplies its own (>=16 bytes).
const CHALLENGE_SECRET =
  process.env.CHALLENGE_SECRET ?? 'm2-handoff-spike-dev-secret-not-for-production';
// Link scheme is configurable: 'https' app link (primary) or 'av' custom
// scheme (Blueprint AV Profile fallback: "at least av:// MUST be supported").
const LINK_SCHEME = process.env.LINK_SCHEME ?? 'https';
// Where the https app link points. On a real deployment this is the wallet
// app's verified app-link host; .invalid TLD here so nothing resolves by accident.
const APP_LINK_BASE = process.env.APP_LINK_BASE ?? 'https://wallet.example.invalid/authorize';
// Operator-pinned attester pubkeys for mode B (D31: either alternative is
// accepted). Defaults to the DEV-ONLY spike keypairs shared with the fake
// wallet.
const ATTESTER_KEY_ID = process.env.ATTESTER_KEY_ID ?? DEV_ATTESTER.key_id;
const ATTESTER_PUBKEY_PEM = process.env.ATTESTER_PUBKEY_PEM ?? DEV_ATTESTER.publicKeyPem;
const ATTESTER_P256_KEY_ID = process.env.ATTESTER_P256_KEY_ID ?? DEV_ATTESTER_P256.key_id;
const ATTESTER_P256_PUBKEY_PEM = process.env.ATTESTER_P256_PUBKEY_PEM ?? DEV_ATTESTER_P256.publicKeyPem;
// ES256 request-object (JAR) signing key — the reference verifier signs its
// request objects by default. Defaults to the DEV-ONLY spike keypair the
// fake wallet pins the public half of.
const REQUEST_SIGNER_KID = process.env.REQUEST_SIGNER_KID ?? DEV_REQUEST_SIGNER.kid;
const REQUEST_SIGNER_PRIVKEY_PEM = process.env.REQUEST_SIGNER_PRIVKEY_PEM ?? DEV_REQUEST_SIGNER.privateKeyPem;
// §6.3 item 6 (DP4 resolved by D74): M3 hardcodes threshold 18 -- no picker,
// no env override. The Q33 THRESHOLD env var this used to read (added to let
// an operator ask for something other than 18 and observe the scanner
// ignore it, since it hardcodes 18 itself) is REMOVED per item 6's explicit
// instruction ("remove the THRESHOLD env override or make any value other
// than 18 fatal"); the preset-bracket / per-origin-lock / named-exception
// policy D74 actually specifies is scanner-side (§6.5 S1/S2), not M3 scope.
const THRESHOLD = 18;
const DEFAULT_TTL_MS = 120_000;
const MAX_TTL_MS = 600_000;
const MAX_BODY_BYTES = 64 * 1024;
const MAX_TRANSACTIONS = 1000; // spike-grade memory cap
// §6.3 item 2/9: path to the ONE flat JSON file backing every persistent
// store (nonces, attester bindings, zktags-seen). Read at call time (inside
// makeVerifier, not here at module top level) so a test file can set this
// per-file in its own before() hook, ahead of importing/calling startServer.

// -------------------------------------------------------------- verifier ----
// ONE chiproof instance for both modes (chiproof 0.3.0: `evidence.require`
// accepts a per-tier `{A?, B?, C?}` object) — tier A stays bare (D27) while
// tier B requires EITHER attester-sig alternative (D31, chiproof 0.4.0's
// any-of `require` groups), on the same instance/nonce store. Resolves the
// former two-instance workaround; see README.md.
export function makeVerifier() {
  // §6.3 item 2/3/9: ONE flat JSON file (JsonFileStore) backs the nonce
  // store, BOTH attester-key stores, and the demo's own zktags-seen dedupe
  // state — persistent across restarts, no in-memory fallback anywhere.
  // Constructed fresh per `makeVerifier()` call (not a module-level
  // singleton) so separate server instances — e.g. one per test file that
  // sets its own DEMO_STORE_PATH — never share state through a shared
  // module import. The constructor is synchronous and throws immediately on
  // any I/O/parse failure (fail closed, item 9) — never caught here.
  const store = new JsonFileStore(process.env.DEMO_STORE_PATH ?? defaultStorePath());
  // D38 (2026-09-01): per-origin device keys, trust-on-first-sight. A real
  // device's mode-B key is no longer expected to be one operator-pinned
  // constant — it now carries its own `pubkey` and gets bound to
  // (scopeDomain, zktag) the first time it's seen. ONE namespaced view per
  // algorithm, backed by the SAME file, so separate server/verifier
  // instances — e.g. one per test file — never share bindings via a shared
  // module import; and not shared BETWEEN the two algorithms either: a
  // device that verified once under sig-p256/1 for a given zktag and later
  // (e.g. across a fallback) presented sig-ed25519/1 for the SAME zktag must
  // not collide with an unrelated algorithm's binding — each plug's key
  // space is independent (JsonFileStore#attesterView namespaces by
  // algoLabel for exactly this reason). The pinned
  // ATTESTER_*_KEY_ID/PUBKEY_PEM env override still works unchanged (D31
  // predates D38): a pinned key_id is resolved before the store is ever
  // consulted, letting the owner keep pinning a real Pixel key today, ahead
  // of the scanner sending `pubkey`.
  const attesterStoreEd25519 = store.attesterView('ed25519');
  const attesterStoreP256 = store.attesterView('p256');
  const verifier = createVerifier({
    scopeDomain: SCOPE_DOMAIN,
    challengeSecret: CHALLENGE_SECRET,
    threshold: THRESHOLD,
    stores: { nonce: store },
    tiers: { max: 'B' },
    evidence: {
      plugs: {
        [SIG_ED25519_KEY]: sigEd25519({
          keys: [{ key_id: ATTESTER_KEY_ID, pubkey: createPublicKey(ATTESTER_PUBKEY_PEM) }],
          attesterStore: attesterStoreEd25519,
        }),
        [SIG_P256_KEY]: sigP256({
          keys: [{ key_id: ATTESTER_P256_KEY_ID, pubkey: createPublicKey(ATTESTER_P256_PUBKEY_PEM) }],
          attesterStore: attesterStoreP256,
        }),
      },
      // D31: any one of the operator's accepted attester-sig alternatives
      // satisfies mode B — supersedes D30's single-required-plug framing.
      require: { A: [], B: [[SIG_ED25519_KEY, SIG_P256_KEY]] },
    },
  });
  return { verifier, store };
}

// ------------------------------------------------------------ app links ----
export function buildAppLinks(requestUri, clientId) {
  const q = `client_id=${encodeURIComponent(clientId)}&request_uri=${encodeURIComponent(requestUri)}`;
  return {
    https: `${APP_LINK_BASE}?${q}`,
    av: `av://authorize?${q}`,
  };
}

// ------------------------------------------------------------ HTML page ----
// §6.3 item 5: the browser page imports the SAME compare.mjs node --test
// exercises, via GET /compare.mjs (route above) -- read once at module load,
// not per-request (the file never changes while the process runs).
const COMPARE_JS = readFileSync(new URL('./compare.mjs', import.meta.url), 'utf8');

// §6.3 items 1-9: real, kept M3 demo page (moved off the throwaway spike
// shape, item 12) -- mobile-first, no horizontal scroll at 360px, outcome
// block ABOVE the handoff block so it's on-screen without scrolling when the
// browser regains focus after the app returns (owner device nit,
// docs/logs/M3-POC-EVIDENCE-2026-09-04.md), tier A/B vocabulary only (D19,
// never "mode A/B" on the page), no tier C control, no threshold picker, no
// ZK language, no PII (tier A/B carry none; the zktag pseudonym MAY be shown).
const PAGE = `<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>zkagent age gate demo</title>
<style>
  * { box-sizing: border-box; }
  html, body { max-width: 100%; overflow-x: hidden; }
  body { font: 15px/1.5 system-ui, sans-serif; margin: 0 auto; padding: 1rem; max-width: 32rem; color: #1a1a1a; background: #fff; }
  h1 { font-size: 1.3rem; margin: 0 0 .3rem; }
  h2 { font-size: 1.05rem; margin: 1.4rem 0 .4rem; }
  p.lead { color: #444; margin: 0 0 1rem; }
  .buttons { display: flex; flex-direction: column; gap: .6rem; margin-bottom: 1rem; }
  button { font: inherit; font-weight: 600; padding: .8rem 1rem; cursor: pointer; border: 1px solid #333; border-radius: 8px; background: #f7f7f7; width: 100%; }
  button:active { background: #eee; }
  section { border-top: 1px solid #ddd; padding-top: .8rem; margin-top: .8rem; }
  code, pre { background: #f2f2f2; border-radius: 3px; }
  code { padding: .1rem .3rem; overflow-wrap: anywhere; }
  pre.scrollpre { padding: .6rem; margin: .3rem 0; overflow-x: auto; max-width: 100%; white-space: pre; }
  a { overflow-wrap: anywhere; word-break: break-all; }
  .muted { color: #666; font-size: .85em; }
  #outcomeHeading { font-weight: bold; }
  #outcomeHeading.allowed { color: #0a7a2f; }
  #outcomeHeading.notAllowed { color: #a10000; }
  #outcomeHeading.noAnswer { color: #8a5400; }
  #alreadyRegistered { font-weight: bold; color: #a15a00; border: 1px solid #e0b060; background: #fff6e8; padding: .5rem .7rem; border-radius: 6px; margin: .4rem 0; }
  #qrimg { max-width: 240px; width: 100%; height: auto; display: block; }
  table { border-collapse: collapse; width: 100%; }
  th, td { border: 1px solid #ddd; padding: .3rem .5rem; text-align: left; font-size: .85em; overflow-wrap: anywhere; }
  th { background: #f2f2f2; }
  td.differs { background: #fff6e8; }
  .tablewrap { overflow-x: auto; max-width: 100%; }
  details summary { cursor: pointer; font-weight: 600; margin-top: 1.2rem; }
  details p { color: #444; }
</style>
<div>
<h1>zkagent age gate demo</h1>
<p class="lead">Two things a website can ask this app to prove — no name, date of birth, or any other personal field is ever sent.</p>
<div class="buttons">
  <button id="goA">Prove you're over 18</button>
  <button id="goB">Prove you're a unique adult human</button>
</div>

<section id="outcome" hidden>
  <h2 id="outcomeHeading"></h2>
  <div id="alreadyRegistered" hidden>Already registered at this site</div>
  <p id="outcomeLine"></p>
  <p class="muted">What the app sent back:</p>
  <pre id="verdictJson" class="scrollpre"></pre>
  <p id="storeState" class="muted"></p>
</section>

<section id="handoff" hidden>
  <h2>Continue on your phone</h2>
  <p><a id="applink" href="#"></a></p>
  <img id="qrimg" width="240" height="240" alt="QR code encoding the av:// handoff link">
  <p class="muted">Tap the link on this phone, or scan the QR with another phone's camera app.</p>
</section>

<section id="compareSection" hidden>
  <h2 id="compareHeader">Scan 1 done — waiting for scan 2</h2>
  <div class="tablewrap">
    <table id="compareTable">
      <thead><tr><th>Field</th><th>Scan 1</th><th>Scan 2</th><th>Same or differs</th></tr></thead>
      <tbody></tbody>
    </table>
  </div>
  <p class="muted" id="compareCaption"></p>
  <p class="muted">Kept in this browser tab's memory only — reloading the page resets it.</p>
</section>

<details>
  <summary>About this demo</summary>
  <p>The device's key is per-origin and bound to this site the first time it's seen (D38) —
  a returning device must keep presenting the same key it bound the first time, or the site
  refuses it.</p>
  <p><strong>Chip-authentication caveat:</strong> a document without chip authentication
  (<code>chip_auth: false</code>, e.g. some passports) is clone-replayable — a cloned document
  mints the identical zktag as the genuine holder's. "Unique adult human" is only as strong
  as chip authentication allows.</p>
</details>
</div>
<script type="module">
import { diffPresentations } from '/compare.mjs';

let pollTimer = null;
let lastTierAPresentation = null; // §6.3 item 5: last of the two kept tier-A presentations
const outcomeSection = document.getElementById('outcome');
const handoffSection = document.getElementById('handoff');
const outcomeHeading = document.getElementById('outcomeHeading');
const alreadyRegistered = document.getElementById('alreadyRegistered');
const outcomeLine = document.getElementById('outcomeLine');
const verdictJson = document.getElementById('verdictJson');
const storeState = document.getElementById('storeState');
const compareSection = document.getElementById('compareSection');
const compareHeader = document.getElementById('compareHeader');
const compareCaption = document.getElementById('compareCaption');
const compareTbody = document.querySelector('#compareTable tbody');

function log(...args) { console.log('[zkagent-demo]', ...args); }

function renderOutcome(tier, s) {
  outcomeSection.hidden = false;
  const v = s.verdict;
  let headingClass = 'noAnswer';
  let headingText = 'No answer';
  let line = 'The verifier could not check (' + v.reason + ').';
  if (v.allowed === true) {
    headingClass = 'allowed';
    headingText = tier === 'B' ? 'Registered as a unique adult human' : 'Proved: over 18';
    line = tier === 'B'
      ? 'This document is now registered as a unique adult human at this site.'
      : 'You proved you are over 18.';
  } else if (v.allowed === false) {
    headingClass = 'notAllowed';
    headingText = 'Not proved';
    line = 'Not proved (' + v.reason + ').';
  }
  outcomeHeading.textContent = headingText;
  outcomeHeading.className = headingClass;
  outcomeLine.textContent = line;
  // §6.3 item 4: "already registered" is its own distinct heading/block, not
  // a prefix on the allowed line -- true whenever this tier-B zktag was
  // already registered here, independent of whether this presentation's own
  // evidence also happened to check out.
  alreadyRegistered.hidden = !(tier === 'B' && s.already_registered);
  verdictJson.textContent = JSON.stringify(v, null, 2);
  storeState.textContent = tier === 'B'
    ? 'zktag already seen at this site: ' + (s.zktag_seen_before ? 'yes' : 'no')
    : '';
  log('outcome', { tier, allowed: v.allowed, reason: v.reason, already_registered: s.already_registered });
}

function renderCompare(tier, presentation) {
  if (tier !== 'A' || !presentation) return;
  const prev = lastTierAPresentation;
  lastTierAPresentation = presentation;
  if (!prev) {
    compareSection.hidden = false;
    compareHeader.textContent = 'Scan 1 done — waiting for scan 2';
    compareTbody.innerHTML = '';
    compareCaption.textContent = '';
    return;
  }
  compareHeader.textContent = 'Both scans received';
  const rows = diffPresentations(prev, presentation);
  compareTbody.innerHTML = '';
  const differingFields = [];
  for (const r of rows) {
    const tr = document.createElement('tr');
    const fmt = (v) => (v === undefined ? '(absent)' : JSON.stringify(v));
    tr.innerHTML = '<td>' + r.field + '</td><td>' + fmt(r.a) + '</td><td>' + fmt(r.b) + '</td>'
      + '<td class="' + (r.same ? '' : 'differs') + '">' + (r.same ? 'same' : 'differs') + '</td>';
    compareTbody.appendChild(tr);
    if (!r.same) differingFields.push(r.field);
  }
  compareCaption.textContent = 'Fields that differ between the two scans: ' + differingFields.join(', ')
    + ' — every other field matches. The nonce is a fresh, single-use value every scan; the '
    + 'timestamp fields differ because each challenge is minted at the moment you tap the button, '
    + 'not because anything about you differs.';
  log('tier-A compare', { differingFields });
}

async function start(tier) {
  if (pollTimer) clearInterval(pollTimer);
  const mode = tier; // wire-level field is still named "mode" (server.mjs contract, unchanged)
  const res = await fetch('/ui/presentations', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ mode }) });
  const tx = await res.json();
  outcomeSection.hidden = true;
  handoffSection.hidden = false;
  log('transaction created', { transactionId: tx.transactionId, tier });
  const a = document.getElementById('applink');
  a.href = tx.app_link; a.textContent = tx.app_link;
  document.getElementById('qrimg').src = tx.qr;
  pollTimer = setInterval(async () => {
    const r = await fetch('/ui/presentations/' + tx.transactionId);
    if (!r.ok) return;
    const s = await r.json();
    if (s.status === 'done') {
      clearInterval(pollTimer); pollTimer = null;
      log('poll done', { transactionId: tx.transactionId });
      renderOutcome(tier, s);
      renderCompare(tier, s.presentation);
    }
  }, 1000);
}
document.getElementById('goA').addEventListener('click', () => start('A'));
document.getElementById('goB').addEventListener('click', () => start('B'));
</script>`;

// -------------------------------------------------------------- helpers ----
function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY_BYTES) { reject(new Error('body_too_large')); req.destroy(); return; }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function sendJson(res, status, obj) {
  const body = JSON.stringify(obj, null, 2);
  res.writeHead(status, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(body) });
  res.end(body);
}

function b64urlToJson(s) {
  try { return JSON.parse(Buffer.from(String(s), 'base64url').toString('utf8')); }
  catch { return undefined; }
}

// ------------------------------------------------------------------ app ----
export function createApp() {
  const { verifier, store } = makeVerifier();
  const requestSignerKey = createPrivateKey(REQUEST_SIGNER_PRIVKEY_PEM);
  const byTransactionId = new Map(); // transactionId -> tx
  const byRequestId = new Map();     // requestId -> tx

  function origin(req) {
    return `http://${req.headers.host ?? BIND_HOST}`;
  }

  async function handle(req, res) {
    const url = new URL(req.url, origin(req));
    const path = url.pathname;

    // GET / — the demo age-gate page
    if (req.method === 'GET' && path === '/') {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      res.end(PAGE);
      return;
    }

    // GET /compare.mjs — the pure tier-A diff module (§6.3 item 5), served
    // as-is so the SAME file node --test imports is what the browser page
    // imports as a native ES module -- one source of truth, no bundler.
    if (req.method === 'GET' && path === '/compare.mjs') {
      res.writeHead(200, { 'content-type': 'text/javascript; charset=utf-8' });
      res.end(COMPARE_JS);
      return;
    }

    // POST /ui/presentations — RP backend initialises a transaction
    if (req.method === 'POST' && path === '/ui/presentations') {
      if (byTransactionId.size >= MAX_TRANSACTIONS) {
        sendJson(res, 503, { error: 'too_many_transactions' });
        return;
      }
      let body = {};
      try { body = JSON.parse((await readBody(req)) || '{}'); } catch { body = {}; }
      const mode = body.mode === 'B' ? 'B' : 'A';
      // ttlMs override is a SPIKE affordance so the expiry negative is testable
      // without waiting out the 2-minute default. Clamped; not part of the shape.
      let ttlMs = DEFAULT_TTL_MS;
      if (Number.isFinite(body.ttlMs) && body.ttlMs > 0) ttlMs = Math.min(body.ttlMs, MAX_TTL_MS);

      const transactionId = randomBytes(12).toString('base64url');
      const requestId = randomBytes(12).toString('base64url');
      const challenge = verifier.issueChallenge({ tier: mode, ttlMs });

      const responseUri = `${origin(req)}/wallet/direct_post`;
      const requestUri = `${origin(req)}/wallet/request.jwt/${requestId}`;
      const requestObject = {
        // OpenID4VP-style envelope in the captured shape (M2-CAPTURE Finding 1):
        // response_mode MUST be direct_post; client_id_scheme redirect_uri
        // (client id = the response_uri).
        response_type: 'vp_token',
        response_mode: 'direct_post',
        client_id: responseUri,
        client_id_scheme: 'redirect_uri',
        response_uri: responseUri,
        state: requestId,
        // DCQL block in the captured shape. Carried for wire fidelity; the
        // credential this spike actually verifies is chiproof's zkagent/1
        // presentation (`zkagent` member below).
        dcql_query: {
          credentials: [{
            id: 'proof_of_age',
            format: 'mso_mdoc',
            meta: { doctype_value: 'eu.europa.ec.av.1' },
            claims: [{ path: ['eu.europa.ec.av.1', 'age_over_18'] }],
          }],
        },
        // zkagent payload: the chiproof challenge rides in the request.
        // Mode A: tier A, bare evidence set (D27). Mode B: tier B, EITHER
        // sig-ed25519/1 OR sig-p256/1 (D31) — evidence_required carries the
        // same alternatives shape as chiproof's evidence.require, so the app
        // knows it may send whichever its Keystore actually produced.
        zkagent: {
          spec: 'zkagent/1',
          tier: mode,
          evidence_required: mode === 'B' ? [[SIG_ED25519_KEY, SIG_P256_KEY]] : [],
          evidence: [],
          challenge,
        },
      };

      const links = buildAppLinks(requestUri, responseUri);
      // D68 part b: render a real QR image of the av:// link (the actual
      // cross-device scan payload), not the https variant — a data URI so
      // no separate image route/static file is needed for a throwaway spike.
      const qrDataUrl = await QRCode.toDataURL(links.av, { margin: 1, width: 240 });
      const tx = {
        transactionId, requestId, mode, challenge, requestObject,
        status: 'pending', verdict: null, createdAt: Date.now(),
      };
      byTransactionId.set(transactionId, tx);
      byRequestId.set(requestId, tx);
      // Value-free: transactionId, mode, ttlMs, threshold only — never the
      // challenge, nonce, or anything a real device would carry. threshold
      // here is the verifier's own configured value (Q33), logged so an
      // operator can see what was asked for next to the verdict that comes
      // back below.
      // eslint-disable-next-line no-console
      console.log(`[apps/demo] tx created transactionId=${transactionId} mode=${mode} ttlMs=${ttlMs} threshold=${THRESHOLD}`);

      sendJson(res, 201, {
        transactionId,
        requestId,
        mode,
        request_uri: requestUri,
        app_link: LINK_SCHEME === 'av' ? links.av : links.https,
        app_link_av: links.av,
        app_link_https: links.https,
        qr: qrDataUrl, // data:image/png;base64,... of app_link_av (D68 part b)
      });
      return;
    }

    // GET /wallet/request.jwt/{requestId} — app fetches the request by reference
    if (req.method === 'GET' && path.startsWith('/wallet/request.jwt/')) {
      const requestId = path.slice('/wallet/request.jwt/'.length);
      const tx = byRequestId.get(requestId);
      if (!tx) { sendJson(res, 404, { error: 'unknown_request' }); return; }
      // ES256-signed request object (JAR, RFC 9101) — matches the EU reference
      // verifier's sign-by-default. Claims = exactly the request-object JSON;
      // typ per the OpenID4VP request-object convention (oauth-authz-req+jwt).
      const jws = signJws(tx.requestObject, requestSignerKey, REQUEST_SIGNER_KID);
      res.writeHead(200, {
        'content-type': 'application/oauth-authz-req+jwt',
        'content-length': Buffer.byteLength(jws),
      });
      res.end(jws);
      return;
    }

    // POST /wallet/direct_post — app POSTs the response; browser never sees it
    if (req.method === 'POST' && path === '/wallet/direct_post') {
      let raw;
      try { raw = await readBody(req); } catch { sendJson(res, 413, { error: 'body_too_large' }); return; }
      const ctype = String(req.headers['content-type'] ?? '');
      let state, vpToken;
      if (ctype.includes('application/json')) {
        let body = {};
        try { body = JSON.parse(raw || '{}'); } catch { /* fall through */ }
        state = body.state; vpToken = body.vp_token;
      } else {
        // OpenID4VP direct_post is application/x-www-form-urlencoded
        const form = new URLSearchParams(raw);
        state = form.get('state'); vpToken = form.get('vp_token');
      }
      if (!state || !vpToken) { sendJson(res, 400, { error: 'missing_state_or_vp_token' }); return; }
      const tx = byRequestId.get(state);
      if (!tx) { sendJson(res, 404, { error: 'unknown_state' }); return; }
      if (tx.status === 'done') { sendJson(res, 409, { error: 'already_responded' }); return; }

      const presentation = b64urlToJson(vpToken);
      let verdict;
      if (presentation === undefined) {
        // Not decodable — an answer was received and it did not check out.
        verdict = realNo('vp_token_undecodable');
      } else if (presentation?.challenge?.nonce !== tx.challenge.nonce) {
        // Response must answer THIS transaction's challenge. A sealed challenge
        // from some other transaction is not this transaction's answer.
        verdict = realNo('state_challenge_mismatch');
      } else {
        // chiproof is the verdict core; the single instance's per-tier
        // evidence.require does the mode routing (D30, chiproof 0.3.0).
        // verify() never throws; ok:false maps to allowed:null inside the
        // library (never {ok:false,allowed:false}).
        verdict = await verifier.verify(presentation);
      }
      tx.status = 'done';
      tx.verdict = verdict;
      // §6.3 item 5: the page's tier-A comparison table needs the RECEIVED
      // presentation payload's own fields (spec/tier/claim/challenge/evidence
      // — none of them PII for tier A/B, per §6.3 item 9's MUST NOT), not
      // just chiproof's verdict. `presentation` is a sibling field alongside
      // `verdict` in the poll response below, exactly as it arrived
      // (b64urlToJson's decode of vp_token) — chiproof's verdict itself
      // stays completely untouched by this addition.
      tx.presentation = presentation === undefined ? null : presentation;
      // §6.3 item 3/4: tier-B duplicate rejection. This is the DEMO's own
      // dedupe state -- chiproof stores nothing (D3, FR3) and its verdict
      // is never rewritten here (the wire contract with the scanner stays
      // exactly what chiproof produced); `zktagSeenBefore`/`alreadyRegistered`
      // are sibling fields the poll response adds alongside `verdict`.
      // `zktagSeenBefore` reflects the store's state BEFORE this
      // presentation touches it; `alreadyRegistered` is true whenever this
      // tier-B zktag was already registered here, regardless of whether
      // THIS presentation's own evidence also happened to check out (a
      // stale/mismatched replay of an already-registered document is still
      // "already registered", not "unregistered").
      tx.zktagSeenBefore = false;
      tx.alreadyRegistered = false;
      if (tx.mode === 'B' && typeof verdict.zktag === 'string' && verdict.zktag.length > 0) {
        tx.zktagSeenBefore = await store.hasZktagBeenSeen(SCOPE_DOMAIN, verdict.zktag);
        tx.alreadyRegistered = tx.zktagSeenBefore;
        if (verdict.ok === true && verdict.allowed === true) {
          await store.markZktagSeen(SCOPE_DOMAIN, verdict.zktag);
        }
      }
      // Value-free: no zktag, nonce, pubkey, sig, or state -- transactionId,
      // tier, and the verdict's own ok/allowed/reason/evidence are the whole
      // point (the owner's side can't see any of this otherwise, since only
      // the browser page renders the verdict today). `attesterStatus` reads
      // D38's own signal, the SAME channel the verdict already carries: the
      // plug's `attester_bound_first_sight` note rides `verdict.warnings`
      // (evidence.js's existing pass-through), and `verdict.evidence` (§6.2
      // item 9) already lists which plug verified -- no new field needed
      // here either, just describing what's already there.
      {
        const attesterStatus = Array.isArray(verdict.warnings) && verdict.warnings.includes('attester_bound_first_sight')
          ? 'bound_first_sight'
          : (Array.isArray(verdict.evidence) && verdict.evidence.length > 0 ? 'matched' : 'n/a');
        // eslint-disable-next-line no-console
        console.log(
          `[apps/demo] verdict transactionId=${tx.transactionId} tier=${verdict.tier ?? tx.mode} `
          + `threshold=${THRESHOLD} ok=${verdict.ok} allowed=${verdict.allowed} reason=${verdict.reason} `
          + `evidence=${JSON.stringify(verdict.evidence ?? [])} attester=${attesterStatus}`,
        );
      }
      sendJson(res, 200, { accepted: true });
      return;
    }

    // GET /ui/presentations/{transactionId} — page polls its backend
    if (req.method === 'GET' && path.startsWith('/ui/presentations/')) {
      const transactionId = path.slice('/ui/presentations/'.length);
      const tx = byTransactionId.get(transactionId);
      if (!tx) { sendJson(res, 404, { error: 'unknown_transaction' }); return; }
      if (tx.status !== 'done') { sendJson(res, 200, { status: 'pending' }); return; }
      // §6.3 item 3/4: zktag_seen_before/already_registered are sibling
      // fields alongside chiproof's own, untouched verdict -- see the
      // direct_post handler above for why they are computed there.
      sendJson(res, 200, {
        status: 'done',
        verdict: tx.verdict,
        presentation: tx.presentation,
        zktag_seen_before: tx.zktagSeenBefore,
        already_registered: tx.alreadyRegistered,
      });
      return;
    }

    sendJson(res, 404, { error: 'not_found' });
  }

  return createServer((req, res) => {
    handle(req, res).catch((err) => {
      // Wire-level failure only. Verdict semantics live in chiproof; an HTTP
      // 500 is "no answer", never an allowed:false.
      try { sendJson(res, 500, { error: 'internal', detail: String(err?.message ?? err) }); }
      catch { /* socket gone */ }
    });
  });
}

// §6.3 item 7/10: every non-internal IPv4 address on this machine, so an
// operator running BIND_HOST=0.0.0.0 sees the actual LAN URL(s) to open on
// the phone -- printed at startup, never guessed at or hardcoded.
function lanUrls(port) {
  const nets = networkInterfaces();
  const urls = [];
  for (const addrs of Object.values(nets)) {
    for (const addr of addrs ?? []) {
      if (addr.family === 'IPv4' && !addr.internal) urls.push(`http://${addr.address}:${port}`);
    }
  }
  return urls;
}

export function startServer(port = 0) {
  // createApp() runs makeVerifier() synchronously, which constructs the
  // JsonFileStore synchronously -- a bad DEMO_STORE_PATH throws HERE,
  // before any socket is opened (fail closed, item 9).
  const server = createApp();
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, BIND_HOST, () => {
      const actual = server.address().port;
      resolve({
        server,
        port: actual,
        url: `http://${BIND_HOST}:${actual}`,
        lanUrls: lanUrls(actual),
        close: () => new Promise((r) => server.close(r)),
      });
    });
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  let started;
  try {
    started = await startServer(Number(process.env.PORT ?? 8787));
  } catch (err) {
    // Fail closed (item 9): a bad/unwritable DEMO_STORE_PATH, or any other
    // startup-time failure, exits non-zero with a clear message -- never a
    // silent in-memory fallback, never a bare stack trace.
    // eslint-disable-next-line no-console
    console.error(`[apps/demo] FATAL: could not start: ${err?.message ?? err}`);
    process.exit(1);
  }
  const { url, lanUrls: urls } = started;
  // eslint-disable-next-line no-console
  console.log(`apps/demo verifier listening on ${url} (link scheme: ${LINK_SCHEME}, bind host: ${BIND_HOST})`);
  if (urls.length > 0) {
    // eslint-disable-next-line no-console
    console.log(`  LAN: ${urls.join(', ')}`);
  } else if (BIND_HOST !== '127.0.0.1') {
    // eslint-disable-next-line no-console
    console.log('  (no non-internal IPv4 interface found -- only loopback is reachable)');
  }
}
