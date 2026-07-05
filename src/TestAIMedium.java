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
}
