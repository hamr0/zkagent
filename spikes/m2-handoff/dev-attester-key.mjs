// spikes/m2-handoff/dev-attester-key.mjs — DEV-ONLY attester Ed25519 keypair.
// Like the baked-in CHALLENGE_SECRET: spike convenience so the server and the
// fake wallet agree on a key without shared state. NOT a secret, NOT for any
// real deployment — a real attester generates and holds its own private key
// (D30: "every attestor can create their own private key").
export const DEV_ATTESTER = Object.freeze({
  key_id: 'dev-attester-1',
  privateKeyPem: `-----BEGIN PRIVATE KEY-----
MC4CAQAwBQYDK2VwBCIEIPtWcNvwWRcg2XPGugMPkrUTnewi56ewjx5XhqhU5BBd
-----END PRIVATE KEY-----`,
  publicKeyPem: `-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEA3cldSPzepfNtAh3cANzBf60M3VUUTpHytkB9NfD/sQU=
-----END PUBLIC KEY-----`,
});
