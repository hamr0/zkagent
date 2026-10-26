// node --test — PUBLIC_SCHEME (server.mjs), the one knob the test-only TLS deploy kit (deploy/) needs: behind a
// TLS-terminating proxy the node process still speaks plain HTTP, so the URIs it hands the phone must say https.
// Each case starts the REAL server as a child process (the scheme is read once at module load), asks it for a
// transaction, and reads the URIs back. A bad value must be a startup failure, never a silent fallback to http.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const SERVER = new URL('../server.mjs', import.meta.url).pathname;

function start(extraEnv) {
  const dir = mkdtempSync(join(tmpdir(), 'zkagent-demo-scheme-'));
  const env = { ...process.env, PORT: '0', DEMO_STORE_PATH: join(dir, 'store.json') };
  delete env.PUBLIC_SCHEME;
  Object.assign(env, extraEnv);
  const child = spawn(process.execPath, [SERVER], { env, stdio: ['ignore', 'pipe', 'pipe'] });
  let out = '';
  child.stdout.on('data', (c) => { out += c; });
  child.stderr.on('data', (c) => { out += c; });
  const stop = () => { child.kill('SIGTERM'); rmSync(dir, { recursive: true, force: true }); };
  return { child, stop, output: () => out };
}

// PORT=0 prints "listening on http://127.0.0.1:<port>"; poll the output for that line (no fixed sleep).
async function listeningUrl(s) {
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    const m = /listening on (http:\/\/\S+)/.exec(s.output());
    if (m) return m[1];
    if (s.child.exitCode !== null) throw new Error(`server exited early: ${s.output()}`);
    await new Promise((r) => setTimeout(r, 50));
  }
  throw new Error(`server never reported listening: ${s.output()}`);
}

async function createTx(env) {
  const s = start(env);
  try {
    const url = await listeningUrl(s);
    const res = await fetch(`${url}/ui/presentations`, {
      method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}',
    });
    assert.equal(res.status, 201);
    return await res.json();
  } finally {
    s.stop();
  }
}

test('PUBLIC_SCHEME=https makes every URI the server hands out https, on the Host the request arrived on (here the loopback address the test connects to)', async () => {
  const tx = await createTx({ PUBLIC_SCHEME: 'https' });
  assert.match(tx.request_uri, /^https:\/\/127\.0\.0\.1:\d+\/wallet\/request\.jwt\//);
  assert.match(decodeURIComponent(tx.app_link_av), /client_id=https:\/\/127\.0\.0\.1:\d+\/wallet\/direct_post/);
});

test('default (PUBLIC_SCHEME unset) is still http', async () => {
  const tx = await createTx({});
  assert.match(tx.request_uri, /^http:\/\/127\.0\.0\.1:\d+\/wallet\/request\.jwt\//);
});

test('a PUBLIC_SCHEME that is neither http nor https stops the server at startup', async () => {
  const s = start({ PUBLIC_SCHEME: 'ftp' });
  const code = await new Promise((resolve) => s.child.once('exit', resolve));
  assert.notEqual(code, 0);
  assert.match(s.output(), /PUBLIC_SCHEME must be 'http' or 'https'/);
  s.stop();
});
