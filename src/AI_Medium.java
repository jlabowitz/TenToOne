import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ROADMAP item 1 (design/ai-and-polish.md §1's "Medium" tier). Extends
 * AI_Easy to inherit its card-play engine (tryToWin/tryToLose) unchanged --
 * only bet() is overridden. Adds the full opponent-bet-aware betting
 * mechanism (§4: distribution-aware signal, grain-of-salt trust dampener,
 * self-evident guaranteed-win floor, risk-tolerance scaling) and the
 * last-round betting heuristic fix (§5).
 *
 * ROADMAP item 2 (design/ai-v2-opponent-modeling.md, this doc's own
 * §6-recommended first pass) layers on top of the above, all self-contained
 * in this class -- no new engine hooks, AI_Easy/AI_Zombie untouched:
 * <ul>
 *   <li>tryToWin (§2.2/§2.3): splits AI_Easy's single "win by highest"
 *       branch into below-bet (unchanged) vs. above-bet (now win by lowest
 *       sufficient winner, preserving high/guaranteed cards).</li>
 *   <li>tryToLose's reacting branch, losers non-empty (§2.7): a suit-void
 *       tiebreak among already-safe losers -- prefer completing an actual
 *       void unconditionally (case 1), else a graded partial-progress
 *       preference weighing void progress against the candidate's own value
 *       (case 2), gated on holding at least one trump card.</li>
 *   <li>tryToLose's reacting branch, losers empty/forced win (§3.4): spend
 *       the most dangerous forced-win card now (trump, guaranteed-win-
 *       qualifying first; then other trump; then non-trump ranked
 *       high-to-low) rather than AI_Easy's flat getLowestValue(legalCards).</li>
 *   <li>pipelineBet (§4.3): an additive trump-weighting bonus on top of the
 *       untouched inherited naturalBet() call, growing as hand size shrinks.
 *       See trumpWeightedNaturalBet's own doc for why this is additive
 *       rather than an AI_Easy edit (naturalBet()'s own NUM_HIGH_TRUMP/
 *       numCardsFactor() are private there).</li>
 * </ul>
 * Leading-branch tryToWin/tryToLose (cardsPlayed.isEmpty()) are untouched --
 * explicitly out of scope this pass (§2.7's own leading-branch tension is
 * flagged, not resolved, by the design doc; §1.2's leading-decline mechanic
 * is a later-pass item entirely).
 *
 * NOT wired into Game.java's AI construction -- selecting AI_Medium in an
 * actual game is item 3/10's dev-settings/mode-selection territory
 * (deferred), out of scope here.
 */
public class AI_Medium extends AI_Easy {
    /** §4.4 step 2's K -- countTopValues sample size for ownTrumpStrength. */
    private static final int TRUMP_STRENGTH_SAMPLE_SIZE = 3;
    /**
     * v2 doc §4.3's independent trump-count read for trumpWeightedNaturalBet
     * -- mirrors AI_Easy's own NUM_HIGH_TRUMP constant in spirit (both name
     * "how many top-ranked trump cards count as high"), but is a genuinely
     * separate constant: AI_Easy's NUM_HIGH_TRUMP is private (and scaled by
     * its own private numCardsFactor()), so this can't reuse or reweight
     * that specific term in place without widening AI_Easy's visibility --
     * see trumpWeightedNaturalBet's doc for why an independent, additive
     * read was chosen instead.
     */
    private static final int TRUMP_WEIGHT_SAMPLE_SIZE = 6;

    private final AIPersonality personality;

    public AI_Medium(String name, AIPersonality personality) {
        super(name);
        this.personality = personality;
    }

    /**
     * ROADMAP item 27/persistent-game-state design doc §2a: overrides
     * AI_Easy.ARCHETYPE_ID ("ai_easy") -- AI_Medium extends AI_Easy for its
     * card-play engine, but its actual archetype identity is its personality,
     * not AI_Easy's.
     */
    @Override
    public String archetypeId() {
        return personality.id();
    }

    @Override
    public void bet(BettingContext context) {
        Hand hand = getHand();
        Suit trump = context.trump();
        int maxBet = hand.getNumCards();

        int bet;
        if (maxBet == 1) {
            // design doc §5: the last round is a full replacement of the
            // normal pipeline, not a further adjustment on top of it -- the
            // usual opponent-aware math doesn't meaningfully apply to a
            // 1-card hand.
            bet = lastRoundBet(trump, context.isFirstBettor());
        } else {
            bet = pipelineBet(trump, context);
        }

        // design doc §3: applied last, regardless of which branch above
        // produced `bet` -- AI_Medium isn't Expert-tier, so it gets the same
        // conservative round-down default as AI_Easy/AI_Zombie, not the
        // hand-aware tie-break.
        int finalBet = roundAwayFromForbiddenBet(bet, maxBet, context.sumOfPriorBets(),
                context.isLastBettor(), context.totalBetsCannotEqualTricks());

        setBet(finalBet);
        System.out.println(getName() + " bets " + finalBet);
    }

    /**
     * ROADMAP item 1 (design doc §5). Reuses AI_Easy's inherited naturalBet()
     * for the "is this card high enough" check rather than a new threshold:
     * with a 1-card hand and this card non-trump, naturalBet(trump) reduces
     * to exactly AI_Easy's numHighCards read (numHighTrump is always 0 with
     * no trump card held), i.e. the same Jack-or-above threshold AI_Easy
     * already uses for "high card."
     */
    private int lastRoundBet(Suit trump, boolean isFirstBettor) {
        Card onlyCard = getHand().getCard(0);
        if (onlyCard.getSuit() == trump) {
            return 1;
        }
        if (!isFirstBettor) {
            return 0;
        }
        return naturalBet(trump) >= 1 ? 1 : 0;
    }

    // --- ROADMAP item 2 (design/ai-v2-opponent-modeling.md §2/§3): card play ---

    /**
     * v2 doc §2.2/§2.3: splits AI_Easy's single "win by highest" branch into
     * two opposite rules. Leading (cardsPlayed empty) and "no winners exist"
     * are both unchanged from AI_Easy -- only the reacting, winners-non-empty
     * case is touched.
     */
    @Override
    public Card tryToWin(List<Card> cardsPlayed, List<Card> legalCards, Suit trump) {
        if (cardsPlayed.isEmpty()) {
            return super.tryToWin(cardsPlayed, legalCards, trump);
        }
        Card highest = cardsPlayed.get(Round.determineTrickWinner(cardsPlayed, trump));
        List<Card> winners = getWinningCards(highest, legalCards, trump);
        if (winners.isEmpty()) {
            return super.tryToWin(cardsPlayed, legalCards, trump);
        }
        // §2.2 (below bet, still needs wins): keep AI_Easy's existing
        // highest-winner choice, preserving a low card in reserve for
        // tryToLose territory later. §2.3 (above bet, bonus already
        // unrecoverable): win by the lowest sufficient margin instead,
        // preserving high/guaranteed cards for later tricks.
        return (getTrickScore() > getBet()) ? getLowestValue(winners) : getHighestValue(winners);
    }

    /**
     * v2 doc §2.7/§3.4: leading (cardsPlayed empty) is unchanged from
     * AI_Easy -- §2.7's own leading-branch idea is explicitly out of scope
     * this pass. Reacting has two new tiebreaks layered on top of AI_Easy's
     * existing losers-empty/losers-non-empty split.
     */
    @Override
    public Card tryToLose(List<Card> cardsPlayed, List<Card> legalCards, Suit trump) {
        if (cardsPlayed.isEmpty()) {
            return super.tryToLose(cardsPlayed, legalCards, trump);
        }
        Card highest = cardsPlayed.get(Round.determineTrickWinner(cardsPlayed, trump));
        List<Card> losers = getLosingCards(highest, legalCards, trump);
        if (losers.isEmpty()) {
            // §3.4: forced win -- spend the most dangerous card now instead
            // of AI_Easy's flat getLowestValue(legalCards).
            return forcedWinTiebreak(legalCards, trump);
        }
        // §2.7: reorder among already-safe losers; never changes whether a
        // candidate is safe, only which safe candidate is picked.
        return suitVoidTiebreak(losers, trump);
    }

    /**
     * v2 doc §2.7: among already-safe losers, prefer whichever discard makes
     * progress toward a suit void, gated on holding at least one trump card
     * (§0.6(a) -- a void only matters if the AI can exploit it with trump).
     * Trump itself is never a void-completion/progress candidate in either
     * case below -- §0.6(a)'s mechanic is about being void in some *other*
     * suit while holding trump, not about voiding trump. Two cases, case 1
     * always wins when both apply; falls back to AI_Easy's plain
     * getHighestValue(losers) (which may legitimately return a trump card,
     * if that's genuinely the highest-value safe loser) when neither fires.
     */
    private Card suitVoidTiebreak(List<Card> losers, Suit trump) {
        if (countSuit(getHand().getCards(), trump) == 0) {
            return getHighestValue(losers);
        }

        // Case 1 (unconditional): completing an actual void. Trump is never
        // a candidate here -- §0.6(a)'s void+trump payoff is specifically
        // about being void in a suit *other than* trump while still holding
        // trump; voiding trump itself would throw away the very thing this
        // tiebreak exists to preserve.
        List<Card> voidCompleting = new ArrayList<>();
        for (Card card : losers) {
            if (card.getSuit() == trump) {
                continue;
            }
            int remainingAfterPlaying = countSuit(getHand().getCards(), card.getSuit()) - 1;
            if (remainingAfterPlaying == 0) {
                voidCompleting.add(card);
            }
        }
        if (!voidCompleting.isEmpty()) {
            return getHighestValue(voidCompleting);
        }

        // Case 2 (graded, gated by value): partial progress toward void,
        // weighed against the candidate's own value via two personality
        // dials, per AIPersonality.voidProgressWeight()/cardValueWeight().
        // Same trump exclusion as case 1, same reason.
        List<Card> qualifying = new ArrayList<>();
        for (Card card : losers) {
            if (card.getSuit() == trump) {
                continue;
            }
            int remainingAfterPlaying = countSuit(getHand().getCards(), card.getSuit()) - 1;
            double normalizedValue = card.getValue().ordinal() / personality.valueNormalizationScale();
            double score = personality.voidProgressWeight() * (1.0 / remainingAfterPlaying)
                    - personality.cardValueWeight() * normalizedValue;
            if (score > 0) {
                qualifying.add(card);
            }
        }
        if (!qualifying.isEmpty()) {
            return getHighestValue(qualifying);
        }

        return getHighestValue(losers);
    }

    /**
     * v2 doc §3.4: every legal card would win (losers empty) -- rank
     * candidates into three tiers by how dangerous they'd be to still be
     * holding once forced into a future lead, and spend the most dangerous
     * one now rather than AI_Easy's flat getLowestValue(legalCards) (which
     * hoards exactly the wrong cards for this state). Tiers, most to least
     * dangerous: (1) trump that's currently guaranteed-win-qualifying (the
     * top AI.selfEvidentGuaranteedWins(hand, trump) held trump cards by
     * rank), (2) other trump, (3) non-trump, ranked high-to-low by value
     * (new this pass -- previously any non-trump forced-win card was an
     * equally-fine bottom tier). Picks the highest tier available; within a
     * tier, the highest-value candidate.
     */
    private Card forcedWinTiebreak(List<Card> legalCards, Suit trump) {
        List<Card> ownTrumpDesc = new ArrayList<>(getHand().getCardsOfSuit(trump));
        ownTrumpDesc.sort(Comparator.comparing(Card::getValue).reversed());
        int guaranteedCount = selfEvidentGuaranteedWins(getHand(), trump);

        List<Card> qualifyingTrump = new ArrayList<>();
        List<Card> otherTrump = new ArrayList<>();
        for (int i = 0; i < ownTrumpDesc.size(); i++) {
            Card card = ownTrumpDesc.get(i);
            if (!legalCards.contains(card)) {
                continue;
            }
            if (i < guaranteedCount) {
                qualifyingTrump.add(card);
            } else {
                otherTrump.add(card);
            }
        }
        if (!qualifyingTrump.isEmpty()) {
            return getHighestValue(qualifyingTrump);
        }
        if (!otherTrump.isEmpty()) {
            return getHighestValue(otherTrump);
        }

        List<Card> nonTrump = new ArrayList<>();
        for (Card card : legalCards) {
            if (card.getSuit() != trump) {
                nonTrump.add(card);
            }
        }
        return getHighestValue(nonTrump);
    }

    // --- ROADMAP item 2 (design/ai-v2-opponent-modeling.md §4.3): betting ---

    /**
     * v2 doc §4.3: an additive trump-weighting bonus layered on top of the
     * untouched inherited naturalBet() call, growing as hand size shrinks
     * toward the round's final rounds. Additive rather than an AI_Easy edit:
     * naturalBet()'s own NUM_HIGH_TRUMP/numCardsFactor() are private on
     * AI_Easy, so this can't reweight that specific term in place without
     * either widening AI_Easy's visibility or duplicating its exact private
     * scaling formula. Instead, this computes an independent "how many top
     * trump cards does this hand hold" read (TRUMP_WEIGHT_SAMPLE_SIZE, via
     * the already-public countTopValues/hand.getCardsOfSuit(trump)) and adds
     * only the *incremental* bonus the multiplier implies, on top of
     * naturalBet()'s own unmodified output.
     *
     * Per the doc's own explicit gotcha: accumulated as doubles throughout,
     * with the (int) cast deferred to the very last step -- never truncate
     * an intermediate per-term contribution before combining it with
     * naturalBet's own already-computed int result. This is exactly what
     * would otherwise silently swallow a small K (e.g. 0.25) for a
     * still-meaningful hand, per the doc's own worked table (§4.3).
     *
     * trumpWeightMultiplier(numCardsThisRound) = 1.0 + K * (10 -
     * numCardsThisRound) / 8 -- 1.0 (no-op) at numCardsThisRound == 10 (this
     * game's max hand size, Game.numCardsThisRound()'s "10 - roundIndex"),
     * growing to 1.0 + K at numCardsThisRound == 2. Only ever reached from
     * pipelineBet, which per bet()'s own branch is only called when
     * maxBet != 1 -- so this dial's real operating range is 10 down to 2,
     * matching the doc's own note.
     */
    private int trumpWeightedNaturalBet(Suit trump) {
        int naturalBet = naturalBet(trump);
        int numCardsThisRound = getHand().getNumCards();
        int numHighTrump = countTopValues(getHand().getCardsOfSuit(trump), TRUMP_WEIGHT_SAMPLE_SIZE);

        double multiplier = 1.0 + personality.trumpWeightK() * (10 - numCardsThisRound) / 8.0;
        double bonus = numHighTrump * (multiplier - 1.0);

        return (int) Math.round(naturalBet + bonus);
    }

    /** ROADMAP item 1 (design doc §4.4's full pipeline, steps 1-7). */
    private int pipelineBet(Suit trump, BettingContext context) {
        Hand hand = getHand();
        int naturalBet = trumpWeightedNaturalBet(trump);

        int ownTopTrumpCount = countTopValues(hand.getCardsOfSuit(trump), TRUMP_STRENGTH_SAMPLE_SIZE);
        double ownTrumpStrength = (double) ownTopTrumpCount / TRUMP_STRENGTH_SAMPLE_SIZE;
        double reactionScale = 1 - ownTrumpStrength;

        double signalStrength = signalStrength(context);
        double trust = personality.opponentBetTrust();

        long rawSwing = Math.round(signalStrength * trust * reactionScale * naturalBet);

        double personalityMultiplier = 1 + (0.5 - personality.riskTolerance());
        long concentrationAdjustment = Math.round(rawSwing * personalityMultiplier);

        int guaranteedWins = selfEvidentGuaranteedWins(hand, trump);

        return Math.max(guaranteedWins, naturalBet - (int) concentrationAdjustment);
    }

    /** ROADMAP item 1 (design doc §4.1). */
    private double signalStrength(BettingContext context) {
        List<Integer> priorBets = context.priorBets();
        if (priorBets.isEmpty()) {
            return 0.0;
        }

        double numCardsThisRound = getHand().getNumCards();
        double expectedPerPlayer = numCardsThisRound / context.numPlayers();
        double maxSurprise = numCardsThisRound - expectedPerPlayer;
        if (maxSurprise <= 0) {
            // degenerate edge case (e.g. a 1-player game) -- avoid
            // divide-by-zero/negative, treat as no signal at all.
            return 0.0;
        }

        double maxConfidence = 0.0;
        for (int priorBet : priorBets) {
            double surprise = Math.max(0, priorBet - expectedPerPlayer);
            double confidence = clamp(surprise / maxSurprise, 0.0, 1.0);
            maxConfidence = Math.max(maxConfidence, confidence);
        }
        return maxConfidence;
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(max, Math.max(min, value));
    }
}
