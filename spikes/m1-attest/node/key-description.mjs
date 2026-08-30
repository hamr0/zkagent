// Decode the Android key attestation extension payload (OID
// 1.3.6.1.4.1.11129.2.1.17), a DER-encoded KeyDescription structure,
// per developer.android.com "Verifying hardware-backed key pairs
// with key attestation" / the AOSP KeyMint attestation record schema.
import {
  TAG_CLASS,
  readSequence,
  readSet,
  readIntegerBigInt,
  readEnumerated,
  readBoolean,
  readOctetString,
  findContextTag,
  readExplicit,
} from './der.mjs';

const VERIFIED_BOOT_STATE = { 0: 'Verified', 1: 'SelfSigned', 2: 'Unverified', 3: 'Failed' };

// AuthorizationList context tag numbers we care about (full list per AOSP schema;
// only these are decoded — everything else in the AuthorizationList is left alone).
const TAG = {
  ROOT_OF_TRUST: 704,
  OS_VERSION: 705,
  OS_PATCH_LEVEL: 706,
  ATTESTATION_APPLICATION_ID: 709,
  VENDOR_PATCH_LEVEL: 718,
  BOOT_PATCH_LEVEL: 719,
};

// seqTlv here is the RootOfTrust SEQUENCE already unwrapped from its [704] EXPLICIT tag by the caller.
function parseRootOfTrust(buf, seqTlv) {
  const children = seqTlv.children;
  const verifiedBootKey = readOctetString(buf, children[0]);
  const deviceLocked = readBoolean(buf, children[1]);
  const verifiedBootStateNum = readEnumerated(buf, children[2]);
  const verifiedBootHash = children[3] ? readOctetString(buf, children[3]) : null;
  return {
    verifiedBootKey: verifiedBootKey.toString('hex'),
    deviceLocked,
    verifiedBootState: VERIFIED_BOOT_STATE[verifiedBootStateNum] ?? `unknown(${verifiedBootStateNum})`,
    verifiedBootHash: verifiedBootHash ? verifiedBootHash.toString('hex') : null,
  };
}

// AttestationApplicationId ::= SEQUENCE { packageInfoRecords SET OF AttestationPackageInfo, signatureDigests SET OF OCTET_STRING }
// AttestationPackageInfo ::= SEQUENCE { packageName OCTET_STRING, version INTEGER }
function parseAttestationApplicationId(buf) {
  const seq = readSequence(buf, 0);
  const [packageInfoSetTlv, sigDigestSetTlv] = seq.children;
  const packageInfoSet = readSet(buf, packageInfoSetTlv.valueStart - packageInfoSetTlv.headerLen);
  const sigDigestSet = readSet(buf, sigDigestSetTlv.valueStart - sigDigestSetTlv.headerLen);
  const packages = packageInfoSet.children.map((pkgTlv) => {
    const pkgSeq = readSequence(buf, pkgTlv.valueStart - pkgTlv.headerLen);
    const packageName = readOctetString(buf, pkgSeq.children[0]).toString('utf8');
    const version = readIntegerBigInt(buf, pkgSeq.children[1]).toString();
    return { packageName, version };
  });
  const signatureDigests = sigDigestSet.children.map((d) => readOctetString(buf, d).toString('hex'));
  return { packages, signatureDigests };
}

function extractAuthList(buf, listSeq) {
  const children = listSeq.children;
  const out = {};

  const rot = findContextTag(children, TAG.ROOT_OF_TRUST);
  if (rot) {
    const inner = readExplicit(buf, rot);
    const rotSeq = readSequence(buf, inner.valueStart - inner.headerLen);
    out.rootOfTrust = parseRootOfTrust(buf, rotSeq);
  }

  const osVer = findContextTag(children, TAG.OS_VERSION);
  if (osVer) out.osVersion = Number(readIntegerBigInt(buf, readExplicit(buf, osVer)));

  const osPatch = findContextTag(children, TAG.OS_PATCH_LEVEL);
  if (osPatch) out.osPatchLevel = Number(readIntegerBigInt(buf, readExplicit(buf, osPatch)));

  const vendorPatch = findContextTag(children, TAG.VENDOR_PATCH_LEVEL);
  if (vendorPatch) out.vendorPatchLevel = Number(readIntegerBigInt(buf, readExplicit(buf, vendorPatch)));

  const bootPatch = findContextTag(children, TAG.BOOT_PATCH_LEVEL);
  if (bootPatch) out.bootPatchLevel = Number(readIntegerBigInt(buf, readExplicit(buf, bootPatch)));

  const appId = findContextTag(children, TAG.ATTESTATION_APPLICATION_ID);
  if (appId) {
    const octet = readOctetString(buf, readExplicit(buf, appId));
    out.attestationApplicationId = parseAttestationApplicationId(octet);
  }

  return out;
}

// Top-level: KeyDescription ::= SEQUENCE {
//   attestationVersion INTEGER, attestationSecurityLevel ENUMERATED,
//   keymintVersion INTEGER, keymintSecurityLevel ENUMERATED,
//   attestationChallenge OCTET_STRING, uniqueId OCTET_STRING,
//   softwareEnforced AuthorizationList, hardwareEnforced AuthorizationList }
const SECURITY_LEVEL = { 0: 'Software', 1: 'TrustedEnvironment', 2: 'StrongBox' };

export function decodeKeyDescription(extensionOctetValue) {
  // The extension's OCTET STRING value itself contains the DER-encoded KeyDescription SEQUENCE.
  const seq = readSequence(extensionOctetValue, 0);
  const c = seq.children;
  const attestationVersion = Number(readIntegerBigInt(extensionOctetValue, c[0]));
  const attestationSecurityLevelNum = readEnumerated(extensionOctetValue, c[1]);
  const keymintVersion = Number(readIntegerBigInt(extensionOctetValue, c[2]));
  const keymintSecurityLevelNum = readEnumerated(extensionOctetValue, c[3]);
  const attestationChallenge = readOctetString(extensionOctetValue, c[4]).toString('utf8');
  const uniqueId = readOctetString(extensionOctetValue, c[5]).toString('hex');
  const softwareEnforced = readSequence(extensionOctetValue, c[6].valueStart - c[6].headerLen);
  const hardwareEnforced = readSequence(extensionOctetValue, c[7].valueStart - c[7].headerLen);

  return {
    attestationVersion,
    attestationSecurityLevel: SECURITY_LEVEL[attestationSecurityLevelNum] ?? `unknown(${attestationSecurityLevelNum})`,
    keymintVersion,
    keymintSecurityLevel: SECURITY_LEVEL[keymintSecurityLevelNum] ?? `unknown(${keymintSecurityLevelNum})`,
    attestationChallenge,
    uniqueId,
    softwareEnforced: extractAuthList(extensionOctetValue, softwareEnforced),
    hardwareEnforced: extractAuthList(extensionOctetValue, hardwareEnforced),
  };
}

export const ATTESTATION_EXTENSION_OID = '1.3.6.1.4.1.11129.2.1.17';
