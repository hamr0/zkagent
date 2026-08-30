#!/usr/bin/env node
// Verify + decode an Android hardware key attestation certificate chain.
// Stdlib only (node:crypto, node:fs). Zero npm dependencies (M1 riskiest
// assumption #4: is this implementable inside our dependency rules?).
//
// Usage: node verify-attestation.mjs <chain.pem> [--at <ISO date>]
//   <chain.pem> = concatenated PEM certs, LEAF FIRST, root last.
//   --at        = evaluate validity as of this date instead of now
//                 (for reproducible runs); default is the current time.
//
// Exit code: 0 = chain valid + extension decoded. Non-zero = any failure
// (parse error, chain break, root mismatch, expired/not-yet-valid cert) —
// never a silent partial; the JSON report's ok/chainValid fields also
// carry the reason.
//
// NOTE: node:crypto's X509Certificate.verify() only checks the signature —
// it does NOT check notBefore/notAfter. An earlier version of this script
// reported chainValid:true for a chain whose root had already expired.
// Date validity is checked explicitly below, per-cert, and folded into
// chainValid. The leaf's 1970-2048 window (a fixed Keystore placeholder,
// not a real validity claim) is not special-cased — it simply passes like
// any other cert.

import { readFileSync } from 'node:fs';
import { loadPinnedRoots, matchesPinnedRoot } from './google-hardware-roots.mjs';
import { decodeChainFromPem } from './decode-chain.mjs';

// Verify each cert in the chain was issued by the next one (leaf -> ... -> root).
function verifyChainLinks(certs) {
  const results = [];
  for (let i = 0; i < certs.length - 1; i++) {
    const subject = certs[i];
    const issuer = certs[i + 1];
    let issuedCheck = false;
    let sigCheck = false;
    let error = null;
    try {
      issuedCheck = subject.checkIssued(issuer);
      sigCheck = subject.verify(issuer.publicKey);
    } catch (e) {
      error = e.message;
    }
    results.push({ subjectIndex: i, issuerIndex: i + 1, checkIssued: issuedCheck, signatureValid: sigCheck, error });
  }
  return results;
}

// Per-cert date validity check against `at` (a Date). Returns one entry per cert.
function checkValidity(certs, at) {
  return certs.map((c, i) => {
    const notBefore = c.validFromDate;
    const notAfter = c.validToDate;
    const validNow = at >= notBefore && at <= notAfter;
    return {
      index: i,
      validNow,
      reason: validNow ? null : at < notBefore ? 'not yet valid' : 'expired',
    };
  });
}

function parseArgs(argv) {
  const file = argv[0];
  let at = new Date();
  for (let i = 1; i < argv.length; i++) {
    if (argv[i] === '--at') {
      const raw = argv[++i];
      at = new Date(raw);
      if (Number.isNaN(at.getTime())) {
        console.error(`Invalid --at date: ${raw}`);
        process.exit(2);
      }
    }
  }
  return { file, at };
}

function main() {
  const { file, at } = parseArgs(process.argv.slice(2));
  if (!file) {
    console.error('Usage: node verify-attestation.mjs <chain.pem> [--at <ISO date>]');
    process.exit(2);
  }

  const report = {
    file,
    evaluatedAt: at.toISOString(),
    ok: false,
    chainValid: false,
    reason: null,
    rootPinned: null,
    chain: [],
    extension: null,
  };

  let decoded;
  try {
    decoded = decodeChainFromPem(readFileSync(file, 'utf8'));
  } catch (e) {
    report.reason = `parse error: ${e.message}`;
    console.log(JSON.stringify(report, null, 2));
    process.exit(1);
  }

  const { certs, chain, extension } = decoded;
  report.extension = extension;

  const linkResults = verifyChainLinks(certs);
  const allLinksOk = linkResults.every((r) => r.checkIssued && r.signatureValid && !r.error);
  report.linkVerification = linkResults;

  const validityResults = checkValidity(certs, at);
  const allValidNow = validityResults.every((r) => r.validNow);
  report.chain = chain.map((c, i) => ({ ...c, validNow: validityResults[i].validNow, validityReason: validityResults[i].reason }));

  const pinnedRoots = loadPinnedRoots();
  const topCert = certs[certs.length - 1];
  const matchedRoot = matchesPinnedRoot(topCert, pinnedRoots);
  report.rootPinned = matchedRoot ? { matchedFile: matchedRoot.file } : null;

  report.chainValid = allLinksOk && matchedRoot !== null && allValidNow;
  if (!allLinksOk) report.reason = 'one or more chain links failed checkIssued/signature verification';
  else if (!matchedRoot) report.reason = 'top-of-chain cert does not byte-match any pinned Google hardware root';
  else if (!allValidNow) {
    const bad = validityResults.filter((r) => !r.validNow).map((r) => `cert ${r.index} (${r.reason})`);
    report.reason = `one or more certs are not valid at ${at.toISOString()}: ${bad.join(', ')}`;
  }

  report.ok = report.chainValid && extension && !extension.error;
  if (!report.ok && !report.reason) report.reason = extension?.error ?? 'unknown failure';

  console.log(JSON.stringify(report, null, 2));
  process.exit(report.ok ? 0 : 1);
}

main();
