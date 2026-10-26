// TEST-ONLY preflight + link maker for the TLS deploy kit (deploy/README.md). Node 22, no dependencies; run from ANY
// machine that can reach the VPS (the host name is read from the environment, never written down).
//
//   VERIFIER_HOST=<name> node check.mjs            preflight: does each site behave as the phone should see it?
//   VERIFIER_HOST=<name> node check.mjs link good|mismatch|badcert
//                                                 print ONE single-use av:// link for that site (120 s lifetime)
//
// The preflight repeats, with node's own TLS stack and crypto, what the scanner does for a request: fetch the
// well-known key over TLS, fetch the signed request object, verify the ES256 signature with that key. It proves the
// SERVER side of the kit is arranged correctly; it does not replace the phone run.
import https from 'node:https';
import { createPublicKey, verify } from 'node:crypto';

const HOST = process.env.VERIFIER_HOST;
if (!HOST) { console.error('set VERIFIER_HOST'); process.exit(2); }
const SITES = {
  good: { origin: `https://${HOST}`, expect: 'signature verifies' },
  mismatch: { origin: `https://${HOST}:8444`, expect: 'key fetched, signature does NOT verify' },
  badcert: { origin: `https://${HOST}:8445`, expect: 'TLS refused (certificate not trusted)' },
};

function call(url, { method = 'GET', body, insecure = false } = {}) {
  return new Promise((resolve, reject) => {
    const r = https.request(url, { method, rejectUnauthorized: !insecure, headers: body ? { 'content-type': 'application/json' } : {} }, (res) => {
      let data = '';
      res.on('data', (c) => { data += c; });
      res.on('end', () => resolve({ status: res.statusCode, body: data }));
    });
    r.on('error', reject);
    r.end(body);
  });
}

const b64uJson = (s) => JSON.parse(Buffer.from(s, 'base64url').toString('utf8'));

async function probe(name) {
  const { origin } = SITES[name];
  let tx;
  try {
    tx = await call(`${origin}/ui/presentations`, { method: 'POST', body: '{"mode":"A"}' });
  } catch (e) {
    return { verdict: `TLS refused (${e.code ?? e.message})`, matches: name === 'badcert' };
  }
  if (tx.status !== 201) return { verdict: `unexpected HTTP ${tx.status} creating a session`, matches: false };
  const { request_uri } = JSON.parse(tx.body);
  const jws = (await call(request_uri)).body;
  const wk = await call(`${origin}/.well-known/zkagent-verifier`);
  if (wk.status !== 200) return { verdict: `well-known HTTP ${wk.status}`, matches: false };
  const [h, p, s] = jws.split('.');
  const key = createPublicKey({ key: JSON.parse(wk.body), format: 'jwk' });
  const ok = verify('sha256', Buffer.from(`${h}.${p}`), { key, dsaEncoding: 'ieee-p1363' }, Buffer.from(s, 'base64url'));
  const schemeOk = b64uJson(p).response_uri.startsWith(`${origin}/`);
  return { verdict: `${ok ? 'signature verifies' : 'key fetched, signature does NOT verify'}; response_uri on this origin: ${schemeOk}`, matches: name === 'good' ? ok && schemeOk : name === 'mismatch' ? !ok : false };
}

if (process.argv[2] === 'link') {
  const site = SITES[process.argv[3]];
  if (!site) { console.error('link good|mismatch|badcert'); process.exit(2); }
  // insecure only here: we are just creating a session on the server; the PHONE does the real TLS decision.
  const tx = await call(`${site.origin}/ui/presentations`, { method: 'POST', body: '{"mode":"A"}', insecure: true });
  console.log(JSON.parse(tx.body).app_link_av);
} else {
  let bad = 0;
  for (const name of Object.keys(SITES)) {
    const r = await probe(name);
    if (!r.matches) bad++;
    console.log(`${r.matches ? 'OK  ' : 'FAIL'} ${name.padEnd(8)} expected: ${SITES[name].expect} | saw: ${r.verdict}`);
  }
  process.exit(bad ? 1 : 0);
}
