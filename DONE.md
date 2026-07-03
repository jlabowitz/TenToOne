# Ten to One — Completed Work

Archive of finished `ROADMAP.md` items, moved out on 2026-07-02 to keep the
active roadmap focused on what's next. This is historical record —
implementation detail and rationale for work already shipped, not a task
queue. Item numbers below are the original `ROADMAP.md` queue numbers at the
time each shipped; they are superseded by `ROADMAP.md`'s 2026-07-02
renumbering of the active queue, so don't cross-reference these numbers
against current roadmap items.

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

### 1. Thread model fix (Handler race + Game.stop() self-join) — done, pushed
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

### 1. Trick-state indicators (leader, led suit, high card) — done, not yet pushed
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

### 1. In-window round & game-flow UX — done, not yet pushed
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

### 2. Invalid-move on-screen feedback — done, not yet pushed, **not yet visually confirmed**
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

**Caveat, flagged explicitly rather than silently claimed as done:** this
shipped without the live "launch and look" visual check this project
normally relies on as its primary QA signal (per the reduced-flow default) —
the machine's screen was locked when the implementer attempted it via a
`Robot`-driven harness, and still locked on a follow-up attempt. Confidence
here rests on thorough unit coverage of both pure-logic seams (message
selection, fade timing) plus a direct read of the diff confirming it matches
the design spec's illustrative code almost verbatim — not on having actually
seen the message render. **Next session should do a 10-second sanity check**
(deliberately click an off-suit card and an early trump lead, confirm both
messages appear and fade correctly) before this is fully trusted.

---

For why these were sequenced the way they were relative to each other (e.g.
item 5 reordered ahead of item 6), see git history on `ROADMAP.md` — that
session-specific reasoning wasn't preserved here since it's no longer
actionable now that all five are shipped.
