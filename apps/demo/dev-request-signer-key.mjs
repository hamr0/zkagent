// spikes/m2-handoff/dev-request-signer-key.mjs — DEV-ONLY ES256 (P-256)
// request-object signing keypair. Same standing as dev-attester-key.mjs and
// the baked-in CHALLENGE_SECRET: spike convenience so the server and the fake
// wallet agree without shared state. NOT a secret, NOT for any real deployment.
export const DEV_REQUEST_SIGNER = Object.freeze({
  kid: 'dev-request-signer-1',
  privateKeyPem: `-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgfo7/MG7ZttBghhSp
89T3CkohkIxfDxa0ya3jhl9gPGahRANCAAQLT5gcrGRnz8V9+nurXsE93UpG7DE2
57hu8A00oEy/RLVwf39Fiem3+PAMeoozmVzwiN8TmZQQO4FuZCtF3Wbb
-----END PRIVATE KEY-----`,
  publicKeyPem: `-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEC0+YHKxkZ8/Fffp7q17BPd1KRuwx
Nue4bvANNKBMv0S1cH9/RYnpt/jwDHqKM5lc8IjfE5mUEDuBbmQrRd1m2w==
-----END PUBLIC KEY-----`,
});
