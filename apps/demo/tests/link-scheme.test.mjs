// node --test — PRD §6.3 item 13 / D76: the sideloaded scanner only reaches
// this demo via av:// VIEW intents (D76), so LINK_SCHEME must default to
// 'av' -- an unset LINK_SCHEME must never hand back an unreachable https://
// app link. LINK_SCHEME is read into a module-level const at import time
// (server.mjs), so each scheme under test needs its own subprocess -- an
// in-process startServer() call could only ever observe the first value.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const serverScript = fileURLToPath(new URL('../server.mjs', import.meta.url));

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
        resolve({ child, url: m[1] });
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

async function createTxAppLink(url) {
  const res = await fetch(`${url}/ui/presentations`, {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}',
  });
  assert.equal(res.status, 201);
  const tx = await res.json();
  return tx.app_link;
}

test('LINK_SCHEME unset defaults to av:// (D76: the sideloaded scanner cannot reach an https app link)', async () => {
  const dir = mkTmpDir('link-scheme-default');
  const { child, url } = await spawnServer({ DEMO_STORE_PATH: join(dir, 'store.json') });
  try {
    const appLink = await createTxAppLink(url);
    assert.match(appLink, /^av:\/\/authorize\?/);
  } finally {
    child.kill();
    rmSync(dir, { recursive: true, force: true });
  }
});

test('LINK_SCHEME=https still selects the https app link explicitly', async () => {
  const dir = mkTmpDir('link-scheme-https');
  const { child, url } = await spawnServer({ DEMO_STORE_PATH: join(dir, 'store.json'), LINK_SCHEME: 'https' });
  try {
    const appLink = await createTxAppLink(url);
    assert.match(appLink, /^https:\/\/.+request_uri=/);
  } finally {
    child.kill();
    rmSync(dir, { recursive: true, force: true });
  }
});
