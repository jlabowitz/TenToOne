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
| 1 | AI & polish | ready — **user wants to scope this directly with `game-designer` before any implementation starts** | M, open-ended | `game-designer` → `senior-developer` |
| 2 | Achievement toast redesign (box notification, non-fading, click-to-highlight) | ready | S-M | `game-designer` → `senior-developer` |
| 3 | Dev mode: jump-to-round + extensible dev settings | ready | S-M | `game-designer` → `senior-developer` |
| 4 | Accessibility: Enter-to-submit name, Tab-focus to Start Game | ready | S | `senior-developer` |
| 5 | In-game legend for trick-state indicator symbols | ready | S | `game-designer` → `senior-developer` |
| 6 | Legal-card min/max hand indicators | ready | S | `game-designer` → `senior-developer` |
| 7 | Round summary panel enhancements (bonus-count column, rank column, phrasing/bold) | ready | S-M | `game-designer` → `senior-developer` |
| 8 | In-play round/card-count HUD + hamburger menu | ready | M | `game-designer` → `senior-developer` |
| 9 | Replay/score history (persistent storage) | ready | S-M | `senior-developer` |
| 10 | Difficulty tiers: freeplay vs. journey mode | blocked — depends on item 1 | M-L, open-ended | agent TBD |
| 11 | Trump-card/hand `Handler` leak | deferred | S | `senior-developer` |
| 12 | Multi-monitor DPI rescale | deferred | unclear, likely M-L | `senior-developer` |
| 13 | Distributable executable + GitHub Release | deferred | S-M | `senior-developer` |
| 14 | Multi-profile support | deferred | unclear, likely M | agent TBD |
| 15 | Bapi visual/wording flourish | deferred | S | agent TBD |
| 16 | "OP"/"Cheater" guaranteed-best-cards easter egg | deferred | unclear | agent TBD |
| 17 | Full visual overhaul | deferred | XL, open-ended | agent TBD |
| 18 | `src/` restructuring | deferred — design/planning only | design-only | agent TBD |
| 19 | Full multiplayer web app (accounts, single/multiplayer) | deferred — needs its own dedicated scoping conversation before any work starts | XL, open-ended | agent TBD |

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

### 1. AI & polish — `ready` — M, open-ended — `game-designer` → `senior-developer`
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
- **Betting-legality rule (added 2026-07-04, from `SUGGESTIONS.md`):** a
  genuine rule gap, not covered by the original design — the total of all
  bets placed in a round can currently equal the round's card count, but
  real trick-taking-game rules require at least one player to miss their
  bet, so the sum must never exactly equal the card count. Needs to become
  a togglable setting (default **on**), implying a settings-menu surface
  that doesn't exist yet. Affects every bettor, human and AI: needs an
  `isLegalBet`-shaped check, and every existing AI's bet-rounding logic
  likely needs adjustment to avoid landing on now-illegal totals (user
  suggested rounding differently, and manually deciding a round-up/
  round-down tie-break when the boundary bet is the AI's only sensible
  choice) — the user is fine with this specific rounding-behavior change
  touching `AI_Easy`/`AI_Zombie`, distinct from the standing "don't modify
  existing AI" rule, which is about *strategy*, not compliance with a
  corrected core rule. A smarter AI could reason about legality using real
  hand knowledge (e.g. holding the trump Ace makes betting 0 illogical even
  with a weak rest-of-hand) — raise that alongside the other AI-strategy
  ideas above in the same conversation.
- Item 10 below (difficulty tiers / journey mode) depends on whatever
  AI-tier structure comes out of this conversation — see that item.

### 2. Achievement toast redesign (box notification, non-fading, click-to-highlight) — `ready` — S-M — `game-designer` → `senior-developer`
Added 2026-07-04, after the user played the achievement toast shipped in
`DONE.md` item 13 and its three follow-up fixes (longer hold,
click-to-dismiss, in-game Achievements button) and decided the whole
presentation is the wrong shape, not just under-tuned. **This replaces the
current centered-fading-banner toast entirely** — not another tuning pass.
Concrete replacement spec, from the user's own sketch:
- A bordered box in the **top-left corner** (not centered/top-banner).
- **No fade-out at any point** — stays fully solid/visible until the user
  dismisses it (the just-shipped auto-fade and click-to-dismiss behavior
  from `DONE.md` item 13 is superseded here).
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
built for the current toast (`DONE.md` item 13) is likely still reusable —
this is a presentation-layer redesign, not a new architecture.

**Promoted from `deferred` to `ready`** during 2026-07-04's roadmap
reprioritization pass — this was already fully spec'd with nothing blocking
it, and it directly addresses the user's own stated dissatisfaction with
what just shipped, so leaving it deferred served no purpose.

### 3. Dev mode: jump-to-round + extensible dev settings — `ready` — S-M — `game-designer` → `senior-developer`
Added 2026-07-04 from user suggestions (`SUGGESTIONS.md`). A developer-only
mode, accessible for now (not necessarily gated later), that lets the user
jump straight to a specific round instead of playing through from round 1 —
mainly to speed up manual testing/playtesting of later-round behavior
(outcome banner, high round numbers, endgame achievements, etc.), which
today requires playing a full 10-round game every time. User expects more
settings to accumulate here over time, so the entry point/UI should be built
as an extensible small settings surface, not a single-purpose round-jump
hack.

Design questions for the `game-designer` pass: how it's triggered (a hidden
hotkey, a command-line flag/system property, a debug menu reachable from the
Start Screen), whether jumping to round N needs to fabricate plausible
bet/score state for the skipped rounds or can start clean at round N with
zero prior history, and how future dev settings get added to whatever
surface this creates without redesigning it each time.

### 4. Accessibility: Enter-to-submit name, Tab-focus to Start Game — `ready` — S — `senior-developer`
Added 2026-07-04 from user suggestions (`SUGGESTIONS.md`). Two small
keyboard-accessibility gaps on the Start Screen: hitting Enter after typing
a name should submit/start the game (same effect as clicking Start Game),
and Tab should be able to move focus to the Start Game button so a mouse
isn't required to proceed. This was already flagged as a deferred
future-pass item when the Start Screen originally shipped (see `DONE.md`
item 10's "deliberately deferred to a future options-menu pass" note on
Enter-to-submit) — this is that pass. No design ambiguity here, routed
straight to `senior-developer` rather than through `game-designer`.

### 5. In-game legend for trick-state indicator symbols — `ready` — S — `game-designer` → `senior-developer`
Flagged as a gap during the trick-state indicators' design spec (2026-07-02,
shipped — see `DONE.md`): none of the three indicators (trick-leader dot,
led-suit HUD line, high-card ring) explain themselves to a new player on
first sight. Was blocked on the rules/instructions view existing as a place
to put it — unblocked now that `RulesView` has shipped with a reserved,
measured-but-empty legend slot waiting for exactly this
(`RulesView.LEGEND_TOP`/`LEGEND_BOTTOM`/`CONTENT_LEFT`/`CONTENT_RIGHT`, see
`DONE.md`'s start-screen entry) — implementer should draw into that slot,
not invent new bounds.

### 6. Legal-card min/max hand indicators — `ready` — S — `game-designer` → `senior-developer`
Added 2026-07-03 from user suggestions (`SUGGESTIONS.md` idea #1). A red
circle indicator on/near the lowest-valued legal card and a green circle
indicator on/near the highest-valued legal card in the human's hand, similar
in spirit to the existing trick-leader/high-card indicators (see `DONE.md`'s
trick-state indicators entry) — reuse `Player.legalCards()` to determine
which cards qualify each trick. Likely benefits from the same
design-spec-then-implement approach the trick-state indicators used, given
that item's history of pixel-collision bugs the spec's arithmetic didn't
catch on the first pass.

### 7. Round summary panel enhancements — `ready` — S-M — `game-designer` → `senior-developer`
Added 2026-07-04 from user suggestions (`SUGGESTIONS.md`), three related
tweaks to `RoundSummaryPanel`/`GameOverBanner` (see `DONE.md` item 8 for
that panel's original design):
- A new column showing each player's running count of bet-bonus hits so far
  in the game (how many rounds they've bet exactly right), placed to the
  left of the existing score column.
- Rename the "Round Delta" column to clearer phrasing (user suggested
  "Round Score" as one option, not finalized) and visually emphasize each
  player's own score (bold or similar) so it stands out from opponents'.
- Add a rank column to the left of the existing columns; user has floated
  (as a stretch, not required for this pass) eventually replacing plain
  rank numbers with stylized 1st/2nd/3rd-place imagery or gold/silver/
  bronze medals, possibly reserved for the final game-over screen only
  rather than every round summary.

Given `RoundSummaryPanel`'s history of pixel-collision/layout bugs on prior
passes (see `DONE.md` items 7, 8, 11), this is worth a real `game-designer`
spec pass on exact column layout rather than eyeballing it.

### 8. In-play round/card-count HUD + hamburger menu — `ready` — M — `game-designer` → `senior-developer`
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

### 9. Replay/score history (persistent storage) — `ready` — S-M — `senior-developer`
Added 2026-07-03 from user suggestions (`SUGGESTIONS.md` idea #4). A history
of past game scores the user can look back at, stored in local persistent
storage. The persistence layer this needs now already exists — see `DONE.md`
item 13 (Achievement system), which shipped `SaveStore`/`SaveData` backed by
a `Properties` file at `<user.home>/.tentoone/save.properties` (or a
profile-scoped variant, see `SaveStore.resolveSaveFile(String)`). Extend
that store/format rather than building a second, separate persistence
mechanism.

### 10. Difficulty tiers: freeplay vs. journey mode — `blocked` — M-L, open-ended — agent TBD (downstream of item 1)
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

### 11. Trump-card/hand `Handler` leak — `deferred` — S — `senior-developer`
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

### 12. Multi-monitor DPI rescale — `deferred` — size unclear (likely M-L) — `senior-developer`
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

### 13. Distributable executable + GitHub Release — `deferred` — S-M — `senior-developer`
Added 2026-07-04 per user request, to make the game shareable with
non-developer players (no git/JDK required on their end). Proposed approach,
not yet scoped in detail: use `jpackage` (bundled with JDK 21) to produce a
self-contained Windows app-image or installer with a private Java runtime
embedded, then attach it as a binary asset on a tagged GitHub Release. A full
`.exe`/`.msi` installer needs the WiX Toolset as a build-time dependency; a
plain app-image skips that but ships as a folder to unzip rather than a
single installer file — that tradeoff is unresolved. **Deferred**: user
doesn't need this now.

### 14. Multi-profile support — `deferred` — size unclear, likely M — agent TBD
Added 2026-07-04 from user suggestions (`SUGGESTIONS.md`): multiple named
profiles on the same device, each with their own achievements/stats
(and, per item 9 above, presumably their own replay/score history too).
Already anticipated, not yet built: `DONE.md` item 13 (Achievement system)
shipped `SaveStore.resolveSaveFile(String profileName)` specifically so
this wouldn't require a storage-format migration later — today it's always
called with one implicit default profile. Whoever picks this up still needs
to design profile creation/switching UI (presumably on or near the Start
Screen, where the name is already entered) and decide how it interacts with
name entry (is the typed name the profile, or a separate concept?) — not
scoped further here since the user hasn't weighed in on that yet.
**Deferred**: user doesn't need this now.

### 15. Bapi visual/wording flourish — `deferred` — S — agent TBD
Spun off from the Bapi easter-egg achievement (`DONE.md` item 13,
2026-07-04): once the "Bapi" achievement itself is implemented, there's an
open, explicitly deferred question about whether entering that name should
also change something visually/textually elsewhere in the game — the user's
own brainstorm, not yet decided. Two rough options floated during that
item's design pass: a subtle warm-palette tint (session-only, contained to a
specific UI element rather than a full reskin) or a single extra personal
line attached to the achievement's own unlock toast. User explicitly said
they don't know what they want yet — revisit now that the achievement itself
has shipped and they've seen it in action.

### 16. "OP"/"Cheater" guaranteed-best-cards easter egg — `deferred` — size unclear — agent TBD
Raised by the user 2026-07-04 while finalizing the achievement system's
design: entering a name like "OP," "Super OP," or "Cheater" (exact wording
undecided — user floated these as brainstorm examples, not final) would put
the human player in a "god mode" where they're always dealt the best cards
each round. Distinct from the Bapi achievement (`DONE.md` item 13) — this
would need real deal-logic changes (guaranteed best cards), not just a
name-triggered achievement unlock, so it's a heavier lift than a cosmetic
easter egg. User explicitly said this isn't necessary right now — logged for
later rather than scoped in detail.

### 17. Full visual overhaul — `deferred` — XL, open-ended — agent TBD
Added 2026-07-02 per user request: "much later down the line," a full
visual/art overhaul of the game beyond the functional UI fixes above.
Intentionally deferred — revisit once the functional/UX backlog (item 1) is
in a good place; scoping it now would be premature.

### 18. `src/` restructuring — `deferred` — design/planning only, not to be done now — agent TBD
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

### 19. Full multiplayer web app (accounts, single/multiplayer) — `deferred` — XL, open-ended, needs its own scoping conversation — agent TBD
Added 2026-07-04 from user suggestions (`SUGGESTIONS.md`): a "final version"
idea of a full web-app rewrite/expansion with Google sign-in, single-player
and multiplayer modes, playable with friends over the network. This is a
genuine architecture pivot away from the current desktop Swing/no-backend
shape (`CLAUDE.md` frames this project as a personal, not-currently-shipping
desktop app) — a web stack, user accounts, and real-time multiplayer netcode
aren't an incremental feature on top of what exists today, they're close to
a parallel project. **Explicitly deferred, not scoped further here** — same
treatment as item 1 (AI & polish) and item 18 (`src/` restructuring): needs
its own dedicated conversation with the user before any implementation
starts, not something to plan or estimate speculatively in this queue entry.

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
- 2026-07-04 (later same day): a full triage/reprioritization pass. Item 2
  (Achievement system) shipped, was confirmed pushed (`commit 2919d38`), and
  moved to `DONE.md` as that file's item 13 — this file's queue renumbered
  accordingly. `SUGGESTIONS.md` was triaged in full: 4 new items added
  (dev mode / jump-to-round, Start Screen keyboard accessibility, round
  summary panel enhancements bundling three related suggestions, and
  multi-profile support), one suggestion (a betting-legality rule requiring
  a round's total bets never equal its card count) was folded into item 1's
  existing scoping-material list rather than made a standalone item, since
  it's gated behind the same held `game-designer` conversation and touches
  the same AI-betting surface; one suggestion (an achievement-unlock
  notification popup) was dropped as a less-detailed duplicate of the
  already-tracked, more-recent achievement-toast-redesign item; and one
  suggestion (a full web-app multiplayer rewrite with Google sign-in) was
  deliberately *not* triaged in either direction — it's a full architecture
  pivot away from this project's current desktop-Swing/no-backend shape, a
  genuine product fork rather than an incremental item, so it was flagged
  back to the user for an explicit decision instead of being guessed at; it
  remains in `SUGGESTIONS.md` pending that answer. Rather than simply
  appending the 4 new items at the end of the queue, the whole active
  backlog was reordered by value/leverage: the achievement-toast redesign
  (fully spec'd already, and directly responsive to the user's own
  dissatisfaction with the just-shipped toast) was promoted from `deferred`
  to `ready` and moved near the top; the new dev-mode item was placed early
  for its testing-velocity leverage on every future item; low-value/large-
  scope/deferred items (visual overhaul, `src/` restructuring, DPI rescale,
  distributable executable, the two easter-egg items, Bapi flourish, new
  multi-profile item) were grouped toward the bottom. Old item numbers from
  before this pass are not preserved here given how many items moved and how
  many were inserted mid-queue rather than appended — consult git history if
  the prior numbering is needed. Separately, every item's primary-owner
  column was normalized from a `senior-frontend-developer`/
  `senior-backend-developer` split to plain `senior-developer` throughout
  (not just item 2, which got this fix in isolation on 2026-07-04 earlier
  the same day) — this project has no real frontend/backend seam per
  `CLAUDE.md`, and leaving some items on the old split read as an
  inconsistency once one item had already been corrected. Item 9 (Replay/
  score history)'s writeup was also corrected: it previously described "no
  persistence exists yet" as a shared gap with item 2 — since item 2 shipped
  first, item 9 now correctly points at the persistence layer (`SaveStore`/
  `SaveData`) that already exists rather than describing it as future work.
- 2026-07-04 (later still, same day): the one item left unresolved from the
  triage pass above — a full multiplayer web-app rewrite (Google sign-in,
  single/multiplayer) — was resolved by the user: log it as a deferred
  "someday" item needing its own scoping conversation, same treatment as
  items 1 and 18, rather than estimating or planning it here. Added as new
  item 19, appended at the end without renumbering the rest of the queue
  (consistent with how items 13/14 were appended earlier the same day) since
  it carries no priority signal and sits alongside the other deferred/
  large-scope items already at the bottom. Removed from `SUGGESTIONS.md`,
  which returns to empty.

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
</content>
