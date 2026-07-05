# Design: AI & Polish (Roadmap Item 1)

Status: design-only, produced by `game-designer` per the user's request to scope
this directly before any implementation. No production code was touched to
produce this document. Everything below is grounded in the actual classes as
they exist today (`AI.java`, `AI_Easy.java`, `AI_Zombie.java`, `Player.java`,
`Human.java`, `Round.java`, `Trick.java`, `Hand.java`, `Card.java`,
`CardValue.java`, `Suit.java`, `Game.java`, `BetStepper.java`) — see inline
citations throughout.

**Revision history:** this is a third pass. Pass 2 reworked opponent-bet-aware
betting, card-counting, and RNG-tiering after the first draft; **this pass**
(per explicit instruction, likely the last design pass before implementation)
reworks opponent-bet-aware betting again into a single, more precise
mechanism (distribution-aware, trust-dampened, hand-strength-floored),
resolves the settings-surface question with an implementation decision, and
adds an off-suit extension to card-counting. Section numbers are kept stable
across all three passes (nothing renumbered) so this stays easy to diff.
The final section (§10) is an updated v1-vs-defer recommendation — this pass
meaningfully changes that recommendation for opponent-aware betting, flagged
there explicitly.

## 0. Grounding: what exists today

- A game is 10 rounds, `numCardsThisRound = 10 - roundIndex` (`Game.java`
  `numCardsThisRound()`), so hand size decreases 10 → 1. **Round 10 (the
  final round) is exactly the 1-card-per-player case** the last-round
  heuristic bug (§5) is about.
- Up to 5 players (1 `Human` + up to 4 AI, `Game`'s constructor always
  instantiates `AI_Easy` for every AI name today — this is the seam item 10's
  tier/mode-selection work and this doc's tier structure will need to change).
- `Player` is the abstract base (`bet(Suit trump)`, `playCard(...)`,
  `legalCards(...)`); `AI extends Player` adds shared strategy helpers
  (`getLosingCards`, `getWinningCards`, `getLowestValue`, `getHighestValue`,
  `countSuit`, `countTopValues`) — **note `countTopValues` is already
  available on the base `AI` class to every AI subclass, not gated behind any
  new infrastructure** (see §4, this matters for what's achievable in v1);
  `AI_Easy`/`AI_Zombie extends AI`.
- `Round.bet(int currentPlayer)` calls `player.bet(trump)` once per player,
  **in turn order starting from the round's starting player**, i.e. the same
  order that will lead the round's first trick. This loop is where
  opponent-bet-aware betting (§4) and the legality check (§3) both plug in —
  it already iterates in exactly the order needed to know "bets placed
  earlier this round" and "am I the last bettor."
- `Human.isValidBet(int bet, int maxBet)` is the one existing bet-validation
  predicate (pure static, unit-tested in `TestHumanBet.java`). Its *shape*
  (pure static predicate, unit-tested) is a good pattern to follow — but, as
  the user independently flagged, it is **not** a good structural home for a
  check that AI bettors also need: a rule shared by every bettor shouldn't
  live under a class named `Human`. §3 fixes this.
- There is **no settings/options surface anywhere in the codebase** (grepped,
  zero hits). §3 records the user's resolution for v1: a plain code-only
  config holder now, a real UI later reading/writing the same object — no
  further design work needed on this from me, noted for completeness.
- `Player.bet(Suit trump)` only receives the trump *suit*, never the trump
  indicator *card* (its rank). `Trick.play()` only gives an AI the current
  trick's cards-so-far (`cardsPlayed`), and nothing today tells a player what
  happened in a *previous* trick this round. Both are real gaps, but **only
  for genuine cross-round card-counting (§6)** — corrected from earlier
  passes: opponent-bet-aware betting (§4) turns out **not** to need either of
  these hooks, since it only needs each prior bettor's already-visible bet
  plus the reacting AI's own hand (both trivially available today). Flagged
  in §6 as new hooks to hand off, not something I've designed around by
  inventing a workaround.

---

## 1. Proposed AI tier/variant structure

New classes, alongside (never editing) `AI_Zombie`/`AI_Easy`. Each tier adds
exactly one new capability over the previous tier, so the difficulty curve is
legible and each tier can be understood as "everything the previous tier does,
plus X":

| Tier | Class | Extends | Adds over previous tier |
|---|---|---|---|
| 0 (Zombie) | `AI_Zombie` (existing, untouched) | `AI` | Baseline: flat bet (`numCards/5`), plays first legal card. No strategy at all. |
| 1 (Easy) | `AI_Easy` (existing, rounding-legality patch only — see §3) | `AI` | Hand-strength bet heuristic (`countTopValues` on trump/non-trump), `tryToWin`/`tryToLose` card play based on bet-vs-trickScore. No opponent awareness, no last-round fix, no counting. |
| 2 (Medium) | **new** `AI_Medium` | `AI_Easy` | Inherits Easy's card-play engine (`tryToWin`/`tryToLose`) unchanged (via `extends AI_Easy`, only overriding `bet()`); adds the **full** opponent-bet-aware betting mechanism (§4 — distribution-aware signal, grain-of-salt trust, self-evident guaranteed-win floor) and the last-round heuristic fix (§5). Revised this pass: the opponent-aware mechanism no longer needs to be split into a "cheap" vs. "full" version by tier — see §4's note on why it turns out to need no new engine infrastructure at all. |
| 3 (Hard) | **new** `AI_Hard` | `AI_Medium` | Adds real card-counting (§6) — own-hand-only "self-evident" guaranteed wins (already available at Medium) are upgraded to **counting-confirmed** guaranteed wins (informed by actually-observed cards, via the new engine hooks in §6.3), feeding both bet confidence and card-play. |
| 4 (Expert) | **new** `AI_Expert` | `AI_Hard` | Higher recall-capacity/recall-accuracy tier (§6.4); optional off-suit tracking (§6.5, configurable, not automatically on); **per-opponent bet-trust history** (§4.2) replacing the flat trust constant with a per-opponent trend built from this match's results so far; also the tier that reasons about bet-legality using real hand knowledge rather than the fixed round-down default (§3's "hand-aware tie-break"). |

Notes on this structure:
- Extending `AI_Easy`/`AI_Medium`/`AI_Hard` rather than re-deriving each
  tier's card-play logic from scratch is a deliberate choice: it means each
  new class only needs to override the one method it's actually changing,
  keeping the new code small and the tier relationship self-documenting in
  the class hierarchy itself. This still satisfies the "don't modify
  `AI_Easy`/`AI_Zombie`" constraint — subclassing an existing class to reuse
  its public behavior is not editing it.
- `AI_Zombie` deliberately stays a dead-end (not extended by anything) — it's
  intentionally the "no real strategy" floor, nothing should build on it.
- This 5-tier ladder (Zombie/Easy/Medium/Hard/Expert) is the structure item
  10's freeplay-tier-picker and journey-mode unlock ladder should consume —
  see §9.

**Dev-mode testing hook (short-term, ties to item 3):** separate from item
10's long-term journey-mode unlock progression, the user needs an in-game way
to swap which AI tier/personality is seated in a game *now*, for testing
purposes, before item 10 exists. This is naturally an entry in item 3's
already-planned extensible dev-settings surface (e.g. a per-seat tier/
personality picker), not a new surface of its own. The same dev-settings
surface should also expose, per opponent seat, which personality/tier is
actually running under the hood (see §7) — pure debugging/testing
visibility, not a player-facing feature. Both of these are blocked on item 3
existing at all, so sequence after it, not concurrently with it.

## 2. (folded into §1/§3)

Kept as an intentional placeholder (see the revision history note at the top)
so section numbers stay stable across all three passes.

## 3. Betting-legality rule (`isLegalBet`)

**Rule name:** *"Total bets cannot equal number of tricks."* This is the
phrasing that should appear anywhere this rule is surfaced to the player (a
settings toggle label, an in-game rejection message), not an internal/
technical description.

**The rule itself:** the sum of all bets placed in a round must never exactly
equal that round's card count — at least one player must miss their bet.
Standard trick-taking convention (this game already resembles "Oh Hell"/
"Nomination Whist") is that only the *last* bettor is constrained by this,
since the total isn't known until everyone before them has bet — every
earlier bettor's choice is unconstrained. In this codebase, "last bettor" =
the last player in `Round.bet()`'s loop, i.e. the player right before the
round's starting player in turn order (the analogue of "the dealer" in the
classic rule).

**Exact check.** This must **not** be implemented by calling into
`Human.isValidBet` — a rule shared by every bettor, human or AI, shouldn't be
namespaced under a `Human`-specific method. Proposed home is `Round.java`,
alongside its existing static rule helpers (`isHigher`,
`determineTrickWinner`) — `Round` already owns `numCards` and orchestrates
the betting loop, so it's the natural shared home:

```java
// Proposed home: Round.java. Range-checking is inlined here rather than
// delegating to Human.isValidBet, since this check must serve every
// bettor, not just Human.
static boolean isLegalBet(int bet, int maxBet, int sumOfPriorBets,
                          int numCardsThisRound, boolean isLastBettor,
                          boolean totalBetsCannotEqualTricks) {
    if (bet < 0 || bet > maxBet) {
        return false;
    }
    if (!totalBetsCannotEqualTricks || !isLastBettor) {
        return true;
    }
    int forbiddenBet = numCardsThisRound - sumOfPriorBets;
    return bet != forbiddenBet;
}
```

Note `forbiddenBet` is only ever actually reachable when it falls inside
`[0, maxBet]` — if prior bets already overshoot or undershoot the card count
enough that no legal remaining bet could hit it exactly, the check is
naturally a no-op (every bet stays legal), which is correct and requires no
special-casing.

**`Human.isValidBet`'s fate:** once `Round.isLegalBet` exists and subsumes
its range check, `Human.isValidBet` should likely be retired in favor of
`Human.bet()` calling the new shared method directly — not kept alongside it
as a second, redundant check. The user is fine leaving the exact mechanics of
that refactor (rename vs. delete vs. delegate) to `senior-developer` at
implementation time.

**Where it plugs in:** `Round.bet(int currentPlayer)`'s loop already knows
`numCards` (a field), the loop index (`i == numPlayers() - 1` → last bettor),
and can accumulate `sumOfPriorBets` from each `player.getBet()` as it goes.
The gap is that `Player.bet(Suit trump)`'s signature doesn't currently expose
any of `sumOfPriorBets`/`isLastBettor`/`numCardsThisRound`/the setting toggle
to the bettor — **this is a signature change** (mirroring how `playCard`
already threads contextual state like `cardsPlayed`/`leading`/`trumpBroken`
through its parameters), not something I'm implementing, but it's a small,
well-precedented one. Hand off to `senior-developer`.

**Settings framework — resolved for v1, noted here for completeness (not
designed further, this is an implementation decision the user already
made):** rather than building a real Settings/Options screen now, v1 builds
a plain config-holder object/class (e.g. a `GameSettings`-style plain data
holder, matching this codebase's existing `SaveData` convention) holding
`totalBetsCannotEqualTricks` (default `true`) and any future settings,
exposed only in code for now — no UI. A real Settings screen, whenever it's
designed, reads/writes that same holder rather than a parallel mechanism.
Testing until then is just flipping the field's value directly in code/
tests. This resolves what earlier passes flagged as an open dependency/gap
(a real Settings surface not existing anywhere) — that gap is now
resolved-for-v1 by this decision, not still open.

**UX gap this rule creates, worth flagging (priority 5):** `BetStepper`'s
increment/decrement already clamp to `[0, maxBet]`, so today `Human`'s
`isValidBet` false-branch is dead code — the UI physically can't produce an
out-of-range value. The new forbidden value is different: it's a single
*interior* value the stepper can absolutely still produce. If the human is
the last bettor and clicks "Bet" on the forbidden value, this must not
silently no-op — it needs real feedback, mirroring the existing
`IllegalPlayFeedback` pattern used for illegal card plays, e.g. "Total bets
can't equal {N} — pick a different value." This is new UX, not just backend
logic, flagging so it isn't missed. (This UX affordance is independent of
the settings-framework resolution above — it's needed regardless of whether
the toggle lives in code or a real UI.)

**Rounding/tie-break behavior for `AI_Easy`/`AI_Zombie` (the explicitly
sanctioned exception):**

Both AIs' bet computations currently produce a value with no retained
fractional signal to break ties by (`AI_Easy`'s formula is an integer sum of
integer counts; `AI_Zombie`'s is integer division) — so I'm specifying one
concrete, deterministic rule rather than leaving it ambiguous:

1. Compute the natural bet exactly as today (unchanged strategy).
2. If this AI is the last bettor, the setting is on, and the natural bet
   equals the forbidden value `F`:
   - If `F == 0` (can't round down), bump up to `1`.
   - Else if `F == maxBet` (can't round up), bump down to `maxBet - 1`.
   - Otherwise, **round down** to `F - 1`. (Conservative default: an Easy/
     Zombie-tier AI shouldn't be reasoning about *which* direction is
     better — that reasoning is exactly what distinguishes the Expert tier,
     see below — so the simplest, safest default is to bet slightly under
     its own estimate rather than over.)

This is scoped as a pure rounding/legality patch to `AI_Easy`/`AI_Zombie`'s
bet *output*, not a change to either's hand-strength *reasoning* — consistent
with the user's explicit exception (compliance with a corrected core rule,
not a strategy change).

**Hand-aware tie-break for `AI_Expert` (§1's top tier):** instead of the
fixed round-down default, `AI_Expert` should decide the direction using real
hand knowledge — e.g. if forced off a natural bet by holding the trump Ace —
**the literal highest card in the game, a guaranteed trick winner whenever
trump is legally in play in that trick, not merely a strong one** — round
**up** rather than down, since betting artificially low despite a truly
unbeatable card is its own kind of bad play. This is the one genuinely
deterministic case in this whole design (see §4's guaranteed-win floor,
which formalizes exactly which cards qualify as unconditionally certain, and
§4.6 on why almost nothing else in this document should be read as
certainty).

**Verification:** propose a new `TestBetLegality.java` (or similar, named to
reflect wherever `isLegalBet` actually ends up living — not
`TestHumanBet.java`), mirroring `TestHumanBet.java`'s exact style — one
`@Test` per case (`sum of priors + bet != numCards is legal`, `bet ==
forbiddenBet when isLastBettor is illegal`, `same bet is legal when NOT last
bettor`, `toggle off makes everything legal again`, `forbidden value outside
[0,maxBet] is a no-op`). This project has no existing check for bet legality
at all today — flagging that gap explicitly per this role's priority 3,
since it doesn't exist yet for me to confirm passes.

## 4. Opponent-bet-aware betting

Reworked a second time this pass. The previous pass's two-signal design
("crowding" + "concentration") is replaced by **one unified, distribution-
aware signal**, because the user's own examples showed the aggregate-sum-
based "crowding" signal could misfire (see §4.1), and because a hand-strength
override and a trust/grain-of-salt dampener are both now first-class parts
of the formula rather than afterthoughts.

**Important correction from the previous pass:** this entire mechanism turns
out to need **no new engine infrastructure** beyond the bet-signature change
already required for §3's legality rule (to thread `sumOfPriorBets`/prior
bets through) — it only needs each prior bettor's already-known bet, plus
the reacting AI's own hand via the base `AI` class's existing
`countTopValues` helper. The previous pass incorrectly gated the "full"
version of this mechanism behind Hard/Expert on the assumption it needed
card-counting infrastructure; it doesn't. This is reflected in §1's table
(now on `AI_Medium`) and in §10's updated recommendation.

### 4.1 The signal: per-bet surprise, not aggregate sum

The previous pass's "crowding" signal (a flat -1 whenever the *sum* of prior
bets crowded the round) doesn't hold up against the user's own examples:

- Round of 4, 5 players, prior bets **1, 1, 1, 1** → the user would still bet
  their natural amount (e.g. 1). Every bet here is unremarkable (close to
  what's statistically expected), even though the *sum* (4) already equals
  the round's card count. A sum-based signal would incorrectly treat this as
  "crowded" and shave a trick off anyway.
- Round of 4, 5 players, prior bets **1, 0, 0, 4** → the user would bet 0
  (assuming a weak/no-trump hand). Here it's not the sum that matters, it's
  that *one specific bet* (4) is a wildly extreme outlier.

This is exactly why the signal must be **per-bet, not aggregate**:

- `expectedPerPlayer = numCardsThisRound / numPlayers` — the statistically
  "even" bet if the round's tricks were split evenly among every seat, e.g.
  a round of 4 cards among 5 players → `expectedPerPlayer = 0.8`.
- For each prior bet `b_i`: `surprise_i = max(0, b_i - expectedPerPlayer)`
  (only *over*-betting relative to expectation is a "they're claiming
  outsized strength" signal — under-betting isn't).
- `maxSurprise = numCardsThisRound - expectedPerPlayer` (the largest possible
  surprise: betting the literal maximum, `numCardsThisRound`). If
  `maxSurprise <= 0` (a degenerate edge case, e.g. a 1-player game), treat
  `signalStrength` as 0 rather than dividing by zero or a negative number.
- `confidence_i = clamp(surprise_i / maxSurprise, 0, 1)` per prior bet.
- `signalStrength = max_i(confidence_i)` — **the single most extreme prior
  bet dominates the read**, not an average. This is what makes the mechanism
  distribution-aware: 1,1,1,1 produces four small `confidence` values (each
  ≈0.06 in the example above) and a small `signalStrength`; 1,0,0,4 produces
  one `confidence` of 1.0 (from the lone 4) and `signalStrength = 1.0`,
  regardless of the other three bets being unremarkable.

### 4.2 Grain of salt: a trust dampener, never full belief

Per the user's explicit instruction, no opponent-bet signal should ever be
treated as ground truth — "the real information you have is your hand, and
that needs to be the main factor of your bet." A `trust` factor in `[0, 1]`
multiplies the raw signal before it's allowed to move the bet at all:

- **`AI_Medium`/`AI_Hard`**: a flat `baseTrust` constant, proposed default
  **0.8** — not 1.0 (never full belief, per the user's instruction) but not
  so low it erases a genuinely extreme signal either (see §4.4's worked
  examples, which need this specific value to reproduce the user's own
  stated outcomes). Exposed as a tunable personality/tier dial (see §7),
  since the user expects real playtesting/tuning here.
- **`AI_Expert`**: replaces the flat constant with a **per-opponent trust
  history** built from this match's results so far — has this specific
  opponent generally hit their bets, or trended over/under across the
  rounds played so far this game? A simple proposed shape: track, per
  opponent, a running average of `|actualTrickScore - bet|` across
  completed rounds this match, and map it into a trust range (proposed
  **[0.4, 1.0]**, not `[0, 1]` — even a habitually-inaccurate bettor's
  signal is never fully zeroed out, matching the "grain of salt always"
  framing rather than swinging to the opposite extreme of total dismissal).
  A consistently-accurate opponent's bets trend toward `1.0` trust; a
  consistently-off opponent's trend toward the `0.4` floor. This uses
  `confidence_i * trust_i` per bettor (rather than the flat `baseTrust`) when
  computing `signalStrength = max_i(confidence_i * trust_i)` for Expert.
  **This is new, genuinely heavier scope** (needs new intra-match,
  per-opponent state tracking that doesn't exist anywhere in this codebase
  today) — flagged again in §10 as a defer candidate.

### 4.3 Hand-strength override: guaranteed wins are never talked down

Per the user's example — holding the trump Ace and King, an opponent's huge
bet shouldn't talk the AI down from betting on both, since both are
*unconditionally* certain wins, not just a "strong read." This needs to be a
hard floor on the final bet, not just a softer scaling factor (§4.4 shows
why: scaling alone isn't suf2ficient to protect the bet in this exact
scenario).

**`selfEvidentGuaranteedWins(hand, trump)`** — computable from the AI's own
hand alone, no counting/observation needed at all:

```
held = trump-suited cards in hand, sorted ascending by value
count = 0
expectedNext = ACE
for v in held, highest-to-lowest:
    if v == expectedNext:
        count++
        expectedNext = the value one rank below v
    else:
        break   // first gap in the run-to-the-top stops counting
return count
```

This counts held trump cards that form an **unbroken run down from the
Ace**, entirely within the AI's own hand — e.g. holding the Ace and King
counts both (2): the King can only be beaten by the Ace, and the AI already
knows the Ace isn't anywhere else (only one exists, and it's in this AI's
own hand). Holding only the King (no Ace) counts 0 — the Ace *could* be
elsewhere, so the King isn't unconditionally safe.

Available from `AI_Medium` (needs nothing beyond the AI's own hand — no new
engine hooks). `AI_Hard`/`AI_Expert` upgrade this via real card-counting
(§6): a held trump card *outside* the self-evident run can still become
guaranteed if the AI has actually observed (via §6's hooks) that every card
in its threshold/gap set (§6.1) has already been played — a strict superset
of the self-evident rule, since counting can only add more confirmed-safe
cards, never fewer.

**The floor itself:** `adjustedBet = max(guaranteedWins, naturalBet - concentrationAdjustment)`
— see §4.4 for `concentrationAdjustment`'s definition. No opponent signal, no
matter how strong, can push the final bet below `guaranteedWins`.

### 4.4 Putting it together, with worked examples

Full pipeline, in order:

1. `naturalBet` — the AI's own hand-strength read (inherited `AI_Easy`-style
   heuristic for Medium; upgraded by real counting for Hard/Expert, §6).
2. `ownTrumpStrength = countTopValues(hand.getCardsOfSuit(trump), K) / K`
   (`K` e.g. 3); `reactionScale = 1 - ownTrumpStrength` — a *continuous*
   dampener (not the hard floor) reflecting that a hand with real, if not
   literally guaranteed, trump strength should be less rattled by an
   opponent's big bet than an empty-handed one.
3. `signalStrength` from §4.1, `trust` from §4.2.
4. `rawSwing = round(signalStrength * trust * reactionScale * naturalBet)`
   — deliberately **not capped at 1**; a maximal, fully-trusted signal
   against a trump-empty hand can shave the AI's *entire* natural bet.
5. `concentrationAdjustment = round(rawSwing * personalityMultiplier)` (risk
   tolerance, §4.5).
6. `guaranteedWins` from §4.3.
7. `adjustedBet = max(guaranteedWins, naturalBet - concentrationAdjustment)`.
8. (Downstream, unchanged from earlier passes) §5's last-round override, then
   §3's legality tie-break, are applied after this, each operating on the
   previous stage's output.

**Worked example 1 — "1,1,1,1" (user's own example, unremarkable distribution):**
Round of 4, 5 players, `expectedPerPlayer = 0.8`. This AI is the 5th bettor,
`naturalBet = 1`, holds no trump (`reactionScale = 1`). Each prior bet is 1:
`surprise = 1 - 0.8 = 0.2`, `maxSurprise = 3.2`, `confidence = 0.0625` each.
`signalStrength = 0.0625`. With `trust = 0.8`, neutral personality
(`personalityMultiplier = 1`): `rawSwing = round(0.0625 * 0.8 * 1 * 1) = round(0.05) = 0`.
`concentrationAdjustment = 0`. `guaranteedWins = 0`.
`adjustedBet = max(0, 1 - 0) = 1`. **Matches "still bet 1."**

**Worked example 2 — "1,0,0,4" (user's own example, one extreme outlier):**
Same round shape. This AI's `naturalBet = 2`, holds no trump
(`reactionScale = 1`). Prior bets 1, 0, 0 each produce small/zero
`confidence`; the bet of 4 produces `confidence = (4-0.8)/3.2 = 1.0`.
`signalStrength = 1.0` (the max, dominated by the one outlier).
`rawSwing = round(1.0 * 0.8 * 1 * 2) = round(1.6) = 2`.
`concentrationAdjustment = 2`. `guaranteedWins = 0`.
`adjustedBet = max(0, 2 - 2) = 0`. **Matches "bet 0."**

**Worked example 3 — the guaranteed-win floor doing real work (user's
Ace+King example):** Same round shape. This AI holds the trump Ace and King
(`selfEvidentGuaranteedWins = 2`), `naturalBet = 2`. An opponent bets the max
(4): `signalStrength = 1.0` as above. `ownTrumpStrength = countTopValues`
over top-3 trump values `= 2/3 ≈ 0.67` (holds 2 of the top 3) →
`reactionScale ≈ 0.33`. `rawSwing = round(1.0 * 0.8 * 0.33 * 2) = round(0.53) = 1`.
`concentrationAdjustment = 1`. Without the floor, this would give
`adjustedBet = max(0, 2 - 1) = 1` — **wrong**, since the user was explicit
they'd still bet 2. With the floor: `adjustedBet = max(guaranteedWins=2, 2-1=1) = 2`.
**Matches "of course I'm still going to bet 2,"** and demonstrates why
`guaranteedWins` needs to be a hard floor, not merely folded into
`reactionScale`'s continuous dampening — the continuous dampener alone
(`reactionScale`) isn't quite strong enough on its own in this exact
scenario to fully protect the bet; the explicit floor is what closes the
gap.

### 4.5 Risk tolerance: complement, not substitute

**Recommendation, unchanged from the previous pass: both risk tolerance and
this opponent-aware mechanism are needed, as separate, orthogonal axes.**
Risk tolerance (§7) is a static, context-blind personality trait (baseline
appetite for risk every round); this mechanism is dynamic and
context-sensitive (only reacts when there's something to react to). Wired
together via `personalityMultiplier` in §4.4 step 5: `riskTolerance` in
`[0, 1]`, `0.5` neutral; bold personalities (near 1) dampen the swing toward
`0.5×`, cautious personalities (near 0) amplify it toward `1.5×`;
`personalityMultiplier = 1 + (0.5 - riskTolerance)`. Risk tolerance scales
the *magnitude* of a reaction §4.1's signal *detects*; neither substitutes
for the other.

### 4.6 Betting remains probabilistic — the guaranteed-win floor is the one exception

Per the user's explicit correction: nothing in this design (a strong hand,
holding the lead, an opponent's bet, this mechanism's whole signal) should
be read as *certainty* about the round's outcome, **except** the narrow,
formally-defined `guaranteedWins` count in §4.3 — which is a genuine,
rules-grounded certainty (per `Round.isHigher`, nothing can ever beat a
self-evidently-guaranteed card when it's played), not a heuristic dressed up
as one. Everything else in this design — `naturalBet`, `reactionScale`,
`signalStrength`, even a "strong hand" or leader-position read (§5) — is a
soft, revisable estimate about a round that remains genuinely uncertain
until it's actually played out. Even a leader holding a strong non-trump
card can still lose a trick (e.g. if every other player happens to also
hold — and is forced to follow with — the same led suit, denying anyone the
chance/need to trump it, is not itself a losing scenario for the leader, but
other configurations of who-holds-what absolutely can be) — the mechanisms
in this document should never be implemented or described in a way that
implies otherwise.

### 4.7 Tier assignment (updated)

Per §1's table: `AI_Medium` gets the **entire** mechanism above (§4.1-§4.4,
§4.6) except the per-opponent trust upgrade — this was previously split
across Medium/Hard/Expert on the mistaken assumption it needed
card-counting infrastructure; it doesn't, since it only needs the AI's own
hand plus already-visible prior bets. `AI_Hard`/`AI_Expert` upgrade
`guaranteedWins` via real counting (§6). `AI_Expert` additionally gets the
per-opponent trust history (§4.2) in place of the flat `baseTrust`, and
optional off-suit tracking (§6.5). See §10 for how this changes the v1/defer
recommendation.

## 5. Last-round betting heuristic fix

**Confirmed approved as-is by the user** — no changes requested to this
section's scope or reasoning across any pass.

**The bug, precisely:** in the final round (`numCardsThisRound == 1`), every
player holds exactly one card. `AI_Easy`'s `countTopValues`-based formula
treats any held Jack-or-above non-trump card as "high," contributing to a bet
of 1 regardless of the AI's position in turn order. This overvalues a
non-trump card, because **with one card per hand, a non-trump card can only
ever win the trick if the suit it belongs to is the suit that actually gets
led** — and whether that happens is entirely outside a non-leading player's
control (everyone plays their one forced card regardless of what's led, so
"following suit" isn't a choice here). A high non-trump card held by the
*leader*, by contrast, guarantees that suit gets led, so it wins unless
someone else's single card happens to be trump.

**Concrete rule** (scoped precisely to `numCardsThisRound == 1`, not
generalized further):
- If the AI's one card is **trump**: bet 1, regardless of position (trump
  beats any non-trump card played by anyone; the only way to lose is a
  higher trump elsewhere, a comparatively rare risk — a probabilistic edge,
  not the §4.3 guaranteed-win case, since another player could hold a higher
  trump card).
- If the AI's one card is **non-trump** and the AI is the round's starting
  player (i.e. `Round.bet()`'s first bettor this round — the same player who
  will lead the round's single trick): bet 1 if the card is high enough to be
  a near-certain winner among that suit (reuse `AI_Easy`'s existing
  high-card threshold logic for this — no new numeric threshold invented).
- If the AI's one card is non-trump and the AI is **not** the starting
  player: bet 0, regardless of the card's rank — its win condition depends
  on a suit-match it has no control over, on top of no trump being played.

**Where "starting player" comes from:** `Round.bet(int currentPlayer)`'s
first loop iteration bets exactly the round's starting player, who is also
who leads the round's only trick — so "is this AI the first bettor this
round" is the exact same condition as "will this AI lead," decidable from
the same signature change already needed for §3/§4 (an `isFirstBettor`-style
flag, or simply `i == 0` inside `Round.bet()`'s existing loop, threaded down
the same way).

**Why scoped only to the 1-card round:** the same reasoning technically
generalizes to any round size, but the roadmap's bug report was specifically
about the 1-card case, and hand sizes above 1 already have `AI_Easy`'s
existing win/loss trick-by-trick strategy doing real work across multiple
tricks, which dilutes a single bad high-card assumption rather than deciding
the whole round on it. The user agreed the final round feels meaningfully
different as a player and did not ask for this to be widened — logged here
as a noticed-but-not-acted-on idea for `AI_Hard`/`AI_Expert` at most, not a
change to this fix.

## 6. Card-counting tiered AI design

### 6.1 What's actually tracked: threshold cards + gap-fillers

The user's own play pattern: they track cards *higher than their highest
held trump* — e.g. holding trump 9 means watching for 10/J/Q/K/A of trump,
since any of those beats the 9. Additionally, if they hold a **gap** in
their own trump cards — e.g. holding trump 9 *and* trump 7, a gap at 8 —
they also specifically track the gap card (8 of trump), since whether it's
still out matters for whether the 7 (not the 9) can still be beaten by
something in between.

Formalized: for a hand's held trump values sorted ascending
`v_1 < v_2 < ... < v_k` (`v_k` = highest held trump), the tracked set is:

- **Threshold set:** every trump value strictly above `v_k` (the highest
  held card) — these are the only cards that can beat the AI's best trump.
- **Gap sets:** for each consecutive pair `(v_i, v_{i+1})`, every trump value
  strictly between them — these matter for whether `v_i` specifically (not
  the AI's best card) can still be beaten by something.

**Worked example:** holding trump 9 and trump 7. Tracked set =
`{10, J, Q, K, A of trump}` (threshold, above the 9) ∪ `{8 of trump}` (gap,
between 7 and 9). This is a finite, exactly-computable set from the AI's own
hand at bet time (bounded by at most 12 values even in the worst case of
holding only a single low trump card) — tractable to track precisely rather
than approximate, and it matches the user's two-part mental model exactly.

As the round's tricks are played, the AI's own held trump cards get played
one at a time; the tracked set should be recomputed relative to whichever
trump cards remain in the AI's hand at that point in the round. Note this
same threshold/gap structure is also exactly what §4.3's
`selfEvidentGuaranteedWins` computes over (a threshold/gap set that's
entirely self-held rather than externally observed) — the counting-tier
upgrade to `guaranteedWins` is precisely "the same tracked set, but confirmed
exhausted by observation instead of by already holding it."

### 6.2 Sources of information

Three sources feed the tracked set, in order of certainty:
1. **The AI's own hand** — trivially known, no uncertainty.
2. **The trump indicator card** (`Round`'s `trumpCard`, drawn from the deck
   and rendered face-up all round via `Round.renderTrumpCard()`). This is
   public information visible to every player from the start of the round —
   if it happens to be a value in the AI's tracked set, that's resolved with
   zero uncertainty from turn 1.
3. **Trump cards played in completed tricks so far this round**, across all
   players (not just the current trick-in-progress up to the AI's own turn).

### 6.3 Two real engine gaps this exposes (specify-and-hand-off, not implemented here)

1. `Player.bet(Suit trump)` only exposes the trump *suit*, never the trump
   indicator card's *rank*. Source 2 above needs the actual `Card`, so this
   signature needs to become something like `bet(Card trumpIndicator, ...)`
   (subsuming the plain `Suit` via `trumpIndicator.getSuit()`) — combine with
   §3/§4/§5's already-needed signature changes into one coherent new
   signature rather than three separate follow-up changes.
2. Nothing today tells a player what happened in a trick *after* their own
   turn in it, or in a *previous* trick this round. Source 3 above needs a new
   lifecycle hook: propose `Player.onTrickComplete(List<Card> cardsPlayed, Suit trump)`
   (default no-op on `Player`, overridden only by counting-AI variants),
   called once per trick by `Round.playRound()` right after it determines the
   trick's winner (it already has the full `cardsPlayed` list there).

Given both are needed, the counting AI's own `bet()` override is a natural
place to *reset* its per-round tracked-set memory (bet() fires exactly once
per round, before any of that round's tricks) — no additional "round start"
hook is needed beyond what §3/§4/§5 already require.

### 6.4 RNG-tiering: two independent dials, not one

**How many cards/facts an AI attempts to track at all** is roughly as
important as how accurately it recalls each one — "the ability to recall 52
cards with perfect accuracy vs. 2 cards with 50% accuracy is a huge
difference." Two crossed, independent axes:

- **`recallCapacity`** (integer): how many entries from §6.1's full tracked
  set the AI actually *attempts* to track at all. If the full tracked set
  for the current hand is larger than `recallCapacity`, the AI only tracks
  the `recallCapacity` entries **closest in value to its own held trump
  cards**, dropping the rest entirely (not tracked at any accuracy — they
  simply aren't attempted).
- **`recallAccuracy`** (`[0.0, 1.0]`): for each entry the AI *does* attempt to
  track, the probability that a given sighting (via the trump-indicator at
  bet time, or `onTrickComplete` mid-round) is correctly registered. Rolled
  once per sighting; a failed roll leaves that entry's status unresolved in
  the AI's internal model (still "possibly out there") even though the real
  game state has changed.

These are deliberately **not collapsed into one composite number** — a
personality can be high-capacity/low-accuracy or low-capacity/high-accuracy,
and those should feel like genuinely different failure modes to play
against.

Proposed tier defaults (illustrative, tunable during implementation/
playtesting, not final):

| Tier | `recallCapacity` | `recallAccuracy` |
|---|---|---|
| `AI_Hard` | 3 (tracks only the few most locally-relevant entries) | 0.75 |
| `AI_Expert` | 8 (effectively "most of what matters" for typical hand sizes) | 0.95 |

### 6.5 Optional off-suit tracking (`AI_Expert` extension, configurable)

New this pass, per the user's example: "if I hold the King of Spades
(off-suit), I might watch for the Ace of Spades" — but this is explicitly
"much less useful information anyway, especially in later rounds," and
should be skipped entirely when the AI's own card in that suit is too low to
matter ("if I only have a 6 of Spades as my highest Spades, I am never going
to track the higher Spades").

- **Same mechanism as §6.1**, applied to the AI's highest-held card in each
  non-trump suit, not just trump.
- **Relevance gate:** only track a given non-trump suit if the AI's highest
  held card in that suit meets or exceeds a configurable rank cutoff
  (`offSuitRelevanceThreshold`, proposed default `TEN`) — below that, skip
  the suit entirely, matching the "never track higher Spades with only a 6"
  example.
- **Weaker signal:** off-suit sightings use their own, lower accuracy
  multiplier (`offSuitAccuracyMultiplier`, proposed default `0.5`, applied on
  top of the tier's `recallAccuracy`) — off-suit information is inherently
  weaker than trump-tracking, since an off-suit card can be beaten by *any*
  trump regardless of rank, not just a higher card in the same suit.
- **Configurable, not hardcoded on:** a boolean `offSuitTrackingEnabled`
  dial, default **off** even for `AI_Expert` — per the user's explicit
  request, given how much tuning/playtesting is expected across these
  variants, this is a knob (personality- or tier-level), not a fixed
  behavior.

This is an extension of §6's already-flagged engine gaps (§6.3) — it doesn't
introduce new infrastructure beyond what trump-counting already needs, just
applies the same tracked-set machinery to additional suits.

## 7. Named AI personalities

**Confirmed loved as-is by the user across passes — no changes to the core
concept.** This pass adds new dials to the config record (below) reflecting
§4's grain-of-salt trust factor and §6.5's off-suit tracking.

Rather than one class per name (an unbounded, hard-to-maintain 1:1 mapping),
personalities are a **thin flavor layer** over the tier classes in §1: each
name carries a small config bundle (display name + numeric tuning knobs), and
the tier class (`AI_Medium`/`AI_Hard`/`AI_Expert`) takes that config as a
constructor parameter rather than being hard-coded. This keeps the class
count at the 5 tiers from §1 while still giving every named AI a genuinely
distinct feel.

**Confirmed correct as designed (no change needed):** each tier sets
sensible *default* values for all the dials below; a named personality's
config *overrides* whichever defaults it wants to differ on, rather than
every personality needing to specify every field from scratch.

Proposed config shape (illustrative, not a final list — naming is a matter
of taste I'd want the user's sign-off on):

```java
record AIPersonality(
    String name,
    int tier,
    double riskTolerance,             // §4.5
    double opponentBetTrust,          // §4.2 -- baseTrust; ignored (overridden by
                                       // per-opponent history) at Expert tier
    int recallCapacity,               // §6.4
    double recallAccuracy,            // §6.4
    boolean offSuitTrackingEnabled,   // §6.5
    double offSuitAccuracyMultiplier, // §6.5
    int offSuitRelevanceThreshold     // §6.5, a CardValue rank cutoff
) {}
```

- `tier` selects which class gets instantiated (§1).
- `riskTolerance` feeds §4.5's `personalityMultiplier`.
- `opponentBetTrust` feeds §4.2's `baseTrust` for Medium/Hard (Expert
  computes its own per-opponent value instead, ignoring this field).
- `recallCapacity`/`recallAccuracy` feed §6.4 for Hard/Expert-tier
  personalities (ignored below that tier) — e.g. a "Count-y Carl" (high
  capacity, so-so accuracy) vs. a "Sharp-Eyed Sal" (low capacity,
  near-perfect) are both plausible personalities at the same tier with
  genuinely different feels.
- `offSuitTrackingEnabled`/`offSuitAccuracyMultiplier`/
  `offSuitRelevanceThreshold` feed §6.5, Expert-tier only, off by default.

This directly satisfies "each with an associated distinct play style" without
requiring a new strategy class per name — the tier class supplies the
mechanism, the personality config supplies the flavor.

**Dev-mode connective note:** as flagged in §1, item 3's dev-settings surface
should expose which personality/tier is actually running under the hood for
each opponent seat, for debugging/testing visibility — this is purely a
testing aid, not a player-facing reveal, and is blocked on item 3 existing.

## 8. Not scoped here

**ML stretch idea:** the user floated eventually making AI strategy fully
ML-driven ("find the optimal strategy"). Raising it here per the roadmap's
instruction, deliberately not scoped further — this is a genuinely separate
project and premature to plan against a tier ladder that doesn't exist yet.
The user is separately logging this as its own explicit `ROADMAP.md` item so
it isn't lost — no action needed from me on this.

**Unconfirmed candidates — need explicit user confirmation, not assumed:**
- **Opponent card-count display** (showing how many cards each opponent has
  left) — not designed here one way or the other.
- **Play animations** — same; no design produced.

## 9. Feeding item 10 (difficulty tiers / journey mode)

§1's 5-tier ladder (Zombie → Easy → Medium → Hard → Expert) is exactly the
ordered structure item 10 needs:
- **Freeplay mode**: a straightforward tier picker over these 5 classes (plus
  whichever named personalities from §7 get attached to each tier).
- **Journey mode**: the same 5 tiers double as the unlock ladder verbatim
  (beat Zombie N times → unlock Easy → beat Easy N times → unlock Medium →
  etc.) — item 10 can consume this ladder directly rather than inventing its
  own.
- The dev-mode per-seat tier/personality swap (§1, §7) is explicitly a
  short-term testing aid, not a substitute for item 10's real, persistent
  unlock progression.

Item 10 still owns its own open questions (unlock-count `N`, mode-selection
UI, persistence of unlock progress) — not scoped here.

## 10. Scope recommendation: what's realistic for a first pass

Updated this pass — the opponent-aware betting mechanism's scope changed
significantly (lighter than previously thought), and one genuinely new,
heavier ask was added (per-opponent trust history). Flagging both explicitly
per instruction, since `project-manager` will use this section plus this
round's feedback to set final v1 scope.

**Solid for v1 (self-contained, no open design questions left, small/
well-precedented engine changes):**
- §1's 5-class tier scaffolding — foundational, everything else depends on
  it existing.
- §3's bet-legality rule itself (`isLegalBet`, the rounding/tie-break fix for
  `AI_Easy`/`AI_Zombie`), **plus its now-resolved settings framework** (a
  plain code-only config holder, no UI decision blocking it).
- §5's last-round fix — small, already approved, no open questions.
- **§4's entire opponent-aware betting mechanism for `AI_Medium`**
  (distribution-aware signal, flat-`baseTrust` grain-of-salt dampener,
  self-evident guaranteed-win floor, risk-tolerance multiplier) — **upgraded
  from the previous pass's recommendation**: this was previously split, with
  the "full" version deferred behind assumed card-counting infrastructure.
  That assumption was wrong (see §4's note) — the whole mechanism only needs
  the AI's own hand and already-visible prior bets, both available today
  with no new engine hooks beyond the bet-signature change §3 already needs.
  I'd now recommend this ship in the same pass as the tier scaffolding, not
  deferred.
- §7's personality config layer, including this pass's new
  `opponentBetTrust` field (still no counting-infrastructure dependency).

**I'd recommend considering for a later pass, not this one:**
- All of §6 (threshold/gap card-counting via the new `onTrickComplete`/
  trump-indicator-rank engine hooks, the two-axis recall dial, and this
  pass's new §6.5 off-suit extension) — unchanged from the previous pass's
  read, still the heaviest, most infrastructure-dependent piece, now
  slightly larger in scope with the off-suit add-on. The counting-upgraded
  (non-self-evident) `guaranteedWins` detection referenced in §4.3 is
  bundled with this, since it depends on the same hooks.
- **§4.2's Expert-tier per-opponent bet-trust history — new this pass, and
  genuinely heavier than the rest of §4.** It needs new intra-match,
  per-opponent statistics tracking (running bet-accuracy per opponent across
  the rounds played so far) that has no precedent anywhere in this
  codebase — the closest existing thing, `SaveData`, is cross-game
  persistent stats, not intra-match per-opponent tracking, so this isn't a
  small extension of existing state. Recommend bundling this with the
  Hard/Expert counting-tier follow-up work rather than v1, even though the
  rest of §4 (Medium-tier, flat-trust version) is now recommended for v1.
- The dev-mode AI-swap/personality-visibility hooks (§1, §7) — genuinely
  useful but strictly sequenced after item 3 exists.

This is a recommendation, not a decision — leaving the actual `ROADMAP.md`
scoping call to the user/PM.
