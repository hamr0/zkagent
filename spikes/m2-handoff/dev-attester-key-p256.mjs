// spikes/m2-handoff/dev-attester-key-p256.mjs — DEV-ONLY attester P-256
// keypair, for the sig-p256/1 alternative (D31: the verifier accepts either
// sig-ed25519/1 or sig-p256/1, not one fixed plug — device.mjs picks whichever
// its Keystore actually produced, F2). Like dev-attester-key.mjs (Ed25519):
// spike convenience so the server and the fake wallet agree on a key without
// shared state. NOT a secret, NOT for any real deployment — a real attester
// generates and holds its own private key (D30/D31).
export const DEV_ATTESTER_P256 = Object.freeze({
  key_id: 'dev-attester-p256-1',
  privateKeyPem: `-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgykDPg/BaAkJsoGnC
clCALXwLHwJZtQSuOdeeGd6g1LOhRANCAATQM2JUghfoX6DZc7RUr9SNdvmQ4dxf
lo47zXMIuHy+QW4ZJgAuIy5psviVtiU1t4bCoLnneRqaltKJZtbTiZgw
-----END PRIVATE KEY-----`,
  publicKeyPem: `-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE0DNiVIIX6F+g2XO0VK/UjXb5kOHc
X5aOO81zCLh8vkFuGSYALiMuabL4lbYlNbeGwqC553kampbSiWbW04mYMA==
-----END PUBLIC KEY-----`,
});
