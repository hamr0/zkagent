# Design plan: scanner main screen without the tab strip (Variant F)

Status: owner-approved design (live-canvas, 2026-10-27). Build input for a new M3b chunk 4 item (D115). The ruling and the milestone item come first (scope gate), then the build.

Built in 4f6703e (D115, item 25); kept as the design record.

## Summary

- **Scope:** page. Redesign of `apps/scanner` main screen (`activity_main.xml`, `MainActivity`, `PaneState`).
- **Winner:** Variant F, a synthesis of A (question first, dots) and B (big Verify), with owner edits.
- **Key changes:**
  - The four-tab strip (Scan, Log, Diagnostics, Settings) is removed.
  - There are three swipeable pages: Scan, Log, Settings. Diagnostics moves into Settings as a collapsed section at the bottom.
  - The top bar shows a centred page title (just "Scan", "Log" or "Settings"), with a tappable "‹ Prev" on the left and "Next ›" on the right. Each arrow is hidden at its end page.
  - Page dots sit at the bottom. There are no in-page headings, because the title already names the page.
  - The Scan page, from top to bottom:
    1. The site's question in a dark navy box with white text, with no light-blue fill and no left bar.
    2. The hint line ("Type your details, then VERIFY" / "Hold your document to the top of the back of your phone" / "Keep still…").
    3. The three fields, always visible, outlined with floating labels, and empty for each scan. Document details are never saved (item 6).
    4. A large VERIFY button.
    5. Directly under it, an always-visible last-scan status line, bold on a tinted strip and coloured by status: delivered is teal ✓, refused is red ✗, none yet is grey –.

## Files to change

- [ ] `apps/scanner/app/src/main/res/layout/activity_main.xml`: remove the `TabLayout`; add the pager, title bar with prev/next, and dots; restyle the Scan page.
- [ ] `apps/scanner/app/src/main/java/com/tananaev/passportreader/PaneState.kt`: there are three pages now (Scan, Log, Settings). `TAB_DIAGNOSTICS` folds into Settings, so map old saved values to Settings. An incoming admitted `av://` intent still selects Scan (M2 item 17).
- [ ] `MainActivity.kt`: page selection, the prev/next arrows, the dots, and Diagnostics inside Settings. The single pane-visibility writer rule (item 16) stays.
- [ ] `res/values/colors.xml`, `res/values-night/colors.xml`: the navy ask box (day and night), and the status strip colours (teal, red and grey tints, using the existing `refusal_text`).
- [ ] `res/values/strings.xml`: the page titles and arrow content descriptions.
- [ ] Tests: `PaneStateTest` (page mapping, handoff → Scan, restore of an old Diagnostics value) and the last-scan status mapping (pure class).

## Implementation steps

1. Write the D115 ruling and the new milestone item first, as docs only.
2. Use a pager already on the classpath. `androidx.viewpager2` arrives transitively with `material`. If you use it directly, state that it is transitive and not a new library; otherwise use plain Views. Add no new third-party dependency.
3. Keep `PaneState` as the single owner of the selected page, so that page selection stays testable outside the Activity.
4. Restyle the Scan page as described in the summary.
5. Make the last-scan line a pure projection of the newest log entry's outcome glyph state (item 22 pass/fail/pending) plus its existing text. Add no new state.
6. Move Diagnostics (masterlist probe, device-key probe) into a collapsed section at the bottom of Settings.

## Component API / state

- `PaneState.selectedPage`: one of `SCAN`, `LOG`, `SETTINGS`. It is saved in instance state, and old `TAB_DIAGNOSTICS` maps to `SETTINGS`.
- `LastScanStatus.of(newestEntry)` returns `Delivered` (teal), `Refused` (red), `NotSent` (neutral, for "Read OK — nothing was sent.") or `None` ("No scans yet.").
- The Verify flow (unchanged logic): VERIFY → lock → "TAP AND VERIFY" + hold hint → "Reading document…" → outcome popup → back to Scan with the status line updated.

## Required UI states

- Scan has these states: no site (local scan), site asking (navy question), locked (TAP AND VERIFY), reading, outcome popup, and refused on arrival (red popup, D112).
- Last scan: delivered, refused, not sent, none yet.
- Log: empty, and with entries.
- Settings: tier C off (switch hidden, unavailable text) and tier C on (switch plus the three paragraphs); Diagnostics collapsed and expanded.

## Accessibility checklist

- Touch targets are at least 48dp, including the prev/next arrows.
- The arrows have content descriptions ("Previous page: Log", "Next page: Settings").
- The dots are decorative (`importantForAccessibility="no"`); the title is announced on page change.
- The last-scan line is a live region (polite), and its status is not conveyed by colour alone (✓ ✗ –).
- Text on the navy box and the tinted strips must reach a contrast of at least 4.5:1, day and night.

## Testing checklist

- [ ] Unit: page mapping, handoff → Scan, old Diagnostics restore → Settings, last-scan status mapping (literal expectations plus a negative control).
- [ ] Device: no tab strip; swipe and arrows reach Log and Settings; Diagnostics is at the bottom of Settings; a site link lands on Scan from any page; the page survives a dark-mode toggle; the last-scan line shows teal after a delivery and red after a refusal; dark mode was read by the owner.

## Design tokens

| Token | Day | Night |
|---|---|---|
| primary | #009688 | #26a69a |
| ask box bg / text | #0D2A4A / #FFFFFF | #0D2A4A / #FFFFFF |
| status delivered | teal on a 14% teal tint | same |
| status refused | `refusal_text` #B3261E on a 14% tint | #FF8A80 |
| status none | muted on chip grey | muted |
| field | outlined, 6dp radius, floating label | same |
