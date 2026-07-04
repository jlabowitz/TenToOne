# Ten to One — Completed Work

Archive of finished `ROADMAP.md` items, moved out on 2026-07-02 to keep the
active roadmap focused on what's next. This is historical record —
implementation detail and rationale for work already shipped, not a task
queue. Item numbers below are permanent and sequential in chronological ship
order — not tied to `ROADMAP.md`'s queue positions, which get renumbered as
that file's active backlog changes — so it's safe to cross-reference these
numbers from elsewhere without them going stale.

### 1. Repo hygiene sweep — done, pushed
Untracked stale `out/*.class`, added `.gitignore`, deleted dead
`Players.java`, removed dead `Window.paint()`/`p3.gif` code, derived trump
border from `Card.WIDTH`/`HEIGHT` constants. Commit `7fc7e14`.

### 2. Card image caching — done, pushed
`Card.render` no longer calls `ImageIO.read` from disk every frame; images
are loaded once into a cache. QA measured ~874,000x speedup on cached vs.
cold image loads. Commit `42aafd6`.

### 3. Canvas/frame insets sizing fix — done, pushed
`Window.java` now sizes the Canvas itself to 840x630 and lets `frame.pack()`
grow the frame around it, instead of sizing the frame directly (previously
the frame's OS chrome shrank the actual drawable canvas below 840x630,
clipping the leftmost card in the human's hand). Extracted a testable
`Window.buildFrame()` seam; `TestWindowSizing.java` locks in the invariant
without popping a window during test runs. Commit `83e0660`.

### 4. Bet input validation stopgap — done, pushed
`Human.bet` now loops on `hasNextInt`/range-check (0..numCards) instead of
crashing on non-numeric input or accepting out-of-range values. Commit
`f2629e9`.

### 5. Mouse-driven betting + persistent bet/score display — done, pushed
Two parts, scoped together since both concern the human's on-screen UI real
estate: (a) clickable numeric-stepper bet input (0..numCards), replacing the
last remaining console input during play; (b) persistent on-screen display
of the human's own bet and score for the duration of each round, mirroring
what AI opponents already show. Commit `783bc62`.

Approved design (`game-designer`, revised once after user feedback rejected
an initial 11-button row as cluttered):
- **Bet input**: numeric stepper, not discrete buttons — a single
  horizontal row `[ − ][ value ][ + ][ Bet ]` anchored at x=620, y=585-619.
  Decrement/increment adjust the draft value by 1, clamped (no wraparound)
  at 0/numCards. "Bet" commits, reusing the existing `Human.isValidBet`
  predicate as a defensive check. Implemented as one composite `GameObject`
  (`BetStepper`) added to `Handler` on entry to `Human.bet()` and removed on
  return, mirroring `Human.playCard`'s existing lifecycle pattern. Hover
  feedback and press-and-hold auto-repeat on the arrows were deliberately
  deferred — no `MouseMotionListener`/repeat-timer capability exists in the
  codebase, and the range is small enough (at most 10 discrete clicks) that
  the UX cost is low. Covered by `TestBetStepper.java` (pure hit-test
  geometry, no window).
- **HUD**: fixed text at x=620, three lines at y=430/448/466, same format as
  the AI HUD (`trickScore/bet`, `Score: N`). While the stepper is
  mid-adjustment pre-submit, the HUD intentionally still shows the last
  *committed* bet, not the in-progress draft value (the stepper's own value
  display already shows the draft) — not a defect.
- **Bundled fix**: `Player.bet` was never reset between rounds (only
  `trickScore` was, via `resetTrickScore()`) — fixed alongside the HUD work
  so a new round doesn't show last round's bet as already placed.

### 6. Thread model fix (Handler race + Game.stop() self-join) — done, pushed
`Handler`'s object list was a `LinkedList` walked by index while a separate
thread concurrently added/removed objects during play — a genuine data race
(proven by a regression test that fails on the old code) and an O(n^2)
per-frame walk. Swapped to `CopyOnWriteArrayList` with enhanced-for
iteration in `tick()`/`render()`; public method signatures unchanged, no
callers touched.

`Game.stop()` had a self-join deadlock (its own thread joining itself) and,
once that was fixed, an ordering bug (`running` only flipped `false` after
the blocking `join()` returned, which would hang any future external
caller). `stop()` now sets `running = false` unconditionally up front and
only joins when called from a thread other than the one it's stopping;
no longer `synchronized`. Code review caught a follow-on gap this surfaced —
removing `synchronized` broke `thread`'s cross-thread visibility, so it
needed `volatile` too (not just `running`) — fixed and re-confirmed.

QA soak-tested beyond the unit suite: two custom scratch harnesses ran 180s
of sustained concurrent tick/render vs. add/remove (128,305 cycles, 327M+
mutation ops, zero exceptions) and 1,000 `start()`/`stop()` lifecycle cycles
across both self-join and external-join paths (zero hangs). New
`TestHandler.java` (4 tests) and 2 new `TestGame.java` tests. Commit
`fae32e5`.

### 7. Trick-state indicators (leader, led suit, high card) — done, not yet pushed
Three indicators, one `game-designer` spec pass covering all three since
they render at the same place/time: a black dot marking the current trick
leader, a "Led: [suit]" HUD line for the trick-in-progress, and a gold ring
around the current highest-valued card, recomputed incrementally as each
card lands (reusing `Round.determineTrickWinner`/`isHigher`, not new
comparison logic). Commit `c23ff0f`.

Approved design placed the leader dot above-left of each player's name and
sized the high-card ring at a 16-18px offset. Live testing (this project's
actual QA loop — see the "Process notes" section below and the session's
own retrospective in memory) surfaced pixel-collision bugs the design
spec's arithmetic didn't catch, fixed over several rounds:
- Leader dot moved from an above/left offset (collided with the "Led:" line
  one row above; risked left-edge clipping for the leftmost AI seat at x=0)
  to `FontMetrics`-measured placement immediately right of the name text —
  robust to name length and screen position by construction.
- High-card ring shrank (offset 16-18 → 13-14) and the AI-played card's
  vertical offset from its name widened (+20 → +30, with the AI's
  trickScore/score lines pushed from +150/+170 to +165/+185) to give the
  ring real clearance from surrounding text on both sides, not a ~2px gap.
- Per a followup request, the whole human HUD block (name, Led: line,
  bet/tricks, score) moved from beside the human's hand to sit next to the
  trump card instead, since that reads as more related information.

Also fixed one build-hygiene issue hit mid-session, unrelated to this
feature's logic: a fast kill/recompile/relaunch cycle left a stale `build/`
missing a regenerated class file (`NoClassDefFoundError` on a switch-over-
enum synthetic class), not caught by `javac` as a compile error. Resolved
with a clean rebuild; `CLAUDE.md`'s Build section now calls this out.

### 8. In-window round & game-flow UX — done, pushed
Three sub-parts, one `game-designer` spec pass covering all three since they
render at the same points in the game loop (trick resolves → round ends →
game ends): (a) a round-transition/score-summary modal (scrim + centered
panel showing each player's bet/tricks/round delta/running total, gold
"(bonus!)" highlighting when bet == tricks taken), (b) a bold click-to-
continue text prompt placed in a verified-clear band between the AI row and
the trump card (existing blocking point in `Human.nextTrick()`, unchanged
trigger), (c) a permanent end-of-game outcome banner (win/lose title, final
standings sorted by score, an explicit "close this window to exit" footer
so the frozen final frame doesn't read as hung).

New classes: `RoundResultRow` (pure data), `ModalOverlay` (shared scrim/
panel/font base for (a)/(c)), `RoundSummaryPanel`, `GameOverBanner`,
`NextTrickPrompt`. `Game.mouseInput` promoted from constructor-local to a
field; `Human.nextTrick()` gained a `try/finally` around its existing
blocking wait. `snapshotRoundResults()`/`applyTotals()` snapshot each
player's bet/tricksTaken *before* `adjustScores()` resets `trickScore` to 0
— a data-lifecycle gotcha the design spec flagged explicitly.

Per this project's reduced-flow default (review/QA opt-in, not automatic —
see `ROADMAP.md`'s cadence-override note), this shipped without a separate
`senior-code-reviewer`/`senior-qa-frontend` pass: the implementer
self-verified via 64/64 passing unit tests (new
`TestGame.snapshotRoundResultsCapturesBonusHitBoundary`, TDD'd against the
bonus-hit boundary) plus a throwaway reflection-driven harness that drove
real `MouseEvent`s through the actual rendering pipeline and screenshotted
all three trigger points (both bonus/non-bonus round-summary branches, both
win/lose banner branches) via `Robot` — confirmed no pixel collisions,
deleted before finishing since it wasn't part of the shipped diff. User
separately hands-on played a shortened game (skip-ahead-to-round-9 launcher,
also throwaway) through to the outcome banner and confirmed it looked right.

Old console `System.out.println` calls (scores, "won the trick", etc.) were
deliberately left in place — the spec didn't ask for their removal and
removing them wasn't attempted opportunistically. They now duplicate what's
on screen; flagged as a likely target once the "eliminate the terminal"
goal is revisited, not fixed here.

### 9. Invalid-move on-screen feedback — done, pushed, **user-confirmed**
A branch-aware fading red→white text message (not a border flash or shake —
both considered and rejected: a border risked the same pixel-collision class
of bug the trick-indicators item hit, since `Hand.layoutCards()` spaces
cards only 24px apart at a 10-card hand; a shake would need `Hand`'s shared
per-frame layout recompute to add rather than overwrite an offset, touching
logic every card depends on for a cosmetic effect). `game-designer`'s spec
found the ticket's framing was incomplete: `Player.legalCards()` has two
independent illegal-move reasons (leading trump before it's broken, vs. not
following suit), not one, so a single generic message would have actively
misled in the trump-lead case.

New `IllegalPlayFeedback` (a `GameObject`, added/removed around
`Human.playCard()`'s click loop exactly like `BetStepper`/`NextTrickPrompt`'s
lifecycle) shows the branch-appropriate message in the same verified-clear
text band `NextTrickPrompt` already established, solid red for ~0.5s then
linearly interpolating to white over ~1.5s (RGB interpolation, not real
alpha blending — deliberately avoids `AlphaComposite`, new territory this
project's raw `Canvas`/`BufferStrategy` pipeline hasn't touched). New
`Human.illegalReason()` picks the message; `colorAt()` is a pulled-out pure
function for the fade math, both covered by new unit tests
(`TestIllegalPlayFeedback`, `TestHumanIllegalReason`) — 73/73 tests passing
on a clean rebuild.

**Update:** the live check flagged as owed above was completed once the
user was back at their computer — hands-on playtest confirmed both messages
render and fade correctly, no issues found.

### 10. Start screen + rules view + player name input — done, pushed, **user-confirmed (one bug found and fixed, see item 12)**
Three parts per `game-designer`'s MVP scope call: a combined **Start Screen**
(title, name field, Rules button, Start Game button — one screen, not two),
a shared **Rules View** reachable both pre-launch and mid-game (via a new
hotspot on the existing `NextTrickPrompt`, so item 7's legend has a home
without any later restructuring), and **name entry** replacing the hardcoded
`"Jacob"`. Deliberately deferred to a future options-menu pass: player-count/
difficulty selection, Enter-to-submit, and validation feedback on an
empty-name submit (silently ignored, matching `BetStepper`'s existing
invalid-input convention) — flagged as a UX-feel watch-item for a future
live playtest, not fixed preemptively.

New classes: `StartScreen`, `RulesView`, `TypingTarget`/`KeyInput` (this
codebase's first-ever keyboard input plumbing, mirroring `MouseInput`'s
`extends *Adapter` shape). `Game`'s constructor signature changed
(`List<String> playerNames` → `List<String> aiNames` — the human is no
longer a list slot, captured live via the new `captureHumanName`/
`runStartScreen` seam instead) and its static AI name list dropped from 5 to
4 entries (`"Jacob"` removed, human seated first regardless). `Human.
nextTrick()` now loops to check the Rules hotspot instead of a single
blocking click. `CLAUDE.md`'s Test section corrected to list all 16 test
classes (previously only named 2, stale since well before this session).

**`MAX_NAME_LENGTH` measured at 13, not the design spec's illustrative 16**
— confirmed via real `FontMetrics` against `RoundSummaryPanel`'s actual name
column and the codebase's actual default font, independently re-verified by
both `senior-code-reviewer` and `senior-qa-backend`.

Per this project's reduced-flow default, this item was judged risky enough
(a `Game` constructor/lifecycle signature change, plus new cross-thread
key-event plumbing) to route through `senior-code-reviewer` and
`senior-qa-backend` rather than skip them — the one item so far in this
backlog to get that treatment. Review caught one real, previously-latent
bug: `keyInput`'s target reference to a discarded `StartScreen` was never
cleared, fixed by adding `keyInput.setTarget(null)` to `runStartScreen()`'s
cleanup `finally`. QA traced (not just empirically tested) that no
construction path can reach the real blocking `runStartScreen()` call in
`HeadlessGame`, and ran the full suite 11 times (113/113 passing every run,
zero flakes) — separately confirmed once more from a clean rebuild in this
session, same result.

**Update:** the 5 live checkpoints flagged as owed above were walked once
the user was back at their computer — all confirmed working (keyboard
focus, Rules round-trip preserving a partial name, name reaching the HUD,
hotspot clearance, name rendering in `RoundSummaryPanel`/`GameOverBanner`),
with one real gap found: the Rules button was only reachable during the
between-tricks pause, not while a bet or card-play decision was pending —
see item 12 for the fix.

### 11. Play again (in-window restart) — done, pushed
`game-designer` scoped the one genuine open question — does "Play Again"
reappear at the Start Screen, or skip straight into a fresh game reusing
the same name — and sent it back as a real product call rather than
deciding it unilaterally; user chose **reappear** (lets a typo get fixed,
or the machine handed to a different player, before committing).

Restart happens **in place on the same `Game` instance**: `play()`'s round
loop now sits inside an outer infinite loop (round loop → outcome banner →
`awaitPlayAgain()` → `restartForNewGame()` → repeat), not a full `Game`/
`Window`/thread reconstruction — reconstructing would spawn a second real
`JFrame` and render thread with no teardown path for the old one.
`renderPlayers()` stays a one-shot call (calling it again would silently
double-add every `Player` to `Handler`'s per-frame walk — flagged
proactively in the design spec as a real, non-obvious trap, not discovered
the hard way). No `start()`/`stop()` thread-lifecycle calls anywhere in the
restart path — the render thread already runs continuously across all 10
rounds of one game; restart just extends that same continuity across games.

New `Player.resetForNewGame()` (bundles `resetBet()`/`resetTrickScore()`/
the new `resetScore()`/trick-leader/leading-suit/`hand = null` — mirrors
this class's existing small-dedicated-reset-method convention) fixed a real
gap: nothing today had ever reset a player's running game score between
games, since no code path needed to before this item. `GameOverBanner`
gained a "Play Again" hotspot (geometry verified against real
`FontMetrics`); `showGameOverBanner()` now returns the banner instance so
`play()` can remove it once Play Again is clicked, instead of leaving it
permanent.

Per this project's reduced-flow default, this item was judged risky enough
(game lifecycle/restart logic) to route through `senior-code-reviewer` and
`senior-qa-backend` rather than skip them. Review confirmed no double-add,
no thread-lifecycle calls, human found by `ID` not seat index, hand
correctly nulled; also caught a real bug found during self-verification
(not a design ambiguity): `StartScreen` didn't paint its own background, so
reappearing after a restart let stale AI HUD text bleed through behind it
(harmless at first launch when no players exist yet, no longer true once
players persist across a restart) — fixed with an opaque white fill,
matching `RulesView`'s existing defensive approach. QA ran the full 133-test
suite clean, then drove two consecutive full restart cycles with real
`Robot` screenshots and direct state inspection — player count constant
across restarts, bleed-through fix held both cycles, names/scores/hands
reset correctly each time, no thread hangs.

One real, pre-existing bug found during review, deliberately **not** fixed
here — logged instead as `ROADMAP.md`'s new tracked item: `Round.
renderTrumpCard()`/`renderPlayerHand()` never call `handler.removeObject()`,
so each restart now leaks one trump card and one `Hand` object into
`Handler`'s permanently-growing list (previously capped at 10 trump cards
per process lifetime, since a process only ever played one game before this
feature existed). No visible symptom — trump cards render at a fixed pixel
position, so newer ones simply paint over older, invisible ones — and the
realistic impact (hundreds of Play-Again clicks in one sitting) doesn't
matter for how this game is actually played. Judged not worth expanding an
already-large, already-reviewed diff to fix a general `Round.java`
correctness gap that predates this session entirely.

### 12. Rules button not reachable during bet/card-play — done, pushed
User-reported gap, found during hands-on testing of item 3: the Rules
hotspot only appeared during the between-tricks pause
(`NextTrickPrompt`'s), not while betting (`BetStepper` showing) or choosing
a card (`IllegalPlayFeedback` already on screen) — exactly the moments a
new player is most likely to want the rules. This was actually anticipated
and explicitly deferred in item 3's original design spec as a future
options-menu follow-up; user's real playtest brought it forward sooner than
expected.

Both `BetStepper` and `IllegalPlayFeedback` now render the same Rules
hotspot `NextTrickPrompt` already had, reusing its exact proven-safe
coordinates (x=760-820, y=265-295) rather than new geometry. Small, low-risk
extension of an already-shipped pattern — skipped `senior-code-reviewer`/QA
per this project's reduced-flow default, live-verified via `Robot`
screenshots instead: no collisions in either screen, opens/returns
correctly, bet-stepper value and full hand preserved on return.

---

For why these were sequenced the way they were relative to each other (e.g.
item 5 reordered ahead of item 6), see git history on `ROADMAP.md` — that
session-specific reasoning wasn't preserved here since it's no longer
actionable now that all five are shipped.
