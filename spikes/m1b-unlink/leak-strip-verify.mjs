// Validates whether stripping vk_sha256 from the wire (the proposed fix)
// still lets a legitimate presentation verify via chiproof's zkPassport
// plug, when the plug is registered with ONLY that document's own vks
// (single-vk-per-stage case) vs with BOTH documents' vks pinned together
// (multi-vk-per-stage case, the realistic "one service accepts many
// countries" deployment the fix is meant to serve).
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { zkPassport } from '../../packages/chiproof/src/plugs/zk-passport.js';

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
function stripVk(presentation) {
  const clone = JSON.parse(JSON.stringify(presentation));
  const stages = clone.evidence[0].data.stages;
  for (const st of Object.keys(stages)) delete stages[st].vk_sha256;
  return clone;
}
function mergeVks(a, b) {
  const out = {};
  for (const st of Object.keys(a)) out[st] = [a[st], b[st]];
  return out;
}

async function runCase(label, vks, presentation, ctx) {
  const plug = zkPassport({ bbPath: BB_PATH, vks, threshold: 18 });
  const start = process.hrtime.bigint();
  const r = await plug.verify(presentation.evidence[0], ctx);
  const ms = Number(process.hrtime.bigint() - start) / 1e6;
  return { label, result: r, ms: +ms.toFixed(1) };
}

async function main() {
  const results = [];
  for (const doc of ['nl', 'us']) {
    const presentation = loadPresentation(doc, 'site-a-r1');
    const stripped = stripVk(presentation);
    const ctx = { nonce: presentation.challenge.nonce, claim: presentation.claim, tier: 'A', scopeDomain: 'site-a', now: Date.now() };

    // Case 1: single-doc registration (only this doc's vks pinned), field stripped
    results.push(await runCase(`${doc} single-vk-registration, stripped`, VK_PATHS(doc), stripped, ctx));

    // Case 2: multi-doc registration (both NL and US vks pinned), field stripped
    const both = mergeVks(VK_PATHS('nl'), VK_PATHS('us'));
    results.push(await runCase(`${doc} multi-vk-registration (nl+us pinned), stripped`, both, stripped, ctx));

    // Case 3 (control): multi-doc registration, field present (today's wire format)
    results.push(await runCase(`${doc} multi-vk-registration (nl+us pinned), field PRESENT`, both, presentation, ctx));
  }
  console.log(JSON.stringify(results, null, 1));
}
main().catch((e) => { console.error(e); process.exit(1); });
