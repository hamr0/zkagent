// Google hardware attestation root certificates, pinned from:
//   https://developer.android.com/privacy-and-security/security-key-attestation
// (section "Root certificate"), fetched 2026-08-29.
//
// Google publishes multiple self-signed roots (different key algorithms /
// generations); a valid chain's top cert must byte-match one of these.
// SHA-256 of each PEM file's bytes as fetched (see fixtures/public/google-hardware-roots/):
//   root_0.pem (RSA, serial F1C172A699EAF51D):                            25c0e389777a2f3bfc5f6e812862fb15af76162fa7ee6b46d100e9d5b5738667
//   root_1.pem (RSA, CN=Key Attestation CA1, serial 84A9D0297B0EB58AE7FF0E80DE760605 — legacy software-attestation root): 979d0f7fba6c2e28cc05a5d67e8f21f705b90379291cbc80291eb224cc11586b
//   root_2.pem (RSA, serial E8FA196314D2FA18):                            dafd8256c789f519c4766e1efec70515bc34a065cd10155d01593f7d085763e1
//   root_3.pem (RSA, serial D50FF25BA3F2D6B3):                            9377d92c9dfed9b781467c5ce1b36068007d11f18813123d4277b205baf45c85
//   root_4.pem (RSA, serial C36B7C44B9AE1831):                            c620e80bd059799071891a2638520cf11184060fd311643c928cbc222c061325
//
// We pin by re-parsing each PEM with crypto.X509Certificate and comparing
// DER bytes (cert.raw), not by string/PEM comparison, to avoid whitespace
// false-negatives/positives.
import { readFileSync, readdirSync } from 'node:fs';
import { X509Certificate } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOTS_DIR = path.join(__dirname, '..', 'fixtures', 'public', 'google-hardware-roots');

export function loadPinnedRoots() {
  const files = readdirSync(ROOTS_DIR).filter((f) => f.endsWith('.pem')).sort();
  return files.map((f) => {
    const pem = readFileSync(path.join(ROOTS_DIR, f), 'utf8');
    const cert = new X509Certificate(pem);
    return { file: f, cert };
  });
}

export function matchesPinnedRoot(cert, pinnedRoots) {
  return pinnedRoots.find((r) => Buffer.compare(r.cert.raw, cert.raw) === 0) ?? null;
}
