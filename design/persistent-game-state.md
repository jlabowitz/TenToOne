# Design: Persistent Game-State Model (prerequisite for Roadmap items 10 & 14)

Status: **plan/design-only**, written at the user's direct request before any
implementation. No production code has been touched. This doc proposes the
data model and save/load mechanics that both `ROADMAP.md` item 10 ("back to
main menu" without losing the in-progress game) and item 14 (resume after
closing the app entirely) need underneath them — the user asked for this
piece scoped and built *first*, with the full hamburger-menu UI (item 10)
designed afterward on top of it.

## 0. Why this is one piece of work, not two

Item 10's "back to main menu" and item 14's "resume after restart" are the
same underlying need at two different durability levels:

- Item 10: suspend the in-progress `Game`/`Round` state in memory, show the
  Start Screen, come back to the exact same state later in the *same process*.
- Item 14: the same state, but written to disk so it survives the process
  exiting entirely.

If item 10 is built as pure in-memory suspend (just don't null out the
objects) and item 14 is built later as a separate disk format, they'll
diverge and one will need a rewrite. Building the **snapshot model once** —
a plain-data representation of "everything needed to reconstruct a game in
progress" — lets item 10 use it in-memory (snapshot → keep in a field →
restore) and item 14 use the exact same snapshot serialized to JSON on disk.
That snapshot model is this doc's scope. Actually wiring it into a menu (item
10) or an app-launch resume prompt (item 14) is explicitly **not** covered
here — both remain their own `game-designer` passes afterward, per the
existing roadmap notes on each.

## 1. What has to be captured

Walking the live object graph (`Game` → `Round` → `Trick`/`Player`/`Hand`/
`Deck`/`Card`):

**Game-level** (`Game.java`):
- `roundIndex` (0-9)
- `roundStartingPlayer` (seat index)
- Each player's identity: name, and *what kind* of player they are — human,
  or which AI class + personality (`AI_Easy`, or `AI_Medium` +
  `AIPersonality.MEDIUM_BALANCED`/`BOLD`/`CAUTIOUS`). This is new state to
  capture — nothing today serializes "which AI this seat is."
- Each player's running `score`.
- Ephemeral per-game achievement counters (`roundsHitBonusThisGame`,
  `wasSoleLastAtHalfway`) — cheap to include so a resumed game doesn't
  silently break `FLAWLESS_GAME`/`COMEBACK_KID` tracking mid-game.

**Round-level** (`Round.java`):
- `trumpCard` (suit + value) and derived `trump` suit.
- `trumpBroken`.
- Each player's `hand` (list of cards), `bet`, `hasBet`, `trickScore`.
- Trick-leader/leading-suit flags.

**Revised after the user's follow-up — the undealt deck is not needed at
all.** `Round`'s constructor draws every player's full hand plus the trump
card up front (`deal()`, then one more `draw()` for `trumpCard`) before any
betting or play happens, and nothing anywhere calls `Deck.draw()` again for
the rest of that round (checked: `Round.java`'s only two call sites). Once a
round starts, whatever cards are left in the deck are simply dead for that
round — no player will ever hold or see them, so there is nothing to
reconstruct. Dropping this field entirely (not deriving it from a seed
either — see §4a) removes a chunk of the storage/reconstruction surface for
free, independent of the seed question below.

**Trick-in-progress** (`Trick.java`), since the user specifically wants cards
captured "as they are played," not just at trick boundaries:
- `cardsPlayed` so far this trick, **and which seat played each one** (index
  into the player list, not just the card) — needed to reconstruct whose
  turn is next.
- `currentPlayer` (whose turn to play next).
- `leading` suit (once the first card of the trick has landed).
- The running high-card pointer is derivable from `cardsPlayed` + trump via
  `Round.determineTrickWinner` — no need to store it separately.

**Not captured** (derivable/re-rendered, not game-logic state): pixel
positions (`x`/`y`), the `trump`/`highCard` render flags on `Card` (these are
just `Round.determineTrickWinner`/known-trump derivations, recomputed on
load), `Handler` registration, thread/`Window` state, `SaveData`/achievement
unlock state (already its own persistence via `SaveStore` — untouched by
this).

## 2. Proposed data model

Plain data-holder classes/records, no behavior — mirrors this codebase's
existing convention for transport objects (`SaveData`, `RoundResultRow`,
`AIPersonality`). All fields are primitives, strings, enums, or lists of the
above, which matters for §4 (serialization).

```
GameStateSnapshot
  saveFormatVersion: String
  savedAt: Instant
  roundIndex: int
  roundStartingPlayer: int
  roundsHitBonusThisGame: int
  wasSoleLastAtHalfway: boolean
  players: List<PlayerSnapshot>
  round: RoundSnapshot          // null if between rounds (e.g. saved right after game-end, before next Round is constructed)

PlayerSnapshot
  name: String
  archetypeId: String            // stable id, e.g. "human", "ai_easy", "ai_medium_balanced" -- see §2a
  score: int
  bet: int
  hasBet: boolean
  trickScore: int
  hand: List<CardSnapshot>

RoundSnapshot
  trumpCard: CardSnapshot
  trumpBroken: boolean
  currentTrick: TrickSnapshot   // null between tricks (all players have played 0 cards, nobody's mid-trick)

TrickSnapshot
  trickStartPlayer: int
  currentPlayer: int
  leadingSuit: Suit             // null if trick just started, no card landed yet
  cardsPlayedBySeat: List<SeatCardPlay>   // ordered as played

SeatCardPlay
  seatIndex: int
  card: CardSnapshot

CardSnapshot
  suit: Suit
  value: CardValue
```

## 2a. Player identity: a stable id, decoupled from display names or Java identifiers

The user's own concern, and a real one: AI archetypes will likely grow
(new personalities/tiers, e.g. items 25/26) and might occasionally get
renamed or retired. If a save file's player-identity field is derived from
something that's allowed to change for cosmetic reasons — `AIPersonality`'s
own display `name` field ("Balanced"/"Bold"/"Cautious", explicitly flagged in
that class's own doc as **not yet final**, pending the user's sign-off on
real naming), or a Java enum/class name that gets refactored — a save written
today silently breaks (or worse, silently resolves to the *wrong* archetype)
the moment that cosmetic rename ships.

**Recommendation**: give every archetype (human, and each AI class+personality
combination) its own small, explicit, permanent string id, stored separately
from anything display-facing:
- Add an `id` field to `AIPersonality` itself (e.g. `"medium_balanced"`),
  set once per constant, independent of its `name` field — renaming `name`
  from "Balanced" to something else later never touches `id`.
- Give non-personality-based AI classes (`AI_Easy`, `AI_Zombie`) their own
  constant ids the same way (e.g. `"ai_easy"`, `"ai_zombie"`).
- A small static registry (e.g. `PlayerArchetypeRegistry`, id → factory
  closure that builds the right `Player` subclass/personality given a name)
  is what `fromSnapshot` actually looks up — never a `switch` on Java type
  names or personality display names.
- **Ids are append-only.** Adding a new AI is just adding a new id + registry
  entry. Retiring one: leave its id in the registry mapped to nothing, or
  simply let it go unregistered — see below for what happens then.
- **Unknown id on load** (an old save references an id that's since been
  removed, or the save is otherwise corrupt): don't try to partially
  reconstruct or silently substitute a different archetype — treat the whole
  load as failed and fall back to "no saved game," mirroring
  `SaveStore.parse()`'s existing whole-file-falls-back-to-defaults
  convention exactly (`SaveStore.java:107-129`) rather than inventing a new
  partial-recovery strategy.

This is a one-time, cheap addition now (two new small fields/constants) that
fully insulates saves from cosmetic renames later — much cheaper than
retrofitting it after the first rename actually corrupts someone's save.

## 3. Reconstruction

A `GameStateCodec` (or similarly named class) with two directions:
- `toSnapshot(Game/Round/Trick, players)` → `GameStateSnapshot` — reads the
  live object graph.
- `fromSnapshot(GameStateSnapshot)` → rebuilds fresh `Player`/`Hand`/`Deck`/
  `Round`/`Trick`-shaped state. This is **not** a live `Round`/`Trick`
  replay — it's closer to a set of constructors that take a snapshot instead
  of dealing fresh, since `Round`'s current constructor always deals and
  draws a trump card from a new shuffled `Deck`. `Round`/`Trick` will likely
  need new constructor overloads (or a small refactor) that accept
  pre-built state instead of generating it — worth flagging now since it
  touches those classes' constructors, not just new standalone code.

## 3a. The seed idea — good idea, but kept separate from this doc's reconstruction path

The user's follow-up floated seeding the RNG (the deck shuffle specifically,
but noted it could extend to future randomly-generated opponent selection
too) so state could be re-derived from a seed instead of stored directly.
Two things worth separating here:

- **For this doc's specific job (resuming an in-progress round), the seed
  doesn't end up helping**, once §1's revision above is accounted for: the
  undealt deck is already dropped entirely (nothing needs it), and the
  *dealt* cards (hands, trump, cards already played this trick) can't be
  cheaply re-derived from just "the shuffle seed" — replaying a seed back to
  the current moment would require *also* storing every prior trick's plays
  this round (who played what, in order) so the replay lands on the same
  point, which is strictly more data and more logic than just storing
  "each player's hand + cards played this trick" directly, exactly the extra
  reconstruction complexity the user is already most worried about (see their
  message above). So: recommend **not** making resume depend on seed replay.
  Storing live hands/cards directly is simpler and safer for this specific
  job.
- **The seed idea is still a good one in its own right** — just for a
  different purpose than snapshot-reconstruction: reproducible bug reports
  ("here's the seed, replay the exact same deal"), and deterministic test
  fixtures for exactly the reconstruction codec this doc is proposing (a
  seeded `Deck` makes it trivial to write a test that deals a known hand,
  instead of hand-constructing `Card` lists or fighting a real shuffle's
  randomness). It's not on `ROADMAP.md` today — added as new item 27 per the
  user's explicit request; see that file. Scope there: make `Deck`'s
  shuffle accept an injectable seed (defaulting to a freshly-generated one
  each time so today's behavior is unchanged), and record/expose whichever
  seed actually got used so it *could* be logged or surfaced later. The
  "opponent types generated" extension the user mentioned doesn't apply yet
  — AI seat assignment is hardcoded today (`Game.java`'s constructor switch),
  not randomly generated — so that half is speculative/future scope only,
  noted in the new item rather than acted on now.

## 4. Serialization format: JSON, hand-rolled vs. a library

The user specifically suggested JSON. Two ways to get there:

- **Hand-rolled writer/parser**, in the same spirit as `SaveStore`'s
  hand-rolled `Properties` read/write (no dependency, `SaveStore`'s own class
  doc already establishes this project's precedent of writing its own
  persistence code rather than pulling in a library). The snapshot shape
  above is simple enough (ints, strings, enums, nested lists of small
  records) that a ~100-150 line recursive writer + a small parser is
  realistic, and this project already has zero non-test dependencies
  (`CLAUDE.md` — only JUnit, vendored, test-only).
- **A real JSON library** (e.g. `org.json`, Gson, Jackson) — less code to
  write and maintain, but this would be the project's **first non-test
  external dependency**, and per `CLAUDE.md` there's no build tool (no
  Maven/Gradle) — adding a library means manually vendoring a jar into `lib/`
  and adding it to the `javac`/`java` classpath, same friction as JUnit today
  but for production code, not just tests.

**Recommendation: hand-rolled JSON**, matching `SaveStore`'s existing
precedent and avoiding the first production dependency for a format this
small and fully under our control. The user separately asked whether this
even needs to be JSON given `SaveStore`'s existing flat `Properties` format
already works — worth confirming why JSON specifically, not `Properties`,
for *this* data: `Properties` is a flat string-key-value map, and this
snapshot has real nesting with variable-length lists at multiple depths
(N players → each with a variable-length hand → each a 2-field card; a
trick-in-progress with 0-4 cards played so far). That's expressible in
`Properties` (synthetic indexed keys like `player.2.hand.3.suit`), but it's
more error-prone to write and parse correctly than a natural nested format,
for a part of the system whose reconstruction correctness is the main
worry (per the user's own follow-up). JSON's nesting maps directly onto the
shape in §2 with no flattening/index-key bookkeeping. Same dependency
tradeoff as item 2's open SQLite-vs-`Properties` question — worth resolving
both consistently if that item comes up around the same time.

## 5. Storage layer

A new class alongside `SaveStore` — e.g. `GameStateStore` — same pattern:
`<user.home>/.tentoone/`, atomic temp-file-then-move writes (copy
`SaveStore.save`'s exact approach), never throws out to the caller.

Per the user's own note ("perhaps multiple saved games... could be relevant
to defensively program for"): give `GameStateStore` a `slotId` parameter from
day one — e.g. `resolveGameStateFile(String slotId)`, mirroring
`SaveStore.resolveSaveFile(String profileName)`'s existing pattern exactly —
even though only one slot (`"current"`, or reusing `SaveStore.DEFAULT_PROFILE`)
is ever written today. This is the same seam `SaveStore` already left for
multi-profile (item 4): the storage-layer API is ready for multiple slots/
profiles now, so neither item 4 (profiles) nor a future "pick which saved
game to resume" UI needs a format migration later — but no slot-picker UI,
and no actual second slot, gets built in this phase.

## 6. When snapshots get written

This codebase has **no clean-shutdown hook** (confirmed directly in
`SaveStore`'s own class doc — that's why achievement saves happen at
round-end/game-end/name-submission instead of at exit). The same constraint
applies here: closing the window is `JFrame.EXIT_ON_CLOSE` (`Window.java`),
which gives no chance to run code first. So persistence must happen
continuously at safe checkpoints, not "on close."

**Revised per the user's follow-up**, refining "after every card played"
down to something cheaper but equally safe: opponents play instantly (no
user-visible pause between AI turns within a trick), so there's no need to
write a snapshot after *each* of those — only around points where the human
actually interacts, plus the natural pause points that already exist in the
UI:
- Right before the human's turn is awaited, and right after it completes —
  bet entry (`BetStepper`) and card play (`Human.playCard`) alike. This is
  the moment closest to "the app could plausibly be closed right now," and
  it's already a real wait (blocked on a click), unlike the AI turns
  sandwiched around it.
- At each trick's existing click-to-continue gate (`Human.nextTrick()` /
  `NextTrickPrompt`, `Human.java:170-177`) — this is already a deliberate
  pause in the UI, so it's free to hang a checkpoint off of, both right
  before showing it (trick just resolved) and right after (moving on).
- At round-end (`RoundSummaryPanel`'s own click-to-continue), same reasoning.

This still satisfies "capture cards as they are played" (each of the
opponents' plays before the human's next turn all landed in a single
snapshot, but a resume mid-trick never loses more than the human's own
just-completed action) while writing an order of magnitude fewer snapshots
than a per-card-per-AI-turn cadence would — a real simplification for
performance *and* for how many places in the code need a
`saveGameState()` call wired in. Practically this likely still means one
shared `saveGameState()`-style call, invoked from `Human`'s own blocking
click-loops (bet/play/nextTrick) rather than scattered through `Trick`'s
per-player loop, similar in spirit to how `Game.play()` already calls
`saveStore.save(saveData)` at its own existing checkpoints
(`Game.java:310`).

## 7. Explicitly out of scope for this phase

- The hamburger menu itself, and the "back to main menu" in-memory suspend
  flow (item 10's own UI/UX) — this phase only builds the snapshot model
  those features will call into.
- The "resume after relaunch" prompt/UX (item 14's own UX question — silent
  resume vs. explicit prompt vs. dedicated "continue game" entry point).
- Any multi-slot **UI** (picking between several in-progress saved games) —
  only the storage-layer seam for it (§5).
- Multi-profile integration (item 4) — `GameStateStore` should accept a
  profile/slot id the same way `SaveStore` already does, but wiring it to
  real profiles waits for item 4.
- Actually resuming play on a reconstructed `Game`/`Round`. `GameStateCodec.
  fromSnapshot` + `Game`'s snapshot constructor faithfully rebuild the object
  graph (players, hands, bets, the round/trick in progress), but calling
  `Round.bet()`/`Round.playRound()` on the reconstructed `Round`, or
  `Game.renderPlayers()` on the reconstructed `Game`, is not yet safe: `bet()`
  would re-run betting and clobber the restored bets, `playRound()` would
  restart its trick loop from scratch instead of resuming
  `round.getCurrentTrick()` where the snapshot left off, and `renderPlayers()`
  would re-`handler.addObject()` every already-registered player, doubling
  their per-frame tick()/render() calls. None of this is reachable today —
  nothing wires an actual resume-and-continue-playing UI yet — but whoever
  builds that UI (item 10/14) needs to make these three call sites
  resume-aware first.

## 8. Suggested sequencing (for the user to confirm, not decided here)

Two reasonable ways to fold this into `ROADMAP.md`, left for the user's
call rather than picked unilaterally:

1. Promote this as its own new roadmap item, positioned as a hard
   prerequisite for item 10 (and de-facto absorbs most of item 14's
   "what does resume need" scope, leaving item 14 as just "the app-launch
   resume UX built on top of this").
2. Fold this into item 14 directly (retitle/expand it to cover the snapshot
   model + storage layer), and make item 10 explicitly depend on item 14
   instead of being independently `ready`.

No `ROADMAP.md` edits made yet — flagging this doc's existence and the
proposed sequencing for the user's explicit go-ahead before touching that
file, consistent with how design docs have been handled for every other item
so far (`design/ai-and-polish.md`, `design/ai-v2-opponent-modeling.md`).
