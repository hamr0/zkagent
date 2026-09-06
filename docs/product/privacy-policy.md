---
type: reference
title: zkagent Scanner — Privacy Policy
status: stable
sources: [apps/scanner/app/src/main/AndroidManifest.xml, apps/scanner/app/src/main/java/com/tananaev/passportreader/ReportLog.kt, apps/scanner/app/src/main/java/com/tananaev/passportreader/ReportLogStore.kt, apps/scanner/app/build.gradle.kts, apps/scanner/app/src/main/res/xml/network_security_config.xml, docs/product/customer-guide.md, docs/wiki/decisions.md]
---

# zkagent Scanner — Privacy Policy

**Effective date: 2026-09-06.**

## Who this covers

This policy covers the app published to Google Play's closed testing track by
the owner of this open-source project, package name `com.zkagent.scanner`
("the app"). The app is a showcase build of the open-source zkagent scanner
(Apache-2.0). Anyone else who builds and signs their own copy of this source
under their own package name runs their own app and must publish their own
policy — see "Licence" below.

## What the app reads

The app reads the chip built into your passport or ID card over NFC, but only
after you type in the document details (document number, date of birth, and
expiry date) yourself and hold the document to your phone. Nothing is read
automatically or in the background, and NFC only activates while a scan is in
progress.

## What it derives, and what it sends

From the chip, the app checks the issuing government's own signature and
derives a single yes/no answer to a question a website asked (for example,
"is this person over 18"). That yes/no answer — and, only if you choose the
website's "recognise me again" option, a pseudonymous tag computed just for
that website — is sent to the website you opened the scan from. Nothing else
about the document ever leaves the phone: not your name, date of birth,
document number, photo, or nationality.

The per-website tag (when used) cannot be linked across two different
websites — the same document produces a different, unrelated tag at every
other website, by design.

The app talks only to the website that asked for a scan. There is no server
run by us in this path, no central database, and no list of users kept
anywhere by the app's developer.

## What it never sends or stores

- No name, date of birth, document number, photo, or nationality is ever
  transmitted or written to disk, by the app or by us.
- No server of ours is contacted for any purpose. The only network
  destination is the website that started your scan.
- No analytics, no crash reporting, no advertising, and no third-party SDKs
  of any kind are built into this app — checked directly against the app's
  build dependencies, which list only passport/ID-chip-reading libraries and
  a standard Android biometric-prompt library.

## What's stored on your phone

The app keeps a local scan log so you can review your own past scans. Each
entry records the website's address, a plain-language summary of what
happened (for example "sent: a site-only pseudonym + a signed claim"), and a
technical detail block — never a document field, name, date of birth, key
byte, signature value, or nonce. The log holds at most the 20 most recent
entries and is stored only in the app's own private storage on your device.

The app also keeps, on your device only: any cryptographic keys it generated
for a website you chose to be recognised at, and which age question a given
website has locked in (so the same website can't later ask you a different
age).

Android's automatic app backup is turned off for this app, so none of this
data is ever copied off your device by the operating system.

**To delete everything:** clear the app's storage from Android's Settings, or
uninstall the app. Either action erases the scan log, the device keys, and
the per-website locks completely and immediately — there is nothing left on
a server for us to delete, because nothing is ever sent to one.

## Biometrics

If you choose the "recognise me again" option at a website, the app asks for
your fingerprint, face, or device PIN before it signs anything. This uses
Android's own biometric system; your biometric data is handled entirely by
your device's operating system and never reaches the app itself, let alone
leaves the device.

## Permissions this app requests

| Permission | Why |
|---|---|
| NFC | To read the chip in your passport or ID card during a scan |
| Internet | To send your scan's answer to the website that requested it |
| Access network state | To check for a working connection before attempting to send |
| Use biometric | To ask for fingerprint/face/PIN before signing a "recognise me again" answer |

## Children

This app is not directed at children and is not designed to collect data
from them. It exists to let a website check an age fact about whoever is
using it — including thresholds such as 15 or 16 — without collecting any
data from anyone, adult or minor.

## Changes and contact

Questions or concerns about this app can be raised as an issue on its GitHub
repository: https://github.com/hamr0/zkagent/issues. This policy may be
updated as the app changes; the effective date above will change when it is.

## Licence

zkagent is Apache-2.0 licensed and open source. Anyone who builds and
distributes their own copy of this app is a separate operator, running their
own build under their own package name, and is responsible for their own
privacy policy — this document covers only the showcase build published by
this project's owner.
