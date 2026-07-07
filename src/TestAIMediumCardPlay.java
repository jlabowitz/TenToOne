import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Tests for AI_Medium's card-play overrides (design/ai-v2-opponent-modeling.md
 * §2/§3, the doc's §6-recommended first v2 pass): the win-by-least split
 * (§2.2/§2.3), the reacting-branch suit-void tiebreak inside tryToLose
 * (§2.7), and the endgame forced-win tiebreak (§3.4). Betting-side coverage
 * (§4.3's trump-weighting dial) lives alongside AI_Medium's existing betting
 * tests in TestAIMedium.java instead, matching that file's existing theme.
 *
 * Follows TestAIMedium.java's own convention: construct a real Hand via
 * handOf(...), drive the real method, assert on the returned Card. Since
 * Card doesn't override equals()/hashCode() (see TestHand.java's own
 * reference-identity convention, assertSame against hand.getCard(...)),
 * assertions here compare against the exact Card instance added to the hand,
 * not a newly-constructed equal-looking Card.
 */
public class TestAIMediumCardPlay {

    private static Hand handOf(Card... cards) {
        Hand hand = new Hand(840, 480, ID.AI);
        for (Card card : cards) {
            hand.addCard(card);
        }
        return hand;
    }

    private static AI_Medium aiWith(Hand hand, int bet, int trickScore) {
        AI_Medium ai = new AI_Medium("Test", AIPersonality.MEDIUM_BALANCED);
        ai.setHand(hand);
        ai.setBet(bet);
        for (int i = 0; i < trickScore; i++) {
            ai.wonTrick();
        }
        return ai;
    }

    // --- §2.2/§2.3: win-by-least is two rules, not one ---

    /**
     * §2.2 worked example B1: below bet (0/1), hand holds 2H/AH (trump
     * hearts), both winners over a weak non-trump running high card.
     * Confirms this branch stays "win with the highest winner" -- a
     * regression lock, not a new fix.
     */
    @Test
    public void belowBetStillPlaysHighestWinner() {
        Card two = new Card(Suit.HEARTS, CardValue.TWO);
        Card ace = new Card(Suit.HEARTS, CardValue.ACE);
        AI_Medium ai = aiWith(handOf(two, ace), 1, 0);
        List<Card> cardsPlayed = List.of(new Card(Suit.CLUBS, CardValue.THREE));
        List<Card> legalCards = ai.getHand().getCards();

        Card played = ai.tryToWin(cardsPlayed, legalCards, Suit.HEARTS);

        assertSame(ace, played);
    }

    /**
     * §2.3 worked example B2 (the actual bug fix): same hand shape (7H/AH),
     * but the AI is already above its bet (2/1) -- bonus already
     * unrecoverable this round. Should now play the lowest sufficient
     * winner (7H), preserving the Ace, instead of today's
     * getHighestValue(winners).
     */
    @Test
    public void aboveBetPlaysLowestSufficientWinner() {
        Card seven = new Card(Suit.HEARTS, CardValue.SEVEN);
        Card ace = new Card(Suit.HEARTS, CardValue.ACE);
        AI_Medium ai = aiWith(handOf(seven, ace), 1, 2);
        List<Card> cardsPlayed = List.of(new Card(Suit.CLUBS, CardValue.THREE));
        List<Card> legalCards = ai.getHand().getCards();

        Card played = ai.tryToWin(cardsPlayed, legalCards, Suit.HEARTS);

        assertSame(seven, played);
    }

    /**
     * Boundary test: trickScore == bet must still route to tryToLose, not
     * either of tryToWin's branches -- verified through the real strategy()
     * dispatch (inherited from AI_Easy, unchanged), not by calling tryToWin/
     * tryToLose directly, since the dispatch itself is what's under test
     * here. Hand is void in the led suit (clubs) so both the trump winner
     * (AH) and the non-trump loser (2D) are legally available -- if
     * dispatch were wrong (routed to tryToWin instead), the AI would win
     * with the Ace instead of safely losing with the 2 of diamonds.
     */
    @Test
    public void atBetRoutesToTryToLoseNotTryToWin() {
        Card ace = new Card(Suit.HEARTS, CardValue.ACE);
        Card two = new Card(Suit.DIAMONDS, CardValue.TWO);
        AI_Medium ai = aiWith(handOf(ace, two), 1, 1);
        List<Card> cardsPlayed = List.of(new Card(Suit.CLUBS, CardValue.THREE));

        Card played = ai.strategy(cardsPlayed, Suit.CLUBS, Suit.HEARTS, false);

        assertSame(two, played);
    }

    // --- §2.7: reacting-branch suit-void tiebreak inside tryToLose ---

    /**
     * Worked example, case 1 fires: hand holds 4D/9C/6C plus KS (trump), all
     * void in the led suit (hearts). Today's plain rule would play 9C
     * (highest of the three). The tiebreak should instead play 4D --
     * completing an actual void (the AI's last diamond) -- since case 1
     * always wins over case 2/the plain default when it applies.
     */
    @Test
    public void suitVoidTiebreakCase1PrefersVoidCompletingCard() {
        Card fourD = new Card(Suit.DIAMONDS, CardValue.FOUR);
        Card nineC = new Card(Suit.CLUBS, CardValue.NINE);
        Card sixC = new Card(Suit.CLUBS, CardValue.SIX);
        Card kingS = new Card(Suit.SPADES, CardValue.KING);
        AI_Medium ai = aiWith(handOf(fourD, nineC, sixC, kingS), 1, 1);
        List<Card> cardsPlayed = List.of(new Card(Suit.HEARTS, CardValue.THREE));

        Card played = ai.strategy(cardsPlayed, Suit.HEARTS, Suit.SPADES, false);

        assertSame(fourD, played);
    }

    /**
     * Worked example, case 2 fires (the user's K,Q vs. A,2,3 case): hand
     * holds KS/QS (2 cards, both precious) and AC/2C/3C (3 cards), plus a
     * trump card, all void in the led suit. Spades is numerically closer to
     * void (2 remaining) than clubs (3 remaining), but KS/QS both fail
     * cardValueWeight's cheapness bar -- so the tiebreak should play 3C
     * (the highest of clubs' qualifying candidates, 2C/3C), not touch the
     * spades, and specifically not AC either (AC's own value also fails the
     * bar despite clubs being the qualifying suit).
     */
    @Test
    public void suitVoidTiebreakCase2PrefersCheapPartialProgressOverPreciousNearVoid() {
        Card kingS = new Card(Suit.SPADES, CardValue.KING);
        Card queenS = new Card(Suit.SPADES, CardValue.QUEEN);
        Card aceC = new Card(Suit.CLUBS, CardValue.ACE);
        Card twoC = new Card(Suit.CLUBS, CardValue.TWO);
        Card threeC = new Card(Suit.CLUBS, CardValue.THREE);
        Card trump = new Card(Suit.HEARTS, CardValue.TWO);
        AI_Medium ai = aiWith(handOf(kingS, queenS, aceC, twoC, threeC, trump), 1, 1);
        List<Card> cardsPlayed = List.of(new Card(Suit.DIAMONDS, CardValue.THREE));

        Card played = ai.strategy(cardsPlayed, Suit.DIAMONDS, Suit.HEARTS, false);

        assertSame(threeC, played);
    }

    /**
     * Correct no-op: hand holds KD/QD/KC/QC (all precious, none completing
     * a void, none clearing the cheapness bar) plus a trump card. Neither
     * case should fire, so the tiebreak must fall back to today's plain
     * getHighestValue(losers) unchanged (the highest-value candidate --
     * asserted on value rather than a specific instance, since KD/KC tie).
     */
    @Test
    public void suitVoidTiebreakNoOpWhenNeitherCaseFires() {
        Card kingD = new Card(Suit.DIAMONDS, CardValue.KING);
        Card queenD = new Card(Suit.DIAMONDS, CardValue.QUEEN);
        Card kingC = new Card(Suit.CLUBS, CardValue.KING);
        Card queenC = new Card(Suit.CLUBS, CardValue.QUEEN);
        Card trump = new Card(Suit.HEARTS, CardValue.TWO);
        AI_Medium ai = aiWith(handOf(kingD, queenD, kingC, queenC, trump), 1, 1);
        List<Card> cardsPlayed = List.of(new Card(Suit.SPADES, CardValue.THREE));

        Card played = ai.strategy(cardsPlayed, Suit.SPADES, Suit.HEARTS, false);

        assertEquals(CardValue.KING, played.getValue());
    }

    /**
     * Regression (senior-code-reviewer catch on the AI v2 diff): trump must
     * never be a case-1/case-2 candidate in the tiebreak -- §0.6(a)'s
     * void+trump payoff is about being void in some *other* suit while
     * holding trump, not about voiding trump itself. Hand holds 2H (the AI's
     * only trump card) plus 9C/6C, and is void in the led suit (diamonds) so
     * all three are legal. An opponent has already trumped in with 5H (a
     * higher heart than the AI's 2H), so the running high card is 5H and all
     * three of the AI's cards are losers, including 2H. Before this fix,
     * case 1 wrongly treated 2H as void-completing (it's the AI's only
     * heart) and played it, discarding the AI's only trump for no reason.
     * With trump excluded from both cases: case 1 has no candidates (2
     * clubs held, so playing either leaves 1 remaining -- not an actual
     * void); case 2's grading (voidProgressWeight=0.5, cardValueWeight=1.0,
     * valueNormalizationScale=12.0 per MEDIUM_BALANCED) scores 9C at
     * 0.5 - (7/12.0) = -0.083 (fails) and 6C at 0.5 - (4/12.0) = +0.167
     * (qualifies) -- so the tiebreak should land on 6C, not fall through to
     * the plain-fallback default (which would also legitimately have been
     * allowed to pick 2H, since being highest-value-loser and being
     * void-preferred are different things -- this hand just doesn't reach
     * that fallback because 6C qualifies under case 2 first).
     */
    @Test
    public void suitVoidTiebreakNeverTreatsTrumpAsVoidCandidate() {
        Card twoH = new Card(Suit.HEARTS, CardValue.TWO);
        Card nineC = new Card(Suit.CLUBS, CardValue.NINE);
        Card sixC = new Card(Suit.CLUBS, CardValue.SIX);
        AI_Medium ai = aiWith(handOf(twoH, nineC, sixC), 1, 1);
        List<Card> cardsPlayed = List.of(
                new Card(Suit.DIAMONDS, CardValue.THREE),
                new Card(Suit.HEARTS, CardValue.FIVE));

        Card played = ai.strategy(cardsPlayed, Suit.DIAMONDS, Suit.HEARTS, false);

        assertSame(sixC, played);
    }

    /**
     * Regression: §2.2/§2.3's tryToWin branches are untouched by the §2.7
     * addition, since §2.7 only ever touches tryToLose. Re-runs the same
     * hand shape as the above-bet fix test directly through tryToWin.
     */
    @Test
    public void suitVoidAdditionDoesNotAffectTryToWin() {
        Card seven = new Card(Suit.HEARTS, CardValue.SEVEN);
        Card ace = new Card(Suit.HEARTS, CardValue.ACE);
        AI_Medium ai = aiWith(handOf(seven, ace), 1, 2);
        List<Card> cardsPlayed = List.of(new Card(Suit.CLUBS, CardValue.THREE));
        List<Card> legalCards = ai.getHand().getCards();

        Card played = ai.tryToWin(cardsPlayed, legalCards, Suit.HEARTS);

        assertSame(seven, played);
    }

    // --- §3.4: endgame forced-win tiebreak ---

    /**
     * Forced win (losers empty), both legal cards are trump: AH (the AI's
     * only trump run -> guaranteed-win-qualifying, tier 1) and 5H (trump,
     * not qualifying, tier 2). Both beat the running high card (2H). The
     * tiebreak should spend the dangerous, qualifying card (AH) now, not
     * today's getLowestValue(legalCards) (which would have picked 5H).
     */
    @Test
    public void forcedWinSpendsQualifyingTrumpBeforeNonQualifyingTrump() {
        Card ace = new Card(Suit.HEARTS, CardValue.ACE);
        Card five = new Card(Suit.HEARTS, CardValue.FIVE);
        AI_Medium ai = aiWith(handOf(ace, five), 1, 1);
        List<Card> cardsPlayed = List.of(new Card(Suit.HEARTS, CardValue.TWO));

        Card played = ai.strategy(cardsPlayed, Suit.HEARTS, Suit.HEARTS, true);

        assertSame(ace, played);
    }

    /**
     * Forced win, no trump held at all: both legal cards are non-trump
     * (AD/3D), both beating the running high card (2D). Per §3.4's
     * refinement, the non-trump tier is ranked high-to-low by value (an
     * off-suit Ace before an off-suit 3), not a flat bottom tier -- so the
     * tiebreak should play AD, not today's getLowestValue(legalCards)
     * (which would have picked 3D).
     */
    @Test
    public void forcedWinWithNoTrumpPrefersHighestNonTrumpNotLowest() {
        Card ace = new Card(Suit.DIAMONDS, CardValue.ACE);
        Card three = new Card(Suit.DIAMONDS, CardValue.THREE);
        AI_Medium ai = aiWith(handOf(ace, three), 1, 1);
        List<Card> cardsPlayed = List.of(new Card(Suit.DIAMONDS, CardValue.TWO));

        Card played = ai.strategy(cardsPlayed, Suit.DIAMONDS, Suit.HEARTS, false);

        assertSame(ace, played);
    }

    /**
     * Regression: the forced-win tiebreak only applies inside tryToLose's
     * losers-empty branch -- states 1/3 (tryToWin) are unaffected. Re-runs
     * the below-bet and above-bet worked examples directly through
     * tryToWin, confirming the same results as their own dedicated tests.
     */
    @Test
    public void forcedWinTiebreakDoesNotAffectTryToWinStates() {
        Card twoH = new Card(Suit.HEARTS, CardValue.TWO);
        Card aceH = new Card(Suit.HEARTS, CardValue.ACE);
        AI_Medium belowBet = aiWith(handOf(twoH, aceH), 1, 0);
        Card belowBetPlayed = belowBet.tryToWin(List.of(new Card(Suit.CLUBS, CardValue.THREE)),
                belowBet.getHand().getCards(), Suit.HEARTS);
        assertSame(aceH, belowBetPlayed);

        Card sevenH = new Card(Suit.HEARTS, CardValue.SEVEN);
        Card aceH2 = new Card(Suit.HEARTS, CardValue.ACE);
        AI_Medium aboveBet = aiWith(handOf(sevenH, aceH2), 1, 2);
        Card aboveBetPlayed = aboveBet.tryToWin(List.of(new Card(Suit.CLUBS, CardValue.THREE)),
                aboveBet.getHand().getCards(), Suit.HEARTS);
        assertSame(sevenH, aboveBetPlayed);
    }
}
