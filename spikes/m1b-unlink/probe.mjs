// M1b unlinkability probe: two required detectors (structured field diff,
// byte-level longest-common-run) plus planted positive controls, borrowing
// 8een's §7.3 method (docs/02-evidence/M2-EVIDENCE.md lines ~240-275):
// - the behavioural/k-gram checks 8een tried first were retracted as
//   tautological or margin-blind; the check that survived is the longest
//   CONTIGUOUS common byte run, with a planted control that must be caught.
// Never prints document-derived bytes -- only lengths, equal/different
// verdicts, and longest-common-run lengths.
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const M1ZK = join(HERE, '..', 'm1-zk');
const STAGES = ['dsc', 'id_data', 'integrity', 'age'];
const FIELD_NAMES = {
  dsc: ['comm_in', 'comm_out'],
  id_data: ['comm_in', 'comm_out'],
  integrity: ['comm_in', 'comm_out'],
  age: ['comm_in', 'current_date', 'service_scope', 'service_subscope', 'param_commitment', 'nullifier_type', 'nullifier', 'oprf_pk_hash'],
};

function loadPresentation(doc, siteRun) {
  const p = join(M1ZK, 'out', doc, 'm1b', siteRun, 'presentation.json');
  return JSON.parse(readFileSync(p, 'utf8'));
}
function loadBare(site) {
  const p = join(M1ZK, 'out', 'bare', 'm1b', site, 'presentation.json');
  return JSON.parse(readFileSync(p, 'utf8'));
}
function stagesOf(presentation) { return presentation.evidence[0].data.stages; }

// ---------------------------------------------------------------------------
// (a) STRUCTURED FIELD DIFF
// ---------------------------------------------------------------------------
function fieldDiff(pA, pB, label) {
  const sA = stagesOf(pA); const sB = stagesOf(pB);
  const rows = [];
  for (const st of STAGES) {
    const piA = Buffer.from(sA[st].public_inputs, 'base64');
    const piB = Buffer.from(sB[st].public_inputs, 'base64');
    const names = FIELD_NAMES[st];
    for (let i = 0; i < names.length; i += 1) {
      const fA = piA.subarray(i * 32, (i + 1) * 32);
      const fB = piB.subarray(i * 32, (i + 1) * 32);
      rows.push({
        pair: label, stage: st, field: names[i], equal: fA.equals(fB), bytes: 32,
      });
    }
    rows.push({
      pair: label, stage: st, field: 'vk_sha256', equal: sA[st].vk_sha256 === sB[st].vk_sha256, bytes: 32,
    });
    const proofA = Buffer.from(sA[st].proof, 'base64');
    const proofB = Buffer.from(sB[st].proof, 'base64');
    rows.push({
      pair: label, stage: st, field: 'proof_length', equal: proofA.length === proofB.length, bytes: proofA.length,
    });
  }
  const totalA = Buffer.byteLength(JSON.stringify(pA));
  const totalB = Buffer.byteLength(JSON.stringify(pB));
  rows.push({
    pair: label, stage: '(whole)', field: 'total_presentation_length', equal: totalA === totalB, bytes: totalA,
  });
  return rows;
}

// ---------------------------------------------------------------------------
// (b) BYTE PROBE: longest contiguous common byte run (substring), via
// binary search over length + rolling-hash (Rabin-Karp, double modulus to
// avoid spurious collisions) -- NOT k-gram counting (8een's retracted v2).
// ---------------------------------------------------------------------------
const MOD1 = 1_000_000_007n; const BASE1 = 131n;
const MOD2 = 998_244_353n; const BASE2 = 137n;

function rollingHashes(buf, len) {
  const n = buf.length;
  if (len === 0 || len > n) return new Map();
  let h1 = 0n; let h2 = 0n;
  let p1 = 1n; let p2 = 1n;
  for (let i = 0; i < len - 1; i += 1) { p1 = (p1 * BASE1) % MOD1; p2 = (p2 * BASE2) % MOD2; }
  for (let i = 0; i < len; i += 1) {
    h1 = (h1 * BASE1 + BigInt(buf[i])) % MOD1;
    h2 = (h2 * BASE2 + BigInt(buf[i])) % MOD2;
  }
  const map = new Map(); // key -> list of start indices
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

/**
 * True bytewise longest common substring, via binary search on length +
 * rolling hash. Returns {length, offsetA, offsetB} for one maximal match (not
 * just the length) so a control can confirm WHERE the match sits -- e.g.
 * distinguishing "matched at byte 0, a format preamble" from "matched at the
 * byte offset a plant was inserted at".
 */
function longestCommonRun(a, b) {
  let lo = 0; let hi = Math.min(a.length, b.length);
  let best = { length: 0, offsetA: -1, offsetB: -1 };
  const findMatch = (len) => {
    if (len === 0) return { offsetA: 0, offsetB: 0 };
    const mapA = rollingHashes(a, len);
    const mapB = rollingHashes(b, len);
    for (const [k, startsA] of mapA) {
      const startsB = mapB.get(k);
      if (!startsB) continue;
      for (const sa of startsA) {
        for (const sb of startsB) {
          if (a.subarray(sa, sa + len).equals(b.subarray(sb, sb + len))) return { offsetA: sa, offsetB: sb };
        }
      }
    }
    return null;
  };
  while (lo <= hi) {
    const mid = Math.floor((lo + hi) / 2);
    const m = findMatch(mid);
    if (m) { best = { length: mid, ...m }; lo = mid + 1; } else { hi = mid - 1; }
  }
  return best;
}
function runLen(a, b) { return longestCommonRun(a, b).length; }

function serialize(presentation) { return Buffer.from(JSON.stringify(presentation)); }

function byteProbe(pA, pB, label) {
  const rows = [];
  const wholeA = serialize(pA); const wholeB = serialize(pB);
  const wholeMatch = longestCommonRun(wholeA, wholeB);
  rows.push({
    pair: label, scope: '(whole presentation)', longest_common_run: wholeMatch.length, match_offset_a: wholeMatch.offsetA,
  });
  const sA = stagesOf(pA); const sB = stagesOf(pB);
  for (const st of STAGES) {
    const proofA = Buffer.from(sA[st].proof, 'base64');
    const proofB = Buffer.from(sB[st].proof, 'base64');
    const m = longestCommonRun(proofA, proofB);
    rows.push({
      pair: label, scope: `${st}.proof`, longest_common_run: m.length, match_offset_a: m.offsetA,
    });
  }
  return rows;
}

// ---------------------------------------------------------------------------
// (c) POSITIVE CONTROLS
// ---------------------------------------------------------------------------
// Measured (see main()): every real proof pair shares a ~271-byte common
// PREFIX at offset 0 (a fixed UltraHonk proof-format/circuit-shape header,
// not document- or salt-derived -- confirmed identical at offset 0 for
// same-site, cross-site AND cross-document pairs). The plant offset is
// chosen well clear of that prefix so the control measures whether the
// DETECTOR catches the PLANT, not the pre-existing structural floor.
const PLANT_OFFSET = 2000;

function plantByteRun(pA, pB, nBytes) {
  // Copy nBytes from a1's age proof into a COPY of b1's age proof, at a fixed
  // offset well past the known structural prefix.
  const clone = JSON.parse(JSON.stringify(pB));
  const srcProof = Buffer.from(stagesOf(pA).age.proof, 'base64');
  const dstProof = Buffer.from(stagesOf(clone).age.proof, 'base64');
  const offset = Math.min(PLANT_OFFSET, dstProof.length - nBytes - 1);
  srcProof.copy(dstProof, offset, offset, offset + nBytes);
  stagesOf(clone).age.proof = dstProof.toString('base64');
  return clone;
}

function plantFieldControl(pA, pB) {
  // Copy one entire 32-byte field (age.comm_in, index 0) from a1's public
  // inputs into a COPY of b1's public inputs.
  const clone = JSON.parse(JSON.stringify(pB));
  const srcPi = Buffer.from(stagesOf(pA).age.public_inputs, 'base64');
  const dstPi = Buffer.from(stagesOf(clone).age.public_inputs, 'base64');
  srcPi.copy(dstPi, 0, 0, 32);
  stagesOf(clone).age.public_inputs = dstPi.toString('base64');
  return clone;
}

// ---------------------------------------------------------------------------
function summarize(rows) {
  const different = rows.filter((r) => r.equal === false);
  const equal = rows.filter((r) => r.equal === true);
  return { equalCount: equal.length, differentCount: different.length, different };
}

async function main() {
  const nlA1 = loadPresentation('nl', 'site-a-r1');
  const nlA2 = loadPresentation('nl', 'site-a-r2');
  const nlB1 = loadPresentation('nl', 'site-b-r1');
  const usA1 = loadPresentation('us', 'site-a-r1');

  const report = { fieldDiff: [], byteProbe: [], controls: {} };

  // (a) structured field diff
  report.fieldDiff.push(...fieldDiff(nlA1, nlA2, 'nl a1 vs a2 (same-site)'));
  report.fieldDiff.push(...fieldDiff(nlA1, nlB1, 'nl a1 vs b1 (cross-site)'));
  report.fieldDiff.push(...fieldDiff(nlA1, usA1, 'nl-a1 vs us-a1 (cross-document)'));

  // (b) byte probe
  report.byteProbe.push(...byteProbe(nlA1, nlA2, 'nl a1 vs a2 (same-site)'));
  report.byteProbe.push(...byteProbe(nlA1, nlB1, 'nl a1 vs b1 (cross-site)'));
  report.byteProbe.push(...byteProbe(nlA1, usA1, 'nl-a1 vs us-a1 (cross-document)'));

  // (c) positive controls -- byte-run detector, over the age.proof buffer
  // (comparing raw proof bytes, not the JSON-wrapped whole presentation,
  // whose repeated field names ("proof":, "public_inputs":, ...) create an
  // even larger, irrelevant structural floor). Baseline match is confirmed
  // to sit at offset 0 (the known structural prefix, not the plant site)
  // before any plant is applied -- so "caught" means the match MOVED to the
  // plant's offset and GREW to the planted length, not that the pre-existing
  // floor happened to already exceed the plant size (8een's retracted-v1
  // trap). Escalates 8/16/64/128/256/300/512 B: report exactly where it
  // starts catching, i.e. the measured floor, not an assumed one.
  const proofA1 = Buffer.from(stagesOf(nlA1).age.proof, 'base64');
  const proofB1 = Buffer.from(stagesOf(nlB1).age.proof, 'base64');
  const baselineMatch = longestCommonRun(proofA1, proofB1);
  report.controls.baseline_age_proof_match = baselineMatch; // expect offsetA:0 (the format prefix)

  for (const n of [8, 16, 64, 128, 256, 300, 512]) {
    const planted = plantByteRun(nlA1, nlB1, n);
    const plantedProof = Buffer.from(stagesOf(planted).age.proof, 'base64');
    const m = longestCommonRun(proofA1, plantedProof);
    // Caught = the maximal match now sits AT the plant offset (not the
    // pre-existing offset-0 prefix) and covers (at least) the planted bytes.
    const caught = m.offsetA === PLANT_OFFSET && m.length >= n;
    report.controls[`byte_run_plant_${n}B`] = {
      longest_common_run_after_plant: m.length,
      match_offset_a: m.offsetA,
      expected_plant_offset: PLANT_OFFSET,
      caught,
    };
  }

  // (c) field-diff positive control: whole 32B field planted, detector (a).
  {
    const plantedField = plantFieldControl(nlA1, nlB1);
    const rowsBaseline = fieldDiff(nlA1, nlB1, 'age.comm_in baseline').filter((r) => r.stage === 'age' && r.field === 'comm_in');
    const rowsPlanted = fieldDiff(nlA1, plantedField, 'age.comm_in planted').filter((r) => r.stage === 'age' && r.field === 'comm_in');
    report.controls.field_plant_32B = {
      baseline_equal: rowsBaseline[0]?.equal, // expect false pre-plant (fresh salts -> different comm_in)
      after_plant_equal: rowsPlanted[0]?.equal, // expect true: detector must flag equality where none should exist
      caught: rowsPlanted[0]?.equal === true,
    };
  }

  console.log(JSON.stringify(report, null, 1));
}

main().catch((e) => { console.error(e); process.exit(1); });
