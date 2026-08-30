// Confirms each M1b corpus presentation actually VERIFIES with real chiproof
// (createVerifier + the zk-passport/1 plug, real bb 5.0.0) -- an
// unverifiable presentation would prove nothing about unlinkability.
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import { createVerifier } from '../../packages/chiproof/src/index.js';
import { zkPassport } from '../../packages/chiproof/src/plugs/zk-passport.js';
import { InMemoryNonceStore } from '../../packages/chiproof/src/stores/memory.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const M1ZK = join(HERE, '..', 'm1-zk');
const BB_PATH = '/home/hamr/opt/bb/bb';

const VK_PATHS = (doc) => ({
  dsc: join(M1ZK, 'out', doc, 'dsc/bb/vk'),
  id_data: join(M1ZK, 'out', doc, 'bb/vk'),
  integrity: join(M1ZK, 'out', doc, 'integrity/bb/vk'),
  age: join(M1ZK, 'out', doc, 'age2/bb/vk'),
});

function loadPresentation(doc, siteRun) {
  return JSON.parse(readFileSync(join(M1ZK, 'out', doc, 'm1b', siteRun, 'presentation.json'), 'utf8'));
}
function loadBare(site) {
  return JSON.parse(readFileSync(join(M1ZK, 'out', 'bare', 'm1b', site, 'presentation.json'), 'utf8'));
}

async function main() {
  const results = [];
  const combos = [
    ['nl', 'site-a-r1', 'site-a'], ['nl', 'site-a-r2', 'site-a'], ['nl', 'site-b-r1', 'site-b'],
    ['us', 'site-a-r1', 'site-a'], ['us', 'site-a-r2', 'site-a'], ['us', 'site-b-r1', 'site-b'],
  ];
  for (const [doc, siteRun, scopeDomain] of combos) {
    const presentation = loadPresentation(doc, siteRun);
    const allVks = VK_PATHS(doc);
    const plug = zkPassport({ bbPath: BB_PATH, vks: allVks, threshold: 18 });
    const v = createVerifier({
      stores: { nonce: new InMemoryNonceStore({ quiet: true }) },
      allowInMemoryStore: true,
      challengeSecret: Buffer.from('m1b-probe-secret-only-32-bytes!!'),
      scopeDomain,
      threshold: 18,
      tiers: { max: 'A' },
      evidence: { require: ['zk-passport'], plugs: { 'zk-passport': plug } },
    });
    // NOTE: this verifier instance did NOT mint presentation.challenge (that
    // was minted by corpus.mjs's own issueChallenge with a DIFFERENT secret),
    // so verifyChallenge here will reject with nonce_forged -- this step
    // therefore checks the PLUG (bb verification + bindings) directly rather
    // than the full createVerifier pipeline, matching what the plug's own
    // integration tests do (packages/chiproof/tests/integration/zk-passport.test.js).
    const start = process.hrtime.bigint();
    const r = await plug.verify(presentation.evidence[0], {
      nonce: presentation.challenge.nonce, claim: presentation.claim, tier: 'A', scopeDomain, now: Date.now(),
    });
    const ms = Number(process.hrtime.bigint() - start) / 1e6;
    results.push({
      doc, siteRun, plug_verify: r, ms: +ms.toFixed(1),
    });
  }
  for (const site of ['site-a', 'site-b']) {
    const bare = loadBare(site);
    results.push({ bare: site, evidence_count: bare.evidence.length });
  }
  console.log(JSON.stringify(results, null, 1));
}
main().catch((e) => { console.error(e); process.exit(1); });
