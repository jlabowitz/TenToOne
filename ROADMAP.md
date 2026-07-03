# Ten to One — Roadmap

Working backlog for the `overhaul` branch. This file is the source of truth for
task status and priority across sessions — read it before proposing what to do
next, don't re-derive the plan from scratch or from conversation memory alone.
Completed work lives in `DONE.md`, not here — see that file for implementation
history and commit references.

Each task, once started, goes through the full cycle: implement (TDD where
practical) → senior-code-reviewer → senior-qa-backend/frontend → show the user
a running instance → explicit commit go-ahead → commit → explicit push
go-ahead → push.

## Status legend
`ready` — unblocked, not started · `blocked` — waiting on a dependency ·
`deferred` — real, but intentionally low priority. (`done` items have moved to
`DONE.md`.)

## At a glance

| # | Item | Status | Size | Primary owner |
|---|------|--------|------|----------------|
| 1 | In-window round & game-flow UX (transitions, click-to-continue, outcome banner) | ready | M/L | `game-designer` spec → `senior-frontend-developer` |
| 2 | Invalid-move on-screen feedback | ready | S/M | `game-designer` (treatment) → `senior-frontend-developer` |
| 3 | Start screen + rules/options menu + player name input | ready | M/L | `game-designer` spec → `senior-frontend-developer` + `senior-backend-developer` |
| 4 | Play again (in-window restart) | blocked (needs 1) | S/M | `senior-frontend-developer` + `senior-backend-developer` |
| 5 | AI & polish | blocked (comes after the above) | M, open-ended | `game-designer` → `senior-backend-developer` |
| 6 | Multi-monitor DPI rescale | deferred | unclear, likely M-L | `senior-backend-developer` |
| 7 | Full visual overhaul | deferred | XL, open-ended | agent TBD |
| 8 | `src/` restructuring | deferred — design/planning only | design-only | agent TBD |
| 9 | In-game legend for trick-state indicator symbols | blocked (needs 3) | S | `game-designer` → `senior-frontend-developer` |

## Big picture: eliminate the terminal

Stated goal, added 2026-07-02: the game should be fully playable with no
terminal visible at all — every prompt, status message, and outcome should
render in the window. The two console-input points that used to block this
(bet validation, bet entry) are already gone — see `DONE.md` items 4-5.
Remaining terminal touchpoints, each tracked below: round-transition/score/
winner messages, the click-to-continue cue, and the outcome banner (all
folded into item 1); invalid-move feedback (item 2); startup/rules text and
name entry (item 3); and restarting without relaunching the process (item 4).
Treat this as the throughline when scoping any of those items, not just a
description of each in isolation.

## Queue

### 1. In-window round & game-flow UX — `ready` — M/L — `game-designer` spec → `senior-frontend-developer`
Merged 2026-07-02 from three previously separate queue items — round-
transition/score/winner UX, the click-to-continue affordance, and the
end-of-game outcome screen — because all three render at the same points in
the game loop (trick resolves → round ends → game ends) and were already
flagged as overlapping. One `game-designer` spec pass covers all three
sub-parts together, then one implementation pass, rather than touching the
same transition code three separate times:

- (a) **Round-transition & score summary** — scores, bets-vs-tricks-taken,
  and round transitions currently print to console only; nothing appears in
  the window.
- (b) **Click-to-continue affordance** — after a trick resolves, nothing on
  screen indicates a click is expected to advance to the next trick/round —
  today that's discoverable only by trial and error. Add a visible prompt at
  the point the game is already blocking on a human click.
- (c) **End-of-game outcome banner** — when the game ends there's no
  on-screen win/loss indication today (console only, not visible during
  normal play).

Note: "play again" (in-window restart) is deliberately **not** included here
— see item 4.

### 2. Invalid-move on-screen feedback — `ready` — S/M — `game-designer` (treatment) → `senior-frontend-developer`
When the player attempts an illegal move (e.g. a card that doesn't follow
suit), there's no on-screen indication the move was rejected. User suggested
a fading red message but is open to whatever `game-designer` recommends
(border flash, shake, etc.) — needs a design call on exact treatment before
implementation.

Independent of item 1 (different trigger point — an illegal move attempt,
not trick/round resolution) but same "eliminate the terminal" theme;
sequenced after item 1 since it's the smaller, more isolated fix of the two.

### 3. Start screen + rules/options menu + player name input — `ready` — M/L — `game-designer` spec → `senior-frontend-developer` + `senior-backend-developer`
No start screen exists today — the game launches straight into play with
the human hardcoded as "Jacob." Add a start screen with a rules/instructions
view (also reachable mid-game, not just pre-launch) and a name-entry field,
likely the seed of a fuller options menu later. Large and open-ended enough
that `game-designer` should scope an MVP (start screen + rules + name entry)
versus what gets deferred to a later dedicated options-menu pass. Name entry
touches `Player`/`Game` setup, hence the `senior-backend-developer`
co-assignment alongside the frontend screen work.

Sequenced after the higher-frequency in-game fixes (items 1-2) since a start
screen is encountered once per session, not once per trick.

### 4. Play again (in-window restart) — `blocked` (needs 1) — S/M — `senior-frontend-developer` + `senior-backend-developer`
Split out of the old end-of-game item 2026-07-02: showing the outcome (item
1c) and restarting the game are different capabilities. This one needs
actual game-lifecycle/restart logic (re-initializing `Game`/`Round` state
without relaunching the process), not just a rendering addition. Blocked on
item 1 landing first, since "play again" needs an outcome screen to attach
its button to.

### 5. AI & polish — `blocked` (comes after the above) — M, open-ended — `game-designer` → `senior-backend-developer`
`AI_Zombie` is unused but functional (always plays first legal card) — a
natural "easy" difficulty tier if a difficulty picker lands; do not delete
it. Candidates: smarter betting/strategy, opponent card-count display
(overlaps item 1), play animations.

### 6. Multi-monitor DPI rescale — `deferred` — size unclear (likely M-L) — `senior-backend-developer`
Discovered 2026-07-02: dragging the game window from the user's primary
monitor to a secondary monitor with a different Windows display-scale
factor causes blurry/stretched rendering. Root cause: the game renders via a
raw `Canvas` + `BufferStrategy` (`Game.java`), a lower-level pipeline than
standard Swing painting, which is known to not gracefully handle
`WM_DPICHANGED`/per-monitor scale changes — the pixel buffer stays sized for
whichever monitor's DPI was active at creation, and Windows bitmap-stretches
it to fit the new monitor instead of the app redrawing natively. This is
distinct from the old canvas-sizing fix (`DONE.md` item 3, a static
single-monitor insets/cropping bug) and would likely need explicit
DPI-change handling or migrating off raw `Canvas`/`BufferStrategy` toward
Swing's more DPI-aware repaint pipeline.

**Deferred**: doesn't affect the user's normal single-monitor workflow.
Revisit if that changes.

### 7. Full visual overhaul — `deferred` — XL, open-ended — agent TBD
Added 2026-07-02 per user request: "much later down the line," a full
visual/art overhaul of the game beyond the functional UI fixes above.
Intentionally deferred — revisit once the functional/UX backlog (items 1-5)
is in a good place; scoping it now would be premature.

### 8. `src/` restructuring — `deferred` — design/planning only, not to be done now — agent TBD
Added 2026-07-02 per user request, **explicitly planning-only — do not
implement yet**. The current `src/` layout is flat: all production and test
`.java` files live directly under `src/` with no subfolders, a structure the
user describes as "legacy... not sure it was best practice." Worth
reconsidering: separating production classes from test classes
(`TestGame.java`, `TestHand.java`, etc.), and possibly grouping related
production classes into subpackages (e.g. rendering/game-object classes vs.
game-logic classes) rather than everything flat under `src/`.

This is a real migration, not a free reorg, given the project's no-build-tool
setup (see `CLAUDE.md`):
- Compilation is a flat glob (`javac ... src/*.java`) with no recursion —
  any subfolder split requires changing this to a recursive form (an
  explicit file list, a `find`-generated sources list, or adopting a build
  tool, which is a bigger decision of its own).
- Java requires folder structure to match `package` declarations once files
  aren't flat — every moved file needs a `package` statement added, and
  every reference to that class elsewhere needs an `import`. Currently no
  file has a package declaration (everything is in the unnamed/default
  package).
- Tests currently run via bare class names (`JUnitCore TestGame TestHand`)
  with no package prefix; once test classes have packages, those invocations
  need fully-qualified names instead.
- `CLAUDE.md`'s documented Build/Test/Run commands would all need updating
  to match, so this touches project documentation as well as source layout.

Whoever picks this item up should produce a proposed subfolder structure and
a concrete list of what changes (build command, test invocation, CLAUDE.md,
every file's package/import) before touching any files — not attempt the
migration inline with unrelated work.

### 9. In-game legend for trick-state indicator symbols — `blocked` (needs 3) — S — `game-designer` → `senior-frontend-developer`
Flagged as a gap during the trick-state indicators' design spec (2026-07-02,
shipped — see `DONE.md`): none of the three indicators (trick-leader dot,
led-suit HUD line, high-card ring) explain themselves to a new player on
first sight. Natural home is the rules/instructions view being built in
item 3, so this is blocked on item 3 (needs the rules view to exist as a
place to put the legend).

---

## Notes

- Thread model fix (former queue #1) shipped and moved to `DONE.md` on
  2026-07-02 (commit `fae32e5`), unblocking former item 3. Same day, the
  trick-leader item grew in scope per user feedback — led-suit and
  high-card-in-trick indicators folded in alongside the leader symbol — and
  the queue was renumbered accordingly (old #2 → new #1, old #3 → new #2,
  etc.; old #1 removed).
- Completed items (former queue #1-5) moved to `DONE.md` on 2026-07-02 to
  keep this file focused on active/upcoming priority. See `DONE.md` for full
  implementation history and commit references.
- This file was fully reorganized and renumbered on 2026-07-02: three former
  items were merged (round-transition/score/winner UX + click-to-continue +
  end-of-game outcome → new item 3), one was split out (old end-of-game
  item's "play again" scope → new item 6), and the whole not-done queue was
  reprioritized around the "eliminate the terminal" theme. Old item numbers
  from before this reorg are not preserved here — consult git history or
  `DONE.md` if you need the prior numbering.
- Queue renumbered again 2026-07-02 (same day), after the trick-state
  indicators item shipped (commit `c23ff0f`) and was moved to `DONE.md`:
  old items 2-10 became new items 1-9 (old #2 → new #1, old #3 → new #2, ...,
  old #10 → new #9; old #1 removed), same pattern as the renumbering above.
  The new item 9 (in-game legend) was added just before this renumbering, per
  user request, after `game-designer`'s spec pass on the trick-state
  indicators flagged the gap; it's blocked on item 3 (start screen/rules
  view existing as a place to put the legend).

## Process notes

QA's screenshot checks (via `java.awt.Robot`) used to only verify "no
exceptions in the log," not actual visual/pixel quality. This is now
routinely fixed in practice: subagents with a `Read` tool can (and during an
earlier item's QA, did) read a captured screenshot PNG and visually describe
it themselves — vision isn't unique to the main conversation, any Claude
instance with `Read` on an image has it. Use that for any UI-touching change
rather than trusting "no exceptions" alone.

One earlier claim in this file was likely wrong and worth correcting: on
2026-07-02 an attempt to self-capture a screenshot via `Robot` appeared to
show an empty desktop with no game window, leading to a conclusion that "the
automation session can't see the user's actual desktop." The user's own
multi-monitor setup (confirmed same day, when they reported the game
stretching when dragged to a second monitor) makes a much simpler
explanation likely: that capture used
`GraphicsEnvironment.getDefaultScreenDevice()`, which only grabs the
*primary* monitor's bounds — if the window was on/near a different monitor
at that moment, the capture would legitimately miss it without proving
session isolation at all. Windows launched by tool calls in this
conversation were consistently visible to and interactive with the user
throughout the rest of the session. Before assuming screenshots can't work,
try capturing all screens (iterate
`GraphicsEnvironment.getScreenDevices()`), not just the default one.
