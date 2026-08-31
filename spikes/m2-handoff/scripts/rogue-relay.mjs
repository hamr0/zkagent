#!/usr/bin/env node
// spikes/m2-handoff/scripts/rogue-relay.mjs — ON-DEVICE negative for the M2 POC.
// THROWAWAY spike helper. Reuses the rogue-relay idea from tests/roundtrip.test.mjs
// ("negative: tampered request object JWS => wallet refuses BEFORE direct_post").
//
// A man-in-the-middle sitting between the phone and the real verifier:
//   - proxies POST /ui/presentations to the real server, capturing the REAL
//     transactionId, and rewrites the real base -> this relay's base so the
//     phone talks only to the relay;
//   - serves GET /wallet/request.jwt/{id} by fetching the real (validly-signed)
//     request object and TAMPERING its payload (edits the age threshold) while
//     leaving the ES256 signature in place — exactly the attack the on-device
//     wallet must catch;
//   - proxies the poll GET /ui/presentations/{id} to the real server so the page
//     can observe that the REAL transaction stays pending (the wallet refused
//     and never POSTed direct_post).
//
// Usage: REAL=http://127.0.0.1:8790 PORT=8791 node scripts/rogue-relay.mjs
import { createServer } from 'node:http';

const REAL = process.env.REAL ?? 'http://127.0.0.1:8790';
const PORT = Number(process.env.PORT ?? 8791);
let SELF = `http://127.0.0.1:${PORT}`; // rewritten target; phone reaches it via adb reverse
let lastRealTxId = null;

const PAGE = `<!doctype html><meta charset=utf-8>
<meta name=viewport content="width=device-width, initial-scale=1">
<title>ROGUE relay — tampered request (negative)</title>
<style>body{font:15px/1.5 system-ui;max-width:44rem;margin:2rem auto;padding:0 1rem}
button{font:inherit;padding:.6rem 1.2rem}code,pre{background:#f2f2f2;padding:.1rem .3rem}
pre{padding:.6rem;white-space:pre-wrap;word-break:break-all}.bad{color:#b71c1c;font-weight:bold}</style>
<h1>ROGUE relay (on-device negative)</h1>
<p class=bad>This page is a man-in-the-middle. The request object it will hand the
app has been TAMPERED after signing. A correct wallet must REFUSE and never post.</p>
<button id=go>Start tampered mode-A request</button>
<div id=out hidden>
<h2>Same-device app link (points at the ROGUE relay)</h2>
<p><a id=applink href=#></a></p>
<h2>Status of the REAL transaction (proxied)</h2>
<p id=status>waiting…</p><pre id=raw></pre></div>
<script>
let t=null;
document.getElementById('go').addEventListener('click',async()=>{
 if(t)clearInterval(t);
 const r=await fetch('/ui/presentations',{method:'POST',headers:{'content-type':'application/json'},body:'{}'});
 const tx=await r.json();
 document.getElementById('out').hidden=false;
 const a=document.getElementById('applink');a.href=tx.app_link;a.textContent=tx.app_link;
 document.getElementById('status').textContent='waiting for the app to (not) respond…';
 t=setInterval(async()=>{
  const p=await fetch('/ui/presentations/'+tx.transactionId);const s=await p.json();
  document.getElementById('raw').textContent=JSON.stringify(s,null,2);
  if(s.status==='done'){clearInterval(t);t=null;
   document.getElementById('status').textContent='REAL tx went done (unexpected for the negative)';}
  else{document.getElementById('status').textContent='REAL tx still PENDING (wallet refused) ✓';}
 },1000);
});
</script>`;

const server = createServer((req, res) => {
  (async () => {
    const url = new URL(req.url, SELF);
    const path = url.pathname;

    if (req.method === 'GET' && path === '/') {
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
      res.end(PAGE); return;
    }

    if (req.method === 'POST' && path === '/ui/presentations') {
      const r = await fetch(`${REAL}/ui/presentations`, {
        method: 'POST', headers: { 'content-type': 'application/json' }, body: '{}',
      });
      let text = await r.text();
      lastRealTxId = JSON.parse(text).transactionId;
      // Rewrite the real base -> the rogue base so the phone only ever hits us.
      text = text.replaceAll(REAL, SELF).replaceAll(encodeURIComponent(REAL), encodeURIComponent(SELF));
      console.error(`[rogue] created; REAL transactionId=${lastRealTxId}`);
      res.writeHead(r.status, { 'content-type': 'application/json' }); res.end(text); return;
    }

    if (req.method === 'GET' && path.startsWith('/wallet/request.jwt/')) {
      const r = await fetch(`${REAL}${path}`);
      const [h, p, s] = (await r.text()).split('.');
      const payload = JSON.parse(Buffer.from(p, 'base64url').toString('utf8'));
      payload.zkagent.challenge.threshold = 16; // forged AFTER signing -> sig no longer matches
      const p2 = Buffer.from(JSON.stringify(payload)).toString('base64url');
      console.error('[rogue] served TAMPERED request.jwt (threshold->16, signature left intact)');
      res.writeHead(200, { 'content-type': 'application/oauth-authz-req+jwt' });
      res.end([h, p2, s].join('.')); return;
    }

    if (req.method === 'GET' && path.startsWith('/ui/presentations/')) {
      const r = await fetch(`${REAL}${path}`);
      const text = await r.text();
      res.writeHead(r.status, { 'content-type': 'application/json' }); res.end(text); return;
    }

    // If the app ever tried to post here (it must not), record and 200 it.
    if (req.method === 'POST' && path === '/wallet/direct_post') {
      console.error('[rogue] !!! app POSTed direct_post to the rogue relay — wallet did NOT refuse');
      res.writeHead(200, { 'content-type': 'application/json' }); res.end('{"accepted":true}'); return;
    }

    res.writeHead(404); res.end();
  })().catch((e) => { try { res.writeHead(500); res.end(String(e)); } catch { /* gone */ } });
});

server.listen(PORT, '127.0.0.1', () => console.error(`[rogue] listening ${SELF} -> REAL ${REAL}`));
