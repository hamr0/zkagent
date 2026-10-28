#!/usr/bin/env node
// apps/demo/scripts/redirect-proxy.mjs — TEST HELPER, not part of the product. Never deploy it.
//
// A reverse proxy for the device check "the app must not follow a redirect on the calls it makes to the site" (finding #52).
// It sits on the port the phone reaches (default 8787, the `adb reverse` port) and forwards everything to the real demo
// (default 8788) EXCEPT:
//   - POST /wallet/direct_post      never forwarded; answered with the redirect (this is both the app's answer POST and the
//                                   tier-C refusal POST),
//   - GET  /wallet/request.jwt/*    the same, but only with --redirect-get (this is the request_uri GET).
// The redirect points at --landing (default /redirect-landing), a 200 page this proxy serves itself, so a client that follows
// the redirect gets a 200 and the log shows exactly which method it re-issued. Every other request is forwarded unchanged
// (Host header included, so the app's origin pin and same-origin checks still pass).
//
//   node scripts/redirect-proxy.mjs [--listen 8787] [--upstream 8788] [--code 302] [--landing /redirect-landing] [--redirect-get]
//
//   --listen PORT     port the proxy listens on, 127.0.0.1 only (default 8787)
//   --upstream PORT   port of the real demo (default 8788; start it with PORT=8788 node server.mjs)
//   --code N          redirect status: 301, 302, 303, 307 or 308 (default 302)
//   --landing PATH    where the redirect points (default /redirect-landing)
//   --redirect-get    also redirect GET /wallet/request.jwt/* (the request_uri fetch)
//
// Log lines (to stdout): REDIRECTED (a request it answered with the redirect), LANDING (the redirect target was requested:
// the client followed the redirect), FORWARD (passed to the demo). Lengths only; it never logs a body, a header or a query.
// A correct app never produces a LANDING line.
import http from 'node:http';

const args = process.argv.slice(2);
const opt = (n, d) => { const i = args.indexOf(`--${n}`); return i >= 0 && i + 1 < args.length ? args[i + 1] : d; };
const listen = Number(opt('listen', '8787'));
const upstream = Number(opt('upstream', '8788'));
const code = Number(opt('code', '302'));
const landing = opt('landing', '/redirect-landing');
const redirectGet = args.includes('--redirect-get'); // also answer GET /wallet/request.jwt/* with the redirect
const t = () => new Date().toISOString().slice(11, 23);
let landingHits = 0;

http.createServer((req, res) => {
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    const body = Buffer.concat(chunks);
    const path = req.url.split('?')[0];
    if ((req.method === 'POST' && path === '/wallet/direct_post') || (redirectGet && req.method === 'GET' && path.startsWith('/wallet/request.jwt/'))) {
      console.log(`${t()} REDIRECTED ${req.method} ${path} body_len=${body.length} -> ${code} Location: ${landing}`);
      res.writeHead(code, { location: landing, 'content-length': 0 });
      res.end();
      return;
    }
    if (path === landing) {
      landingHits += 1;
      console.log(`${t()} LANDING ${req.method} ${path} body_len=${body.length} hit=${landingHits} -> 200`);
      const b = JSON.stringify({ landing: true, method_seen: req.method });
      res.writeHead(200, { 'content-type': 'application/json', 'content-length': Buffer.byteLength(b) });
      res.end(b);
      return;
    }
    const up = http.request({ host: '127.0.0.1', port: upstream, method: req.method, path: req.url, headers: req.headers }, (ur) => {
      console.log(`${t()} FORWARD ${req.method} ${path} -> ${ur.statusCode}`);
      res.writeHead(ur.statusCode, ur.headers);
      ur.pipe(res);
    });
    up.on('error', (e) => { console.log(`${t()} FORWARD ${req.method} ${path} upstream error ${e.code}`); res.writeHead(502); res.end(); });
    up.end(body);
  });
}).listen(listen, '127.0.0.1', () => console.log(`${t()} proxy listening 127.0.0.1:${listen} -> upstream ${upstream}, direct_post${redirectGet ? ' and request_uri GET' : ''} answer ${code} to ${landing}`));
