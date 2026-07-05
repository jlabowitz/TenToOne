import java.util.List;

/**
 * ROADMAP item 1 (design/ai-and-polish.md §1's "Medium" tier). Extends
 * AI_Easy to inherit its card-play engine (tryToWin/tryToLose) unchanged --
 * only bet() is overridden. Adds the full opponent-bet-aware betting
 * mechanism (§4: distribution-aware signal, grain-of-salt trust dampener,
 * self-evident guaranteed-win floor, risk-tolerance scaling) and the
 * last-round betting heuristic fix (§5).
 *
 * NOT wired into Game.java's AI construction -- selecting AI_Medium in an
 * actual game is item 3/10's dev-settings/mode-selection territory
 * (deferred), out of scope here.
 */
public class AI_Medium extends AI_Easy {
    /** §4.4 step 2's K -- countTopValues sample size for ownTrumpStrength. */
    private static final int TRUMP_STRENGTH_SAMPLE_SIZE = 3;

    private final AIPersonality personality;

    public AI_Medium(String name, AIPersonality personality) {
        super(name);
        this.personality = personality;
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

    /** ROADMAP item 1 (design doc §4.4's full pipeline, steps 1-7). */
    private int pipelineBet(Suit trump, BettingContext context) {
        Hand hand = getHand();
        int naturalBet = naturalBet(trump);

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
