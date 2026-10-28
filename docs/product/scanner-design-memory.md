# Design memory: zkagent Scanner

## Brand tone
- Plain, calm and utilitarian, with Android Material conventions.
- Avoid: decorative chrome, tab strips, duplicate headings, and technical text in user-facing popups.

## Layout and spacing
- Portrait only, on an 8dp grid with 16dp gutters.
- There are three swipeable pages (Scan, Log, Settings) with no tab strip. The centred title has prev/next arrows, and dots sit at the bottom.
- Diagnostics lives collapsed at the bottom of Settings.

## Typography
- Roboto. Body text is 14–16sp, and the primary action label is 18sp.

## Color
- Primary is teal #009688 (night #26a69a).
- The site's question is a dark navy box (#0D2A4A) with white text.
- Red (`refusal_text` #B3261E, night #FF8A80) means "it didn't work", and is used for every non-success popup (D113).
- Success popups use the normal text colour.
- Status strips are teal ✓ for delivered, red ✗ for refused, and grey – for none yet. Colour is never the only signal.

## Interaction patterns
- Forms: the fields are always visible, outlined with floating labels, and empty for each scan. Document details are never saved.
- There is one large primary action (VERIFY). The hint line sits above it and the last-scan status directly below it.
- Modals: blocking outcome dialogs are for results the user must acknowledge. Use a Snackbar for transient facts.

## Accessibility rules
- Touch targets are at least 48dp, and contrast is at least 4.5:1 day and night.
- Status lines are live regions, and icons accompany colour.

## Repo conventions
- Keep logic in pure classes (`PaneState`, `SessionDisplay`, `TierCText`), with the Activity thin, because JVM unit tests stub Android views.
- Owner-worded strings are literal constants, and tests assert the literals independently.
