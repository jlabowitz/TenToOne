# Design: AI v2 — Opponent-Modeling & Endgame Heuristics (Roadmap Item 2)

Status: **design-only**, produced by `game-designer` per `ROADMAP.md` item 2's
routing (`game-designer` → `senior-developer`). No production code was
touched to produce this document, and none should be until the user has
reviewed it — same review-only convention as `design/ai-and-polish.md`
(item 1's design doc), which this doc is explicitly *not* reopening (see §0.3
and §4). Everything below is grounded in the actual classes as they exist
**today, post-item-1** (`AI.java`, `AI_Easy.java`, `AI_Medium.java`,
`AIPersonality.java`, `Player.java`, `Round.java`, `Trick.java`, `Hand.java`,
`Card.java`, `CardValue.java`, `Game.java`) — see inline citations
throughout.

**Revision history:** this is the second pass. Pass 1 (produced 2026-07-05)
covered Buckets A-D as originally scoped from `ROADMAP.md` item 2's triage.
**This pass**, driven by the user's detailed section-by-section feedback on
pass 1, makes the following changes: clarifies exactly what `legalCards`/
`winners`/`losers` range over, since pass 1 left this ambiguous (§0.2, §2.1);
adds a new suit-void/"suits open" strategy concept spanning both card play
(§2.7) and betting (§4.4), grounded in a new shared framing (§0.6), none of
which existed in pass 1; gives §1.2's defensive "voluntarily decline a
winnable trick" half a first full worked-example design pass now, rather than
leaving it a one-paragraph deferral (implementation timing is unchanged — see
§6); reconciles the user's own restatement of the offensive bait mechanic
against §1.3's text, including confirming a likely typo in the user's own
notes; rewords §2.1's "by circumstance rather than by design intent" line,
which pass 1 phrased in a way that read (incorrectly) as dismissing the
user's own original reasoning behind the win-by-least suggestion; adds a new
seat-position-in-trick dimension, shared by §2.3 (above-bet win-by-least) and
§3.4 (forced-win tiebreak) and explicitly reusing §1.4's `PlayContext` hook
rather than proposing a second mechanism; refines §3.4's forced-win heuristic
with an off-suit high-card sub-ranking; resolves §4.1's hypothesis 2
(opponent-bet-trust varying by personality) from an open question to a
confirmed design direction; and adds concrete candidate numbers for §4.3's
trump-weighting dial, including a real numeric interaction (integer
truncation quietly swallowing small multiplier values in later rounds) that
only surfaced from actually running the arithmetic rather than reasoning
about the formula shape alone. Section numbers from pass 1 are kept stable
throughout; new material is appended as a new subsection at the end of its
parent section (e.g. §2.7, §0.6) rather than inserted mid-sequence, with one
necessary exception (§4.4 is new and pushes pass 1's old §4.4 down to §4.5) —
called out explicitly at that section so the renumber doesn't cause
confusion when diffing against pass 1.

**This third pass**, driven by the user's explicit statement that this is
meant to be the **last** revision round before implementation scheduling,
focuses on actually closing out open threads rather than appending more open
questions. Concretely: fixes a real logical bug in §2.7's worked example (the
"tiebreak changes the outcome" hand has no hearts, so the led suit is
deducible with certainty, not genuinely ambiguous — the text previously left
it unnamed as if it were); generalizes §2.7's reacting-branch tiebreak from a
binary "last card of a suit" trigger into a graded preference that weighs how
close a suit is to void against the actual value of the candidate cards,
worked through a concrete `K,Q` vs. `A,2,3` example; adds a new cross-cutting
§0.7 stating once (and referencing from every affected bucket, rather than
repeating) that every numeric threshold this doc introduces or has introduced
must land as a named, configurable `AIPersonality`-style dial, never an inline
literal in strategy logic; reworks §0.6 to name two genuinely
**independent** value directions for suit-concentration rather than one —
void+trump as standing guaranteed-win potential (supports betting high) and
suit-concentration alone, with or without trump, as reduced exposure to being
forced into a risky card (supports confident low/zero bets) — since the
previous framing incompletely treated the second as a mere caveat on the
first rather than a real, separate mechanism (**a real gap this pass leaves
open, flagged rather than silently fixed since §4.4 wasn't part of this
pass's assigned scope: §4.4's own candidate betting-signal mechanism still
only operationalizes direction (a), a bonus scaled by holding trump — folding
direction (b) into its own explicit betting-signal candidate remains
unresolved, despite §0.6 itself already saying §4.4 "proposes folding both
directions in"**); rebuilds §1.2.2's worked example
from scratch, since the previous hand (one card of each of the four suits)
could not actually produce the winners/losers reacting choice the text
claimed, and explicitly confirms §1.2's decline mechanic is reacting-only by
construction; adds §1.2.4, a judgment call on the user's "leading advantage"
observation (not designed as its own mechanic this pass — reasoning recorded
inline); adds an entirely new §1.6, a third and genuinely distinct Bucket A
mechanic (the "Sacrifice" mechanic — declining an available win specifically
to force a `trickScore == bet` opponent into an unwanted win and bust their
bonus), confirmed by the user to be different from both §1.2's defensive
decline and §1.3's offensive bait, not a restatement of either; reconciles a
real safety gap in §1.3's "middling-high non-trump" bait lead (unsafe in
exactly the state baiting is preconditioned on) by subordinating it to the
existing "low trump" variant rather than presenting the two as equal options;
fixes a real conflation in §2.3 between "at bet" and "over bet" opponents —
only the former is a genuine non-contesting signal, the latter remains a live
(if statistically fading) threat; adds explicit §4.3 guidance to accumulate
`naturalBet()`'s formula as `double`s and defer any rounding cast to the very
end, addressing the silent-truncation risk the numeric table already
surfaced; logs two user-flagged "note for later" items (personality-level
execution noise/RNG, and per-personality `NUM_HIGH_TRUMP`/`NUM_HIGH_CARDS`
thresholds) as explicitly captured-but-deferred, not designed, in a new
§4.6; and flags that §4.4 (in its pass-1/pass-2 form — this pass leaves it
untouched, per the gap noted above) has not actually had a close
read-through from the user yet, despite being correctly marked
deferred/investigation-only — not to be treated as reviewed/signed-off.
**§6 (Scope recommendation), stale since pass 2 and never updated for pass 3
until now, is brought current**: confirms §1.6 (the new Sacrifice mechanic)
belongs in the same later-pass bucket as §1.3/§1.4, since it needs the
identical `PlayContext`-style hook (not a self-contained override the way
§2/§3 are); confirms §2.3's corrected seat-position/at-bet-vs-over-bet
reasoning and §2.7's generalized tiebreak don't change what's recommended for
the first pass, only sharpen claims already made; and updates the
user-appetite note to cover all three now-fully-specified Bucket A flavors
(decline, bait, sacrifice), not just the first two. Section numbers are again
kept stable; new material is again appended at the end of its parent section
(§0.7, §1.2.4, §1.6, §4.6), matching pass 2's own convention.

This is the **next active item after item 1**, per `ROADMAP.md`'s own framing
of item 2 (added 2026-07-05 from `SUGGESTIONS.md` triage, folding suggestions
#8, #9, #15/"Punish", #16, #21, #22, #27).

---

## §0. Grounding: what's changed since `design/ai-and-polish.md`, and what's shared

### 0.1 The tier ladder today (not the 5-tier ladder item 1's doc originally scoped)

Only two real strategy tiers exist right now: `AI_Easy` (hand-strength bet +
`tryToWin`/`tryToLose` card play) and `AI_Medium` (`extends AI_Easy`,
overrides **only** `bet()` — see `AI_Medium.java`'s own class doc: "Inherits
Easy's card-play engine (`tryToWin`/`tryToLose`) unchanged... only overriding
`bet()`"). `AI_Hard`/`AI_Expert` don't exist yet (deferred to `ROADMAP.md`
item 26). This matters a lot for this doc: **`AI_Medium` today has zero
card-play advantage over `AI_Easy`** — all of its "smarts" are in betting.
Every bucket below that touches card play (A, B, C) is proposing the first
real card-play upgrade over `AI_Easy`, not a tweak to something already
smart.

### 0.2 The existing card-play engine, exactly as it stands (`AI_Easy.java`)

**Scope clarification, added this pass since it will recur for every reader:**
everywhere this doc says `legalCards`/`winners`/`losers`, it means **this AI's
own hand**, filtered down to whatever `Player.legalCards(...)`
(`Player.java:198-218`) says is legal to play right now — itself derived
entirely from `getHand()` plus this trick's follow-suit rule and the
trump-broken flag (§0.5). It is never a broader view: not other players'
hands, not the full trick history, not any card besides what's needed to
compute this trick's running high card. `winners`/`losers` (below) are always
`legalCards` further filtered against that running high card via
`Round.isHigher`. Stating this once here rather than re-deriving it at each
use.

`strategy(cardsPlayed, leading, trump, trumpBroken)` (`AI_Easy.java:54-64`)
dispatches on exactly one condition: `trickScore == bet` → `tryToLose(...)`,
else → `tryToWin(...)`. This is a **two-state** dispatch, not three — it
does not distinguish "still needs wins" (`trickScore < bet`) from "already
overshot" (`trickScore > bet`); both fall into the `tryToWin` branch today.
§2 below is precisely about why that collapse is wrong.

- **`tryToWin`** (`AI_Easy.java:95-109`): if leading (`cardsPlayed.isEmpty()`),
  plays `getHighestValue(legalCards)` — the single highest-value legal card,
  irrespective of suit/trump status (there's already a `// TODO: this could
  be smarter (either purposely select trump or not)` comment on this exact
  branch, `AI_Easy.java:97`, confirming this is a known-rough spot, not
  something I'm newly discovering). If reacting, it computes `winners` (legal
  cards that beat the trick's running high card, via
  `Round.determineTrickWinner`/`AI.getWinningCards`) and, if any exist, plays
  `getHighestValue(winners)` — **the highest of its own winning candidates**,
  not the lowest. If no winners exist, it plays `getLowestValue(legalCards)`
  (lose by the largest possible margin).
- **`tryToLose`** (`AI_Easy.java:66-93`): if leading, prefers a non-trump card
  if one exists (avoids trump specifically), else the lowest legal card. If
  reacting, it computes `losers` (legal cards that don't beat the running
  high card); if any exist, plays `getHighestValue(losers)` — the highest
  *safe* card, i.e. burn a strong-but-still-losing card now rather than a
  weak one. **If no losers exist** (forced win — every legal card would win),
  it plays `getLowestValue(legalCards)` — win by the smallest possible
  margin. This forced-win fallback is exactly the case §3 (endgame lead-risk)
  is about.

Both methods work off `AI.getWinningCards`/`getLosingCards`
(`AI.java:24-43`), which delegate to `Round.isHigher` — comparisons that
already work uniformly across trump and non-trump suits, not a
trump-specific code path. This matters for the "generalize beyond trump"
instruction in Bucket B/§2 below: the mechanism these buckets modify already
operates suit-agnostically: no separate trump-only code path needs
generalizing, it's already general.

### 0.3 What's genuinely locked vs. genuinely open

**Locked, not reopened by this doc:** `AI_Medium`'s betting pipeline
(`AI_Medium.pipelineBet`, `signalStrength`, the guaranteed-win floor via
`AI.selfEvidentGuaranteedWins`, the personality-multiplier wiring) — spec'd
in full in `design/ai-and-polish.md` §4, implemented verbatim in
`AI_Medium.java` (commit `9c5fc94`). Bucket D (§4 below) explicitly treats
this as ground truth to investigate *against*, not a design still being
decided.

**Genuinely open, what this doc is about:** everything in `AI_Medium`'s (and
`AI_Easy`'s, by inheritance) *card play* — `tryToWin`/`tryToLose` have never
been touched by any AI-tier work so far. This is also where the constraint
from `ROADMAP.md`'s "Note for whoever picks up item 1" still applies
verbatim: **never modify `AI_Easy`/`AI_Zombie`** — every fix below is either a
new `AI_Medium` override (small, self-contained) or a new engine hook handed
off to `senior-developer` (never an edit to the existing two classes'
strategy).

### 0.4 The scoring rule this whole doc leans on (`Game.adjustScores`)

`Game.adjustScores()` (`Game.java:435-444`):

```java
if (player.getBet() == player.getTrickScore()) {
    player.increaseScore(player.getTrickScore() + roundBonus);
} else {
    player.increaseScore(player.getTrickScore());
}
```

Two consequences that ground §2 and §3 directly, and that I haven't seen
written down anywhere else in this codebase's docs, so stating them
explicitly:

1. **There is no penalty for overshooting a bet, only a lost bonus.** Every
   trick won is always worth +1 raw score, whether it's under, at, or over
   the bet. The only thing at stake is `roundBonus`, and that's only earned
   for an *exact* match. This is why, once `trickScore > bet`, continuing to
   try to win more tricks is strictly correct (pure upside, nothing left to
   protect) — which is exactly what `tryToWin` already does by falling
   through to it once `trickScore != bet` is true again after an overshoot.
   **This part of the current two-state dispatch is not a bug.**
2. **`trickScore == bet` is a fragile, reversible-for-the-worse state, not a
   safe one.** The bonus is only banked at game-scoring time, at the end of
   the round — mid-round, hitting `trickScore == bet` exactly means the AI
   is now trying to *protect* a still-revocable bonus by avoiding any further
   win for the rest of the round. One more won trick (even by force) permanently
   forfeits it (score keeps rising by trick count, but `roundBonus` is gone
   for that round, forever — `trickScore` never decreases). This is the
   entire premise of §3.

### 0.5 What card-play strategy can and can't see today

`Player.playCard(cardsPlayed, leading, trump, trumpBroken)` /
`AI.strategy(...)` take exactly those four parameters (`Player.java:191`,
`AI.java:21`) — no opponent bet, no opponent trick score, no view of any
*previous* trick this round, no reference to the other `Player` objects at
all. `Trick.play()` (`Trick.java:24-77`) calls `player.playCard(...)` on one
player at a time and never passes sibling `Player` references down — this
isn't an oversight I need to patch around, it's a real, structural absence of
opponent-facing context at card-play time, unlike betting, which already got
a context object (`BettingContext`, item 1's own precedent) precisely because
it needed several extra pieces of context beyond the four `playCard`-style
primitives. §1 depends on an analogous new object; §2 and §3 don't (they only
need the AI's own already-visible `getBet()`/`getTrickScore()`/`getHand()`,
which every `Player` already exposes on itself).

### 0.6 New shared framing: suit-concentration as a structural advantage — two independent directions, not one

**Definition.** An AI is "void" in a suit when it holds zero cards of that
suit. This section states these ideas once, referenced by both §2.7 (card
play) and §4.4 (betting) below, so they aren't re-derived twice.

**Reworked this pass.** Pass 2's version of this section only named one value
direction (void + trump). The user pushed back that this was genuinely
incomplete, not just imprecisely worded — there is a **second, independent**
way suit-concentration pays off, one that doesn't need trump at all and
supports the opposite betting posture. Both are stated in full below,
labeled (a) and (b) throughout the rest of this doc so neither gets lost or
re-merged into "the same idea."

**(a) Void + trump = standing guaranteed-win potential — supports betting
high.** Per `Player.legalCards` (`Player.java:198-218`), the follow-suit rule
only restricts a player who *holds* the led suit -- a player void in the led
suit may legally play any card in hand, including trump. So: if the AI holds
zero cards of suit X (X != trump) and holds at least one trump card, then
whenever X is later led by anyone, the AI is free to play trump -- and, per
`Round.isHigher`, any trump beats any non-trump unconditionally. Every other
player who still *holds* X and must follow suit is capped below trump
automatically. **The only remaining uncertainty is another player who is
also void in X and also elects to play trump** -- at that point it's an
ordinary trump-vs-trump comparison (the same uncertainty any trump-led trick
already has). Short of that one case, being void in X while holding trump
converts "whoever leads X, I might win or lose" into "whoever leads X, I have
a winning option on demand" -- a standing piece of control, not a one-off
lucky break. This is the structural advantage the user originally described;
"guaranteed" should be read with that one caveat, not as a literal,
unconditional 100%. This direction argues for betting **higher** — the AI has
real standing offense.

**(b) Suit-concentration alone (with or without trump) = reduced exposure to
being forced into a risky card — supports betting confidently low or zero,
new this pass.** A hand concentrated into few suits is valuable for a
*low-bet* strategy even with **zero trump**, because concentration itself
reduces how often the AI is forced to reveal or play a dangerous high card at
all — this has nothing to do with ever winning a trick, so it's a genuinely
different mechanism from (a), not a weaker version of it. Worked example: a
round of 8 (8-card hands), and the AI's hand is made entirely of two 4-card
off-suit runs, no trump at all. As long as roughly half of each suit's cards
are relatively low, this hand can likely lose every trick — not because
losing is hard to arrange (§0.2's `tryToLose` reacting branch always has a
cheap answer as long as one lower card of that suit remains unplayed), but
because **any given suit is only led on roughly `1/(number of suits)` of
tricks on average, not 50%** — most of the AI's cards in a given suit never
get forced into play at all. In a suit that hasn't been led yet, the AI
usually gets to discard its dangerous high cards as off-suit filler in tricks
led by other suits, well before that suit is itself ever led on it — or, when
it finally is led, still has enough low-card buffer within that suit to lose
safely. **(a) and (b) matter for opposite betting strategies, and it's worth
being explicit about that rather than leaving it implicit:** (a) is a reason
to bid *higher* (real standing offense); (b) is a reason to bid *confidently
low or zero* (low defensive exposure, not offense at all). A hand can have
(a), (b), both, or neither — they're independent axes of the same underlying
"concentration" shape, not two names for the same thing, and a future
implementer should not collapse them into a single bonus term without
tracking which betting direction each is meant to support.

**Why this also matters for betting, not just mid-round card play.** The
same logic applies to a hand's shape *before a single card is played*, for
both directions. For (a): a hand already void or one-away-from-void in
several suits (concentrated into only, say, 2-3 suits out of the round's full
suit count, one of them trump) carries more standing guaranteed-win potential
than a same-strength-by-raw-high-card-count hand spread evenly across every
suit -- especially in earlier/higher-card-count rounds, where a hand *could*
easily be spread across every suit and usually is, making a narrow,
concentrated hand a real outlier worth betting differently on. For (b): the
same narrow, concentrated shape is *also* informative even without trump, for
the reduced-exposure reason above. `naturalBet()` (`AI_Easy.java:40-45`) has
no notion of either direction today -- it only ever counts *values* within
trump/non-trump, never how many *suits* are represented at all. §4.4 proposes
folding both directions in as betting signals; §2.7 proposes folding a
mid-round version of (a) in as a card-play tiebreak (direction (b) has no
mid-round card-play counterpart — it's purely a pre-round hand-shape signal).

### 0.7 Cross-cutting principle (new this pass): every threshold this doc introduces is a tunable, not hardcoded logic

Stated once here and referenced by each affected bucket below, rather than
repeated at each site. The user's own framing, worth quoting directly since
it's the reason this section exists: *"I don't know exactly how this is
working in the code, whether these strategies are their own class... I think
we also have config variables for almost all of them too. But I want to be
able to apply these strategies and play with them as we fine tune the NPCs.
So make sure they are all open to variable change rather than huge logic
change."*

**The principle:** every numeric threshold, weight, or cutoff this doc
introduces -- anywhere a strategy above compares a value against some cutoff
to decide whether, or how strongly, to act -- should land as a named,
configurable field on `AIPersonality` (or an equivalent per-tier config
record) once implemented, following the same "inert dial" convention
`AIPersonality.java` already establishes (its own class doc: fields exist
ahead of the tier that consumes them, inert-by-default for tiers/personalities
that don't use them yet) -- not as an inline literal buried in `AI_Medium`'s
strategy logic. The goal is that retuning any of this doc's behavior is a
config-value change the user can iterate on directly, not a code edit. This is
a design principle to hold implementation to, not a new mechanic in itself --
nothing elsewhere in this doc changes shape because of this section; it only
commits every numeric knob among them to arrive as a config field.

**Concretely, every one of the following should be a dial, not a hardcoded
constant, once implemented** (cross-referenced from each site rather than
restated there):
- §1.2's feasibility/preciousness checks (currently exact `>=` math with no
  slack -- if a safety margin/buffer is ever layered on top of the strict
  inequality, that margin is exactly the kind of value this principle covers)
- §1.6's sacrifice-mechanic feasibility check (reuses §1.2's own math, same
  note applies)
- §2.7's generalized void-progress weighting (`voidProgressWeight`,
  `cardValueWeight`, and the value-normalization scale)
- §4.3's `trumpWeightMultiplier`'s `K`
- §4.4's void-count baseline/threshold, for both the (a)- and
  (b)-direction signals

---

## §1. Bucket A: the "Punish" mechanic

**User-flagged HIGH PRIORITY — "trivially easy to exploit" today.**

### 1.1 The exploit, precisely

A human leads a middling-high trump early in the round. Any AI reacting to
that lead that's currently in `tryToWin` mode (i.e. `trickScore != bet`,
which per §0.2 covers *both* below-bet and above-bet) will greedily take the
trick with `getHighestValue(winners)` if it can beat that trump at all —
even when doing so isn't remotely urgent. The AI has now revealed (to an
attentive human) that it no longer holds a high trump card, and the human can
lead more aggressively in later tricks knowing that AI can no longer punish
them for it. This works today regardless of tier, since `AI_Medium` inherits
this exact card-play behavior unchanged (§0.1).

This mechanic has **three** independent flavors, not one or two — worth
stating explicitly rather than treating "Punish" as a single idea, and
corrected this pass (pass 2 only named two): an **offensive** half (the AI
itself deliberately baiting an opponent, per `ROADMAP.md`'s own phrasing:
"should recognize when *leading* a trick an opponent is likely to win... is a
deliberate bait to draw out that opponent's high/guaranteed-win card" — §1.3),
a **defensive** half (the AI not falling for the exact same bait itself, which
is the concrete exploit the user described — §1.2), and a third,
**sacrifice** flavor added this pass (the AI declining an available win
specifically to force a `trickScore == bet` opponent into an unwanted win and
bust their bonus — §1.6; the user was explicit this is not a restatement of
the offensive bait mechanic, and it isn't). They need different amounts of
new infrastructure — see 1.4.

### 1.2 The defensive half — mostly already fixed by Bucket B, one harder residual piece

Half of the defensive fix is just Bucket B's own correction (§2): an AI
that's *already over* its bet (state 3) switching from "win by biggest
margin" to "win by smallest sufficient margin" directly closes the "reveal my
best trump for no reason" leak for that state. That's implemented as part of
§2, not duplicated here.

The harder residual piece: an AI **below** its bet (state 1) still needs to
win *some* number of future tricks equal to `bet - trickScore`, but nothing
today distinguishes "I need to win tricks in general" from "I must win *this
specific* trick." `tryToWin` always cashes in a win the instant it's legally
available (`AI_Easy.java:95-109` has no branch that ever voluntarily
declines a winnable trick). This is a genuinely new capability (a voluntary
decline of an available win, something `tryToWin` never does at all today)
and is more behaviorally invasive than anything else in this document.
**Per the user's explicit request this pass, what follows is a real first
design pass on this mechanic -- not just the one-paragraph deferral pass 1
left it as. §6 still separately addresses whether this changes the
recommended implementation phasing (it doesn't -- see §6).**

#### 1.2.1 The new capability, precisely

**Precondition:** `trickScore < bet` (state 1 only -- this never applies to
states 2/3, which already have their own rules in §2/§3) **and** `winners` is
non-empty **and** `losers` is also non-empty (if `losers` is empty, this is a
forced win exactly like §0.2's existing fallback -- there's nothing to
decline *to*, so the decline capability doesn't apply; only relevant when the
AI has a genuine choice between winning and safely losing this specific
trick).

**Feasibility check (self-contained, no new hook -- same as the rest of this
bucket's non-1.3/1.4 pieces):** let `winsStillNeeded = bet - trickScore` and
`tricksRemainingAfterThis = getHand().getNumCards() - 1` (the AI's own hand
size before playing this card, minus the card it's about to play, per §1.4's
existing note that this is already derivable with no new hook). Declining is
only *mathematically* safe to consider when
`tricksRemainingAfterThis >= winsStillNeeded` -- otherwise this trick isn't
optional at all, and `tryToWin` should behave exactly as it does today
(win now, per 2.2's already-correct logic).

**Preciousness check (the actual reason to decline, not just "is it
optional"):** among this trick's `winners`, the AI should only consider
declining when *every* available winning candidate is itself one of the
AI's scarce, guaranteed-win-caliber cards (i.e. would count toward
`AI.selfEvidentGuaranteedWins`, or is otherwise the AI's only remaining card
capable of beating this specific running high card). If a cheaper, ordinary
winning card is available among `winners` that isn't part of that scarce set,
there's no reason to decline at all -- `tryToWin`'s existing 2.2 behavior
(win with the highest available winner, preserving low cards, §2.2) already
handles that case correctly; this new capability only fires when winning
*at all* this trick means spending something scarce.

**If both checks pass:** decline the trick -- play from `losers` instead of
`winners`, using the same "highest safe card" convention `tryToLose`'s
reacting branch already uses (`getHighestValue(losers)`, reusing the existing
helper rather than inventing a new selection rule for the discard itself).

#### 1.2.2 Worked example (rebuilt this pass — the previous version was internally inconsistent)

**Why the previous worked example didn't actually work, stated directly
rather than just relabeled.** Pass 2's version used a hand with exactly one
card of each of the four suits (`A♠` trump, `5♥`, `7♦`, `3♣`) and claimed a
reacting choice between `winners = {A♠}` and `losers = {5♥, 7♦, 3♣}`. That's
not achievable with that hand: per `Player.legalCards`
(`Player.java:198-218`), when reacting, a player who *holds* the led suit must
follow it — with exactly one card per suit, whichever suit is actually led,
the AI holds exactly one legal card (its lone card of that suit) and has no
choice at all. A real multi-candidate winners/losers split while reacting is
only possible when the AI is **void in the led suit** (§0.5/§0.6) — which
that hand wasn't, for any suit. It also can't be read as a leading scenario
instead, since `winners`/`losers` are defined (§0.2) relative to the trick's
running high card, which doesn't exist yet while leading — **this decline
mechanic is reacting-only by construction, matching its own precondition text
in §1.2.1, and does not apply to a leading decision at all** (see §1.2.4 for
a related but separate idea the user raised about leading specifically).

**Rebuilt hand.** AI is at `0/2` (bet 2, needs both wins), hand size 4 (4
tricks left this round including the current one), `♠` is trump. Hand holds
`A♠` (its only trump card -- 1 self-evident guaranteed win per
`AI.selfEvidentGuaranteedWins`), `7♦`, `5♦`, `3♣` — **two** diamonds this
time, and **zero** hearts, so the AI is genuinely void in hearts. The trick
was actually led with a `♥` card (a mid-value non-trump heart) — since the AI
holds no hearts, per `Player.legalCards` it is void in the led suit and all
four of its cards become legal to play, not just one. Per `Round.isHigher`
(`Round.java:208-215`), a card only beats the running high card if it shares
the winning card's suit (and out-ranks it) or is trump — `7♦`/`5♦`/`3♣` are
neither (off-suit, non-trump), so they lose automatically regardless of
value; only `A♠` (trump) beats the led heart. So `winners = {A♠}`,
`losers = {7♦, 5♦, 3♣}` — a genuine choice, both sets non-empty, exactly
§1.2.1's precondition.

- **Feasibility:** `winsStillNeeded = 2`, `tricksRemainingAfterThis = 3`.
  `3 >= 2` -- feasible to skip this trick and still reach the bet from the
  remaining tricks.
- **Preciousness:** `winners` is exactly `{A♠}` -- the AI's only trump, its
  only guaranteed-win-caliber card. Winning this trick means spending it on
  a trick that wasn't forced and isn't urgent.
- **Decision:** decline. Play `getHighestValue(losers)` = `7♦` instead,
  keeping `A♠` in reserve. Resulting hand: `{5♦, 3♣, A♠}` -- still needs 2
  wins from 3 remaining tricks, still feasible, and the AI's one guaranteed
  answer is still unrevealed and unspent, directly closing §1.1's exploit (a
  human who led into this trick learns nothing about whether this AI still
  holds a trump answer).

#### 1.2.3 Honest limits -- what this doesn't solve

This is a **heuristic gamble, not a guarantee**, and should be described to
the user as such rather than oversold: the feasibility check only confirms
declining is *mathematically possible*, not that a future winning opportunity
will actually materialize (the same "no lookahead, imperfect information"
honesty §3.4 states explicitly for a different heuristic applies here too --
the AI cannot know what will actually be led in future tricks, or what it
will be void in). A below-bet AI that declines and then never gets another
clean winning look before the round ends could **miss its bet entirely** --
a real, new failure mode this capability introduces that doesn't exist today
(today's greedy `tryToWin` can undershoot a bet, but never by declining a
trick it could have won). This is exactly why this remains a bigger, more
behaviorally invasive change than anything else in this document, and why
§6 still recommends it as its own follow-up implementation pass rather than
bundled with Bucket A's offensive half or Buckets B/C -- despite now having a
full design pass behind it.

One more honest caveat: declining an obvious win is itself a small
information leak of its own (an attentive human watching an AI conspicuously
*not* take an available win could infer it's protecting something strong) --
weaker and less legible than actually revealing the card by playing it, but
worth naming rather than claiming this capability is informationally free.

#### 1.2.4 A related idea, judged separately — the "leading advantage" (new this pass)

**The observation, in the user's own words:** *"If you were leading... you
probably want to try to win with a card and you have a better chance if you
start with it, even if it is low... every card is 'buffed' when it is
leading. It is stronger than in any other position. That is another kind of
stat you could calculate."* The underlying claim: a led card has no existing
running high card to beat, so its effective win-probability is structurally
higher than the same card would have while reacting — meaning a below-bet AI
might sometimes prefer to lead with a lower card (leaning on this "leading
advantage") rather than always reserving its highest cards for reacting.

**Judgment call, since the user asked this not be silently dropped: this is a
real, plausible idea, but I'm not designing it as its own mechanic in this
pass, and here's the reasoning.** §1.2 (this section) is deliberately scoped
to a **reacting-only** decision, by construction of its own precondition
(`winners`/`losers` require an existing running high card, per §0.2/§1.2.2's
corrected worked example above) — the leading advantage is a different
question entirely (which card to *lead*, not whether to decline an available
win) and doesn't fit inside §1.2's shape without a separate mechanism. More
importantly, actually *quantifying* "how much stronger is a given card while
leading" needs some estimate of the probability nothing beats it before the
trick closes — which depends on what other players are holding, an
imperfect-information question this doc has already ruled out of scope for a
different heuristic on very similar grounds (§3.4's lookahead argument: a
meaningful fraction of the relevant cards are either undealt this round or in
an opponent's hand, information the AI structurally doesn't have, not merely
information it's too slow to search). A believable version of this idea would
either need a crude proxy (e.g., "assume roughly `1/numPlayers` chance any
single opponent holds a higher card of this suit or trump," which is really a
card-counting-adjacent estimate, `ROADMAP.md` item 26's territory per §5) or
would have to stay a much cruder heuristic than that.

**What's already covered without a new mechanism.** Two pieces of this idea
are already live elsewhere in this doc, worth naming so this doesn't read as
untouched: §1.3's bait mechanic already leans on exactly this "a low trump
lead is cheap to offer and only trump can beat it" reasoning (§1.3's "low
trump" variant, reconciled further in §1.3 itself this pass) — that's the
leading-advantage idea already applied to one specific, bounded case
(baiting a target's trump) rather than as a generic across-the-board lead
preference.

**Recommendation:** log this as a real, possibly-quantifiable idea worth
revisiting once card-counting infrastructure (item 26) exists to ground a
real probability estimate, rather than either designing a crude standalone
version now or silently dropping it. Not scoped into this pass's Bucket A
recommendation (§6).

### 1.3 The offensive half — the mechanic, specified

**Precondition (self-contained, no new hook needed):** the AI is only
eligible to *bait* when it's already in `tryToLose` territory
(`trickScore == bet`, §0.4) **and** is currently the trick's leader
(`cardsPlayed.isEmpty()`) **and** the candidate lead card is not one of its
own `AI.selfEvidentGuaranteedWins`-qualifying cards (§4.3 of
`design/ai-and-polish.md`, already shared on the base `AI` class,
`AI.java:154-174`) — the AI must not accidentally bait away one of its *own*
precious cards. All three of these are already computable from the AI's own
state today.

**Target-selection (needs a new hook — see 1.4):** a plausible bait target is
an opponent who is themselves still below their own bet (`trickScore < bet`
for that opponent) — they're the ones with a live incentive to spend a strong
card to win a trick right now, rather than duck it. An opponent who has
already secured or overshot their bet has no such incentive (symmetric to
Bucket B/C's own logic, once those exist) and isn't a useful target — leading
into them wastes a good lead for no informational gain.

**Card selection, revised this pass — the two candidate leads are not equal
alternatives.** Pass 2 presented "middling-high non-trump" and "low trump" as
two coequal candidate leads, with "which of the two a personality prefers"
left as an open dial. The user flagged a real safety problem with that
framing (see the reconciliation below): a middling-high non-trump lead is
unsafe in exactly the state baiting is preconditioned on
(`trickScore == bet`), because it reintroduces §3's landmine risk (a real,
nonzero chance the AI's own lead survives unbeaten and wins by accident,
exactly the outcome state 2 exists to avoid). The low-trump lead doesn't have
this problem (only another trump can beat a trump lead, §0.6). So, corrected
ordering: **prefer the low-trump lead whenever the AI holds at least one
trump card** — it baits a target's own trump at very low risk to the AI.
**Only fall back to a middling-high non-trump lead when the AI holds no trump
at all** (so the low-trump option isn't available), and treat that fallback
as the weaker, riskier variant, not an equal first choice. If no opponent
currently qualifies as a target (nobody below their own bet) under either
variant, fall back further to `tryToLose`'s existing safe-lead default
(lowest non-trump) — there's no reason to spend a decent card baiting
nobody.

**Reconciling the user's own restatement (this pass).** The user re-derived
this mechanic independently and it's worth diffing against the text above
rather than just restating it as equivalent. Their words: *"If someone is
already at their bet, they want to lose all their tricks. If you can force
them into winning a trick, they 'lose 9 points' (10 for bonus minus 1 for the
trick they won). If you're leading you have a lot of choice — maybe you want
to lead with a low trump to bait out everyone's [guaranteed-win card], maybe
you would rather play a low card to get rid of a suit."*

- The target-selection logic (an already-at-or-over-bet opponent has every
  incentive to duck, so baiting *them* is exactly where the payoff is) is a
  restatement of, not a departure from, 1.3's precondition/target-selection
  text above — no gap there.
- **The "low trump" idea is a genuine refinement 1.3's original "middling-high"
  card-selection rule didn't fully cover.** A middling-high non-trump lead
  (today's text) baits by being strong enough that only a genuinely valuable
  card beats it. A **low trump** lead is a different, cheaper variant: since
  it's trump, only another trump can beat it (§0.6) — so it specifically
  entices whoever holds a stronger trump (a target's own scarce, guaranteed-
  win-caliber card, per §1.2.1's same "preciousness" concept) into deciding
  whether to spend it on a cheap trick, at very low cost to the AI if nobody
  bites.
  **Safety caveat, reconciled this pass — these are not equal alternatives.**
  The user flagged this directly: *"if you have bet = trickscore and lead
  with K of offsuit, there is a nonzero chance you could win the next trick,
  you are not incentivizing someone to win with their trump. You have just
  put yourself in a risky position. Better to play a low trump there if you
  have it so it will probably lose. Then you can throw your K later as
  offsuit."* Baiting's own precondition (`trickScore == bet`, state 2) is
  precisely the fragile state §0.4/§3 already establish as one where an
  unwanted accidental win is maximally costly — and a middling-high
  non-trump lead reintroduces exactly that risk squarely inside its own
  precondition (a nonzero chance nobody holds a higher card of that suit, so
  the AI's own lead wins by accident). The low-trump variant doesn't carry
  this problem. **Corrected this pass:** the low-trump lead is the AI's
  *preferred* variant whenever it holds trump; the middling-high non-trump
  lead is now understood as the weaker, riskier fallback for when the AI
  holds no trump at all — not a coequal option a personality freely dials
  between, as pass 2 framed it. See the revised "Card selection" text above
  for the corrected ordering.
- **"Play a low card to get rid of a suit" is the offensive lead applying
  §2.7's suit-void preference to itself**, not a separate idea: since baiting
  only happens while already leading in `tryToLose` territory (this section's
  own precondition), the lead card choice can simultaneously serve the bait
  (a plausible target exists) and the AI's own suit-void progress (§2.7) when
  a candidate does both. §2.7 already flags a real tension for suit-void
  leads specifically (a higher void-completing lead card risks accidentally
  winning, §3's landmine risk) — the same caution applies here.
- **Likely typo, flagging rather than silently reproducing it:** the user's
  notes on the *defensive* reserve side (not this section's own offensive
  content, but worth confirming here since it was raised alongside this
  restatement) read: *"after you won a trick, if you want to lose all the
  rest, save a 2 of offsuit so you are very likely to win that trick —
  whereas if you saved a J of offsuit, there is a low chance you could get
  punished and lose."* Read literally, the first clause contradicts itself
  (saving a low card "so you are very likely to win" makes no sense next to
  "if you want to lose all the rest") — **I'm treating "win" in the first
  clause as a typo for "lose"** (saving a 2 makes you very likely to *lose*
  that trick, which is the goal), consistent with the rest of the sentence's
  logic and with `tryToLose`'s own existing "play highest safe loser, keep
  low cards in reserve" behavior (§0.2). The second clause's "get punished
  and lose" reads to me as *also* needing the opposite word — "get punished
  and **win**" is the coherent reading (holding a J in reserve risks it later
  becoming your only remaining card of a suit that's since become high
  enough to force a win, i.e. inflating into exactly the kind of landmine
  §3.4 already describes on the "what to spend now" side, here applied to
  "what to keep in reserve"). **Confirming this reading rather than assuming
  it** — if either correction is wrong, the underlying mechanic is unaffected
  either way: this whole passage is not a new mechanic, it's §3.4's own
  "dangerous card left over" logic restated from the reserve-side rather than
  the spend-side, and is already covered by that section.

### 1.4 The new engine gap this needs (specify-and-hand-off, not implemented here)

Per §0.5, `AI.strategy`/`Player.playCard` have no visibility into any other
player's bet or trick score. Target-selection above needs at least
`(opponentBet, opponentTrickScore)` per opponent at card-play time. Proposed
shape, mirroring `BettingContext`'s precedent (`BettingContext.java`) exactly:
a new `PlayContext`-style object, assembled once per `Trick.play()` call (or
threaded per-card, whichever `senior-developer` judges cleaner given
`Trick`'s existing loop shape at `Trick.java:28-74`), carrying a snapshot of
every other player's `getBet()`/`getTrickScore()` at the moment this AI's
turn comes up. This does **not** need remaining-hand-size info threaded
separately — since `Round.deal()` gives every player the same `numCards` for
the whole round (`Round.java:117-127`), "tricks remaining this round" is
already derivable from the AI's own `getHand().getNumCards()` for every
player uniformly, no new hook needed for that piece.

This is a real new mechanic (new context object + a new call site assembling
it, analogous to `Round.bet()`'s existing `BettingContext` assembly at
`Round.java:62-80`) — I'm specifying the behavior and the shape of the gap,
not implementing it. Hand off to `senior-developer` for the exact
signature/threading decision once this section is approved.

**Expanded this pass — also needed by §2.3/§3.4, with one additional
requirement.** Bucket A's own use only needs a flat snapshot of every
opponent's `(bet, trickScore)`, without regard to turn order. §2.3's and
§3.4's seat-position reasoning needs slightly more: not just *that* snapshot,
but which opponents specifically are still left to act *after* this AI in
the current trick (turn-order-aware, not just "every opponent"), so their
live bet-vs-trick-score state can be read as a signal for whether they're
likely to contest the trick going forward. Concretely, since `Trick.play()`'s
loop already knows the full turn order for the trick (`Trick.java:28-74`,
`currentPlayer`/`nextPlayer` cycling through `players` in a fixed sequence
starting from the trick's leader), the same `PlayContext` object should
expose something like "opponents still to act this trick, in turn order,
each with their live `(bet, trickScore)`" rather than (or in addition to) a
flat all-opponents snapshot. This is the same object and the same call site
— an elaboration of its shape, not a second hook — flagging it here since it
changes what `senior-developer` needs to build slightly, beyond a
Bucket-A-only reading of this section.

**Expanded further this pass — also needed by §1.6, with one more additional
requirement.** §1.6's sacrifice mechanic needs to know not just each
opponent's live `(bet, trickScore)`, but specifically **which player owns the
trick's current running high card** — i.e., per-card ownership within the
trick, not just an unattributed opponent snapshot. `Trick.play()` already
necessarily tracks this internally in order to determine the eventual trick
winner (`Round.determineTrickWinner`), so this is a small elaboration of the
same object's shape (e.g. exposing "current running-high-card's owning
player" alongside the per-opponent `(bet, trickScore)` snapshot), not a
second new mechanism or call site.

### 1.5 Verification

Once the new context exists, `tryToLose`'s leading branch (as overridden by
`AI_Medium`) should be tested the same way `TestAIMedium.java` already tests
betting: construct a real `Hand`, construct a scripted context (a stand-in
for whatever the real opponent snapshot object ends up being), and assert on
the chosen lead card — one test for "no eligible target → falls back to safe
lead," one for "an eligible below-bet target, AI holds trump → picks the low
trump lead" (the corrected preferred variant, per this pass's §1.3
reconciliation), one for "an eligible below-bet target, AI holds no trump →
falls back to the middling-high non-precious card," one for "candidate would
touch a guaranteed-win card → excluded from the choice set." I haven't
written or run any of these — no code exists yet for this bucket to test.

### 1.6 The "Sacrifice" mechanic — declining a win to deny an opponent's bonus (new this pass, a third and distinct Bucket A flavor)

**Not the bait mechanic (§1.3) — the user was explicit these are different
ideas, and they are; stating this up front so the two are never merged.**
*"I still don't think we're on the same page about offensive punish... there's
no 'bait' in my description, I'm not trying to get people to waste their high
trump, that is kind of a different idea."* §1.3's bait mechanic entices an
opponent to *spend* a valuable card on a trick it didn't need to win. This
mechanic is the opposite motion: the AI itself declines a trick it is fully
capable of winning, so that a specific opponent — currently sitting exactly
at their own `trickScore == bet` (state 2, secured-but-fragile, §0.4) — is
forced to win the trick against their will, permanently busting their own
`roundBonus` (§0.4: `trickScore` never decreases once incremented).

**The mechanic, in the user's own words:** *"if an opponent is bet ==
trickScore and they lead or are forced to play a card that is now the highest
valued card in the trick, if they are set to win the trick, sometimes you may
want to sacrifice winning yourself. So if my opponent is 2/2 and I am 0/1, but
there are 5 tricks left. If I have a J of trump, I am never playing it there.
I know I will likely win a future trick, and it is likely better for me to
make them miss their bet."*

**Precondition (needs the new hook — see §1.4's latest expansion):** the
trick's current running high card belongs to an opponent (not this AI) whose
live `(bet, trickScore)` is exactly `trickScore == bet` — that opponent is
currently the one who would "win" the trick if nobody beats them, and winning
is the single outcome they least want. This AI itself holds at least one
legal card in `winners` capable of beating that specific card.

**Feasibility check (reuses §1.2.1's own math, not a second formula — see
§0.7 on keeping this a dial, not a hardcoded literal, if a safety margin is
ever added):** let `winsStillNeeded = bet - trickScore` (this AI's own) and
`tricksRemainingAfterThis = getHand().getNumCards() - 1`; only worth
sacrificing this win when `tricksRemainingAfterThis >= winsStillNeeded`, i.e.
the AI can still plausibly reach its own bet later without this specific
trick. In the worked example above: `winsStillNeeded = 1`,
`tricksRemainingAfterThis = 4` (5 tricks left this round including the
current one) — comfortably feasible, matching the user's own "I know I will
likely win a future trick" framing.

**Decision:** if the precondition and feasibility both hold, decline the win
the same way §1.2 does — play from `losers` instead of `winners`
(`getHighestValue(losers)`, same convention) — letting the targeted
opponent's own current-highest card win the trick instead, permanently
forfeiting their `roundBonus`.

**Why this is a real sacrifice, distinct from §1.2's own decline.** §1.2's
decline exists to preserve a scarce card for a future trick this AI still
needs — it only fires when every winning candidate is itself precious. This
mechanic can fire even when winning *now* would have been an ordinary, cheap
win for the AI (not necessarily a guaranteed-win-caliber card) — the AI is
trading away a win it could easily have afforded, purely to deny an
opponent's bonus, which is a different cost-benefit than §1.2's "don't spend
something scarce" logic. It still respects §1.2.1's feasibility bound (don't
sacrifice a needed win without enough runway to make up for it), but does
**not** require the winning candidate to be scarce/precious the way §1.2's
preciousness check does — denying a bonus is valuable on its own terms,
independent of whether the card spent to *not* win was itself valuable.

**Interaction with §1.2 when both apply to the same trick.** It's possible
for a single trick to satisfy both this section's precondition and §1.2's
(the AI is below its own bet, its only winners are precious, *and* the
trick's current high card belongs to a state-2 opponent) — in that case the
two mechanisms agree (decline), they don't conflict. This section only
meaningfully changes the decision in the case §1.2 alone wouldn't cover: an
ordinary, non-precious win that §1.2 would otherwise take, but that this
mechanism recognizes as worth giving up anyway to deny a specific opponent's
bonus.

**Honest limits, mirroring §1.2.3.** Same heuristic-not-guarantee caveat as
§1.2: the AI is trading a certain, available win for a probabilistic future
one, and could end up missing its own bet if the runway doesn't pan out. It
also depends on the same imperfect-information constraint §3.4 already
states explicitly (no visibility into future tricks or undealt cards) —
feasibility is "mathematically possible," not "guaranteed to work out."

**Scope/hooks:** needs the same `PlayContext`-style hook §1.4 already
proposes for Bucket A's offensive half, with the additional per-card
ownership requirement flagged in §1.4's latest expansion above (which player
owns the trick's current running high card, not just an unattributed
opponent snapshot).

**Verification.** Same pattern as §1.5: construct a scripted `PlayContext`
where the trick's current high card belongs to a state-2 opponent, assert the
AI declines a non-precious win in favor of `losers`; one test confirming the
feasibility bound still blocks the sacrifice when runway is too tight; one
confirming an opponent who is *not* at `trickScore == bet` (e.g. still below
their own bet, so winning helps rather than hurts them) is not treated as a
valid sacrifice target. None of this exists yet — I haven't written or run
any of it.

---

## §2. Bucket B: win-by-least is two opposite rules, not one

**This is the correction the user explicitly flagged — `ROADMAP.md`'s
current one-liner only captures one of two necessary branches. Both are
spelled out fully below, with their own worked examples, precisely so this
doesn't collapse back into a single blanket rule.**

### 2.1 Why the existing dispatch conflates two different situations

Per §0.2, `AI_Easy.strategy` only checks `trickScore == bet` vs. everything
else — collapsing "below bet" and "above bet" into the same `tryToWin`
branch, which always plays `getHighestValue(winners)`. But per §0.4, these
are two states with **opposite** correct behavior:

- **Below bet (`trickScore < bet`, still needs wins):** the AI should
  prefer winning **with a higher card**, specifically to keep a genuinely
  low card in reserve for the moment it hits its bet exactly and needs to
  start deliberately losing (`tryToLose`'s territory). Burning a low card to
  win now, and being left holding a high/guaranteed card it doesn't need
  until later, is the "naive lowest-sufficient" mistake the user flagged —
  it strands a guaranteed-win card at exactly the moment (`trickScore == bet`)
  when the AI most wants to be *able* to lose cheaply.
- **Above bet (`trickScore > bet`, already missed, bonus unrecoverable
  per §0.4): the AI should prefer winning with the LOWEST sufficient
  card**, preserving its high/guaranteed cards for later tricks it may
  still want or need to win — every further win is still pure upside
  (§0.4), so there's no reason to burn strength unnecessarily to win a trick
  a weaker card would have won just as well.

**Concretely, `getHighestValue(winners)` (today's actual code) already
produces the correct output for the below-bet case — but that correctness is
a property of *the code's mechanism*, not evidence the code was designed with
this distinction in mind: the method itself never checks bet status at all,
so it has no way to know it should switch behavior for the above-bet case.
(Reworded this pass to be unambiguous about whose reasoning this describes:
this is a claim about `AI_Easy.tryToWin`'s implementation, not about the
reasoning behind the user's own original win-by-least suggestion, #8/#16 —
the user has confirmed they *did* think through the below-bet/above-bet
distinction when proposing it. "By circumstance" describes only why today's
*code* happens to get the below-bet case right despite having no bet-aware
branching — never a claim that the original suggestion itself was
accidental.) That's the actual bug: not "the rule is backwards," but "the
rule doesn't know these are two different states."**
A future implementer should **not** "fix" this by uniformly switching to
"always play lowest sufficient" — that's the wrong fix for the below-bet
case and would reintroduce the exact naive mistake the user described.

### 2.2 Worked example B1 — below bet (confirms today's behavior is already correct here)

AI is at 0/1 (needs exactly 1 more win). Hand holds `2♥` and `A♥` (`♥` is
trump); the trick's current running high card is a non-trump card, so both
of the AI's trump cards qualify as winners (`Round.isHigher` — any trump
beats any non-trump). `tryToWin`'s reacting branch computes
`winners = {2♥, A♥}` and plays `getHighestValue(winners)` = `A♥`. **This
matches "should win the current trick with the Ace now, keeping the 2 in
hand to dump/lose later" exactly — no code change needed for this branch,
just confirmation that this is the intended behavior for this state**, so it
doesn't get accidentally "corrected" away while fixing 2.3.

### 2.3 Worked example B2 — above bet (the actual bug)

Same hand shape (`7♥`/`A♥` this time, still both winners over the same
weak non-trump running high card), but the AI is now at 2/1 — already
overshot its bet, bonus already unrecoverable this round. **Today's code
makes the identical choice it made in B1**: `tryToWin` is invoked again
(since `trickScore(2) != bet(1)`), computes the same `winners` set, and plays
`getHighestValue(winners)` = `A♥` again — burning its Ace unnecessarily, when
`7♥` would have won this trick just as well and the Ace could have been kept
for a tougher trick later in the round. **The fix:** `AI_Medium` should
override the winning-card choice to check `getTrickScore() > getBet()`
specifically, and in that state prefer `getLowestValue(winners)` instead —
`7♥` in this example, leaving the Ace preserved.

**A further dimension, added this pass — seat position within the trick.**
Everything above (2.2's below-bet case too) evaluates "does this card
currently beat the running high card" using only `cardsPlayed` — the cards
already played *this trick, so far*. That's the right question for the
*margin* question (2.2 vs 2.3's "how much to win by," once a win is already
this AI's to have), but it silently assumes the AI is the *last* to act —
otherwise "beats the running high card so far" isn't the same claim as "will
actually win the trick." The user's own framing: *"If I am going last, I know
with certainty no one can beat me. If I am second to last, I don't know, but
if the person [who still has to play] is [already at] 1/1 or 2/2, then I know
that they don't want to try to win for sure."*

Concretely: `cardsPlayed.size()` at the moment `strategy(...)` is called
tells the AI exactly how many opponents have already played this trick
(`Trick.play()`'s loop, `Trick.java:28-74`, calls `player.playCard` once per
remaining seat in turn order) — but nothing today tells the AI how many
opponents still have to act *after* it, or anything about them. Today's code
doesn't distinguish "I'm last, this win is locked in" from "I'm early, three
more players could still contest this" — both currently read `winners` off
the same `cardsPlayed`-so-far computation and treat a currently-winning card
identically either way.

**The general principle, corrected this pass to stop conflating "at bet" and
"over bet" opponents (see the same fix spelled out in full just below):**
confidence that "this card wins the trick" should scale with (a) how many
opponents remain to act after this AI this trick, and (b) what's inferable
about whether each of them has any live incentive to contest it at all — an
opponent already sitting at exactly `trickScore == bet` has no reason to
fight for this trick (mirrors this same document's own state-2 logic, just
applied to *someone else's* state), while an opponent still below their own
bet does, and an opponent already **over** their own bet is a live-but-fading
case, not a settled one (per §0.4, they still have pure upside in winning
more — see below for the fuller reasoning). This is exactly the same signal
Bucket A's target-selection (§1.3) already needs (an opponent's live
bet-vs-trick-score state) — not a second new mechanism, but the same
`PlayContext`-style hook proposed in §1.4, read for a different purpose
(estimating whether a not-yet-acted opponent is even trying to win this
specific trick) rather than choosing a bait target. **Both above-bet
win-by-least (this section) and the forced-win-fallback tiebreak (§3.4)
depend on the same not-yet-built hook** — flagged there too, not re-derived
as a separate piece of infrastructure.

**What this changes about 2.3, concretely, once the hook exists — corrected
this pass to stop conflating "at bet" and "over bet" opponents.** The
previous text here grouped both together as equally non-contesting once the
hook exists. That's wrong against this doc's own §0.4: an opponent who has
already overshot their own bet still has every incentive to keep winning
(pure upside, no penalty for overshooting, §0.4) — they remain a live threat,
not a settled one. Only an opponent sitting exactly at `trickScore == bet`
(state 2, protecting a fragile bonus, §0.4) is a genuine non-contesting
signal, since per §0.2 that opponent's own strategy actively tries to duck
any further win. An over-bet opponent is a different case, and the user's
own reasoning is the right nuance to fold in here rather than treat them as
either fully live or fully dead: *"if they are over their own bet... they
should try to win, but likely they don't have a strong [hand] left... you are
probably exponentially less likely to win 2, 3, 4 more [as the number of
additional wins needed increases]."* Concretely: an over-bet opponent still
counts as contesting, but the confidence that they'll actually beat this
specific card should fade the more consecutive additional wins they'd need
for it to matter — not because their incentive weakens (§0.4 says it doesn't),
but because it becomes statistically less likely they're still holding the
cards to keep winning repeatedly, the further past a plausible remaining hand
strength that would require. This isn't a new formula this pass — it's a
correction to *which* opponents count as the "not contesting" signal (at-bet
only) versus which remain contesting-but-fading (over-bet), so 2.3's
confidence-scaling logic doesn't silently treat both the same way.

So: the above-bet "prefer lowest sufficient winner" preference should only be
applied with full confidence when the AI is last to act, or when every
opponent still left to act is inferably not contesting — which now means
specifically **at** their own bet, not at-or-over. Earlier in the trick, with
opponents still to act who are below their own bet (genuinely live) or over
their own bet (live but fading per the reasoning above), `getLowestValue(winners)`'s
candidate should be read as "lowest currently-sufficient," not "lowest
actually-sufficient" — a real residual uncertainty this doc can name but not
close without the hook. This is not a reason to withhold 2.3's fix itself
(it's still strictly better than always burning the highest winner,
regardless of position) — just a reason not to overclaim precision it doesn't
have yet.

### 2.4 A third state this creates a real interaction with (forward reference to §3)

The two branches above assume the AI is *choosing* to win (state 1) or has
*already* overshot with nothing left to protect (state 3). They do **not**
apply to the fragile, still-live `trickScore == bet` state (state 2, tried
via `tryToLose`, only reaching a win when forced — §0.2's "no losers exist"
fallback). That state needs its own, **opposite-of-2.3's**, preference —
spelled out fully in §3, not here, since it's really about *which future
risk* the forced-win card choice creates, not about margin-of-win at all.
Flagging the seam explicitly so nobody tries to merge state 2 into either of
this section's two branches.

### 2.5 Generalizes beyond trump — already true structurally, stated explicitly

Per `ROADMAP.md`'s "folds #8 and #16" note: this needs to apply to any
"going last, can already win" situation, not just trump. Per §0.2,
`getWinningCards`/`getLosingCards` already operate over `Round.isHigher`
uniformly for any suit context (trump-vs-nontrump and same-suit comparisons
both flow through the same predicate) — so the two branches above (2.2/2.3)
already generalize with no extra work; there is no trump-specific code path
in `tryToWin` that needs separately generalizing. Worth stating so nobody
assumes a second, off-suit-specific implementation is needed.

### 2.6 Verification

Both branches are unit-testable exactly like `TestAIMedium.java`'s existing
worked examples (`TestAIMedium.java:59-111`): construct a real `Hand` with
two winning candidates, drive `AI_Medium`'s (new) card-play override through
a scripted `cardsPlayed`/`leading`/`trump`/`trumpBroken` call, and assert on
the returned `Card`. Recommend one test per branch (B1 confirming the
already-correct below-bet choice stays unchanged, B2 asserting the corrected
above-bet choice) plus one boundary test at the exact state-2/state-3 seam
(`trickScore == bet` must still route to `tryToLose`, not either of this
section's branches). None of this exists yet — I haven't written or run any
of it.

### 2.7 Suit-void tiebreak inside `tryToLose` (new this pass — the user's "suits open" idea)

**Teaching goal:** `tryToLose` (§0.2) already knows how to play safe — lowest
non-trump when leading, highest safe loser when reacting — but treats every
already-safe candidate as equally good. Per §0.6, completing a void (while
holding trump) is a structural upgrade, not just another safe discard. This
adds a **secondary preference**, applied only within the already-safe
candidate set, for whichever discard would complete a void.

**Rule (reacting branch — generalized this pass from a binary `count == 1`
trigger into a graded preference, per the user's explicit push that suit
count alone isn't the whole picture — "not a pure count comparison," in their
words).** Among `losers`, a candidate only ever gets preferred over today's
plain `getHighestValue(losers)` default under one of two cases, both still
gated by the AI holding at least one trump card (§0.6(a)'s own precondition
for a void mattering at all):

1. **Completing an actual void — unconditional, regardless of the
   candidate's own value.** If playing a candidate would bring
   `countSuit(getHand().getCards(), card.getSuit())` to `0` (the AI's last
   remaining card of that suit), always prefer the highest-value candidate
   among all such void-completing candidates over the full-set default. This
   is exactly last pass's rule, unchanged in this case — completing a void is
   what actually unlocks §0.6(a)'s standing guaranteed-win potential,
   independent of which specific card was spent to get there, so it's never
   worth gating on value.
2. **New this pass — partial progress toward void, gated by the candidate's
   own value, not suit count alone.** If no candidate completes an actual
   void, but some suit represented in `losers` still has more than one card
   remaining, its candidates are only worth preferring over the plain
   highest-value default when they're cheap enough that spending them isn't
   giving up real value — weighed via two named dials (per §0.7):
   `voidProgressWeight` (how much a discard's progress toward a suit's
   eventual void is worth, scaled by how close that suit already is — fewer
   cards remaining scores higher) against `cardValueWeight` (how much the
   candidate's own value, on some value-normalization scale TBD at
   implementation, discourages spending it regardless of progress). A suit
   whose only available candidates clear `cardValueWeight`'s cheapness bar is
   a legitimate partial-progress target even when a *different* suit is
   numerically closer to void; a suit whose candidates don't clear that bar
   is excluded from the partial-progress preference entirely, no matter how
   close to void it already is — matching the user's own framing directly:
   *"the K/Q suit's cards are worth preserving regardless of suit-void
   progress."* This is why the rule is genuinely a **weighing**, not a pure
   count comparison: suit count alone decided case 1's binary trigger before
   this pass; case 2 explicitly lets a suit with *more* cards remaining
   out-rank a suit with *fewer* cards remaining, whenever the fewer-card
   suit's only available candidates are too valuable to spend.

Otherwise (no candidate qualifies under either case), fall back to today's
`getHighestValue(losers)` unchanged. Still risk-free regardless of how the
two cases resolve: every card in `losers` is, by definition, already
guaranteed not to win this trick (§0.2), so reordering among them costs
nothing — this generalization only changes *which* already-safe candidate is
picked, never whether a candidate is safe to begin with.

- **Worked example, case 1 fires (the rule's strongest case, same shape as
  last pass — but naming the led suit explicitly this pass, fixing a real gap
  the previous version left unnamed).** Hand (relevant subset) holds `4♦`,
  `9♣`, `6♣`, plus `K♠` (trump). The trick was led with a `♥` card — since
  this hand holds no hearts at all, per `Player.legalCards` the AI is void in
  the led suit *with certainty*, not merely assumed to be, so all three
  non-trump cards are legally available and qualify as `losers`. **Today's
  plain rule:** `getHighestValue(losers)` = `9♣` (9 > 6 > 4) — clubs still at
  1 remaining afterward, diamond untouched, no void completed. **With the
  tiebreak (case 1):** `4♦` completes an actual void (the AI's sole diamond,
  `countSuit` goes to `0`); `9♣`/`6♣` each leave one club behind, only partial
  progress. Case 1 always wins over case 2 when both are available, so the
  tiebreak plays `4♦` — a numerically *lower* card than today's default,
  deliberately, to bank the diamond void (while the AI still holds trump) for
  the next time diamonds are led.
- **Worked example, case 2 fires — the user's `K,Q` vs. `A,2,3` case, new this
  pass.** Hand (relevant subset) holds `K♠`/`Q♠` off-suit (2 cards, both
  high-value) and `A♣`/`2♣`/`3♣` off-suit (3 cards, mixed value), plus trump
  the AI holds separately; the AI is void in the actually-led suit, so all
  five of these cards are `losers`. No candidate completes an actual void
  (spades has 2 remaining, clubs has 3), so case 1 doesn't apply — but case 2
  does. **The naive, pre-this-pass reading** (prefer whichever suit has
  *fewer* cards, full stop) would say spades (2 cards) is closer to void than
  clubs (3 cards), so spend down `K♠`/`Q♠` first — exactly the naive reading
  the user flagged as wrong: spending `K♠` or `Q♠` gives up real value for
  marginal progress, when `2♣`/`3♣` are available in the *other* suit and are
  cheap to spend regardless of clubs being one step further from void. Under
  case 2's weighing, `K♠`/`Q♠` fail `cardValueWeight`'s cheapness bar (per the
  user's own words quoted above, they're precious regardless of spades' void
  progress) and are excluded from the partial-progress preference entirely;
  `2♣`/`3♣` clear it easily. Among the qualifying candidates (`2♣`/`3♣`), the
  tiebreak still prefers the higher of the two (`getHighestValue` within the
  qualifying subset, same convention as case 1) — so it plays `3♣`, leaving
  `2♣` and both spades untouched. `A♣` itself, despite sharing clubs'
  suit-level partial-progress eligibility, doesn't individually qualify (its
  own value fails the same cheapness bar `K♠`/`Q♠` do) — case 2 gates by the
  *candidate's own* value, not merely "is this suit's cheapest card cheap," so
  `A♣` is never a candidate the tiebreak would pick here.
- **Worked example, correct no-op (updated this pass, since the previous
  version's outcome no longer holds under the generalized rule — flagging the
  change rather than silently keeping a stale example around).** Hand holds
  `K♦`, `Q♦`, `K♣`, `Q♣` (two diamonds, two clubs, all four high-value) plus
  trump; all four are `losers`. No candidate completes an actual void (case 1
  doesn't apply, same as before), and this time **no suit's candidates clear
  the cheapness bar either** (unlike the case-2 example above, every available
  card here is precious) — so case 2 doesn't apply either, and the tiebreak
  correctly falls back to `getHighestValue(losers)`, identical to today. Kept
  for the same reason pass 2 included a no-op example: the tiebreak should be
  verifiably inert whenever the structural condition genuinely isn't met, not
  a rule that quietly always finds *some* excuse to fire — this pass's
  version of that guarantee needs *both* cases to fail to stay inert, not
  just "no suit is down to its last card," which is exactly the point of
  generalizing away from a pure count comparison.

**Rule (leading branch — flagged tension, not recommended without a
safeguard, this pass):** the same idea (prefer a void-completing card over
`getLowestValue(legalNoTrump)`'s current default) has a real cost specific to
leading that the reacting branch doesn't have: every card in `losers` is
*already* guaranteed not to win, but a **lead** has no such guarantee —
nothing yet has to beat it, so leading a higher card than necessary (even
non-trump) raises the odds of it surviving unbeaten and accidentally winning
the trick. That's §3's exact landmine risk (an accidental win exactly when
`trickScore == bet`), reintroduced through a different door. I'm not
recommending this half for the first pass without an explicit value cap
(e.g., only prefer the void-completing card if it's still below some
"safe to lead" threshold, TBD) bounding how much risk the tiebreak is allowed
to add — flagging it as a real idea worth a follow-up pass, not silently
folding it into this pass's recommendation.

**Betting counterpart:** §4.4 folds the same underlying concept (§0.6) into
`naturalBet()`'s hand read, at the start of a round rather than mid-round.

**Scope/hooks:** self-contained, own-hand-only (`getHand()`, `countSuit`,
both already on the base `AI` class) — no new engine hook, same category as
§3 (Bucket C), not Bucket A.

**Verification:** same pattern as §2.6/§3.5 — one test per worked example
above (case 1 fires, case 2 fires, correct no-op), plus a regression test
confirming the existing state-1/state-3 `tryToWin` branches (§2.2/§2.3) are
untouched by this addition, since it only ever touches `tryToLose`. Worth one
more test than pass 2's version implied, since case 2 is new: a test
confirming a candidate whose own value fails `cardValueWeight`'s cheapness
bar (e.g. `K♠`/`Q♠` above) is never selected even when its suit is
numerically closer to void than a qualifying alternative — this is the
specific behavior that distinguishes the generalized rule from the old
count-only trigger, so it's the one regression a future implementer could
most easily reintroduce by accident. None of this exists yet — I haven't
written or run any of it.

---

## §3. Bucket C: endgame lead-risk awareness

### 3.1 The anecdote, precisely reconstructed

`Medium Balanced` won a trick with its Ace of trump, bringing `trickScore` to
exactly its `bet` (1/1) — a legitimate state-1 win per §2.2 (below bet,
correctly using its highest winning card). Per `Round.playRound()`
(`Round.java:96-114`), **the winner of a trick always leads the next one**
(`currentPlayer = actualWinner`, line 113) — so winning that trick also made
this AI the leader of the round's next (in this anecdote, final) trick, at
the exact moment its bonus had just become "secured but fragile"
(`trickScore == bet`, §0.4). Leading that trick forced the AI to commit a
card with no "current high card" to safely duck under — unlike reacting
(where `tryToLose`'s losers-based branch can guarantee avoiding a win
whenever a legal losing card exists), leading is a blind commitment, and
whatever the AI led ended up winning anyway, permanently forfeiting the
bonus it had just secured.

### 3.2 The real state this is about — not below/above bet, but "secured and still fragile"

This is not fixed by §2's two branches — 2.2/2.3 are about *margin of
winning* when a win is being chosen or accepted; this is about **becoming
leader as a side effect of winning at all**, while in the specific state
`trickScore == bet`. `tryToLose`'s reacting branch (§0.2) already actively
avoids winning whenever it legally can — the exposure here is narrower and
specific to the **forced-win fallback**: when `losers` is empty, every legal
card wins, and today's code plays `getLowestValue(legalCards)`
(`AI_Easy.java:87`) — which, superficially, *looks* like the "preserve high
cards" instinct from §2.3's above-bet branch, but is actually the **wrong**
instinct here, for a reason specific to this state:

**A high/guaranteed card preserved for "later" is safe only if the AI gets
to *react* with it (duck below a current winner if it wants). If instead
that same card is later forced into a *lead*, it has no one to duck under —
a preserved high or guaranteed card becomes a landmine the instant it has to
be led, since leading with it essentially guarantees winning again, exactly
when the AI can least afford another win.** This is a genuine, three-way
split, not a two-way one — restating for clarity since it's easy to conflate
with §2:

1. Below bet (§2.2): prefer a **higher** winning card.
2. **Secured but fragile (`trickScore == bet`, forced win only — this
   section): prefer spending the AI's own most dangerous remaining card(s)
   (trump, or anything qualifying under `AI.selfEvidentGuaranteedWins`) NOW,
   while it still gets to choose how, rather than hoarding them for a future
   lead it won't get to choose how to play.** This is the opposite of both
   of §2's branches, and opposite of what today's code already does in this
   exact spot.
3. Above bet (§2.3): prefer the **lowest sufficient** winning card.

### 3.3 What's achievable without new engine hooks (unlike §1)

Unlike Bucket A, this doesn't need any new opponent-facing context — the
entire risk assessment is computable from the AI's own `getHand()` (which
cards would remain if a given legal card is played now), its own
`getBet()`/`getTrickScore()`, and the already-shared
`AI.selfEvidentGuaranteedWins` helper. This is self-contained inside a new
`AI_Medium` override, same as §2.

### 3.4 A bounded, honest heuristic, not a guarantee

**Why lookahead is off the table — sharpened this pass with the user's own
reasoning.** Truly *preventing* an unwanted future lead in every case would
need lookahead across the rest of the round — and the user's own framing for
why that's not just expensive but genuinely infeasible is worth stating
directly, because it's a stronger argument than "too much compute": *"you
can't conceivably do a lookahead because you don't even know what cards are
in play — in later rounds a significant portion of the deck isn't even
dealt. That would be way too many calculations."* This is an
imperfect-information problem, not merely a combinatorial one — even an
unlimited-compute AI couldn't search a future trick's outcome with certainty,
because a meaningful fraction of the deck (everything not dealt this round,
plus every opponent's actual hand) is information the AI structurally
doesn't have access to, not information it's merely too slow to search. A
heuristic is the correct *order* of solution here, not a cheaper stand-in
for a search that could otherwise be built.

What I am proposing for a first pass, scoped to exactly the forced-win
fallback in state 2:

- When multiple legal cards would all force a win (`losers` empty), instead
  of `getLowestValue(legalCards)`, prefer whichever forced-win card is most
  "dangerous to have left over": trump cards ahead of non-trump; within
  trump, cards that would qualify for `AI.selfEvidentGuaranteedWins` after
  this trick resolves, ahead of ones that wouldn't. Spend those down now.
- **Refinement this pass — a sub-ranking within the non-trump tier.** If no
  trump/guaranteed-qualifying card is available to spend (the forced-win set
  is entirely non-trump, or the AI holds no trump at all), prefer discarding
  the **highest-value** non-trump card over a low one, rather than treating
  every non-trump forced-win card as equally disposable. The user's own
  reasoning: *"I often like to throw away high cards of off-suit in this
  position if I can't throw away trump/high trump — this way I can get rid of
  my Ace of off-suit so I don't accidentally win with it."* Same underlying
  logic as the trump-first rule above (a card left in hand that's likely to
  accidentally win a future lead is a landmine), just weaker: a bare
  non-trump Ace is only dangerous if it happens to be led into a moment with
  no higher card of that suit still live, whereas a guaranteed-qualifying
  trump card is dangerous unconditionally. The ranking is now three-tier, not
  two:
  1. Trump, `selfEvidentGuaranteedWins`-qualifying (most dangerous — spend
     first).
  2. Trump, not currently qualifying.
  3. Non-trump, ranked high-to-low by value (an off-suit Ace before an
     off-suit 3) — new this pass; previously any non-trump forced-win card
     was treated as an equally-fine bottom tier.
- This is a **tiebreak-level heuristic**, not a guarantee the AI never ends
  up leading an unwanted win later — it only improves the odds by not
  actively hoarding the most dangerous cards during the one moment (forced
  win while already secured) where hoarding them is provably counterproductive.
- The literal "hold back a card that keeps someone else leading, even at
  some other minor cost" framing from `ROADMAP.md`'s item 2 writeup is this
  same idea restated: the "minor cost" is giving up the theoretically ideal
  smallest-margin win, in exchange for not stockpiling a future landmine.

**Scope reconfirmed, stated more explicitly (the user asked for this).**
This entire heuristic applies **only** to state 2 (`trickScore == bet`, the
forced-win fallback where `losers` is empty). It does **not** apply once the
AI is *already over* its bet — that's state 3, governed by §0.4/§2.3's
"every further win is pure upside" logic, which says keep trying to win, and
by §2.3's own "prefer lowest sufficient" rule once a win is happening anyway.
These are different rules for different states, not two readings of the same
rule — restating this explicitly since "spend dangerous cards" (state 2 only)
and "preserve high cards for later" (state 3's opposite instinct, §2.3) are
easy to conflate if the two sections are skimmed side by side.

**Same seat-position mechanism as §2.3, not a second issue.** The user's own
extension: *"if you are at bet = trick score, then yes, you want to still try
to lose if you are early in the trick, with several people after you who want
to win."* This is the identical seat-position gap §2.3 already names, applied
here rather than duplicated: a trick that looks "forced" (every legal card
currently beats the running high card, per `cardsPlayed` so far) may not
*stay* forced once the remaining opponents this trick act — an opponent still
below their own bet, acting after this AI, might play something that changes
what actually wins. Today's forced-win check (`losers.isEmpty()`) is computed
the same way regardless of how many opponents are left to act, for the same
structural reason 2.3's check is: no visibility into who's left to act or
their bet-vs-trick-score state. Once §1.4's `PlayContext` hook exists, the
same seat-position reasoning from 2.3 governs here too — a forced-win read
early in the trick, with several genuinely-still-contesting opponents left to
act, deserves less confidence than the identical-looking read when the AI is
last to act. Not proposing a second mechanism for this — it's the same
dependency, used a second time.

### 3.5 Verification

Same pattern as §2.6 — construct a `Hand` where all legal cards would force
a win, include both a dangerous (trump/guaranteed-qualifying) and a safe
option among them, and assert the dangerous one gets played. Also worth a
regression test confirming state 1 (§2.2) and state 3 (§2.3) are unaffected
by this change — the three states must stay clearly separated in the
implementation, not partially merged. None of this exists yet.

---

## §4. Bucket D: betting-tuning calibration questions on the LOCKED `AI_Medium` v1 formula

**Explicitly not a reopening of `design/ai-and-polish.md` §4's formula shape
(the pipeline in `AI_Medium.pipelineBet`, `AI_Medium.java:74-93`, is treated
as ground truth here) — these are investigation questions to resolve against
the real implemented formula, framed as open questions, not as decisions
already made.**

### 4.1 `Medium Cautious` bet 5 on a weak hand — investigate before assuming a dial fix

Grounded in the actual formula (`AI_Medium.java:74-93`,
`AIPersonality.java:36-37`): `Cautious` has `riskTolerance = 0.1`,
`opponentBetTrust = 0.8` — identical `opponentBetTrust` to `Balanced` (0.8)
and `Bold` (0.8, `AIPersonality.java:33,35`). Two concrete, testable
hypotheses, not yet resolved here:

1. **The dial can only ever push the bet *down*, and only when there's an
   opposing signal to react to.** `personalityMultiplier` (line 87) only
   scales `concentrationAdjustment`, which is `subtracted` from `naturalBet`
   (line 92: `Math.max(guaranteedWins, naturalBet - (int) concentrationAdjustment)`)
   — it can never push a bet *up*, and if `signalStrength` (§4.1 of the
   locked doc) is low because no prior bet looked like an outlier, `rawSwing`
   and everything downstream of it collapses toward 0 **regardless of how
   extreme `riskTolerance` is** (a 1.4× multiplier of ~0 is still ~0). If
   this anecdote happened with no unusually aggressive prior bets on the
   table, **no dial value fixes it** — the actual source would have to be
   `naturalBet()` itself (inherited unmodified from `AI_Easy`,
   `AI_Easy.java:40-45`), which `Cautious`'s dials never touch at all. This
   is worth confirming (or ruling out) directly, since it changes where any
   fix would even go.
2. **Resolved this pass — `Cautious` should vary `opponentBetTrust` too, not
   just `riskTolerance`.** The user confirmed directly: yes, a more cautious
   personality is plausibly one that trusts an opponent's *perceived*
   bet-strength signal *more* (a higher `opponentBetTrust`), not just one
   that overreacts more sharply to whatever signal it happens to register.
   This is no longer an open question needing the user's input — it's a
   confirmed design direction. What's still undecided (correctly left to
   implementation, not this doc) is the actual dial *value* — how much higher
   `Cautious`'s `opponentBetTrust` should be than `Balanced`/`Bold`'s shared
   `0.8` — same category of decision `0.8` itself already is.

**General context for whoever tunes these dials later (the user's own
reasoning, worth recording here rather than losing it).** Betting higher is
more rewarding when it hits — both the extra trick points and the
`roundBonus` (§0.4) scale with a bigger bet landing exactly — but it's
generally easier to guarantee a *loss* than to guarantee a *win* (per §0.2:
`tryToLose`'s reacting branch can always duck below a current winner whenever
a legal losing card exists; there's no symmetric "always able to force a win"
guarantee). A lower bet is therefore lower-risk of *missing* than a higher
one, which is the user's own explanation for why real-world bet totals in
this game tend to skew low. This isn't a new mechanism to build — it's
framing for why `Cautious`'s dials (both `riskTolerance` and now
`opponentBetTrust`) should probably land toward the conservative end of their
ranges, useful grounding for whoever picks concrete values later.

If hypothesis 1 confirms the anecdote had a real opposing signal, the
investigation should instead focus on whether `0.8` is too high a
`baseTrust`/`opponentBetTrust` floor for a genuinely cautious personality — a
**dial value** change (per the user's own framing), not a formula-shape
change.

### 4.2 Bet totals skewing above trick count — real bias or anecdote?

A structural hypothesis worth checking against real data rather than more
anecdotes: `naturalBet()` (`AI_Easy.java:40-45`) is an **isolated**,
per-hand estimate — it has no notion that only one player can win any given
trick, so if every AI at the table independently estimates its own "high
card" count optimistically, the *sum* of independently-optimistic estimates
will structurally tend to exceed the round's actual trick count, especially
since `AI_Medium`'s opponent-aware layer (§4.1 of the locked doc) only
reacts to a specific outlier bet relative to `expectedPerPlayer`, and never
questions whether its **own** `naturalBet` estimate is itself already
generically optimistic. This is a plausible, real mechanism — but I'd flag
it as unconfirmed rather than assumed: a handful of anecdotal playtests
can't yet distinguish "structural formula bias," from "these specific
`NUM_HIGH_TRUMP`/`NUM_HIGH_CARDS` threshold constants
(`AI_Easy.java:5-8`) just run a little hot," from "small-sample variance."

**Recommend resolving this with real data, not more anecdotes:** `ROADMAP.md`
item 25 already logs a headless, UI-less fast-simulation runner
(`SUGGESTIONS.md` #14, building on the existing `HeadlessGame` test
construction path) as scoping material for ML work — the same
infrastructure would cleanly answer this question too (run many simulated
rounds, compare `sum(bets)` to `numCardsThisRound` across a real
distribution). I'm not proposing building that runner as part of this doc —
just noting it's the right tool for this specific investigation whenever it
exists, rather than continuing to reason from single-game anecdotes.

### 4.3 Weighting trump more heavily than off-suit specifically in later rounds

Grounded in the actual formula: `naturalBet()` weights
`numHighTrump * HIGH_TRUMP_PERCENT + numHighCards * HIGH_CARDS_PERCENT`
(`AI_Easy.java:44`) with both percents fixed at `1.0`
(`AI_Easy.java:7-8`) — **equal weighting, constant across every round**.
`numCardsFactor()` (`AI_Easy.java:47-51`) scales both thresholds down
together as hand size shrinks (later rounds), but does **not** shift the
*relative* weight between trump and non-trump — there is no round-index- or
hand-size-conditional reweighting of trump vs. off-suit anywhere in the
current formula.

**How this could be added as a dial, without reopening the locked formula's
shape:** since `numCardsThisRound` is already available at bet time
(`getHand().getNumCards()`), a candidate mechanism is an `AI_Medium`-only
adjustment applied *on top of* the inherited `naturalBet()` call (not an edit
to `AI_Easy`) — e.g. a `trumpWeightMultiplier` that scales up
`numHighTrump`'s contribution specifically as `numCardsThisRound` shrinks,
leaving `numHighCards`'s weight untouched. This would naturally live as a
new field on `AIPersonality` (inert by default for tiers/personalities that
don't use it), matching that record's own already-established convention of
carrying inert dials ahead of the tier that consumes them
(`AIPersonality.java`'s own class doc: "Fields marked 'inert for now'... this
avoids a breaking schema change once `AI_Hard`/`AI_Expert`... land"). This is
a genuinely small, additive dial — not a reason to revisit §4 of the locked
doc's pipeline shape.

**Concrete candidate numbers, added this pass since the user asked to see
them before judging feasibility.** Grounding in the real constants:
`NUM_HIGH_TRUMP = 6`, `NUM_HIGH_CARDS = 2`, both `*_PERCENT` constants fixed
at `1.0` (`AI_Easy.java:5-8`), and `numCardsFactor()` itself already ranges
from `1.0` at `numCards = 10` up to `2.0` at `numCards = 1`
(`AI_Easy.java:47-51`). Note first: `AI_Medium.bet()` only ever calls
`pipelineBet` (where this dial would apply) when `maxBet != 1`
(`AI_Medium.java:33-41`) — the true 1-card last round is fully handled by the
separate `lastRoundBet` path (§5 of the locked doc) and never reaches this
dial at all. So the dial's real operating range is `numCardsThisRound` from
`10` down to `2`, not down to `1` — worth stating explicitly so a worked
example at "the last round" doesn't accidentally describe a code path this
dial can't reach.

A candidate shape, mirroring `numCardsFactor()`'s own linear form for
consistency: `trumpWeightMultiplier(numCardsThisRound) = 1.0 + K * (10 -
numCardsThisRound) / 8` (dividing by 8, not 9, since the dial's real range is
`10` down to `2`, an 8-step span) — `1.0` at `numCardsThisRound = 10` (no
change from today, by construction) climbing to `1.0 + K` at
`numCardsThisRound = 2`. Candidate `K` values and what they'd actually do,
run against real numbers:

| `K` | Multiplier at `numCards=2` | Effect on a hand with `numHighTrump = 2` (naturalBet's trump term) |
|---|---|---|
| `0.25` | `1.25` | `2 * 1.25 = 2.5` → `(int)` truncates to `2` — **no visible change from today at all** |
| `0.5` | `1.5` | `2 * 1.5 = 3.0` → `3`, a real +1 bump |
| `1.0` | `2.0` | `2 * 2.0 = 4.0` → `4`, a +2 bump — likely too aggressive for a hand this small |

**This table surfaces the real tuning risk the user anticipated, and it's a
concrete one, not a vague "could be tricky": `naturalBet()`'s `(int)` cast
(`AI_Easy.java:44`) truncates, so a modest multiplier can be a complete no-op
for exactly the hands it's meant to matter for.** Whether a given
`numHighTrump` count moves at all under a given `K` depends on whether
`numHighTrump * K` clears the next whole number, not on `K` being "big
enough" in the abstract — a hand with `numHighTrump = 1` needs `K >= 1.0` to
ever move by even +1, while a hand with `numHighTrump = 3` only needs
`K >= 0.34`. This means the dial's practical effect is concentrated on hands
that already hold *several* high trump cards — which, per §0.6/§4.4, are
disproportionately the same hands that are also heavily suit-concentrated
(fewer suits held, more of the hand's value packed into trump) — a real, if
unplanned, overlap between this dial and §4.4's suit-void signal worth
flagging to whoever tunes both: they may end up double-counting the same
underlying "this hand is trump-heavy and narrow" hands unless the two dials
are tuned with awareness of each other, not independently. **Recommending
`K` somewhere in the `0.4-0.6` range as a starting candidate** (visible
effect on 2+-high-trump hands without the `K = 1.0` row's likely-too-large
jump), but this is exactly a "needs real play-testing, not just formula
review" dial, same as `0.8`'s `opponentBetTrust` floor in §4.1.

**Rounding/precision guidance, added this pass per the user's direct ask
("let's make sure that we rework so we don't lose important decimal points
when appropriate") after the table above showed `K = 0.25` silently no-op'ing
via truncation.** The table's real lesson generalizes beyond just this one
dial: `naturalBet()`'s `(int)` cast (`AI_Easy.java:44`) truncates today at the
*end* of a two-term formula that's already fully computed by the time the
cast happens, which is fine as long as nothing else touches the formula
between those two terms and the cast — but every dial this doc proposes
bolting on top of `naturalBet()` (this section's `trumpWeightMultiplier`,
§4.4's `suitsHeldBonus`) is exactly the kind of additional term that risks
getting truncated away *individually*, before it ever gets a chance to
combine with the other terms, if an implementer naively applies `(int)` to
each term's own contribution as it's computed rather than to the formula as a
whole. **Recommendation:** accumulate every weighted term of `naturalBet()`
(the existing `numHighTrump`/`numHighCards` terms, plus any new
personality-dial adjustment this doc proposes) as `double`s across the entire
computation, and defer any `(int)`/rounding cast to the very last step, after
every term has already been summed — never cast an intermediate per-term
contribution to `int` and then feed that truncated value into a later term.
This is exactly what would have prevented the `K = 0.25` row's silent no-op
in the table above: `2 * 1.25 = 2.5` truncates to `2` (no visible change)
only if `2.5` itself is the *final* value being cast; if it were instead one
term summed alongside others as a `double` first (e.g. `2.5 + numHighCards`'s
own contribution), the fractional `.5` would still be alive to shift the
final rounded total, rather than disappearing before it ever had the chance
to matter. This is guidance for whoever implements §4.3/§4.4's dials, not a
new formula shape — it doesn't change any of this section's recommended `K`
values, only how the arithmetic should be sequenced once implemented.

**An architecture option worth floating for `senior-developer`, not decided
here.** The user is open to `naturalBet()` itself being restructured for
extensibility, not just to `AI_Medium` bolting an adjustment on top of an
untouched inherited call — their own words: *"maybe natural bet should just
be in Easy and everyone has their own? Or medium has their own and things
could inherit from that in the future."* Concretely: instead of
`naturalBet()` staying a single fixed method on `AI_Easy` that every tier
calls verbatim and only ever adjusts *after the fact* (today's shape, and
what the `trumpWeightMultiplier` proposal above still assumes), it could
become a `protected`, overridable hook — `AI_Easy` keeps its own
implementation as the tier-1 default (verbatim, no behavior change, so
`AI_Easy`/`AI_Zombie`'s actual output is untouched either way, satisfying the
"never modify `AI_Easy`/`AI_Zombie`" constraint from §0.3), and `AI_Medium`
could override it directly (computing the trump-weighted variant itself, by
calling a shared `countTopValues`-based helper) rather than always calling
`super.naturalBet()` and adjusting outside it. **Trade-off, for
`senior-developer` to weigh, not resolved here:** the current
additive-adjustment shape (as originally proposed above) is smaller and more
obviously inert-by-default; the override shape is more extensible once
`AI_Hard`/`AI_Expert` (item 26) want their own `naturalBet` variations too
(per the user's "things could inherit from that in the future"), but is a
real structural change to a method every tier currently shares unmodified.
I'm floating this as an open option, not recommending one over the other —
it's an implementation-shape decision, not a design-content one.

### 4.4 "Suits open" as a betting signal (new this pass — the user's "suits open" idea; note pass 1's old §4.4 is renumbered §4.5 below to make room)

Per §0.6, a hand concentrated into few suits (especially with trump among the
few) carries more standing guaranteed-win potential than the same raw
high-card count spread across every suit — and `naturalBet()`
(`AI_Easy.java:40-45`) never looks at suit spread at all today, only counts
within trump/non-trump. The user's own example: in an early/higher-card-count
round, a hand spanning only 3 suits (out of the game's full suit count) while
everyone else's hands span all 4 is "a major advantage" that today's formula
can't see.

**Candidate mechanism, additive on top of `naturalBet()` (not a change to
`AI_Easy`):** a `suitsHeldBonus` (name TBD) computed from
`countSuit(hand, suit) == 0` across the round's non-trump suits — i.e., how
many suits the hand is *already* void in before a card is played — scaled by
whether the hand also holds trump (per §0.6, a void with no trump isn't the
same advantage). Concretely: something like `+1` to the natural bet per suit
the hand is void in beyond some baseline (e.g. "more voids than the round's
average hand would have by chance," since every hand is naturally void in
*some* suits once `numCardsThisRound` drops below the number of suits — this
needs a baseline that isn't "any void counts," or it would trivially fire in
every late round), conditioned on holding at least one trump card. This is
deliberately underspecified on the exact baseline/threshold — same category
of open dial as §4.3's `trumpWeightMultiplier`, not resolved further in this
pass.

**Why this matters more in earlier/higher-card-count rounds specifically,
per the user's example:** with a full or near-full hand, being concentrated
into only 2-3 suits is genuinely unusual (most hands that size naturally span
every suit), so it's a real, informative signal. In a very late round (say a
2-3 card hand), *most* hands are already void in most suits just from having
so few cards — the same raw "how many suits am I void in" count stops being
distinctive there, so this signal should matter **less** as
`numCardsThisRound` shrinks, likely the **opposite** direction from §4.3's
`trumpWeightMultiplier` (meant to matter *more* as hands shrink). Flagging
this contrast explicitly since it would be easy for an implementer to assume
both dials scale the same direction with round size — they don't, for a
legible reason (one is about trump's raw value increasing per card, the
other about void-count losing its rarity/signal value).

**Scope:** investigation/dial candidate only, same as the rest of Bucket D
(§4.1-§4.3) — not recommended for implementation without a concrete
threshold worked out first, flagged again in §4.5/§6 below.

### 4.5 What this bucket needs, concretely, before any code changes

Bucket D is **investigation, not implementation** — I'm not recommending any
of 4.1-4.4's candidate mechanisms be built yet. The deliverable here is: (a)
confirm/refute hypothesis 1 in 4.1 against real hand data (a few scripted
`TestAIMedium`-style scenarios would settle it cheaply), (b) treat 4.2 as
blocked on real simulated data, not resolvable from anecdote alone, (c) treat
4.3 as a plausible, cheap, additive dial candidate that could ship alongside
Bucket B/C if the user wants it, once named (numeric candidates now
worked out, §4.3), (d) treat 4.4 (new this pass) as a plausible but more
underspecified dial candidate than 4.3 — needs a concrete void-count baseline
worked out before it's implementation-ready.

### 4.6 Two deferred ideas, logged but explicitly not designed this pass (new this pass)

Both flagged directly by the user as things to capture, not to design or
implement now — recording them here rather than only in the top
revision-history note, so neither gets lost by the time a future pass picks
Bucket D back up. Both are betting-formula-adjacent, hence living here rather
than elsewhere.

**(a) Personality-level execution noise/RNG.** The user's own framing: *"you
could also have RNG in terms of error, where sometimes they mess up their
strategy... if you have bad RNG, maybe the AI 'forgets' to apply some
strategy."* The underlying idea: even a personality with a well-tuned set of
dials might occasionally *fail* to apply one of its own strategies correctly
— a small, personality-scoped chance of reverting to a simpler/older
behavior for a single decision, rather than every dial always firing with
perfect consistency. This could plausibly apply to any of the mechanisms in
this doc (a bait lead that doesn't get taken, a decline that doesn't fire, a
bet that doesn't get adjusted) — which is exactly why it isn't designed here:
it's a cross-cutting behavior that would touch every bucket at once, not a
single mechanism with its own bounded scope the way §1.2/§1.6/§2.7 are. The
user was explicit this should not be designed this pass — logged as a real,
plausible future idea only.

**(b) Per-personality `NUM_HIGH_TRUMP`/`NUM_HIGH_CARDS` thresholds.** Today
these are fixed global constants (`AI_Easy.java:5-8`), shared by every
personality and tier. The user's own framing: "not sure we want to do that in
this pass, but a note" — the idea being that different personalities might
reasonably disagree about what counts as a "high" trump or "high" card at
all, not just how strongly they react once that count is known (which is
what §4.1-§4.3's dials already do). This is a bigger change than a new dial
value: it would mean these two constants stop being tier/personality-agnostic
constants and become per-personality fields themselves, which touches
`naturalBet()`'s own inputs, not just its post-hoc weighting — worth flagging
as a materially different (bigger) kind of change than §4.3/§4.4's additive
dials, which is part of why the user didn't want it decided this pass. Logged
as a real, plausible future idea only, not designed or scoped further here.

---

## §5. Explicitly out of scope

**Card-counting / partial-information trump-rank reasoning is not part of
this doc.** That's `ROADMAP.md` item 26, already fully spec'd in
`design/ai-and-polish.md` §6 (threshold/gap tracking, the `onTrickComplete`/
trump-indicator-rank engine hooks, the two-axis recall dial) and already
cross-referenced from item 2's own `ROADMAP.md` writeup. Nothing in Buckets
A-D above depends on or duplicates that infrastructure — Bucket A's new
context (§1.4) only exposes *bets and trick scores*, which are already public
information every player can legitimately see (bets are visible in the HUD
per-player, per `Player.render`, `Player.java:226-253`), not anything
requiring card-counting or hidden-information inference.

---

## §6. Scope recommendation

**Solid for a first v2 pass (self-contained in a new `AI_Medium` card-play
override, no new engine hooks, small and independently testable):**
- **§2 (Bucket B), both branches** — the below-bet confirmation (2.2, no
  code change, just don't regress it) and the above-bet fix (2.3, the
  concrete bug, now also carrying the seat-position caveat added this pass
  — see 2.3's own note, which doesn't block the fix, only bounds its
  claimed precision). **Confirmed this pass:** 2.3's fix to the "at bet" vs.
  "over bet" conflation (only at-bet opponents are a genuine non-contesting
  signal; over-bet opponents remain live, if statistically fading, threats)
  sharpens what the eventual `PlayContext`-based confidence scaling should
  read, but doesn't change this recommendation — 2.3's core fix (prefer
  lowest sufficient winner once above bet) is itself hook-free and ships the
  same way either way. These are the highest-value, lowest-risk items in this
  whole doc: a real, demonstrated bug with a precise, small fix.
- **§2.7's reacting-branch suit-void tiebreak** (generalized further this
  pass, from a binary "last card of a suit" trigger into a graded preference
  weighing suit-void progress against candidate value) — still risk-free by
  construction (only reorders already-safe `losers` candidates), still
  own-hand-only, no new hooks. **Confirmed this pass:** the generalization
  doesn't change this recommendation — it's a more nuanced tiebreak among the
  same already-safe candidate set, not a new risk surface, so it stays a
  first-pass item. The **leading-branch** half is still explicitly flagged as
  needing a safety bound before it's recommended (§2.7) — not included in
  this pass's recommendation as-is.
- **§3 (Bucket C)**, scoped to the tiebreak-level heuristic in 3.4 (spend
  dangerous forced-win cards down while still `trickScore == bet`, now with
  the off-suit high-card sub-ranking added this pass) — also self-contained,
  own-hand-only, no new hooks. Explicitly *not* the full lookahead/search
  version — that's a different order of scope (and, per 3.4's sharpened
  reasoning this pass, not even achievable with unlimited compute given
  imperfect information), not recommended for this pass.
- **§4.3 (Bucket D's trump-weighting dial)** — small, additive, inert by
  default, ships fine alongside the above, though the numeric worked example
  added this pass suggests a real tuning/playtesting pass is needed before
  picking a final `K`, not just a formula-shape review. **§4.1/§4.2/§4.4
  remain investigation-only** for this pass (no code), not blocking the rest.

**I'd recommend considering for a later pass, not this one:**
- **§1.3-1.4 (Bucket A's offensive "bait" mechanic)** — needs a genuinely new
  engine hook (a `PlayContext`-style object exposing opponent bet/trick-score
  at card-play time, mirroring `BettingContext`'s precedent but for a
  different call site, now also needed by §2.3/§3.4's seat-position
  reasoning — see 1.4). Not achievable purely as a subclass override the way
  §2/§3 are. Specified in full in §1.3/§1.4 for `senior-developer` to review
  and implement the hook once this doc is approved — I have not implemented
  it.
- **§1.2 (Bucket A's defensive "voluntarily decline a winnable trick"
  half)** — now has a full first design pass (this revision), not just a
  one-paragraph deferral. **This doesn't change the phasing recommendation
  itself** — it's still a materially bigger behavioral change than anything
  else in this doc (today's `tryToWin` never declines an available win at
  all, and 1.2's own worked example is explicit about the residual risk of a
  decline that doesn't pan out), so I'd still recommend it land as its own
  follow-up implementation pass rather than bundled into the same PR as the
  rest of Bucket A/B/C — but it's now a fully specified follow-up, not an
  unscoped one.
- **§1.6 (Bucket A's new "Sacrifice" mechanic, added this pass)** — belongs in
  this same later-pass bucket, not the first pass alongside §2/§3. Confirming
  this against the doc's own dependency logic rather than just asserting it:
  §1.6's precondition needs to know which opponent owns the trick's current
  running high card and that opponent's live `(bet, trickScore)` (§1.6's own
  precondition text, §1.4's latest expansion) — that's the same
  `PlayContext`-style hook §1.3/1.4's bait mechanic needs, not a self-contained
  own-hand-only check the way §2/§3 are. So §1.6 is blocked on the identical
  new engine work §1.3/1.4 already is, and should be scheduled alongside it
  (or after it lands), not treated as its own separate dependency. It does
  **not**, however, need anything §1.3/1.4 doesn't already require — the
  per-card "who owns the current high card" piece is an elaboration of the
  same object's shape (§1.4), not a second hook — so once that hook exists for
  the bait mechanic, §1.6 is unblocked at essentially no extra infrastructure
  cost. Like §1.2 and §1.3/1.4, it's now fully specified (precondition,
  feasibility check reusing §1.2.1's own math, interaction with §1.2 when both
  apply to the same trick, honest limits, verification plan) — the gap is
  scheduling, not design.
- **§4.2 (bet-total skew)** — genuinely blocked on real simulated data
  (`ROADMAP.md` item 25's headless fast-sim runner, not yet built) rather
  than resolvable by more manual play/anecdote.
- **§4.4 (suit-void betting signal, new this pass)** — same category as
  §4.3 conceptually (small, additive, inert-by-default candidate), but
  underspecified on the actual void-count baseline/threshold (§4.4's own
  note) — not recommended for this pass until that's worked out, unlike
  §4.3, which already has concrete candidate numbers.

**Flagging the user's stated appetite, not a phasing change.** The user was
explicit that §1.2/1.3/1.4 (the full bait mechanic) not making the first v2
pass is a real disappointment — they want to "play around with them soon."
This doesn't change the recommendation above (nothing here was asked to force
bait into the first pass), but it's worth surfacing directly to the
user/`project-manager` for prioritization once this doc round is approved:
the appetite is for the bait mechanic specifically, not generic backlog
pressure, and now that §1.2 has a full design pass behind it (this revision)
alongside §1.3/§1.4's existing full spec **and §1.6's new Sacrifice mechanic
(also fully specified this pass, and — per the confirmation above — unblocked
by the same `PlayContext` hook §1.3/1.4 already needs, at no material extra
infrastructure cost)**, the entire Bucket A mechanic (all three flavors:
defensive decline, offensive bait, sacrifice) is fully designed and ready to
schedule as its own implementation pass whenever it's prioritized — the
remaining gap is engineering time/sequencing, not design work.

This is a recommendation, not a decision — leaving the actual scoping call,
same as `design/ai-and-polish.md` §10, to the user/`project-manager`.

---

## §7. Post-implementation playtest notes (first hands-on session)

The §6-recommended first pass (§2.2/§2.3, §2.7, §3.4, §4.3) has now shipped
(`AI_Medium.java`, reviewed, one bug found and fixed — see below — full suite
green). The user hand-tested it against the pre-existing three-`AI_Medium`-
personalities-plus-one-`AI_Easy` dev wiring in `Game.java` and reported back
live. Capturing that session's observations here, for whoever opens the next
`ai-v2` thread, rather than letting them live only in chat history.

**Bug found and fixed during this session (not part of this section's "open
items" — already resolved):** `suitVoidTiebreak` was letting trump itself
count as a void-completion/progress candidate, contradicting §0.6(a)'s own
framing (void only matters for a suit *other than* trump). Fixed by excluding
`card.getSuit() == trump` from both the case-1 and case-2 candidate loops;
the existing `getHighestValue(losers)` no-op fallback is untouched and may
still legitimately return trump. Regression test added
(`suitVoidTiebreakNeverTreatsTrumpAsVoidCandidate`). Confirmed via a clean
rebuild and the full suite (272 tests passing) that this didn't break
anything else.

### 7.1 Confirmed working as designed (no action needed)

- **§2.3's above-bet "lowest sufficient winner" rule** — the user's own
  restatement during this session ("if they are past their bet... they
  probably should take [a lower-than-highest-trump win] to try to win
  another one later") independently re-derives exactly this rule. No gap.
- **§2.7's suit-void discards** — observed directly in play, described as
  clearly visible and working.
- **§2.2's below-bet "highest sufficient winner" rule — reopened, not actually
  closed, on reflection.** `Medium Balanced`, first trick of the game, reacting
  to an Ace-of-offsuit lead (only trump could beat it), played J of trump over
  its own 2 and 7 of trump. My first pass at this note argued this matches
  §2.2's approved design (spend the higher card now, bank low cards for the
  later duck phase) and left it there. **The user pushed back on that framing
  directly, and the pushback is fair: matching an already-approved spec isn't
  the same claim as being the strategically optimal play, and the user was
  explicit they're arguing the latter, not the former.** Whether J-over-2 is
  actually correct here depends on how many more wins this AI still needs and
  how confident it should be in its other trump (2, 7) as backup winners if
  needed later — exactly the missing piece described in §7.2-D below. This
  anecdote is better read as motivating evidence for §7.2-D's new mechanic
  than as a closed "matches spec, therefore correct" case.
- **§3.4's forced-win non-trump sub-ranking — not actually exercised, correcting
  my own overclaim.** I originally read the same round's final-trick 10-of-offsuit
  win as tier-3 of §3.4 firing correctly. The user corrected this: that trick
  was the round's *last* trick, meaning the AI held exactly one card with no
  legal alternative — there was no tiebreak decision to make at all, so this
  anecdote doesn't confirm (or refute) §3.4's tier-3 logic either way. Removing
  it as a data point; §3.4's non-trump sub-ranking remains untested by live play
  so far.
- **`trumpWeightedNaturalBet`'s `getHand().getNumCards()` read (`AI_Medium.
  java:300`).** The user asked whether this is safe given hand size shrinks
  over the course of a round. Confirmed: `Round.bet(...)` (`Round.java:62-80`)
  runs entirely before `Round.playRound()` (`Round.java:82-114`) touches any
  card — betting happens once per round, with every player's hand still at
  its full round-starting size (`Round.java:58`'s own comment already states
  this: "player's hand size equals numCards for the whole round"). Not a bug.

### 7.2 Open items for the next `ai-v2` pass — logged, not resolved here

**A. Forced-win tier ordering (§3.4) — genuinely unresolved, folds into §7.2-D
below rather than standing alone.** §3.2/§3.4 (this pass) deliberately rank a
trump card higher in the "spend now" priority *specifically because* it's
`selfEvidentGuaranteedWins`-qualifying — the reasoning being that such a card
is the most dangerous one to still be holding (most likely to force another
unwanted win later), so better to burn it now while a win is already
unavoidable this trick. The user's live reaction ran the opposite direction on
first read, but on further discussion (see §7.2-D) it isn't simply "flip the
ordering" — the user's fuller explanation is that the right answer depends on
a dynamic, needs-relative read of the AI's own hand (how many wins it still
plausibly needs vs. how many of its remaining cards it's confident can deliver
them) that today's static `selfEvidentGuaranteedWins` check doesn't capture at
all. **Do not silently flip §3.4's current ordering** — it's an open design
question pending §7.2-D's mechanic being worked out with `game-designer`, not
a resolved "the user wants X instead of Y" instruction. Separately, the user's
own worked example for a related idea ("K of trump, if A of trump was already
played") is a *different* mechanism than `selfEvidentGuaranteedWins` (which is
a pure hand-shape check — do I hold the literal top consecutive trump ranks
myself — with no memory of what's been played by anyone else). Genuine
card-counting (inferring a card is now safe/guaranteed because of what's been
observed played, not just what's in this AI's own hand) doesn't exist anywhere
in the codebase yet and is `ROADMAP.md` item 26's territory, not this doc's —
the user's confirmed this is wanted, "soon," as its own future item.

**B. Seat-position / "last to act" awareness — not a new mechanism, already
named, now with a concrete second anecdote motivating it.** §1.4 (Bucket A)
and §2.3/§3.4 (via §1.4's own expansions) already establish that `winners`/
`losers`/forced-win reads are computed only against cards already played this
trick, with no visibility into how many opponents remain to act or their live
bet/trick-score state — the same `PlayContext`-style hook already scoped for
Bucket A. This session's discussion didn't surface a new mechanism, but did
sharpen *why* it matters: a `winners`/forced-win read taken early in a trick,
with several genuinely-still-contesting opponents left to act, deserves less
confidence than the identical-looking read when the AI is last to act (an
opponent left to act could still beat the AI's candidate "winner," or could
change what actually counts as a forced win). Whoever builds the `PlayContext`
hook for Bucket A should scope this in as the same piece of work, not a
follow-up — it already was scoped this way in §1.4/§3.4, this session just
reconfirms it against a live, played example.

**C. New idea, not yet designed anywhere in this doc: asymmetric round-based
weighting between trump and off-suit high cards in betting.** Prompted by a
`Medium Bold` bet of 1 on a bare off-suit Queen (no trump at all) — the user's
view is that this is too aggressive, and that the underlying issue is broader
than personality tuning: **a high off-suit card is worth relatively more
*early* in a round (when hands are large and every suit is well-represented,
so a bare Ace/King is genuinely likely to lead or hold up) and worth
relatively *less* late in a round (when hands are small, off-suit cards are
increasingly likely to get led into a suit the AI doesn't hold and has to
discard rather than contest) — while trump runs the *opposite* direction,
worth more as the round progresses.** This is a genuinely different, and
currently unaddressed, mechanism from §4.3's `trumpWeightK`: `trumpWeightK`
only ever adds a growing *bonus* to the trump term as hand size shrinks — it
never reduces the non-trump term. Today, `AI_Easy.naturalBet()`'s
`numCardsFactor()` (`AI_Easy.java:47-51`) scales *both* the trump and
non-trump "how many cards count as high" thresholds by the identical factor
as the round progresses — there's no asymmetry between the two suit-groups at
all, in either the inherited formula or `AI_Medium`'s new §4.3 addition. The
user asked directly whether `trumpWeightK` was "the" dial for this — it's
adjacent (it does make trump matter more late-round) but doesn't touch the
off-suit side, which is the piece this idea is actually about. **Confirmed by
the user as committed scope for the next pass** (not just a flagged idea to
maybe revisit) — likely as its own new subsection under Bucket D (betting),
for `game-designer` to work out the actual formula/dial shape.

**D. New mechanic, not yet named anywhere in this doc: a dynamic, needs-relative
"win-confidence" classification of the AI's own hand, re-evaluated trick by
trick.** This is the deeper idea behind both the §2.2 J-of-trump anecdote (A,
above) and the §3.4 forced-win tier-ordering question (A, above) — the user
was explicit these aren't two separate concerns, they're the same missing
mechanic surfacing twice. In the user's own words, describing how they play
by hand: *"I look at what cards do I have that are almost certain or certain
wins, and which are maybe wins. So if I have A of Trump, Q of Trump, even if
I don't have K... I am relatively certain both will be wins. Then if I have a
7 of trump and A of offsuit, I am generally confident I can get a win with 1
of them. If I have 3 Ks of offsuit, I also can probably get a win with one of
them."* — i.e., not a single `selfEvidentGuaranteedWins` count, but multiple
graded *buckets* of win-likelihood spanning trump and non-trump alike, built
at the start of a round from the whole hand. The mechanic's second half is
that these buckets are **not static for the round** — they shrink or grow as
tricks resolve, relative to how many wins are still needed: *"if I think I
need 2 of those 3 cards to win, then if I win with the J early, then I'm only
relatively confident I can win with the 2 or the 7. If I win with the 7, then
I'm pretty confident I can lose with the 2 and win with the J."* In other
words: which specific card is "safe to spend now" vs. "worth preserving"
depends on a live, continuously-updated estimate of how many of the
remaining cards are actually needed to reach the bet, not a fixed rule keyed
only on above/below/at-bet (§2) or a static hand-shape check (§3.4's current
`selfEvidentGuaranteedWins` use). **This is a real, substantial new mechanic
candidate — the user explicitly said it may need its own `game-designer` pass
("this might need to be a new mechanic we pass to the game designer") rather
than a tweak to §2/§3's existing rules.** Logged here for scoping in the next
`ai-v2` thread; no design or implementation attempted on it in this doc.

**Where this leaves the roadmap:** none of A/B/C/D block committing the
current, already-reviewed-and-fixed first pass — they're all inputs for
scoping the *next* pass. Per this session's discussion: B folds directly into
the already-planned `PlayContext`/Bucket A hook work; C is now committed scope
for that next pass, not just a flagged idea; A and D are the same underlying
open question (a new needs-relative win-confidence mechanic) and should be
scoped together as a `game-designer` design pass before any code changes to
§3.4's current ordering.
