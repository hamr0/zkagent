// FIX pass (finding #21, 2026-09-03): drives the spike exactly the way
// MainActivity.presentBareA / presentBareAOnBackground do for a mode-A
// (tier-A) verified handoff, WITHOUT an Android device/JVM bridge — this is
// the "at minimum, prove the JSON your Kotlin builder emits verifies as
// tier A allowed:true" fallback the fix's brief calls for, since driving the
// real Kotlin code from the JVM isn't available in this worktree. The
// presentation object below is constructed by the SAME rules as
// HandoffClient.buildPresentation("A", claim, challenge, null, emptyList())
// + MainActivity.presentBareAOnBackground's claim map — read side by side
// with that function before trusting this script matches it.
//
// Usage: node verify-mode-a-bare.mjs <baseUrl>
//   node verify-mode-a-bare.mjs http://localhost:18790

const base = process.argv[2] ?? 'http://localhost:18790';

async function createTx() {
  const res = await fetch(`${base}/ui/presentations`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ mode: 'A', ttlMs: 600000 }),
  });
  if (res.status !== 201) throw new Error(`create tx: HTTP ${res.status}`);
  return res.json();
}

async function fetchRequestObject(requestUri) {
  // The real app fetches a signed JWS; this script only needs the RAW
  // request-object JSON's zkagent.challenge (nonce/threshold/tier/etc), the
  // same fields HandoffClient/RequestTrust extract AFTER verifying the JWS
  // — verifying the JWS itself is RequestTrust's job, already covered by
  // its own JVM unit tests, not re-proven here.
  const res = await fetch(requestUri);
  const jws = await res.text();
  const payloadB64 = jws.split('.')[1];
  const json = Buffer.from(payloadB64, 'base64url').toString('utf8');
  return JSON.parse(json);
}

// Mirrors HandoffClient.buildPresentation exactly: spec/tier/claim/
// challenge/zktag(if non-null)/evidence.
function buildPresentation(tier, claim, challenge, zktag, evidence) {
  const obj = { spec: 'zkagent/1', tier, claim, challenge };
  if (zktag !== null && zktag !== undefined) obj.zktag = zktag;
  obj.evidence = evidence.map((e) => ({
    type: e.type,
    version: e.version,
    data: { key_id: e.keyId, pubkey: e.pubkeyBase64, sig: e.sigBase64 },
  }));
  return obj;
}

async function directPost(responseUri, state, presentation) {
  const vpToken = Buffer.from(JSON.stringify(presentation), 'utf8').toString('base64url');
  const form = new URLSearchParams();
  if (state) form.set('state', state);
  form.set('vp_token', vpToken);
  const res = await fetch(responseUri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: form.toString(),
  });
  return { status: res.status, body: await res.json() };
}

async function pollVerdict(transactionId) {
  const res = await fetch(`${base}/ui/presentations/${transactionId}`);
  return res.json();
}

async function runOne(label, overThreshold) {
  const tx = await createTx();
  const requestObject = await fetchRequestObject(tx.request_uri);
  const zkagent = requestObject.zkagent ?? requestObject;
  const challenge = zkagent.challenge;
  const threshold = challenge.threshold;
  // Same claim shape as MainActivity.presentBareAOnBackground:
  // mapOf("over_threshold" to overThreshold, "threshold" to threshold).
  const claim = { over_threshold: overThreshold, threshold };
  // Bare tier-A: zktag=null, evidence=[] (item 9, D27) — exactly
  // HandoffClient.buildPresentation("A", claim, challenge, null, emptyList()).
  const presentation = buildPresentation('A', claim, challenge, null, []);
  const state = requestObject.state;
  const post = await directPost(requestObject.response_uri, state, presentation);
  const polled = await pollVerdict(tx.transactionId);
  console.log(`[${label}] direct_post -> HTTP ${post.status} ${JSON.stringify(post.body)}`);
  console.log(`[${label}] polled verdict -> ${JSON.stringify(polled)}`);
  return polled.verdict ?? polled;
}

async function main() {
  const okVerdict = await runOne('over_threshold=true', true);
  const underVerdict = await runOne('over_threshold=false', false);

  const okPass = okVerdict.ok === true && okVerdict.allowed === true && okVerdict.tier === 'A'
    && Array.isArray(okVerdict.evidence) && okVerdict.evidence.length === 0;
  const underPass = underVerdict.ok === true && underVerdict.allowed === false
    && underVerdict.reason === 'under_threshold';

  console.log(`\nRESULT over_threshold=true  -> ok=${okVerdict.ok} allowed=${okVerdict.allowed} tier=${okVerdict.tier} evidence=${JSON.stringify(okVerdict.evidence)}  ${okPass ? 'PASS' : 'FAIL'}`);
  console.log(`RESULT over_threshold=false -> ok=${underVerdict.ok} allowed=${underVerdict.allowed} reason=${underVerdict.reason}  ${underPass ? 'PASS' : 'FAIL'}`);

  if (!okPass || !underPass) {
    process.exitCode = 1;
  }
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
