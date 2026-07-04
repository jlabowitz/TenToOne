# Ten to One — Roadmap

Working backlog for the `overhaul` branch. This file is the source of truth for
task status and priority across sessions — read it before proposing what to do
next, don't re-derive the plan from scratch or from conversation memory alone.
Completed work lives in `DONE.md`, not here — see that file for implementation
history and commit references.

Each task, once started, goes through: implement (TDD where practical) → show
the user a running instance → explicit commit go-ahead → commit → explicit
push go-ahead → push.

**Review/QA cadence override for this project:** `senior-code-reviewer` and
`senior-qa-backend`/`senior-qa-frontend` are **opt-in by default** here, not
run automatically on every item. This is a personal, not-currently-shipping
game — the user's own "launch it and look" hands-on testing covers what
review/QA would otherwise catch, and a full review/QA pass on every small
item previously burned an entire session's budget on one roadmap entry. Skip
review/QA by default. Still route an item through review and/or QA on
judgment if it's genuinely risky (state/lifecycle changes, backend-touching
work) or if the user/orchestrator explicitly asks for a second opinion on
that item — this is "skip by default," not "never run."

## Status legend
`ready` — unblocked, not started · `blocked` — waiting on a dependency ·
`deferred` — real, but intentionally low priority. (`done` items have moved to
`DONE.md`.)

## At a glance

| # | Item | Status | Size | Primary owner |
|---|------|--------|------|----------------|
| 1 | AI & polish | ready — **user wants to scope this directly with `game-designer` before any implementation starts** | M, open-ended | `game-designer` → `senior-backend-developer` |
| 2 | Achievement system (persistent local storage) | ready — **higher priority**, design finalized 2026-07-04 | M | `game-designer` → `senior-developer` |
| 3 | Multi-monitor DPI rescale | deferred | unclear, likely M-L | `senior-backend-developer` |
| 4 | Full visual overhaul | deferred | XL, open-ended | agent TBD |
| 5 | `src/` restructuring | deferred — design/planning only | design-only | agent TBD |
| 6 | In-game legend for trick-state indicator symbols | ready | S | `game-designer` → `senior-frontend-developer` |
| 7 | Trump-card/hand `Handler` leak | deferred | S | `senior-backend-developer` |
| 8 | Legal-card min/max hand indicators | ready | S | `game-designer` → `senior-frontend-developer` |
| 9 | In-play round/card-count HUD + hamburger menu | ready | M | `game-designer` → `senior-frontend-developer` + `senior-backend-developer` |
| 10 | Replay/score history (persistent storage) | ready | S-M | `senior-backend-developer` + `senior-frontend-developer` |
| 11 | Difficulty tiers: freeplay vs. journey mode | blocked — depends on item 1 | M-L, open-ended | agent TBD |
| 12 | Distributable executable + GitHub Release | deferred | S-M | `senior-backend-developer` |
| 13 | Bapi visual/wording flourish | deferred | S | agent TBD |
| 14 | "OP"/"Cheater" guaranteed-best-cards easter egg | deferred | unclear | agent TBD |
| 15 | Achievement toast redesign (box notification, non-fading, click-to-highlight) | deferred | S-M | `game-designer` → `senior-developer` |

All live visual sanity checks previously owed here (invalid-move feedback,
start screen/rules/name entry) were walked by the user once back at their
computer 2026-07-03 — see `DONE.md` items 2-3 for confirmation and the one
bug found (Rules button not reachable during bet/card-play, fixed same day,
`DONE.md` item 5).

Also noted, not acted on: 3 lingering `java.exe` "Ten to One" processes were
observed running earlier in the session (2 from 2026-07-03, 1 from
2026-07-02 night) — almost certainly idle demo/verification windows sitting
at whatever screen they ended on. Not killed without checking with the
user; still worth a manual check/cleanup at some point if they're no longer
needed.

**Note for whoever picks up item 1:** the user explicitly does not want the
existing `AI_Easy`/`AI_Zombie` implementations modified — any smarter
betting/strategy work should take the form of new AI variants alongside
them, not edits to what's already there. `AI_Zombie` remains unused but
intentionally kept as a future "easy" difficulty tier.

## Big picture: eliminate the terminal

Stated goal, added 2026-07-02: the game should be fully playable with no
terminal visible at all — every prompt, status message, and outcome should
render in the window. The two console-input points that used to block this
(bet validation, bet entry) are already gone — see `DONE.md` items 4-5.
Round-transition/score/winner messages, the click-to-continue cue, and the
outcome banner shipped 2026-07-03 (`DONE.md`'s "In-window round &
game-flow UX" entry), as did invalid-move feedback, the start screen/
rules/name-entry item, and play-again restart the same day (`DONE.md` items
2-4) — but the old `System.out.println` calls behind all of these were
deliberately left in place (they now just duplicate on-screen content)
rather than removed opportunistically; revisit at some point as its own
small cleanup pass. **This backlog is now fully shipped** — every item that
motivated it (round transitions, invalid-move feedback, startup/rules/name
entry, and restart) is done; the only loose end is that old-console-output
cleanup, which nothing below is currently tracking as its own item.

## Queue

### 1. AI & polish — `ready` — M, open-ended — `game-designer` → `senior-backend-developer`
**User wants to scope this directly with `game-designer` before any
implementation starts** — hold here rather than delegating ahead on it.
User has specific ideas/direction to bring to that conversation and has
already indicated a strong preference: new AI variants alongside the
existing ones, not modifications to `AI_Easy`/`AI_Zombie` (`AI_Zombie` is
unused but functional — always plays first legal card — kept intentionally
as a future "easy" difficulty tier, still do not delete it). Other
candidates noted previously: opponent card-count display, play animations —
unconfirmed whether these are still wanted, raise them in the scoping
conversation rather than assuming.

**Additional scoping material from user suggestions (`SUGGESTIONS.md` idea
#6, added 2026-07-03)** — raise these in the same `game-designer`
conversation rather than treating them as separate items:
- Opponent-bet-aware betting: let an AI's own bet be influenced by bets
  already placed by opponents earlier in the same round (e.g. on a round of
  4 cards, if earlier bets are 1, 2, 0, an AI leaning toward betting 2-3
  might bet lower since so many opponents already expect to win a trick).
- Last-round betting heuristic fix: user observed an AI bet 1 on a high
  non-trump card in the final round, when the odds favor not betting unless
  going first or holding trump — that specific bad-bet case should be
  tuned/fixed.
- Card-counting RNG-tiered AIs: a new, separate set of AIs (explicitly not
  modifying `AI_Easy`/`AI_Zombie`) that pseudo-count high trump cards for
  near-perfect information (e.g. tracking whether the Ace/King of trump have
  appeared once you hold the Jack and the Queen is the visible trump card) —
  RNG would tier how good a given AI is at this counting.
- Named AI personalities: give each AI a name drawn from a list, each with
  an associated distinct play style.
- User also floated eventually making this ML-driven ("find the optimal
  strategy") as a stretch idea — raise it in the same conversation, not
  scoped further here.
- Item 11 below (difficulty tiers / journey mode) depends on whatever
  AI-tier structure comes out of this conversation — see that item.

### 2. Achievement system (persistent local storage) — `ready` — **higher priority** — M — `game-designer` → `senior-developer`
Added 2026-07-03 from user suggestions (`SUGGESTIONS.md` idea #2, explicitly
flagged by the user as "(Higher priority)"). Local persistent-storage-backed
achievement system: a high-score indicator, a win-streak counter, and
milestone achievements. **Design finalized 2026-07-04** after two
`game-designer` spec passes plus user review — ready to implement directly,
no further design round-trip needed.

**Storage:** a `java.util.Properties` file at
`<user.home>/.tentoone/save.properties` — deliberately not repo-relative, so
it survives regardless of launch directory (including a future `jpackage`
distributable, item 12). Written atomically (temp file + `ATOMIC_MOVE`)
since this project's dev workflow routinely force-kills the running
process; includes a `saveFormatVersion` key as a migration escape hatch.
Read once at startup; written after game-end, round-end (for round-level
score/bonus checks), and name-submission (for the Bapi check) rather than
deferred to process exit, since there's no clean-shutdown hook today.
Shares its `.tentoone` directory with item 10 (Replay/score history) via a
small reusable "resolve app-data dir" utility, without designing item 10's
own file format here.

**Forward-compatible with future multi-profile support:** the user has
floated (and added to `SUGGESTIONS.md`) wanting multiple named profiles
later, each with their own achievements. Not in scope now, but the
save-path resolution should be a parameterized function (e.g.
`resolveSaveFile(profileName)`, defaulting to one implicit profile) so that
adding real profile support later means pointing that function at a
different file per profile, not migrating the save format or rewriting the
storage layer.

**Milestone achievements (10 total, non-hidden — plus the hidden Bapi
achievement below, 11 including it):** `FIRST_VICTORY` (win your
first game), `TEN_GAMES_PLAYED` (play 10 games total), `WIN_STREAK_3`/`_5`/
`_10` (win-streak tiers), `SCORE_OVER_50`/`_100` (single-game score
thresholds — a third `SCORE_OVER_150` tier was scoped and then dropped by
the user: max theoretical score is 155, requiring winning literally every
trick in the game, which is practically unreachable without exceptional
hand RNG), `PERFECT_ROUND` (hit the bet bonus once) and its harder
counterpart `FLAWLESS_GAME` (hit it in all 10 rounds), and `COMEBACK_KID`
(sole last place at the round-5 halfway point, then win the game).

**Bapi easter egg (in scope, fully specified):** entering the player name
"Bapi" (case-insensitive, exact match only — not a substring match) unlocks
a hidden achievement, `BAPI_EASTER_EGG` ("One and Only"). This is a pet name
for the user's partner. Checked at name-submission time
(`Game.runStartScreen()`'s `case START:` branch), same permanent-unlock
semantics as every other achievement. **Explicitly deferred, not this
pass:** any visual/wording flourish tied to that name (background tint,
special phrasing) — see new item 13 below.

**Stats screen:** yes — a small always-visible stat line on the Start
Screen (best score/streak/games played) plus a new "Achievements" button
opening a full list (locked/unlocked; hidden achievements omitted until
unlocked), reusing the existing `RulesView.showBlocking` modal pattern.
Mid-session unlocks show as a toast reusing the `IllegalPlayFeedback`
hold/fade pattern, queued rather than overwrite-in-place (two achievements
can unlock in the same instant).

Note: shares a "needs local persistent storage" dependency with item 10
(Replay/score history) below — the storage mechanism above (format, file
location, read/write timing) is designed to serve both rather than building
two separate ad hoc persistence layers.

### 3. Multi-monitor DPI rescale — `deferred` — size unclear (likely M-L) — `senior-backend-developer`
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

### 4. Full visual overhaul — `deferred` — XL, open-ended — agent TBD
Added 2026-07-02 per user request: "much later down the line," a full
visual/art overhaul of the game beyond the functional UI fixes above.
Intentionally deferred — revisit once the functional/UX backlog (item 1) is
in a good place; scoping it now would be premature.

### 5. `src/` restructuring — `deferred` — design/planning only, not to be done now — agent TBD
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

### 6. In-game legend for trick-state indicator symbols — `ready` — S — `game-designer` → `senior-frontend-developer`
Flagged as a gap during the trick-state indicators' design spec (2026-07-02,
shipped — see `DONE.md`): none of the three indicators (trick-leader dot,
led-suit HUD line, high-card ring) explain themselves to a new player on
first sight. Was blocked on the rules/instructions view existing as a place
to put it — unblocked now that `RulesView` has shipped with a reserved,
measured-but-empty legend slot waiting for exactly this
(`RulesView.LEGEND_TOP`/`LEGEND_BOTTOM`/`CONTENT_LEFT`/`CONTENT_RIGHT`, see
`DONE.md`'s start-screen entry) — implementer should draw into that slot,
not invent new bounds.

### 7. Trump-card/hand `Handler` leak — `deferred` — S — `senior-backend-developer`
Discovered during `senior-code-reviewer`'s pass on the Play-again item (this
session): `Round.renderTrumpCard()` and `Round.renderPlayerHand()` add a
trump `Card`/`Hand` object to the `Handler` every round, but nothing anywhere
ever calls `handler.removeObject()` on either — a pre-existing gap in
`Round.java`, not introduced by Play-again. Previously bounded: a process
only ever played one game before Play-again existed, capping accumulation at
10 trump cards + duplicate same-reference hand entries per process run.
Play-again's `Player.resetForNewGame()` nulling the human's hand (the
correct choice, so each new game gets a genuinely fresh deal) removed that
incidental cap — a long session of repeated "Play Again" clicks now
accumulates these objects with no upper bound.

Practically negligible, not urgent: no visible symptom (trump cards always
render at a fixed pixel position, so older leaked ones are simply painted
over every frame, never seen) and only cheap objects leak (no image data —
that's a separate process-wide static cache, not per-`Card`). Would take on
the order of hundreds-to-thousands of restarts in a single sitting to matter
at all.

Fix, when picked up: track and remove the previous round's trump card in
`Round.renderTrumpCard()` before adding the new one; stop unconditionally
re-adding the same `Hand` reference to the `Handler` every round in
`Round.renderPlayerHand()` (only add once per game, not once per round).

### 8. Legal-card min/max hand indicators — `ready` — S — `game-designer` → `senior-frontend-developer`
Added 2026-07-03 from user suggestions (`SUGGESTIONS.md` idea #1). A red
circle indicator on/near the lowest-valued legal card and a green circle
indicator on/near the highest-valued legal card in the human's hand, similar
in spirit to the existing trick-leader/high-card indicators (see `DONE.md`'s
trick-state indicators entry) — reuse `Player.legalCards()` to determine
which cards qualify each trick. Likely benefits from the same
design-spec-then-implement approach the trick-state indicators used, given
that item's history of pixel-collision bugs the spec's arithmetic didn't
catch on the first pass.

### 9. In-play round/card-count HUD + hamburger menu — `ready` — M — `game-designer` → `senior-frontend-developer` + `senior-backend-developer`
Added 2026-07-03 from user suggestions (`SUGGESTIONS.md` idea #3). Two
related pieces, both about in-play chrome:
- A small always-visible status readout during play (not just between
  rounds) showing the current round number and how many cards are in the
  round — user suggested a small box in the top-right as one option.
- A hamburger-menu icon (top-left, per user's suggestion) exposing: Rules
  (already has a `RulesView` and hotspot pattern to reuse, see `DONE.md`),
  Restart (must show a confirmation popup requiring a second/explicit click
  before actually restarting — don't restart on the first click), "back to
  main menu" (explicitly **not** the same as restart — the user wants the
  in-progress game state kept in memory so returning to the menu doesn't
  lose it, i.e. some kind of suspend/resume rather than a hard reset), and
  Pause.

Design questions to resolve during the `game-designer` pass rather than
assumed: exact hamburger-menu visual treatment, what "pause" freezes (AI
turn timers? animations? both?), and the state-retention mechanism for
"back to main menu without restarting" (this is new territory — nothing
today suspends a game and returns to the start screen without discarding
state).

### 10. Replay/score history (persistent storage) — `ready` — S-M — `senior-backend-developer` + `senior-frontend-developer`
Added 2026-07-03 from user suggestions (`SUGGESTIONS.md` idea #4). A history
of past game scores the user can look back at, stored in local persistent
storage (same "no persistence exists yet" gap as item 2 — see that item's
note about sharing a single storage mechanism rather than building two).

### 11. Difficulty tiers: freeplay vs. journey mode — `blocked` — M-L, open-ended — agent TBD (downstream of item 1)
Added 2026-07-03 from user suggestions (`SUGGESTIONS.md` idea #5). Two
proposed modes: a **freeplay mode** where the user can directly pick an AI
difficulty to play against, and a **journey mode** where harder tiers unlock
progressively (e.g. beat `AI_Zombie` 5 times to unlock "easy," beat "easy" 5
times to unlock "hard," etc.).

**Blocked on item 1**: this only makes sense once there are multiple
distinct, ordered AI difficulty tiers to select/unlock — today there's just
`AI_Easy` and unused `AI_Zombie`. Fold this into the same `game-designer`
conversation scoping item 1's AI strategy work rather than scoping it
independently; it's a UI/mode-selection layer on top of whatever tier
structure that conversation produces, not a separable feature.

### 12. Distributable executable + GitHub Release — `deferred` — S-M — `senior-backend-developer`
Added 2026-07-04 per user request, to make the game shareable with
non-developer players (no git/JDK required on their end). Proposed approach,
not yet scoped in detail: use `jpackage` (bundled with JDK 21) to produce a
self-contained Windows app-image or installer with a private Java runtime
embedded, then attach it as a binary asset on a tagged GitHub Release. A full
`.exe`/`.msi` installer needs the WiX Toolset as a build-time dependency; a
plain app-image skips that but ships as a folder to unzip rather than a
single installer file — that tradeoff is unresolved. **Deferred**: user
doesn't need this now.

### 13. Bapi visual/wording flourish — `deferred` — S — agent TBD
Spun off from item 2's Bapi easter-egg achievement (2026-07-04): once the
"Bapi" achievement itself is implemented, there's an open, explicitly
deferred question about whether entering that name should also change
something visually/textually elsewhere in the game — the user's own
brainstorm, not yet decided. Two rough options floated during item 2's
design pass: a subtle warm-palette tint (session-only, contained to a
specific UI element rather than a full reskin) or a single extra personal
line attached to the achievement's own unlock toast. User explicitly said
they don't know what they want yet — revisit once item 2 ships and they've
seen the achievement itself in action.

### 14. "OP"/"Cheater" guaranteed-best-cards easter egg — `deferred` — size unclear — agent TBD
Raised by the user 2026-07-04 while finalizing item 2's design: entering a
name like "OP," "Super OP," or "Cheater" (exact wording undecided — user
floated these as brainstorm examples, not final) would put the human player
in a "god mode" where they're always dealt the best cards each round.
Distinct from the Bapi achievement (item 2) — this would need real
deal-logic changes (guaranteed best cards), not just a name-triggered
achievement unlock, so it's a heavier lift than a cosmetic easter egg. User
explicitly said this isn't necessary right now — logged for later rather
than scoped in detail.

### 15. Achievement toast redesign (box notification, non-fading, click-to-highlight) — `deferred` — S-M — `game-designer` → `senior-developer`
Added 2026-07-04, after the user played the just-shipped mid-session
achievement toast (item 2) and its three follow-up fixes (longer hold,
click-to-dismiss, in-game Achievements button) and decided the whole
presentation is the wrong shape, not just under-tuned. **This replaces the
current centered-fading-banner toast entirely** — not another tuning pass.
Concrete replacement spec, from the user's own sketch:
- A bordered box in the **top-left corner** (not centered/top-banner).
- **No fade-out at any point** — stays fully solid/visible until the user
  dismisses it (the just-shipped auto-fade and click-to-dismiss behavior
  from this session's follow-up work is superseded here).
- **Continuously animates in a loop** (gold color, pulsing/looping) the
  entire time it's displayed, rather than a static hold.
- **Clicking it opens `AchievementsView` directly**, with the just-unlocked
  achievement visually highlighted in an intuitive way (exact highlight
  treatment — border, background tint, etc. — left to whoever designs
  this).
- Needs a dismiss path for a user who doesn't want to open Achievements
  (a close control, or a distinct "click elsewhere on the box" affordance)
  — worth a `game-designer` pass to nail exact interaction details (e.g.
  dismiss-without-opening vs. click-always-opens-Achievements, the specific
  loop animation) rather than assuming.

The underlying FIFO-queue/`Handler.keepOnTop()` mid-game-visibility plumbing
built for the current toast (item 2 and its follow-ups) is likely still
reusable — this is a presentation-layer redesign, not a new architecture.

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
- Queue renumbered again 2026-07-03, after the round/game-flow UX item
  (former #1: round-transition/score summary, click-to-continue, end-of-game
  banner) shipped and moved to `DONE.md` — old items 2-9 became new items
  1-8 (old #2 → new #1, ..., old #9 → new #8; old #1 removed), same pattern
  as prior renumberings. This also unblocked the "play again" item (old #4,
  new #3), which was waiting on the outcome banner existing to attach a
  restart control to — it's now `ready`. Same session, this file also
  gained the "Review/QA cadence override" note above (a project-manager.md
  protocol change to make review/QA opt-in-by-default for this repo
  self-discoverable, rather than the orchestrator having to restate it each
  delegation).
- Queue renumbered again 2026-07-03 (same day), after invalid-move feedback
  shipped and moved to `DONE.md` (that item's own entry there flags a
  pending live-visual sanity check, screen was locked at implementation
  time) — old items 2-8 became new items 1-7 (old #2 → new #1, ..., old #8
  → new #7; old #1 removed), same pattern as prior renumberings. This is
  the point where the "eliminate the terminal" backlog is down to just the
  start screen and play-again items.
- Queue renumbered again 2026-07-03 (same day), after the start screen/
  rules view/name entry item shipped and moved to `DONE.md` — old items 2-7
  became new items 1-6 (old #2 → new #1, ..., old #7 → new #6; old #1
  removed), same pattern as prior renumberings. This also unblocked the
  in-game legend item (old #7, new #6), which was waiting on the rules view
  existing — it's now `ready`, and shipped with a reserved, pixel-measured
  empty slot for the legend to draw into. The "eliminate the terminal"
  backlog is now down to just the play-again item.
- Queue renumbered again 2026-07-03 (same day), after play-again restart
  shipped and moved to `DONE.md` — old items 2-6 became new items 1-5
  (old #2 → new #1, ..., old #6 → new #5; old #1 removed), same pattern as
  prior renumberings. This is the point where the entire "eliminate the
  terminal" backlog (this file's original organizing theme) is fully
  shipped. New item 6 (trump-card/hand `Handler` leak) was added the same
  session, found by `senior-code-reviewer` during play-again's review pass.
  A user-reported gap found during hands-on testing (Rules button not
  reachable during bet/card-play) was fixed the same session as a quick
  aside rather than tracked as its own queue item — see `DONE.md` item 5.
  Per explicit user instruction, the queue now holds at item 1 (AI &
  polish) rather than delegating ahead on it — user wants to scope that one
  directly with `game-designer` themselves first.
- Queue renumbered again 2026-07-03 (same day), to fold in 6 new
  suggestions triaged from `SUGGESTIONS.md` (removed from that file once
  folded in — see that file's own history if needed): old items 2-6 became
  new items 3-7 (old #2 → new #3, ..., old #6 → new #7; item 1 untouched).
  New item 2 (Achievement system) was inserted right after item 1 per the
  user's explicit "(Higher priority)" flag on that suggestion. New items
  8-11 (legal-card min/max indicators, in-play HUD + hamburger menu,
  replay/score history, difficulty tiers/journey mode) were appended after
  the pre-existing queue rather than interleaved, since none carried an
  explicit priority signal. Two of the user's suggestions were *not* given
  their own items: AI-strategy ideas (opponent-bet-aware betting, a
  last-round betting heuristic fix, card-counting RNG-tiered AIs, named AI
  personalities) were folded into item 1's existing writeup as additional
  scoping material for its already-planned `game-designer` conversation,
  and item 11 was marked `blocked` on item 1 rather than independent, since
  it depends on multiple AI difficulty tiers existing first.
- 2026-07-04: item 2's design finalized after two independent `game-designer`
  spec passes plus user review (see this file's item 2 writeup for the
  locked-in storage/achievement-list/Bapi/profile-extensibility details).
  Two new deferred items were appended without renumbering the rest of the
  queue: item 13 (Bapi visual/wording flourish, split out of item 2's scope)
  and item 14 (an "OP"/"Cheater" guaranteed-best-cards easter egg, a
  separate idea the user floated during the same conversation). Item 2's
  primary-owner column was also corrected from a frontend/backend split to
  `senior-developer`, matching this project's stated generalist-role
  preference in `CLAUDE.md` (this repo has no real frontend/backend seam).

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
