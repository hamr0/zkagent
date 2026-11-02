// apps/demo/scenarios.mjs — milestones.md §6.9.2 item 24 (D111, D122): the 26 scenarios the local test and showcase
// page offers, each with ONE plain-language line, its "needs" marking (Phone / Build / Test-only) and its controls.
// Pure data plus one renderer; server.mjs splices the rendered section into the page. Nothing here carries a document
// value: the controls only name tiers, predicate kinds and public boundaries (item 16).
//
// A control describes the request the page asks the verifier to create:
//   tier            'A' | 'B' | 'C'
//   preds           tier-C predicate list sent as `predicates` (validated by chiproof on the server)
//   host            'localhost' -> the request is minted for the origin http://localhost:<port> (D122): the same demo
//                   posing as a second, unlisted site; sent as `as_host`, accepted on loopback only
//   expect          'no-reply' -> the app sends an unapproved site nothing (D122 amendment), so the page ends on
//                   "No answer" when the request expires, with a line saying so
//   pick            a <select> beside the button: kind 'threshold' (second age, sent as ?threshold=, tier A/B),
//                   'age' (tier-C dob_at_least with that age), 'raw' (tier-C predicates signed unvalidated)
//   form            instead of asking at once, open the shared tier-C form and focus this field (values are typed by the
//                   holder; they never pass through this file)
//   testOnly        rendered only in test mode (DEMO_TEST_MODE=1)

const SECOND_AGES = [15, 16, 21, 60, 65];
const EXPIRY3 = [{ name: 'expiry_beyond', months: 3 }];
// Two shapes this app refuses as a whole: an age not on its list, and more than ten countries (public ISO codes).
const RAW_SHAPES = [
  { label: 'an age not on the list (17)', value: [{ name: 'dob_at_least', age: 17 }] },
  { label: 'eleven countries excluded', value: [{ name: 'nationality_not_in', codes: ['USA', 'NLD', 'DEU', 'FRA', 'ITA', 'ESP', 'GBR', 'CAN', 'AUS', 'JPN', 'BRA'] }] },
];

const askA = { label: 'Ask: prove over 18 (tier A)', tier: 'A' };
const askB = { label: 'Ask: prove a unique adult (tier B)', tier: 'B' };
const askC3 = { label: 'Ask: document valid for more than 3 months (tier C)', tier: 'C', preds: EXPIRY3 };

export const SCENARIOS = [
  { n: 1, line: 'Tier A, over the threshold: the app answers yes and the page shows you proved you are over 18.', needs: ['Phone'], controls: [askA] },
  { n: 2, line: 'Tier A, under the threshold: an honest "no" (allowed false), not an error.', needs: ['Phone', 'an under-age document'], controls: [askA] },
  { n: 3, line: 'Tier A twice: the two answers differ only in nonce and challenge timestamps; a comparison table shows it after the second scan.', needs: ['Phone', 'two scans'], controls: [askA] },
  { n: 4, line: 'Tier B, first scan: the document is registered as a unique adult human at this site.', needs: ['Phone'], controls: [askB] },
  { n: 5, line: 'Tier B with the same document again: the page says "already registered".', needs: ['Phone', 'second scan'], controls: [askB] },
  { n: 6, line: 'One age per site: a different age asked by the same site is refused in the app (any tier, one shared lock).', needs: ['Phone', 'Test-only (second threshold)'], testOnly: true,
    controls: [{ label: 'Ask a second age (tier A)', tier: 'A', pick: { kind: 'threshold', options: SECOND_AGES } }] },
  { n: 7, line: 'Several ages from one site: only a site on the build\'s multi-age list may ask more than one age; any other site is refused.', needs: ['Phone', 'Build (127.0.0.1 in multi_threshold_verifiers)', 'Test-only'], testOnly: true,
    controls: [{ label: 'Ask a second age (tier A)', tier: 'A', pick: { kind: 'threshold', options: SECOND_AGES } }] },
  { n: 8, line: 'Tier C, expiry: is the document valid for more than 3 months? Yes or no, never the date.', needs: ['Phone', 'Build (switch ON, 127.0.0.1 pinned)', 'holder setting ON'], controls: [askC3] },
  { n: 9, line: 'Tier C, name match: do the given names and surname you type match the document? Yes or no, the name is never sent back.', needs: ['same as row 8'], controls: [{ label: 'Fill in the name and ask', tier: 'C', form: 'cGiven' }] },
  { n: 10, line: 'Tier C, date-of-birth range: is the holder at least 18? Yes or no, never the date.', needs: ['same as row 8'], controls: [{ label: 'Ask: at least 18 (tier C)', tier: 'C', preds: [{ name: 'dob_at_least', age: 18 }] }] },
  { n: 11, line: 'Tier C, nationality: confirm one country, or exclude up to ten. Yes or no, never the country.', needs: ['same as row 8'], controls: [{ label: 'Fill in the countries and ask', tier: 'C', form: 'cNatIs' }] },
  { n: 12, line: 'Tier C, document number: does the number you type match? Yes or no, the number is never shown.', needs: ['same as row 8'], controls: [{ label: 'Fill in the number and ask', tier: 'C', form: 'cNumber' }] },
  { n: 13, line: 'Tier C, several questions in one request: every one is answered, or the whole request is refused.', needs: ['same as row 8'], controls: [{ label: 'Pick several questions and ask', tier: 'C', form: 'cExpiry' }] },
  { n: 14, line: 'Tier C refused: the holder\'s setting "Allow additional verifications" is OFF. This page shows one generic "Refused".', needs: ['Phone', 'setting OFF'], controls: [askC3] },
  { n: 15, line: 'Tier C refused: a question this app does not answer (an age not on its list, or more than 10 countries). One generic "Refused".', needs: ['Phone', 'Test-only (raw shape)'], testOnly: true,
    controls: [{ label: 'Ask a question the app does not answer', tier: 'C', pick: { kind: 'raw', options: RAW_SHAPES } }] },
  { n: 16, line: 'Tier C refused: an age that conflicts with the age this site already locked in. One generic "Refused".', needs: ['Phone', 'Test-only (second threshold)'], testOnly: true,
    controls: [{ label: 'Ask a second age (tier C)', tier: 'C', pick: { kind: 'age', options: SECOND_AGES } }] },
  { n: 17, line: 'Tier C from a site that is not on the pin list (the same demo posing as an unlisted site, localhost): the app tells that site nothing, so this page shows "No answer" when the request expires.', needs: ['Phone', 'Build (pin list without this page\'s host)'],
    controls: [{ label: 'Ask as the unlisted site (localhost, tier C)', tier: 'C', preds: EXPIRY3, host: 'localhost', expect: 'no-reply' }] },
  { n: 18, line: 'Tier C when the build has the operator switch OFF: the app tells the site nothing, so this page shows "No answer" when the request expires.', needs: ['Phone', 'Build (switch OFF)'], controls: [{ ...askC3, expect: 'no-reply' }] },
  { n: 19, line: 'Refusals in rows 14 to 16 show the one generic "Refused" here; rows 17 and 18 show "No answer", because the app tells an unapproved site nothing. The reasons show in the app only.', needs: ['as above'], controls: [{ ...askC3, label: 'Ask tier C (see rows 14 to 18)' }] },
  { n: 20, line: '"No answer": the holder walked away and the request expired. Shown differently from "Refused".', needs: ['Phone (walk away)'], controls: [askC3] },
  { n: 21, line: 'Stale or already-used link: opening an old link still shows the question; tapping Verify on a link past its time shows "Verification session expired", and on a used link still in time the website refuses it and the app says "This link was already used".', needs: ['Phone (reopen an old link)'], controls: [askA] },
  { n: 22, line: 'Link once: a forced recreation of the app does not replay the link or start a second verification.', needs: ['Phone', 'ADB recreation'], controls: [askA] },
  { n: 23, line: 'Wrong document details typed in: the app re-arms and keeps the pending link, so you can retype.', needs: ['Phone (type wrong details)'], controls: [askA] },
  { n: 24, line: 'Card lifted mid-read: the app says "Couldn\'t read" and the flow stays open.', needs: ['Phone (lift the card)'], controls: [askA] },
  { n: 25, line: 'Open age verification: a site on no list (the same demo posing as an unlisted site, localhost) still gets an age answer, for tiers A and B.', needs: ['Phone', 'Build ("verifiers": "any")', 'second host = localhost'],
    controls: [{ label: 'Ask as the unlisted site (localhost, tier A)', tier: 'A', host: 'localhost' }, { label: 'Ask as the unlisted site (localhost, tier B)', tier: 'B', host: 'localhost' }] },
  { n: 26, line: 'Open age verification: that same unlisted site is not on the pin list, so tier C is not answered: the app tells it nothing and this page shows "No answer" when the request expires.', needs: ['same as row 25', 'Build (switch ON)'],
    controls: [{ label: 'Ask as the unlisted site (localhost, tier C)', tier: 'C', preds: EXPIRY3, host: 'localhost', expect: 'no-reply' }] },
];

export const LEAD = 'Ask the zkagent app on your phone to prove an age, a unique adult human, or specific facts about a document. Yes or no answers only: no name, date of birth or other personal field is ever sent. Every scenario is listed below.';

const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

function renderControl(n, c, i) {
  const attrs = [`type="button"`, `class="rowgo"`, `data-row="${n}"`, `data-tier="${c.tier}"`];
  if (c.preds) attrs.push(`data-preds="${esc(JSON.stringify(c.preds))}"`);
  if (c.host) attrs.push(`data-host="${esc(c.host)}"`);
  if (c.expect) attrs.push(`data-expect="${esc(c.expect)}"`);
  if (c.form) attrs.push(`data-form="${esc(c.form)}"`);
  if (c.testOnly) attrs.push('data-test-only="1"');
  let pick = '';
  if (c.pick) {
    attrs.push(`data-pick="${c.pick.kind}"`, `data-pick-id="pick-${n}-${i}"`);
    const opts = c.pick.options.map((o) => (typeof o === 'object'
      ? `<option value="${esc(JSON.stringify(o.value))}">${esc(o.label)}</option>`
      : `<option value="${o}">${o}</option>`)).join('');
    pick = `<select id="pick-${n}-${i}" aria-label="${esc(c.label)}">${opts}</select> `;
  }
  return `${pick}<button ${attrs.join(' ')}>${esc(c.label)}${c.testOnly ? ' (test-only)' : ''}</button>`;
}

/** The scenario list as HTML. With testMode false the test-only controls are NOT rendered at all (row, line and needs stay). */
export function renderScenarios(testMode) {
  const items = SCENARIOS.map((s) => {
    const controls = s.testOnly && !testMode
      ? `<p class="muted testonly-off">Test-only: start the demo with DEMO_TEST_MODE=1 to get this control.</p>`
      : s.controls.map((c, i) => renderControl(s.n, { ...c, testOnly: s.testOnly === true }, i)).join('\n');
    return `<li class="row" data-row-item="${s.n}"><p class="rowline"><strong>${s.n}.</strong> <span class="rowtext" data-row-line="${s.n}">${esc(s.line)}</span></p>
<p class="needs" data-row-needs="${s.n}">Needs: ${esc(s.needs.join(', '))}</p>
<div class="rowctl">${controls}</div></li>`;
  }).join('\n');
  const banner = testMode
    ? '<p id="testModeBanner" class="testmode">Test mode is ON: controls marked (test-only) send request shapes the normal page never sends.</p>'
    : '';
  return `<section id="scenarios"><h2>Every scenario</h2>${banner}
<p class="muted">Each row says what it shows and what it needs. "Phone" is a step on the phone, "Build" is a setting of the installed app build, "Test-only" is a request shape the normal page never sends. Answers show as yes, no or a name of a question, never a document value.</p>
<ol class="rows">
${items}
</ol></section>`;
}
