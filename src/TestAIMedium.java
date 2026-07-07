import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Tests for AI_Medium (design/ai-and-polish.md §4/§5/§7): the full
 * opponent-bet-aware betting mechanism, the last-round heuristic fix, and
 * AI.selfEvidentGuaranteedWins (§4.3, shared on the AI base class).
 *
 * The three "worked example" tests below reproduce the design doc §4.4
 * scenarios organically -- real hands are constructed so this class's own
 * naturalBet()/ownTrumpStrength/guaranteedWins computations produce exactly
 * the naturalBet/guaranteedWins values the design doc's worked examples
 * state, rather than asserting the §4.4 arithmetic in isolation from real
 * hand data.
 */
public class TestAIMedium {

    private static Hand handOf(Card... cards) {
        Hand hand = new Hand(840, 480, ID.AI);
        for (Card card : cards) {
            hand.addCard(card);
        }
        return hand;
    }

    // --- AI.selfEvidentGuaranteedWins (§4.3) ---

    @Test
    public void aceAndKingHeldCountsTwoGuaranteedWins() {
        Hand hand = handOf(new Card(Suit.HEARTS, CardValue.ACE), new Card(Suit.HEARTS, CardValue.KING));
        assertEquals(2, AI.selfEvidentGuaranteedWins(hand, Suit.HEARTS));
    }

    @Test
    public void kingWithoutAceCountsZero() {
        Hand hand = handOf(new Card(Suit.HEARTS, CardValue.KING));
        assertEquals(0, AI.selfEvidentGuaranteedWins(hand, Suit.HEARTS));
    }

    @Test
    public void gapInTheRunStopsTheCount() {
        // Ace held, but King missing (gap) before the Queen -> only the Ace
        // (top of the run) counts; the Queen doesn't, since the King could
        // still be elsewhere.
        Hand hand = handOf(new Card(Suit.HEARTS, CardValue.ACE), new Card(Suit.HEARTS, CardValue.QUEEN));
        assertEquals(1, AI.selfEvidentGuaranteedWins(hand, Suit.HEARTS));
    }

    @Test
    public void noTrumpHeldCountsZero() {
        Hand hand = handOf(new Card(Suit.SPADES, CardValue.ACE));
        assertEquals(0, AI.selfEvidentGuaranteedWins(hand, Suit.HEARTS));
    }

    // --- Design doc §4.4 worked examples ---

    /**
     * Worked example 1 ("1,1,1,1"): round of 4, 5 players, this AI is the
     * 5th (last) bettor, naturalBet = 1 (one Ace, no trump held), prior bets
     * 1,1,1,1 (unremarkable distribution) -> still bet the natural amount.
     */
    @Test
    public void workedExample1UnremarkableDistributionStillBetsNatural() {
        AI_Medium ai = new AI_Medium("Test", AIPersonality.MEDIUM_BALANCED);
        Hand hand = handOf(new Card(Suit.SPADES, CardValue.ACE), new Card(Suit.SPADES, CardValue.TWO),
                new Card(Suit.SPADES, CardValue.THREE), new Card(Suit.SPADES, CardValue.FOUR));
        ai.setHand(hand); // no trump (Hearts) held, naturalBet == 1

        ai.bet(new BettingContext(Suit.HEARTS, List.of(1, 1, 1, 1), 5, false, true, true));

        assertEquals(1, ai.getBet());
    }

    /**
     * Worked example 2 ("1,0,0,4"): same round shape, naturalBet = 2 (Ace +
     * King, no trump held), prior bets 1,0,0,4 -- one extreme outlier (the 4)
     * dominates the signal and shaves the AI's entire natural bet.
     */
    @Test
    public void workedExample2ExtremeOutlierShavesEntireNaturalBet() {
        AI_Medium ai = new AI_Medium("Test", AIPersonality.MEDIUM_BALANCED);
        Hand hand = handOf(new Card(Suit.SPADES, CardValue.ACE), new Card(Suit.SPADES, CardValue.KING),
                new Card(Suit.SPADES, CardValue.TWO), new Card(Suit.SPADES, CardValue.THREE));
        ai.setHand(hand); // no trump (Hearts) held, naturalBet == 2

        ai.bet(new BettingContext(Suit.HEARTS, List.of(1, 0, 0, 4), 5, false, true, true));

        assertEquals(0, ai.getBet());
    }

    /**
     * Worked example 3 (guaranteed-win floor): same round shape, this AI
     * holds trump Ace+King (selfEvidentGuaranteedWins = 2), naturalBet = 2,
     * one opponent bets the max (4) -> without the floor this would compute
     * to 1, but the floor protects the bet at 2.
     */
    @Test
    public void workedExample3GuaranteedWinFloorProtectsTheBet() {
        AI_Medium ai = new AI_Medium("Test", AIPersonality.MEDIUM_BALANCED);
        Hand hand = handOf(new Card(Suit.HEARTS, CardValue.ACE), new Card(Suit.HEARTS, CardValue.KING),
                new Card(Suit.SPADES, CardValue.TWO), new Card(Suit.SPADES, CardValue.THREE));
        ai.setHand(hand); // trump (Hearts) Ace+King held, naturalBet == 2, guaranteedWins == 2

        ai.bet(new BettingContext(Suit.HEARTS, List.of(0, 0, 0, 4), 5, false, true, true));

        assertEquals(2, ai.getBet());
    }

    // --- §4.5 risk tolerance actually changes the final bet ---

    @Test
    public void riskToleranceChangesFinalBetInAControlledScenario() {
        Hand boldHand = handOf(new Card(Suit.SPADES, CardValue.ACE), new Card(Suit.SPADES, CardValue.KING),
                new Card(Suit.SPADES, CardValue.TWO), new Card(Suit.SPADES, CardValue.THREE));
        AI_Medium bold = new AI_Medium("Bold", AIPersonality.MEDIUM_BOLD);
        bold.setHand(boldHand);
        bold.bet(new BettingContext(Suit.HEARTS, List.of(1, 0, 0, 4), 5, false, true, true));

        Hand cautiousHand = handOf(new Card(Suit.SPADES, CardValue.ACE), new Card(Suit.SPADES, CardValue.KING),
                new Card(Suit.SPADES, CardValue.TWO), new Card(Suit.SPADES, CardValue.THREE));
        AI_Medium cautious = new AI_Medium("Cautious", AIPersonality.MEDIUM_CAUTIOUS);
        cautious.setHand(cautiousHand);
        cautious.bet(new BettingContext(Suit.HEARTS, List.of(1, 0, 0, 4), 5, false, true, true));

        assertNotEquals("a bolder personality should dampen the swing and a more cautious one amplify it",
                bold.getBet(), cautious.getBet());
        assertEquals(1, bold.getBet());
        assertEquals(0, cautious.getBet());
    }

    // --- §5 last-round fix: the three branches ---

    @Test
    public void lastRoundTrumpCardAlwaysBetsOne() {
        AI_Medium ai = new AI_Medium("Test", AIPersonality.MEDIUM_BALANCED);
        ai.setHand(handOf(new Card(Suit.HEARTS, CardValue.TWO))); // low trump card

        ai.bet(new BettingContext(Suit.HEARTS, List.of(), 2, false, false, true));

        assertEquals(1, ai.getBet());
    }

    @Test
    public void lastRoundNonTrumpFirstBettorHighCardBetsOne() {
        AI_Medium ai = new AI_Medium("Test", AIPersonality.MEDIUM_BALANCED);
        ai.setHand(handOf(new Card(Suit.SPADES, CardValue.ACE)));

        ai.bet(new BettingContext(Suit.HEARTS, List.of(), 2, true, false, true));

        assertEquals(1, ai.getBet());
    }

    @Test
    public void lastRoundNonTrumpFirstBettorLowCardBetsZero() {
        AI_Medium ai = new AI_Medium("Test", AIPersonality.MEDIUM_BALANCED);
        ai.setHand(handOf(new Card(Suit.SPADES, CardValue.TWO)));

        ai.bet(new BettingContext(Suit.HEARTS, List.of(), 2, true, false, true));

        assertEquals(0, ai.getBet());
    }

    @Test
    public void lastRoundNonTrumpNotFirstBettorBetsZeroRegardlessOfRank() {
        AI_Medium ai = new AI_Medium("Test", AIPersonality.MEDIUM_BALANCED);
        ai.setHand(handOf(new Card(Suit.SPADES, CardValue.ACE))); // high card, but not the starting player

        ai.bet(new BettingContext(Suit.HEARTS, List.of(), 2, false, false, true));

        assertEquals(0, ai.getBet());
    }

    // --- design/ai-v2-opponent-modeling.md §4.3: trump-weighting dial on pipelineBet ---

    private static AIPersonality withTrumpWeightK(double trumpWeightK) {
        AIPersonality balanced = AIPersonality.MEDIUM_BALANCED;
        return new AIPersonality(balanced.id(), balanced.name(), balanced.tier(), balanced.riskTolerance(),
                balanced.opponentBetTrust(), balanced.recallCapacity(), balanced.recallAccuracy(),
                balanced.offSuitTrackingEnabled(), balanced.offSuitAccuracyMultiplier(),
                balanced.offSuitRelevanceThreshold(), balanced.voidProgressWeight(), balanced.cardValueWeight(),
                balanced.valueNormalizationScale(), trumpWeightK);
    }

    /**
     * §4.3's dial is a true no-op at numCardsThisRound == 10 (this game's max
     * hand size, the top of the dial's real operating range) -- the
     * multiplier is exactly 1.0 there regardless of K, so two personalities
     * that only differ in trumpWeightK must produce the identical bet on the
     * same 10-card hand. Empty priorBets (no opponent signal) isolates
     * pipelineBet's naturalBet term as the only thing that could differ.
     */
    @Test
    public void trumpWeightingIsNoOpAtTenCardHand() {
        Hand hand = handOf(new Card(Suit.HEARTS, CardValue.ACE), new Card(Suit.HEARTS, CardValue.KING),
                new Card(Suit.HEARTS, CardValue.QUEEN), new Card(Suit.DIAMONDS, CardValue.TWO),
                new Card(Suit.DIAMONDS, CardValue.THREE), new Card(Suit.DIAMONDS, CardValue.FOUR),
                new Card(Suit.DIAMONDS, CardValue.FIVE), new Card(Suit.DIAMONDS, CardValue.SIX),
                new Card(Suit.DIAMONDS, CardValue.SEVEN), new Card(Suit.DIAMONDS, CardValue.EIGHT));

        AI_Medium smallK = new AI_Medium("SmallK", withTrumpWeightK(0.5));
        smallK.setHand(hand);
        smallK.bet(new BettingContext(Suit.HEARTS, List.of(), 4, false, false, true));

        Hand hand2 = handOf(new Card(Suit.HEARTS, CardValue.ACE), new Card(Suit.HEARTS, CardValue.KING),
                new Card(Suit.HEARTS, CardValue.QUEEN), new Card(Suit.DIAMONDS, CardValue.TWO),
                new Card(Suit.DIAMONDS, CardValue.THREE), new Card(Suit.DIAMONDS, CardValue.FOUR),
                new Card(Suit.DIAMONDS, CardValue.FIVE), new Card(Suit.DIAMONDS, CardValue.SIX),
                new Card(Suit.DIAMONDS, CardValue.SEVEN), new Card(Suit.DIAMONDS, CardValue.EIGHT));
        AI_Medium bigK = new AI_Medium("BigK", withTrumpWeightK(5.0));
        bigK.setHand(hand2);
        bigK.bet(new BettingContext(Suit.HEARTS, List.of(), 4, false, false, true));

        assertEquals(smallK.getBet(), bigK.getBet());
    }

    /**
     * A trump-heavy hand's bet visibly increases as the hand shrinks toward
     * the dial's bottom of range (numCardsThisRound == 2): same 2-card,
     * all-trump hand, only trumpWeightK differs (0.0 -- an explicit no-op --
     * vs. MEDIUM_BALANCED's 0.5 default). Empty priorBets again isolates the
     * naturalBet term.
     */
    @Test
    public void trumpWeightingVisiblyIncreasesBetOnSmallTrumpHeavyHand() {
        AI_Medium noWeighting = new AI_Medium("NoWeighting", withTrumpWeightK(0.0));
        noWeighting.setHand(handOf(new Card(Suit.HEARTS, CardValue.ACE), new Card(Suit.HEARTS, CardValue.KING)));
        noWeighting.bet(new BettingContext(Suit.HEARTS, List.of(), 4, false, false, true));

        AI_Medium withWeighting = new AI_Medium("WithWeighting", AIPersonality.MEDIUM_BALANCED);
        withWeighting.setHand(handOf(new Card(Suit.HEARTS, CardValue.ACE), new Card(Suit.HEARTS, CardValue.KING)));
        withWeighting.bet(new BettingContext(Suit.HEARTS, List.of(), 4, false, false, true));

        assertEquals(2, noWeighting.getBet());
        assertEquals(3, withWeighting.getBet());
    }

    /**
     * Proves the doubles-accumulation requirement: with numHighTrump == 2
     * and K == 0.25 at a 2-card hand, the multiplier is 1.25, so the bonus
     * term is numHighTrump * 0.25 == 0.5 -- a fractional contribution that
     * only survives to affect the final rounded bet if it's accumulated as a
     * double alongside naturalBet's own int result before any rounding.
     * A naive implementation that truncated the bonus term to an int on its
     * own first (i.e. (int) (numHighTrump * multiplier) == (int) 2.5 == 2,
     * matching naturalBet's own already-int trump contribution) would
     * silently no-op here -- exactly the K == 0.25 pitfall the design doc's
     * own worked table (§4.3) flags. The correct, deferred-accumulation
     * result is a visible +1 bump (naturalBet 2 -> 3), not a no-op.
     */
    @Test
    public void trumpWeightingDefersRoundingToAvoidSwallowingSmallMultipliers() {
        AI_Medium ai = new AI_Medium("Test", withTrumpWeightK(0.25));
        ai.setHand(handOf(new Card(Suit.HEARTS, CardValue.KING), new Card(Suit.HEARTS, CardValue.QUEEN)));

        ai.bet(new BettingContext(Suit.HEARTS, List.of(), 4, false, false, true));

        assertEquals(3, ai.getBet());
    }
}
