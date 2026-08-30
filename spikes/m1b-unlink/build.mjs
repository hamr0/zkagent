// M1b unlinkability probe: builds FULL fresh mode-A zk-passport/1 presentations
// (all four stages: dsc -> id_data -> integrity -> age) for a given document,
// scope domain (site-a / site-b) and run label, with FRESH random salts at
// every boundary the vendored circuits accept one (dsc/id_data/integrity),
// plus a fresh chiproof challenge nonce carried in the age stage's
// service_subscope. nullifier_secret stays "0x0" (Q23's documented
// non-blinded convention — the OPRF path is untested, see M1B-EVIDENCE.md).
//
// Reads only: spikes/m1-zk/fixtures/real/<doc>.{dg1,sod} (gitignored),
//             spikes/m1-zk/out/masterlist-certs/packaged-certs-full.json,
//             spikes/m1-zk/out/<doc>/{dsc,,integrity,age2}/bb/vk (existing,
//             doc+circuit-specific verification keys, reused as-is: a VK is a
//             deterministic function of the CIRCUIT, not of any witness).
// Writes only: spikes/m1-zk/out/<doc>/m1b/<site>-<run>/{proof,public_inputs,
//              vk_sha256,timings.json,challenge.json} per stage, plus a
//              presentation.json assembling the zk-passport/1 envelope.
// Throwaway spike script: no tests, minimal error handling. No document
// bytes, MRZ text, or PII are ever printed — only byte lengths and timings.
import { createHash, randomBytes } from 'node:crypto';
import {
  readFileSync, writeFileSync, mkdirSync, unlinkSync, existsSync,
} from 'node:fs';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { createRequire } from 'node:module';

const HERE = dirname(fileURLToPath(import.meta.url));
const M1ZK = join(HERE, '..', 'm1-zk');
const M1ZK_RUN = join(M1ZK, 'run');
const requireFromRun = createRequire(join(M1ZK_RUN, 'package.json'));
const {
  Binary, PassportReader, getDSCCircuitInputs, getIDDataCircuitInputs,
  getIntegrityCheckCircuitInputs, getAgeCircuitInputs, getServiceScopeHash,
} = requireFromRun('@zkpassport/utils');

const NARGO = process.env.NARGO ?? join(process.env.HOME, 'opt/noir/bin/nargo');
const BB = process.env.BB ?? join(process.env.HOME, 'opt/bb/bb');
const VENDOR_BIN = join(M1ZK, 'vendor/zkpassport-circuits/src/noir/bin');
const TARGET_DIR = join(M1ZK, 'vendor/zkpassport-circuits/target');
const PACKAGED_CERTS = join(M1ZK, 'out/masterlist-certs/packaged-certs-full.json');

// zkagent/chiproof registry rule for zk-passport/1 (packages/chiproof/src/plugs/zk-passport.js):
// subscopeFromNonce(nonce) = scopeField(domain) = first 31 bytes of sha256(utf8 str), as a Field.
const hash31Hex = (str) => '0x' + createHash('sha256').update(str, 'utf8').digest().subarray(0, 31).toString('hex');

// Per-document circuit selection (Q23 evidence: NL DS/CSCA TBS bucket 1000,
// US bucket 1600; both RSA-2048 DS / RSA-4096 CSCA / SHA-256 throughout).
const DOC_CIRCUITS = {
  nl: {
    dsc: { dir: 'sig-check/dsc/tbs_1000/rsa/pkcs/4096/sha256', json: 'sig_check_dsc_tbs_1000_rsa_pkcs_4096_sha256.json' },
    id_data: { dir: 'sig-check/id-data/tbs_1000/rsa/pkcs/2048/sha256', json: 'sig_check_id_data_tbs_1000_rsa_pkcs_2048_sha256.json' },
  },
  us: {
    dsc: { dir: 'sig-check/dsc/tbs_1600/rsa/pkcs/4096/sha256', json: 'sig_check_dsc_tbs_1600_rsa_pkcs_4096_sha256.json' },
    id_data: { dir: 'sig-check/id-data/tbs_1600/rsa/pkcs/2048/sha256', json: 'sig_check_id_data_tbs_1600_rsa_pkcs_2048_sha256.json' },
  },
};
const INTEGRITY = { dir: 'data-check/integrity/sa_sha256/dg_sha256', json: 'data_check_integrity_sa_sha256_dg_sha256.json' };
const AGE = { dir: 'compare/age/standard', json: 'compare_age.json' };

const VK_PATH = (doc, stage) => ({
  dsc: join(M1ZK, 'out', doc, 'dsc/bb/vk'),
  id_data: join(M1ZK, 'out', doc, 'bb/vk'),
  integrity: join(M1ZK, 'out', doc, 'integrity/bb/vk'),
  age: join(M1ZK, 'out', doc, 'age2/bb/vk'),
}[stage]);

const secs = (f) => { const s = process.hrtime.bigint(); const r = f(); return { r, t: Number(process.hrtime.bigint() - s) / 1e9 }; };
// 31 random bytes (< 2^248) is always a valid field element for BN254 (~2^254).
const freshSalt = () => BigInt('0x' + randomBytes(31).toString('hex'));

// The real fixture files are named by capture id, not by the "nl"/"us" short
// codes used everywhere else in this spike (out/nl, out/us, ...).
const FIXTURE_ID = { nl: 'id-20260830031213', us: 'passport-20260830031745' };

function loadVm(doc) {
  const fid = FIXTURE_ID[doc];
  const dg1 = Binary.from(readFileSync(join(M1ZK, 'fixtures/real', `${fid}.dg1`)));
  const sod = Binary.from(readFileSync(join(M1ZK, 'fixtures/real', `${fid}.sod`)));
  const reader = new PassportReader();
  reader.loadPassport(dg1, sod);
  return reader.getPassportViewModel();
}

// Nested-table-capable TOML writer, ported from spikes/m1-zk/run/toml-writer.js
// (same logic; a new copy here since this spike may only ADD files, not edit
// existing ones). Handles the SaltedValue<T>/OPRFProof struct shapes the
// dsc/integrity/age circuit-input builders return.
function scalarToToml(v) {
  if (typeof v === 'number') return String(v);
  if (typeof v === 'string') return `"${v}"`;
  throw new Error(`unsupported scalar type: ${typeof v}`);
}
function arrayToToml(arr) {
  return '[' + arr.map((x) => (typeof x === 'number' ? String(x) : `"${x}"`)).join(', ') + ']';
}
function writeProverToml(inputs, outPath) {
  const topLevelScalars = [];
  const tables = [];
  function walk(prefix, obj) {
    const scalarsHere = [];
    const nestedTables = [];
    for (const [k, v] of Object.entries(obj)) {
      if (Array.isArray(v)) {
        scalarsHere.push(`${k} = ${arrayToToml(v)}`);
      } else if (typeof v === 'object' && v !== null) {
        nestedTables.push([k, v]);
      } else {
        scalarsHere.push(`${k} = ${scalarToToml(v)}`);
      }
    }
    if (prefix) tables.push(`[${prefix}]\n` + scalarsHere.join('\n'));
    else topLevelScalars.push(...scalarsHere);
    for (const [k, v] of nestedTables) walk(prefix ? `${prefix}.${k}` : k, v);
  }
  walk('', inputs);
  const out = [topLevelScalars.join('\n'), ...tables].filter(Boolean).join('\n\n') + '\n';
  writeFileSync(outPath, out);
}

/** Run one circuit: write Prover-<name>.toml into pkgDir, execute+prove+verify, clean up. */
function runStage({
  pkgDir, circuitJson, vkPath, inputs, name, outDir,
}) {
  mkdirSync(outDir, { recursive: true });
  const proverPath = join(pkgDir, `${name}.toml`);
  writeProverToml(inputs, proverPath);
  const targetJson = join(TARGET_DIR, circuitJson);
  const witnessGz = join(TARGET_DIR, `${name}.gz`);
  let tExec, tProve, tVerify;
  try {
    tExec = secs(() => execFileSync(NARGO, ['execute', '-p', name, name], { cwd: pkgDir, stdio: 'pipe' })).t;
    tProve = secs(() => execFileSync(BB, ['prove', '-k', vkPath, '-b', targetJson, '-w', witnessGz, '-o', outDir], { stdio: 'pipe' })).t;
    tVerify = secs(() => execFileSync(BB, ['verify', '-k', vkPath, '-p', join(outDir, 'proof'), '-i', join(outDir, 'public_inputs')], { stdio: 'pipe' })).t;
  } finally {
    try { unlinkSync(proverPath); } catch {}
    try { unlinkSync(witnessGz); } catch {}
  }
  return { tExec, tProve, tVerify };
}

/**
 * Build one full presentation for (doc, site, run). issueChallenge/nonce
 * supplied by the caller (chiproof's own issueChallenge, so §D20 sealing is
 * exercised for real). Returns { stagesEnvelope, timings, challenge }.
 */
export function buildPresentation({
  doc, site, run, nonce, scopeDomain, nowSeconds,
}) {
  const vm = loadVm(doc);
  const dscC = DOC_CIRCUITS[doc].dsc;
  const idC = DOC_CIRCUITS[doc].id_data;
  const label = `m1b-${doc}-${site}-${run}`;

  const saltDsc = freshSalt();
  const saltIdData = freshSalt();
  const saltsIntegrity = {
    dg1Salt: freshSalt(), expiryDateSalt: freshSalt(), dg2HashSalt: freshSalt(), privateNullifierSalt: freshSalt(),
  };

  const timings = {};
  const stages = {};

  // NOTE: getDSCCircuitInputs is async in the library's .d.ts but returns
  // synchronously-resolvable data for this offline registry; await regardless.
  return (async () => {
    const dscInputs = await getDSCCircuitInputs(vm, saltDsc, JSON.parse(readFileSync(PACKAGED_CERTS, 'utf8')));
    if (!dscInputs) throw new Error(`${doc}: getDSCCircuitInputs failed`);
    let t = runStage({
      pkgDir: join(VENDOR_BIN, dscC.dir), circuitJson: dscC.json, vkPath: VK_PATH(doc, 'dsc'),
      inputs: dscInputs, name: label, outDir: join(M1ZK, 'out', doc, 'm1b', `${site}-${run}`, 'dsc'),
    });
    timings.dsc = t;

    const idInputs = await getIDDataCircuitInputs(vm, saltDsc, saltIdData);
    if (!idInputs) throw new Error(`${doc}: getIDDataCircuitInputs failed`);
    t = runStage({
      pkgDir: join(VENDOR_BIN, idC.dir), circuitJson: idC.json, vkPath: VK_PATH(doc, 'id_data'),
      inputs: idInputs, name: label, outDir: join(M1ZK, 'out', doc, 'm1b', `${site}-${run}`, 'id_data'),
    });
    timings.id_data = t;

    const integInputs = await getIntegrityCheckCircuitInputs(vm, saltIdData, saltsIntegrity);
    if (!integInputs) throw new Error(`${doc}: getIntegrityCheckCircuitInputs failed`);
    t = runStage({
      pkgDir: join(VENDOR_BIN, INTEGRITY.dir), circuitJson: INTEGRITY.json, vkPath: VK_PATH(doc, 'integrity'),
      inputs: integInputs, name: label, outDir: join(M1ZK, 'out', doc, 'm1b', `${site}-${run}`, 'integrity'),
    });
    timings.integrity = t;

    const serviceScope = getServiceScopeHash(scopeDomain);
    const serviceSubscope = BigInt(hash31Hex(nonce));
    const ageInputs = await getAgeCircuitInputs(
      vm, { age: { gte: 18 } }, saltsIntegrity, 0n, serviceScope, serviceSubscope, nowSeconds,
    );
    if (!ageInputs) throw new Error(`${doc}: getAgeCircuitInputs failed`);
    t = runStage({
      pkgDir: join(VENDOR_BIN, AGE.dir), circuitJson: AGE.json, vkPath: VK_PATH(doc, 'age'),
      inputs: ageInputs, name: label, outDir: join(M1ZK, 'out', doc, 'm1b', `${site}-${run}`, 'age'),
    });
    timings.age = t;

    for (const st of ['dsc', 'id_data', 'integrity', 'age']) {
      const outDir = join(M1ZK, 'out', doc, 'm1b', `${site}-${run}`, st);
      const vkPath = VK_PATH(doc, st);
      stages[st] = {
        proof: readFileSync(join(outDir, 'proof')).toString('base64'),
        public_inputs: readFileSync(join(outDir, 'public_inputs')).toString('base64'),
        vk_sha256: createHash('sha256').update(readFileSync(vkPath)).digest('hex'),
      };
    }
    return { stages, timings };
  })();
}

// --- CLI: node build.mjs <doc> <site> <run> <nonce-b64url> <scopeDomain> <nowSeconds> [outFile]
if (import.meta.url === `file://${process.argv[1]}`) {
  const [doc, site, run, nonce, scopeDomain, nowSecondsStr, outFile] = process.argv.slice(2);
  if (!doc || !site || !run || !nonce || !scopeDomain || !nowSecondsStr) {
    console.error('usage: node build.mjs <doc> <site> <run> <nonce> <scopeDomain> <nowSeconds> [outFile]');
    process.exit(1);
  }
  buildPresentation({
    doc, site, run, nonce, scopeDomain, nowSeconds: Number(nowSecondsStr),
  }).then(({ stages, timings }) => {
    const result = { doc, site, run, timings };
    console.log(JSON.stringify(result));
    if (outFile) {
      writeFileSync(outFile, JSON.stringify({ stages }, null, 1));
    }
  }).catch((e) => {
    console.error(JSON.stringify({ ok: false, doc, site, run, error: e.message }));
    process.exit(1);
  });
}
