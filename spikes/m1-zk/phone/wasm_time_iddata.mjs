// Approach C: time id-data (NL, tbs_1000, RSA-2048) proving under bb.js's WASM
// backend on the desktop, to get a WASM-vs-native ratio as a phone-proving
// estimate proxy (no Android device was reachable this session — see
// phone/README.md). Explicitly forces BackendType.Wasm: bb.js defaults to
// spawning the native `bb` binary via a Unix socket when one is available on
// desktop, which would defeat the point of this measurement.
//
// Must be run from spikes/m1-zk/run/ (or copied there) so Node's ESM resolver
// finds @aztec/bb.js under run/node_modules/. Run with, e.g.:
//   cd spikes/m1-zk/run && timeout 280 node --max-old-space-size=4096 wasm_time_iddata.mjs
import { readFileSync } from 'fs';
import { UltraHonkBackend, BackendType, Barretenberg } from '@aztec/bb.js';

const CIRCUIT_JSON = '/home/hamr/PycharmProjects/zkagent/spikes/m1-zk/vendor/zkpassport-circuits/target/sig_check_id_data_tbs_1000_rsa_pkcs_2048_sha256.json';
const WITNESS_GZ = '/home/hamr/PycharmProjects/zkagent/spikes/m1-zk/vendor/zkpassport-circuits/target/nl_witness.gz';

function log(...a) { console.log(new Date().toISOString(), ...a); }

async function main() {
  log('start');
  const circuit = JSON.parse(readFileSync(CIRCUIT_JSON, 'utf8'));
  const witness = readFileSync(WITNESS_GZ);
  log('files read, bytecode field length', circuit.bytecode.length, 'witness bytes', witness.length);

  const t0 = performance.now();
  log('constructing Barretenberg.new({backend: Wasm})...');
  const api = await Barretenberg.new({ backend: BackendType.Wasm, logger: (m) => log('bb.js:', m) });
  log('api ready', (performance.now() - t0).toFixed(1), 'ms');

  const backend = new UltraHonkBackend(circuit.bytecode, api);
  log('backend constructed');

  const t1 = performance.now();
  const proofData = await backend.generateProof(witness);
  const t2 = performance.now();
  log('generateProof (WASM):', (t2 - t1).toFixed(1), 'ms');
  log('proof bytes:', proofData.proof.length, 'public inputs:', proofData.publicInputs.length);

  const t3 = performance.now();
  const ok = await backend.verifyProof(proofData);
  const t4 = performance.now();
  log('verifyProof (WASM):', (t4 - t3).toFixed(1), 'ms, result:', ok);

  log('TOTAL wall (api-init+construct+prove+verify):', (t4 - t0).toFixed(1), 'ms');
  process.exit(0);
}

main().catch(err => { console.error('FAILED:', err); process.exit(1); });
