// Builds the M1b corpus: for each real document (nl, us), three FULL mode-A
// zk-passport/1 presentations (site-a run1, site-a run2, site-b run1) plus a
// BARE mode-A presentation (evidence: []) per site — 6 zk presentations + 4
// bare presentations total. Uses chiproof's own issueChallenge (real D20
// sealed nonces) per presentation. Writes everything under
// spikes/m1-zk/out/<doc>/m1b/ (gitignored) and a corpus index under
// spikes/m1b-unlink/ (scratch only — deleted/ignored, not committed).
import { randomBytes } from 'node:crypto';
import { writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildPresentation } from './build.mjs';
import { issueChallenge } from '../../packages/chiproof/src/index.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const M1ZK = join(HERE, '..', 'm1-zk');
const OUT_INDEX = process.argv[2]
  || '/tmp/claude-1000/-home-hamr-PycharmProjects-zkagent/b0491857-b2eb-4707-940f-6d4fb30da66e/scratchpad/corpus-index.json';

const challengeSecret = randomBytes(32);
const now = Date.now();
const nowSeconds = Math.floor(now / 1000);

const PLAN = [
  { doc: 'nl', site: 'site-a', run: 'r1' },
  { doc: 'nl', site: 'site-a', run: 'r2' },
  { doc: 'nl', site: 'site-b', run: 'r1' },
  { doc: 'us', site: 'site-a', run: 'r1' },
  { doc: 'us', site: 'site-a', run: 'r2' },
  { doc: 'us', site: 'site-b', run: 'r1' },
];

const index = { challengeSecretB64: challengeSecret.toString('base64'), now, docs: {} };

async function main() {
  for (const { doc, site, run } of PLAN) {
    const challenge = issueChallenge({
      tier: 'A', threshold: 18, ttlMs: 10 * 365 * 24 * 3600 * 1000, challengeSecret, now,
    });
    const t0 = Date.now();
    const { stages, timings } = await buildPresentation({
      doc, site, run, nonce: challenge.nonce, scopeDomain: site, nowSeconds,
    });
    const wallMs = Date.now() - t0;
    const presDir = join(M1ZK, 'out', doc, 'm1b', `${site}-${run}`);
    mkdirSync(presDir, { recursive: true });
    writeFileSync(join(presDir, 'challenge.json'), JSON.stringify({ challenge, scopeDomain: site, now }, null, 1));
    const zkPresentation = {
      spec: 'zkagent/1',
      tier: 'A',
      claim: { over_threshold: true, threshold: 18 },
      challenge,
      evidence: [{ type: 'zk-passport', version: 1, data: { stages } }],
    };
    writeFileSync(join(presDir, 'presentation.json'), JSON.stringify(zkPresentation));
    const bareChallenge = null; // bare presentations get their own challenge below (once per site)
    index.docs[doc] = index.docs[doc] || {};
    index.docs[doc][`${site}-${run}`] = { presDir, timings, wallMs };
    console.log(JSON.stringify({ doc, site, run, wallMs, timings }));
  }

  // Bare mode-A baseline (evidence: []), one per site, independent of document.
  index.bare = {};
  for (const site of ['site-a', 'site-b']) {
    const challenge = issueChallenge({
      tier: 'A', threshold: 18, ttlMs: 10 * 365 * 24 * 3600 * 1000, challengeSecret, now,
    });
    const bareDir = join(M1ZK, 'out', 'bare', 'm1b', site);
    mkdirSync(bareDir, { recursive: true });
    const bare = {
      spec: 'zkagent/1', tier: 'A', claim: { over_threshold: true, threshold: 18 }, challenge, evidence: [],
    };
    writeFileSync(join(bareDir, 'presentation.json'), JSON.stringify(bare));
    index.bare[site] = { presDir: bareDir };
    console.log(JSON.stringify({ bare: site }));
  }

  writeFileSync(OUT_INDEX, JSON.stringify(index, null, 1));
  console.log(`wrote ${OUT_INDEX}`);
}

main().catch((e) => { console.error(e); process.exit(1); });
