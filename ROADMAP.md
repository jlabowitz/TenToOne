# Ten to One — Roadmap

Working backlog for the `overhaul` branch. This file is the source of truth for
task status and priority across sessions — read it before proposing what to do
next, don't re-derive the plan from scratch or from conversation memory alone.

Each task, once started, goes through the full cycle: implement (TDD where
practical) → senior-code-reviewer → senior-qa-backend/frontend → show the user
a running instance → explicit commit go-ahead → commit → explicit push
go-ahead → push.

## Status legend
`done` — committed (check the item's own text for push status — not all
`done` items are pushed yet) · `ready` — unblocked, not started ·
`blocked` — waiting on a dependency · `deferred` — real, but intentionally low priority

## Queue

### 1. Repo hygiene sweep — `done`
Untracked stale `out/*.class`, added `.gitignore`, deleted dead `Players.java`,
removed dead `Window.paint()`/`p3.gif` code, derived trump border from
`Card.WIDTH`/`HEIGHT` constants. Commit `7fc7e14`, pushed.

### 2. Card image caching — `done`
`Card.render` no longer calls `ImageIO.read` from disk every frame; images are
loaded once into a cache. QA measured ~874,000x speedup on cached vs. cold
image loads. Commit `42aafd6`, pushed.

### 3. Canvas/frame insets sizing fix — `done`
`Window.java` now sizes the Canvas itself to 840x630 and lets `frame.pack()`
grow the frame around it, instead of sizing the frame directly (previously the
frame's OS chrome shrank the actual drawable canvas below 840x630, clipping
the leftmost card in the human's hand). Extracted a testable
`Window.buildFrame()` seam; `TestWindowSizing.java` locks in the invariant
without popping a window during test runs. Commit `83e0660`, not yet pushed.

### 4. Bet input validation stopgap — `done`
`Human.bet` now loops on `hasNextInt`/range-check (0..numCards) instead of
crashing on non-numeric input or accepting out-of-range values. Commit
`f2629e9`, not yet pushed.

### 5. Mouse-driven betting + persistent bet/score display — `ready` — M — `game-designer` spec → `senior-frontend-developer`
Two parts, scoped together since both concern the human's on-screen UI real
estate: (a) clickable bet buttons (0..numCards), replacing the last
remaining console input during play; (b) persistent on-screen display of the
human's own bet and score for the duration of each round, mirroring what AI
opponents already show (`Player.render`'s `ID.AI` branch, `Player.java`
~106-111, draws `getTrickScore() + "/" + getBet()` and
`"Score: " + getScore()` near each AI's position; the `ID.HUMAN` branch,
~112-121, renders only the hand today -- added 2026-07-02 per user request).
Distinct from item 7, which covers round-transition/end-of-game summary UI,
not this per-round live HUD element.

Reprioritized 2026-07-02 to go next, ahead of item 6, after re-examining
whether the thread-model fix actually needs to precede it: it doesn't. Part
(a) reuses the exact same `MouseInput`/`awaitClick`/`clearClicks` plumbing
already shipped and working for card-play (commit `1153913`) -- the
click-queue handoff is generic `Point`-based hit-testing, directly reusable
for bet-button geometry the same way `Hand.cardAt` hit-tests cards. The race
conditions item 6 targets (`Handler`'s index-based `LinkedList` mutated
across threads) are not gated behind this feature -- `Round.bet()` already
calls `handler.addObject(trumpCard)`/`handler.addObject(playersHand)` on the
game-logic thread every round, concurrently with the render thread's
`Handler.tick()`/`render()` loop, via the identical
`Handler.addObject`/`removeAll` pattern this item would use. This UI adds
more instances of an already-live, already-accepted pattern, not a new
category of risk.

`game-designer` pass reinstated 2026-07-02 (originally skipped for
bet-buttons alone as low-ambiguity) -- with part (b) added, there's now real
layout ambiguity: the human's hand already occupies screen space differently
than the AI row (bottom-of-screen hand vs. top-row AI labels), so where the
human's own bet/score readout and the bet-input buttons go relative to the
hand needs an actual design call, not an assumption. Scope the
`game-designer` pass to exactly two decisions: (a) bet-input button layout,
and (b) live bet+score display placement for the human player.

### 6. Thread model fix (+ `Game.stop()` self-join) — `ready` (after 5) — M/L — `senior-backend-developer`
Re-sequenced 2026-07-02 to follow item 5 rather than precede it (see item 5's
note) -- not a hard blocker for betting, but still the right keystone before
item 7's larger UI lift (score displays, round transitions, winner banner)
piles a third generation of shared render state onto an unaudited model.
Fix `Handler`'s index-based `LinkedList` iteration (the concrete race surface
*and* an O(n^2) walk per frame); define actual thread ownership (logic thread
mutates, render thread reads a safe view); fold in the `Game.stop()`
self-join fix (same thread-lifecycle code, currently unreachable but a latent
deadlock). Hardest item to QA -- races don't unit-test well; expect review to
do the heavy lifting plus a soak-run harness from QA.

### 7. Between-round / end-of-game in-window UX — `blocked` (needs 6) — M — `game-designer` spec → `senior-frontend-developer`
Scores, bets-vs-tricks-taken, round transitions, and the winner are currently
console-only; nothing appears in the window. Decoupled 2026-07-02 from item
5's spec pass (they no longer sit adjacent in the queue) -- this item has
real design surface (score displays, round transitions, winner banner) and
keeps its own dedicated `game-designer` pass, done independently once its
turn comes.

### 8. AI & polish — `blocked` (comes after the above) — M, open-ended — `game-designer` → `senior-backend-developer`
`AI_Zombie` is unused but functional (always plays first legal card) — a
natural "easy" difficulty tier if a difficulty picker lands; do not delete it.
Candidates: smarter betting/strategy, opponent card-count display (overlaps
item 7), play animations.

### 9. Multi-monitor DPI rescale — `deferred` — size unclear (likely M-L) — `senior-backend-developer`
Discovered 2026-07-02: dragging the game window from the user's primary
monitor to a secondary monitor with a different Windows display-scale factor
causes blurry/stretched rendering. Root cause: the game renders via a raw
`Canvas` + `BufferStrategy` (`Game.java`), a lower-level pipeline than
standard Swing painting, which is known to not gracefully handle
`WM_DPICHANGED`/per-monitor scale changes — the pixel buffer stays sized for
whichever monitor's DPI was active at creation, and Windows bitmap-stretches
it to fit the new monitor instead of the app redrawing natively. This is
**distinct from item 3** (which only fixed a static single-monitor
insets/cropping bug) and would likely need explicit DPI-change handling or
migrating off raw `Canvas`/`BufferStrategy` toward Swing's more DPI-aware
repaint pipeline.
**Deferred**: doesn't affect the user's normal single-monitor workflow.
Revisit if that changes.

---

Notes:
- Item 9 (multi-monitor DPI) was item 5 in the old numbering -- moved to the end since it's deferred and everything else outranks it now.

## Known process gap (being fixed)

QA's screenshot checks (via `java.awt.Robot`) verify "no exceptions in the
log," not actual visual/pixel quality — a subagent's textual description of a
screenshot is not the same as someone actually looking at it. The main
conversation (which has vision) should be the one to visually inspect
screenshots for any UI-touching change, using a screenshot the user supplies
if the automation session can't see the user's actual desktop (confirmed
2026-07-02: it can't — different session).
