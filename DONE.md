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

---

For why these were sequenced the way they were relative to each other (e.g.
item 5 reordered ahead of item 6), see git history on `ROADMAP.md` — that
session-specific reasoning wasn't preserved here since it's no longer
actionable now that all five are shipped.
