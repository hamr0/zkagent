// Follow-up to probe.mjs: the 271-B floor found there sits entirely at
// offset 0 in every pair, including cross-document -- i.e. a global
// structural constant, not a detection limit. This script computes the
// CONSTANT MASK (byte positions identical across ALL 6 corpus presentations
// of the same stage/scope) empirically, then re-runs the longest-common-run
// detector excluding any match whose entire span lies inside that mask, so a
// coincidentally-large match is only reported if it involves at least one
// byte that is NOT a corpus-wide constant. Same three pairs as probe.mjs,
// plus the plant ladder (8/11/16/32 B) re-run against the masked detector.
// No document-derived bytes are printed -- only offsets, lengths, and a
// yes/no per plant size.
import { readFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const M1ZK = join(HERE, '..', 'm1-zk');
const STAGES = ['dsc', 'id_data', 'integrity', 'age'];

function loadPresentation(doc, siteRun) {
  return JSON.parse(readFileSync(join(M1ZK, 'out', doc, 'm1b', siteRun, 'presentation.json'), 'utf8'));
}
function stagesOf(p) { return p.evidence[0].data.stages; }
function serialize(p) { return Buffer.from(JSON.stringify(p)); }

const CORPUS = [
  ['nl', 'site-a-r1'], ['nl', 'site-a-r2'], ['nl', 'site-b-r1'],
  ['us', 'site-a-r1'], ['us', 'site-a-r2'], ['us', 'site-b-r1'],
];
const presentations = CORPUS.map(([doc, sr]) => loadPresentation(doc, sr));

// --- constant mask: position i is masked iff buffers[k][i] is the same byte
// value for every k. Requires all buffers the same length (checked). --------
function computeMask(buffers) {
  const lens = new Set(buffers.map((b) => b.length));
  if (lens.size !== 1) throw new Error(`buffers have differing lengths: ${[...lens]}`);
  const len = buffers[0].length;
  const mask = new Uint8Array(len);
  for (let i = 0; i < len; i += 1) {
    const v = buffers[0][i];
    let same = true;
    for (let k = 1; k < buffers.length; k += 1) { if (buffers[k][i] !== v) { same = false; break; } }
    mask[i] = same ? 1 : 0;
  }
  return mask;
}
function maskSize(mask) { let n = 0; for (const b of mask) n += b; return n; }
// Contiguous masked runs, for reporting "where" the mask sits.
function maskRuns(mask) {
  const runs = [];
  let start = -1;
  for (let i = 0; i < mask.length; i += 1) {
    if (mask[i]) { if (start === -1) start = i; } else if (start !== -1) { runs.push([start, i - start]); start = -1; }
  }
  if (start !== -1) runs.push([start, mask.length - start]);
  return runs;
}

// --- masked longest-common-run: binary search on length; a candidate match
// is accepted only if NOT every position in [sa, sa+len) is masked. ---------
const MOD1 = 1_000_000_007n; const BASE1 = 131n;
const MOD2 = 998_244_353n; const BASE2 = 137n;
function rollingHashes(buf, len) {
  const n = buf.length;
  if (len === 0 || len > n) return new Map();
  let h1 = 0n; let h2 = 0n; let p1 = 1n; let p2 = 1n;
  for (let i = 0; i < len - 1; i += 1) { p1 = (p1 * BASE1) % MOD1; p2 = (p2 * BASE2) % MOD2; }
  for (let i = 0; i < len; i += 1) { h1 = (h1 * BASE1 + BigInt(buf[i])) % MOD1; h2 = (h2 * BASE2 + BigInt(buf[i])) % MOD2; }
  const map = new Map();
  const key = (a, b) => `${a}_${b}`;
  map.set(key(h1, h2), [0]);
  for (let i = 1; i <= n - len; i += 1) {
    h1 = ((h1 - BigInt(buf[i - 1]) * p1 % MOD1 + MOD1 * 10n) % MOD1 * BASE1 + BigInt(buf[i + len - 1])) % MOD1;
    h2 = ((h2 - BigInt(buf[i - 1]) * p2 % MOD2 + MOD2 * 10n) % MOD2 * BASE2 + BigInt(buf[i + len - 1])) % MOD2;
    const k = key(h1, h2);
    if (!map.has(k)) map.set(k, []);
    map.get(k).push(i);
  }
  return map;
}
function fullyMasked(mask, start, len) {
  if (start + len > mask.length) return false;
  for (let i = start; i < start + len; i += 1) { if (!mask[i]) return false; }
  return true;
}
function maskedLongestCommonRun(a, b, mask) {
  let lo = 0; let hi = Math.min(a.length, b.length);
  let best = { length: 0, offsetA: -1, offsetB: -1 };
  const findNonTrivialMatch = (len) => {
    if (len === 0) return { offsetA: 0, offsetB: 0 };
    const mapA = rollingHashes(a, len);
    const mapB = rollingHashes(b, len);
    for (const [k, startsA] of mapA) {
      const startsB = mapB.get(k);
      if (!startsB) continue;
      for (const sa of startsA) {
        if (fullyMasked(mask, sa, len)) continue; // trivial: guaranteed equal by mask definition
        for (const sb of startsB) {
          if (a.subarray(sa, sa + len).equals(b.subarray(sb, sb + len))) return { offsetA: sa, offsetB: sb };
        }
      }
    }
    return null;
  };
  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2);
    const m = findNonTrivialMatch(mid);
    if (m) { best = { length: mid, ...m }; lo = mid + 1; } else { hi = mid - 1; }
  }
  return best;
}

const PLANT_OFFSET = 2000;
function plantByteRun(pA, pB, nBytes) {
  const clone = JSON.parse(JSON.stringify(pB));
  const srcProof = Buffer.from(stagesOf(pA).age.proof, 'base64');
  const dstProof = Buffer.from(stagesOf(clone).age.proof, 'base64');
  const offset = Math.min(PLANT_OFFSET, dstProof.length - nBytes - 1);
  srcProof.copy(dstProof, offset, offset, offset + nBytes);
  stagesOf(clone).age.proof = dstProof.toString('base64');
  return clone;
}

function main() {
  const report = { maskSizes: {}, maskRuns: {}, maskedFieldDiff: [], plantLadder: {} };

  // --- per-stage proof masks -------------------------------------------
  const proofMasks = {};
  for (const st of STAGES) {
    const buffers = presentations.map((p) => Buffer.from(stagesOf(p)[st].proof, 'base64'));
    const mask = computeMask(buffers);
    proofMasks[st] = mask;
    report.maskSizes[`${st}.proof`] = { total_bytes: mask.length, masked_bytes: maskSize(mask) };
    report.maskRuns[`${st}.proof`] = maskRuns(mask).map(([off, len]) => ({ offset: off, length: len }));
  }

  // --- whole-presentation mask (only meaningful if all 6 are the same length) --
  const wholeBuffers = presentations.map(serialize);
  const lens = new Set(wholeBuffers.map((b) => b.length));
  report.whole_presentation_lengths_equal = lens.size === 1;
  let wholeMask = null;
  if (lens.size === 1) {
    wholeMask = computeMask(wholeBuffers);
    report.maskSizes['(whole presentation)'] = { total_bytes: wholeMask.length, masked_bytes: maskSize(wholeMask) };
    report.maskRuns['(whole presentation)'] = maskRuns(wholeMask).map(([off, len]) => ({ offset: off, length: len }));
  } else {
    report.maskSizes['(whole presentation)'] = { note: 'lengths differ across corpus, cannot compute a positional mask', lengths: [...lens] };
  }

  // --- masked longest-common-run, same three pairs, per stage proof + whole --
  const pairs = [
    ['nl a1 vs a2 (same-site)', presentations[0], presentations[1]],
    ['nl a1 vs b1 (cross-site)', presentations[0], presentations[2]],
    ['nl-a1 vs us-a1 (cross-document)', presentations[0], presentations[3]],
  ];
  report.maskedByteProbe = [];
  for (const [label, pA, pB] of pairs) {
    for (const st of STAGES) {
      const bufA = Buffer.from(stagesOf(pA)[st].proof, 'base64');
      const bufB = Buffer.from(stagesOf(pB)[st].proof, 'base64');
      const m = maskedLongestCommonRun(bufA, bufB, proofMasks[st]);
      report.maskedByteProbe.push({
        pair: label, scope: `${st}.proof`, longest_common_run_excluding_mask: m.length, match_offset_a: m.offsetA,
      });
    }
    if (wholeMask) {
      const m = maskedLongestCommonRun(serialize(pA), serialize(pB), wholeMask);
      report.maskedByteProbe.push({
        pair: label, scope: '(whole presentation)', longest_common_run_excluding_mask: m.length, match_offset_a: m.offsetA,
      });
    }
  }

  // --- masked plant ladder: 8, 11, 16, 32 B at offset 2000, age.proof --------
  const nlA1 = presentations[0]; const nlB1 = presentations[2];
  const proofA1 = Buffer.from(stagesOf(nlA1).age.proof, 'base64');
  const baselineMasked = maskedLongestCommonRun(proofA1, Buffer.from(stagesOf(nlB1).age.proof, 'base64'), proofMasks.age);
  report.plantLadder.baseline_masked_age_proof_match = baselineMasked;
  for (const n of [8, 11, 16, 32]) {
    const planted = plantByteRun(nlA1, nlB1, n);
    const plantedProof = Buffer.from(stagesOf(planted).age.proof, 'base64');
    const m = maskedLongestCommonRun(proofA1, plantedProof, proofMasks.age);
    const caught = m.offsetA === PLANT_OFFSET && m.length >= n;
    report.plantLadder[`${n}B`] = {
      longest_common_run_after_plant: m.length, match_offset_a: m.offsetA, caught,
    };
  }

  console.log(JSON.stringify(report, null, 1));
}
main();
