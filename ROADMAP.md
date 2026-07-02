# Ten to One — Roadmap

Working backlog for the `overhaul` branch. This file is the source of truth for
task status and priority across sessions — read it before proposing what to do
next, don't re-derive the plan from scratch or from conversation memory alone.

Each task, once started, goes through the full cycle: implement (TDD where
practical) → senior-code-reviewer → senior-qa-backend/frontend → show the user
a running instance → explicit commit go-ahead → commit → explicit push
go-ahead → push.

## Status legend
`done` — committed and pushed · `ready` — unblocked, not started ·
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

### 3. Canvas/frame insets sizing fix — `done` (commit pending)
`Window.java` now sizes the Canvas itself to 840x630 and lets `frame.pack()`
grow the frame around it, instead of sizing the frame directly (previously the
frame's OS chrome shrank the actual drawable canvas below 840x630, clipping
the leftmost card in the human's hand). Extracted a testable
`Window.buildFrame()` seam; `TestWindowSizing.java` locks in the invariant
without popping a window during test runs. Verified via DPI-aware screenshots
before/after — leftmost card no longer clipped, click-through hit-testing
unaffected. Commit pending — see git log for hash once committed.

### 4. Thread model fix (+ `Game.stop()` self-join) — `ready` — M/L — `senior-backend-developer`
Keystone item — items 6/7 below add more shared clickable UI state, and
building them on the current "try/catch as a load-bearing wall" model means
re-auditing them later for the same class of race. Fix `Handler`'s index-based
`LinkedList` iteration (the concrete race surface *and* an O(n²) walk per
frame); define actual thread ownership (logic thread mutates, render thread
reads a safe view); fold in the `Game.stop()` self-join fix (same
thread-lifecycle code, currently unreachable but a latent deadlock). Hardest
item to QA — races don't unit-test well; expect review to do the heavy lifting
plus a soak-run harness from QA.

### 5. Multi-monitor DPI rescale — `deferred` — size unclear (likely M-L) — `senior-backend-developer`
Discovered 2026-07-02: dragging the game window from the user's primary
monitor to a secondary monitor with a different Windows display-scale factor
causes blurry/stretched rendering. Root cause: the game renders via a raw
`Canvas` + `BufferStrategy` (`Game.java`), a lower-level pipeline than
standard Swing painting, which is known to not gracefully handle
`WM_DPICHANGED`/per-monitor scale changes — the pixel buffer stays sized for
whichever monitor's DPI was active at creation, and Windows bitmap-stretches
it to fit the new monitor instead of the app redrawing natively. This is
**distinct from item 3** (which only fixes a static single-monitor
insets/cropping bug) and would likely need explicit DPI-change handling or
migrating off raw `Canvas`/`BufferStrategy` toward Swing's more DPI-aware
repaint pipeline.
**Deferred**: doesn't affect the user's normal single-monitor workflow.
Revisit if that changes.

### 6. Mouse-driven betting + input validation — `blocked` (needs 4) — M — `game-designer` spec → `senior-frontend-developer`
Clickable bet buttons (0..numCards), replacing the last remaining console
input during play. **Bet input validation is extractable as a standalone
stopgap at any time, independent of this item** — `Human.bet`'s
`Scanner.nextInt()` crashes on non-numeric input and accepts any range today;
a `hasNextInt`/range-check loop is ~10 minutes and doesn't need to wait for
the UI work.

### 7. Between-round / end-of-game in-window UX — `blocked` (needs 4) — M — `game-designer` spec → `senior-frontend-developer`
Scores, bets-vs-tricks-taken, round transitions, and the winner are currently
console-only; nothing appears in the window. Spec together with item 6 in one
`game-designer` pass so the window doesn't end up with two competing UI
languages.

### 8. AI & polish — `blocked` (comes after the above) — M, open-ended — `game-designer` → `senior-backend-developer`
`AI_Zombie` is unused but functional (always plays first legal card) — a
natural "easy" difficulty tier if a difficulty picker lands; do not delete it.
Candidates: smarter betting/strategy, opponent card-count display (overlaps
item 7), play animations.

## Known process gap (being fixed)

QA's screenshot checks (via `java.awt.Robot`) verify "no exceptions in the
log," not actual visual/pixel quality — a subagent's textual description of a
screenshot is not the same as someone actually looking at it. The main
conversation (which has vision) should be the one to visually inspect
screenshots for any UI-touching change, using a screenshot the user supplies
if the automation session can't see the user's actual desktop (confirmed
2026-07-02: it can't — different session).
