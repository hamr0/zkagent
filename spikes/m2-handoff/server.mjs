// spikes/m2-handoff/server.mjs — M2 POC step (b): test verifier website.
// THROWAWAY SPIKE (convention: spikes/m0, m1-*). Runs for real; never shipped.
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
//  - TWO chiproof instances (one per mode): chiproof's `evidence.require` is
//    global, not per-tier, so one instance cannot require `sig-ed25519/1` for
//    mode B while keeping mode A bare — flagged upstream; chiproof consumed as-is.

import { createServer } from 'node:http';
import { randomBytes, createPublicKey, createPrivateKey } from 'node:crypto';
import { createVerifier, InMemoryNonceStore, realNo } from 'chiproof';
import { sigEd25519, SIG_ED25519_KEY } from './sig-ed25519-plug.mjs';
import { DEV_ATTESTER } from './dev-attester-key.mjs';
import { signJws } from './jws.mjs';
import { DEV_REQUEST_SIGNER } from './dev-request-signer-key.mjs';

// ---------------------------------------------------------------- config ----
const SCOPE_DOMAIN = process.env.SCOPE_DOMAIN ?? 'm2-handoff.test';
// Spike-only dev secret. A real deployment supplies its own (>=16 bytes).
const CHALLENGE_SECRET =
  process.env.CHALLENGE_SECRET ?? 'm2-handoff-spike-dev-secret-not-for-production';
// Link scheme is configurable: 'https' app link (primary) or 'av' custom
// scheme (Blueprint AV Profile fallback: "at least av:// MUST be supported").
const LINK_SCHEME = process.env.LINK_SCHEME ?? 'https';
// Where the https app link points. On a real deployment this is the wallet
// app's verified app-link host; .invalid TLD here so nothing resolves by accident.
const APP_LINK_BASE = process.env.APP_LINK_BASE ?? 'https://wallet.example.invalid/authorize';
// Operator-pinned attester pubkey for sig-ed25519/1 (mode B). Defaults to the
// DEV-ONLY spike keypair shared with the fake wallet.
const ATTESTER_KEY_ID = process.env.ATTESTER_KEY_ID ?? DEV_ATTESTER.key_id;
const ATTESTER_PUBKEY_PEM = process.env.ATTESTER_PUBKEY_PEM ?? DEV_ATTESTER.publicKeyPem;
// ES256 request-object (JAR) signing key — the reference verifier signs its
// request objects by default. Defaults to the DEV-ONLY spike keypair the
// fake wallet pins the public half of.
const REQUEST_SIGNER_KID = process.env.REQUEST_SIGNER_KID ?? DEV_REQUEST_SIGNER.kid;
const REQUEST_SIGNER_PRIVKEY_PEM = process.env.REQUEST_SIGNER_PRIVKEY_PEM ?? DEV_REQUEST_SIGNER.privateKeyPem;
const DEFAULT_TTL_MS = 120_000;
const MAX_TTL_MS = 600_000;
const MAX_BODY_BYTES = 64 * 1024;
const MAX_TRANSACTIONS = 1000; // spike-grade memory cap

// -------------------------------------------------------------- verifiers ---
// One chiproof instance per mode: `evidence.require` is instance-global in
// chiproof, so requiring sig-ed25519/1 for mode B on the same instance would
// break mode A's bare presentations (evidence_required_missing). Both share
// CHALLENGE_SECRET; each transaction is verified by exactly one instance
// (routed by tx.mode), and the state<->challenge binding below keeps a nonce
// from ever reaching the other instance's store.
export function makeVerifiers() {
  const common = {
    scopeDomain: SCOPE_DOMAIN,
    challengeSecret: CHALLENGE_SECRET,
    // InMemoryNonceStore is test-only; this is a single-process demo/spike,
    // the exact case the explicit override exists for (chiproof.context.md).
    allowInMemoryStore: true,
  };
  const A = createVerifier({
    ...common,
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
  });
  const B = createVerifier({
    ...common,
    stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
    tiers: { max: 'B' },
    evidence: {
      plugs: {
        [SIG_ED25519_KEY]: sigEd25519({
          keys: [{ key_id: ATTESTER_KEY_ID, pubkey: createPublicKey(ATTESTER_PUBKEY_PEM) }],
        }),
      },
      require: [SIG_ED25519_KEY], // D30: default mode-B evidence delivery
    },
  });
  return { A, B };
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
const PAGE = `<!doctype html>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>m2-handoff spike — age gate demo</title>
<style>
  body { font: 15px/1.5 system-ui, sans-serif; max-width: 44rem; margin: 3rem auto; padding: 0 1rem; }
  button { font: inherit; padding: .6rem 1.2rem; cursor: pointer; margin-right: .5rem; }
  code, pre { background: #f2f2f2; padding: .1rem .3rem; border-radius: 3px; overflow-x: auto; }
  pre { padding: .6rem; white-space: pre-wrap; word-break: break-all; }
  .muted { color: #666; font-size: .85em; }
  #verdict { font-weight: bold; }
</style>
<h1>Age gate demo (m2-handoff spike)</h1>
<p>Throwaway M2 POC verifier. Mode A: tier A, bare evidence (D27) — captcha-grade.
Mode B: tier B, <code>sig-ed25519/1</code> required (D30).</p>
<button id="go" data-mode="A">Verify your age (mode A)</button>
<button id="goB" data-mode="B">Verify your age (mode B)</button>
<div id="out" hidden>
  <h2>Same-device app link</h2>
  <p><a id="applink" href="#"></a></p>
  <p class="muted">custom-scheme variant: <code id="avlink"></code></p>
  <h2>Cross-device</h2>
  <pre id="qrtext"></pre>
  <p class="muted">TODO: render this link as a QR image (cross-device-only polish;
  no QR dependency without escalation — link shown as text for now).</p>
  <h2>Status</h2>
  <p id="status">waiting for the app…</p>
  <p id="verdict"></p>
  <pre id="raw"></pre>
</div>
<script>
let pollTimer = null;
async function start(mode) {
  if (pollTimer) clearInterval(pollTimer);
  const res = await fetch('/ui/presentations', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ mode }) });
  const tx = await res.json();
  document.getElementById('out').hidden = false;
  document.getElementById('status').textContent = 'waiting for the app… (mode ' + mode + ')';
  document.getElementById('verdict').textContent = '';
  document.getElementById('raw').textContent = '';
  const a = document.getElementById('applink');
  a.href = tx.app_link; a.textContent = tx.app_link;
  document.getElementById('avlink').textContent = tx.app_link_av;
  document.getElementById('qrtext').textContent = tx.app_link;
  pollTimer = setInterval(async () => {
    const r = await fetch('/ui/presentations/' + tx.transactionId);
    if (!r.ok) return;
    const s = await r.json();
    if (s.status === 'done') {
      clearInterval(pollTimer); pollTimer = null;
      document.getElementById('status').textContent = 'response received';
      document.getElementById('verdict').textContent =
        s.verdict.allowed === true ? 'ALLOWED (over threshold)'
        : s.verdict.allowed === false ? 'NOT ALLOWED (' + s.verdict.reason + ')'
        : 'NO ANSWER — verifier could not check (' + s.verdict.reason + ')';
      document.getElementById('raw').textContent = JSON.stringify(s.verdict, null, 2);
    }
  }, 1000);
}
document.getElementById('go').addEventListener('click', () => start('A'));
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
  const verifiers = makeVerifiers();
  const requestSignerKey = createPrivateKey(REQUEST_SIGNER_PRIVKEY_PEM);
  const byTransactionId = new Map(); // transactionId -> tx
  const byRequestId = new Map();     // requestId -> tx

  function origin(req) {
    return `http://${req.headers.host ?? '127.0.0.1'}`;
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
      const challenge = verifiers[mode].issueChallenge({ tier: mode, ttlMs });

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
        // Mode A: tier A, bare evidence set (D27). Mode B: tier B,
        // sig-ed25519/1 required as the reference default evidence (D30).
        zkagent: {
          spec: 'zkagent/1',
          tier: mode,
          evidence_required: mode === 'B' ? [SIG_ED25519_KEY] : [],
          evidence: [],
          challenge,
        },
      };

      const links = buildAppLinks(requestUri, responseUri);
      const tx = {
        transactionId, requestId, mode, challenge, requestObject,
        status: 'pending', verdict: null, createdAt: Date.now(),
      };
      byTransactionId.set(transactionId, tx);
      byRequestId.set(requestId, tx);

      sendJson(res, 201, {
        transactionId,
        requestId,
        mode,
        request_uri: requestUri,
        app_link: LINK_SCHEME === 'av' ? links.av : links.https,
        app_link_av: links.av,
        app_link_https: links.https,
        qr: null, // TODO: QR data-URL rendering — cross-device-only polish;
                  // no QR npm dependency without escalating. Link is the QR payload.
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
        // from some other transaction is not this transaction's answer. (This
        // also keeps each nonce inside its own mode-verifier's store.)
        verdict = realNo('state_challenge_mismatch');
      } else {
        // chiproof is the verdict core, routed by the transaction's mode.
        // verify() never throws; ok:false maps to allowed:null inside the
        // library (never {ok:false,allowed:false}).
        verdict = await verifiers[tx.mode].verify(presentation);
      }
      tx.status = 'done';
      tx.verdict = verdict;
      sendJson(res, 200, { accepted: true });
      return;
    }

    // GET /ui/presentations/{transactionId} — page polls its backend
    if (req.method === 'GET' && path.startsWith('/ui/presentations/')) {
      const transactionId = path.slice('/ui/presentations/'.length);
      const tx = byTransactionId.get(transactionId);
      if (!tx) { sendJson(res, 404, { error: 'unknown_transaction' }); return; }
      if (tx.status !== 'done') { sendJson(res, 200, { status: 'pending' }); return; }
      sendJson(res, 200, { status: 'done', verdict: tx.verdict });
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

export function startServer(port = 0) {
  const server = createApp();
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => {
      const actual = server.address().port;
      resolve({
        server,
        port: actual,
        url: `http://127.0.0.1:${actual}`,
        close: () => new Promise((r) => server.close(r)),
      });
    });
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const { url } = await startServer(Number(process.env.PORT ?? 8787));
  console.log(`m2-handoff spike verifier listening on ${url} (link scheme: ${LINK_SCHEME})`);
}
