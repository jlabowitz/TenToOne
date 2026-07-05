import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * Tests for Round.isLegalBet -- the shared "total bets cannot equal number of
 * tricks" rule (design/ai-and-polish.md §3), plus the sanctioned rounding/
 * tie-break patch applied to AI_Easy/AI_Zombie's bet *output* (AI.
 * roundAwayFromForbiddenBet) so both AIs stay legal without their
 * hand-strength strategy changing.
 *
 * The range-check coverage this file carries (zero/middle/max/negative/
 * over-max) is migrated from the now-retired Human.isValidBet and
 * TestHumanBet.java -- isLegalBet's range check subsumes that predicate, so
 * that coverage lives here now instead of being lost.
 */
public class TestBetLegality {

    // --- Range checks (migrated from the retired Human.isValidBet) ---

    @Test
    public void zeroIsLegal() {
        assertTrue(Round.isLegalBet(0, 10, 0, 10, false, true));
    }

    @Test
    public void middleValueIsLegal() {
        assertTrue(Round.isLegalBet(5, 10, 0, 10, false, true));
    }

    @Test
    public void exactlyMaxBetIsLegal() {
        assertTrue(Round.isLegalBet(10, 10, 0, 10, false, true));
    }

    @Test
    public void negativeBetIsIllegal() {
        assertFalse(Round.isLegalBet(-1, 10, 0, 10, false, true));
    }

    @Test
    public void betGreaterThanMaxIsIllegal() {
        assertFalse(Round.isLegalBet(11, 10, 0, 10, false, true));
    }

    @Test
    public void zeroMaxBetOnlyZeroIsLegal() {
        assertTrue(Round.isLegalBet(0, 0, 0, 0, false, true));
        assertFalse(Round.isLegalBet(1, 0, 0, 0, false, true));
        assertFalse(Round.isLegalBet(-1, 0, 0, 0, false, true));
    }

    // --- The "total bets cannot equal tricks" rule itself ---

    @Test
    public void sumOfPriorBetsPlusBetNotEqualToNumCardsIsLegal() {
        // 4-card round, prior bets summed to 2, betting 1 more -> total 3, not 4
        assertTrue(Round.isLegalBet(1, 4, 2, 4, true, true));
    }

    @Test
    public void betEqualsForbiddenValueWhenLastBettorIsIllegal() {
        // 4-card round, prior bets summed to 2, forbidden bet is 4-2=2
        assertFalse(Round.isLegalBet(2, 4, 2, 4, true, true));
    }

    @Test
    public void sameBetIsLegalWhenNotLastBettor() {
        // identical numbers to the illegal case above, but not the last bettor
        assertTrue(Round.isLegalBet(2, 4, 2, 4, false, true));
    }

    @Test
    public void toggleOffMakesForbiddenBetLegalAgain() {
        // identical numbers to the illegal case above, but the setting is off
        assertTrue(Round.isLegalBet(2, 4, 2, 4, true, false));
    }

    @Test
    public void forbiddenValueOutsideRangeAboveMaxBetIsNoOp() {
        // 4-card round, prior bets already sum to -1 (impossible in practice,
        // but exercises the "forbidden value can fall outside [0,maxBet]"
        // no-op case) -> forbiddenBet = 4-(-1) = 5, outside [0,4]; every legal
        // (in-range) bet stays legal.
        assertTrue(Round.isLegalBet(4, 4, -1, 4, true, true));
    }

    @Test
    public void forbiddenValueOutsideRangeBelowZeroIsNoOp() {
        // 4-card round, prior bets already sum to 5 (over the card count) ->
        // forbiddenBet = 4-5 = -1, outside [0,4]; every legal bet stays legal.
        assertTrue(Round.isLegalBet(0, 4, 5, 4, true, true));
    }

    // --- AI_Easy/AI_Zombie rounding/tie-break patch (AI.roundAwayFromForbiddenBet) ---

    @Test
    public void roundingBumpsUpToOneWhenForbiddenValueIsZero() {
        // naturalBet=0, maxBet=4, sumOfPriorBets=4 -> forbiddenBet=0
        assertEquals(1, AI.roundAwayFromForbiddenBet(0, 4, 4, true, true));
    }

    @Test
    public void roundingBumpsDownToMaxBetMinusOneWhenForbiddenValueIsMaxBet() {
        // naturalBet=4, maxBet=4, sumOfPriorBets=0 -> forbiddenBet=4=maxBet
        assertEquals(3, AI.roundAwayFromForbiddenBet(4, 4, 0, true, true));
    }

    @Test
    public void roundingRoundsDownByOneInTheGeneralCase() {
        // naturalBet=2, maxBet=4, sumOfPriorBets=2 -> forbiddenBet=2 (interior)
        assertEquals(1, AI.roundAwayFromForbiddenBet(2, 4, 2, true, true));
    }

    @Test
    public void roundingLeavesNaturalBetAloneWhenNotForbidden() {
        assertEquals(2, AI.roundAwayFromForbiddenBet(2, 4, 0, true, true));
    }

    @Test
    public void roundingLeavesNaturalBetAloneWhenNotLastBettor() {
        // naturalBet==forbiddenBet, but not the last bettor -> no patch applied
        assertEquals(2, AI.roundAwayFromForbiddenBet(2, 4, 2, false, true));
    }

    @Test
    public void roundingLeavesNaturalBetAloneWhenToggleOff() {
        assertEquals(2, AI.roundAwayFromForbiddenBet(2, 4, 2, true, false));
    }

    // --- End-to-end wiring smoke tests: the real AI subclasses actually apply the patch ---

    @Test
    public void aiZombieAppliesRoundingWhenForcedToForbiddenValue() {
        AI_Zombie zombie = new AI_Zombie("Zed");
        Hand hand = new Hand(840, 480, ID.AI);
        for (int i = 0; i < 5; i++) {
            hand.addCard(new Card(Suit.values()[i % 4], CardValue.values()[i]));
        }
        zombie.setHand(hand); // maxBet=5, naturalBet = 5/5 = 1

        // last bettor, sumOfPriorBets=4 -> forbiddenBet = 5-4 = 1 = naturalBet
        // -> rounds down to 0 (forbiddenBet is neither 0 nor maxBet)
        zombie.bet(new BettingContext(Suit.HEARTS, List.of(1, 1, 1, 1), 5, false, true, true));

        assertEquals(0, zombie.getBet());
    }

    @Test
    public void aiEasyAppliesRoundingWhenForcedToForbiddenValue() {
        AI_Easy easy = new AI_Easy("Speedy");
        Hand hand = new Hand(840, 480, ID.AI);
        // 10 non-trump (Spades) cards, one of which (the Ace) is a "high card"
        // -- with a 10-card hand, numCardsFactor() == 1.0 exactly, so
        // countTopValues(nonTrumpCards, 2) counts Ace/King-valued cards only.
        // numHighTrump is 0 (no Hearts held), so naturalBet == numHighCards == 1.
        hand.addCard(new Card(Suit.SPADES, CardValue.ACE));
        CardValue[] lowValues = {CardValue.TWO, CardValue.THREE, CardValue.FOUR, CardValue.FIVE,
                CardValue.SIX, CardValue.SEVEN, CardValue.EIGHT, CardValue.NINE, CardValue.TEN};
        for (CardValue value : lowValues) {
            hand.addCard(new Card(Suit.SPADES, value));
        }
        easy.setHand(hand); // maxBet=10, naturalBet expected to be 1

        // last bettor, sumOfPriorBets=9 -> forbiddenBet = 10-9 = 1 = naturalBet
        // -> rounds down to 0
        easy.bet(new BettingContext(Suit.HEARTS, List.of(1, 1, 1, 1, 1, 1, 1, 1, 1), 10, false, true, true));

        assertEquals(0, easy.getBet());
    }
}
