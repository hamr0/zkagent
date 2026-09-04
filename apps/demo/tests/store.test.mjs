// node --test — PRD §6.3 item 10 (opening POC) + item 9 (fail closed):
//   (a) store atomicity and reload
//   (b) tier-B mint -> second mint of the SAME document refused as already-registered
//   (c) restart survival: a fresh process on the SAME store file still refuses the dupe
//   (d) fail-closed startup: an unwritable store path exits non-zero, never falls back
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import {
  mkdtempSync, rmSync, existsSync, readdirSync, writeFileSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { JsonFileStore } from '../store.mjs';
import { startServer } from '../server.mjs';

const pExecFile = promisify(execFile);
const serverScript = fileURLToPath(new URL('../server.mjs', import.meta.url));
const walletScript = fileURLToPath(new URL('../scripts/fake-wallet.mjs', import.meta.url));
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function mkTmpDir(prefix) {
  return mkdtempSync(join(tmpdir(), `zkagent-demo-${prefix}-`));
}

/** Spawns `node server.mjs`, resolves once it prints its listening URL (or rejects on early exit). */
function spawnServer(env) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [serverScript], {
      env: {
        ...process.env, PORT: '0', BIND_HOST: '127.0.0.1', ...env,
      },
    });
    let stdout = '';
    let stderr = '';
    let settled = false;
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
      const m = stdout.match(/listening on (http:\/\/\S+)/);
      if (m && !settled) {
        settled = true;
        resolve({ child, url: m[1], stdout: () => stdout, stderr: () => stderr });
      }
    });
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.on('exit', (code) => {
      if (!settled) {
        settled = true;
        reject(new Error(`server exited before printing a listening URL (code=${code}): ${stderr || stdout}`));
      }
    });
  });
}

function waitExit(child) {
  return new Promise((resolve) => child.on('exit', (code) => resolve(code)));
}

// ---------------------------------------------------------------------------
// (a) store atomicity and reload
// ---------------------------------------------------------------------------

test('store: atomic write leaves no leftover temp file, and a fresh instance reloads the same state', async () => {
  const dir = mkTmpDir('atomicity');
  try {
    const path = join(dir, 'store.json');
    const store1 = new JsonFileStore(path);
    assert.equal(await store1.setIfAbsent('nonce-1', 60_000), true, 'first use is recorded');
    assert.equal(await store1.setIfAbsent('nonce-1', 60_000), false, 'a replay of the same key is refused');
    const view = store1.attesterView('ed25519');
    await view.bind({
      scope: 'example.test', zktag: 'zk-1', key_id: 'kid-1', pubkey: Buffer.from('fake-pubkey-der'),
    });
    await store1.markZktagSeen('example.test', 'zk-1');

    // No leftover .tmp-* files: every write completed its rename.
    const leftovers = readdirSync(dir).filter((f) => f.includes('.tmp-'));
    assert.deepEqual(leftovers, [], 'atomic writes leave no temp files behind');

    // A second, independent instance pointed at the SAME path sees the SAME state.
    const store2 = new JsonFileStore(path);
    assert.equal(await store2.setIfAbsent('nonce-1', 60_000), false, 'reload: the nonce is still spent');
    const bound = await store2.attesterView('ed25519').get({ scope: 'example.test', zktag: 'zk-1' });
    assert.equal(bound.key_id, 'kid-1');
    assert.equal(Buffer.compare(bound.pubkey, Buffer.from('fake-pubkey-der')), 0, 'pubkey round-trips as a Buffer, byte for byte');
    assert.equal(await store2.hasZktagBeenSeen('example.test', 'zk-1'), true, 'reload: the zktag is still marked seen');
    assert.equal(await store2.hasZktagBeenSeen('example.test', 'zk-unseen'), false);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('store: attester bindings are namespaced per algorithm on the SAME file (D38: not shared between ed25519 and p256)', async () => {
  const dir = mkTmpDir('algo-namespace');
  try {
    const store = new JsonFileStore(join(dir, 'store.json'));
    const ed = store.attesterView('ed25519');
    const p256 = store.attesterView('p256');
    await ed.bind({
      scope: 's', zktag: 'z', key_id: 'kid-ed', pubkey: Buffer.from('ed-pubkey'),
    });
    assert.equal(await p256.get({ scope: 's', zktag: 'z' }), undefined, 'p256 sees no binding for a key bound only under ed25519');
    await p256.bind({
      scope: 's', zktag: 'z', key_id: 'kid-p256', pubkey: Buffer.from('p256-pubkey'),
    });
    const edBinding = await ed.get({ scope: 's', zktag: 'z' });
    assert.equal(edBinding.key_id, 'kid-ed', "ed25519's own binding is unaffected by p256's later bind for the same (scope, zktag)");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('store: a corrupt existing file is refused, not silently overwritten (fail closed)', () => {
  const dir = mkTmpDir('corrupt');
  try {
    const path = join(dir, 'store.json');
    writeFileSync(path, '{ not valid json');
    assert.throws(() => new JsonFileStore(path), /not valid JSON/);
    assert.equal(readdirSync(dir).length, 1, 'the corrupt file was left untouched, not replaced');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

// ---------------------------------------------------------------------------
// (b) tier-B duplicate rejection, in-process (see roundtrip.test.mjs's own
// isolated-store pattern for why DEMO_STORE_PATH is set before startServer).
// ---------------------------------------------------------------------------

test('tier-B duplicate rejection: second mint of the same document at this site is already_registered', async () => {
  const dir = mkTmpDir('dupe-inprocess');
  const prevPath = process.env.DEMO_STORE_PATH;
  try {
    process.env.DEMO_STORE_PATH = join(dir, 'store.json');
    const srv = await startServer(0);
    try {
      const first = await pExecFile(process.execPath, [walletScript, '--base', srv.url, '--tier', 'B', '--mode', 'valid']);
      assert.match(first.stdout, /"already_registered": false/, 'first scan: not previously registered');
      assert.match(first.stdout, /"zktag_seen_before": false/);

      const second = await pExecFile(process.execPath, [walletScript, '--base', srv.url, '--tier', 'B', '--mode', 'valid']);
      assert.match(second.stdout, /"already_registered": true/, 'second scan of the SAME document: already registered');
      assert.match(second.stdout, /"zktag_seen_before": true/);
      // The chip_auth caveat (D29): this spike has no chip, so the presented
      // zktag is a fixed SYNTHETIC value both times -- proving the demo's
      // OWN dedupe fires on repeat zktag, independent of chiproof's verdict
      // (which still says allowed:true both times -- the evidence itself is
      // genuinely valid on the second scan too, exactly D29's point: a
      // clone/replay of a chip_auth:false document is indistinguishable
      // from the genuine holder scanning again).
      assert.match(second.stdout, /RESULT tier=B mode=valid: allowed=true reason=evidence-verified -> AS EXPECTED/);
    } finally {
      await srv.close();
    }
  } finally {
    if (prevPath === undefined) delete process.env.DEMO_STORE_PATH; else process.env.DEMO_STORE_PATH = prevPath;
    rmSync(dir, { recursive: true, force: true });
  }
});

// ---------------------------------------------------------------------------
// (c) restart survival: a FRESH process on the SAME store file still refuses
// the duplicate -- this is the actual opening-POC claim (item 10), not just
// same-process persistence.
// ---------------------------------------------------------------------------

test('restart survival: duplicate rejection holds after the server process is killed and restarted on the same store file', async () => {
  const dir = mkTmpDir('restart');
  const storePath = join(dir, 'store.json');
  try {
    const run1 = await spawnServer({ DEMO_STORE_PATH: storePath });
    let first;
    try {
      first = await pExecFile(process.execPath, [walletScript, '--base', run1.url, '--tier', 'B', '--mode', 'valid']);
    } finally {
      run1.child.kill('SIGTERM');
      await waitExit(run1.child);
    }
    assert.match(first.stdout, /"already_registered": false/, 'first scan, first process: not previously registered');

    // A brand-new process, same store file, different (ephemeral) port.
    const run2 = await spawnServer({ DEMO_STORE_PATH: storePath });
    let second;
    try {
      second = await pExecFile(process.execPath, [walletScript, '--base', run2.url, '--tier', 'B', '--mode', 'valid']);
    } finally {
      run2.child.kill('SIGTERM');
      await waitExit(run2.child);
    }
    assert.match(second.stdout, /"already_registered": true/, 'same document, fresh process, same store file: still already registered');
    assert.match(second.stdout, /RESULT tier=B mode=valid: allowed=true reason=evidence-verified -> AS EXPECTED/, 'the evidence itself still verifies fine -- the demo\'s dedupe is a separate layer from chiproof\'s verdict');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

// ---------------------------------------------------------------------------
// (d) fail-closed startup: an unwritable DEMO_STORE_PATH must exit non-zero
// with a clear message, never fall back to anything in-memory.
// ---------------------------------------------------------------------------

test('fail-closed startup: an unwritable DEMO_STORE_PATH exits non-zero with a clear message, no server starts', async () => {
  const dir = mkTmpDir('unwritable');
  try {
    // A regular FILE where DEMO_STORE_PATH's PARENT directory needs to be --
    // mkdirSync(..., {recursive:true}) hits ENOTDIR on this, deterministically,
    // without relying on chmod/uid semantics that can vary by how this is run.
    const blocker = join(dir, 'blocker');
    writeFileSync(blocker, 'not a directory');
    const badPath = join(blocker, 'nested', 'store.json');

    const child = spawn(process.execPath, [serverScript], {
      env: {
        ...process.env, PORT: '0', BIND_HOST: '127.0.0.1', DEMO_STORE_PATH: badPath,
      },
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (c) => { stdout += c; });
    child.stderr.on('data', (c) => { stderr += c; });
    const code = await waitExit(child);

    assert.notEqual(code, 0, 'the process must exit non-zero, not hang or start listening');
    assert.match(stderr, /FATAL/, 'a clear message on stderr, not a bare stack trace or silence');
    assert.doesNotMatch(stdout, /listening on/, 'no server ever started listening');
    assert.equal(existsSync(badPath), false, 'no store file was created');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
