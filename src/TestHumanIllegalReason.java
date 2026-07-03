import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for Human.illegalReason(cardsPlayed, leading), the pure branch logic
 * backing the illegal-move feedback message (ROADMAP item 1).
 *
 * Player.legalCards() has two independent branches: leading (cardsPlayed
 * empty) and following (cardsPlayed non-empty). Each has its own distinct
 * illegal-move reason, so illegalReason must not collapse them into one
 * generic message -- a leading-branch violation is "don't lead trump early,"
 * not "follow suit."
 */
public class TestHumanIllegalReason {

    @Test
    public void emptyCardsPlayedGivesTrumpNotBrokenMessage() {
        List<Card> cardsPlayed = Collections.emptyList();
        assertEquals("Trump hasn't been broken yet -- lead a different suit.",
                Human.illegalReason(cardsPlayed, Suit.HEARTS));
    }

    @Test
    public void nonEmptyCardsPlayedGivesFollowSuitMessageWithLedSuitName() {
        List<Card> cardsPlayed = List.of(new Card(Suit.HEARTS, CardValue.ACE));
        assertEquals("You must follow suit -- play a Hearts card.",
                Human.illegalReason(cardsPlayed, Suit.HEARTS));
    }

    @Test
    public void followSuitMessageInterpolatesCorrectLedSuit() {
        List<Card> cardsPlayed = List.of(new Card(Suit.CLUBS, CardValue.KING));
        assertEquals("You must follow suit -- play a Spades card.",
                Human.illegalReason(cardsPlayed, Suit.SPADES));
    }
}
