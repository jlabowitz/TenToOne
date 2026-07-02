import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Tests for Hand.cardAt hit-testing.
 *
 * Layout contract (mirrors Hand.render for the human hand):
 * card i sits at x = handX * i / numCards, y = handY, and covers the
 * half-open pixel ranges [x, x + Card.WIDTH) by [y, y + Card.HEIGHT).
 *
 * The human hand's x is Game.WIDTH (840) and its y is 480 in-game.
 */
public class TestHand {
    private static final int HAND_X = 840;
    private static final int HAND_Y = 480;

    private Hand humanHand(Card... cards) {
        Hand hand = new Hand(HAND_X, HAND_Y, ID.HUMAN);
        for (Card card : cards) {
            hand.addCard(card);
        }
        return hand;
    }

    @Test
    public void clickInsideFirstCardReturnsIt() {
        // Two cards: card 0 at x=0, card 1 at x=420, both at y=480
        Hand hand = humanHand(
                new Card(Suit.CLUBS, CardValue.FIVE),
                new Card(Suit.HEARTS, CardValue.ACE));
        assertSame(hand.getCard(0), hand.cardAt(30, 530));
    }

    @Test
    public void clickInsideSecondCardReturnsIt() {
        Hand hand = humanHand(
                new Card(Suit.CLUBS, CardValue.FIVE),
                new Card(Suit.HEARTS, CardValue.ACE));
        assertSame(hand.getCard(1), hand.cardAt(425, 481));
    }

    @Test
    public void clickInGapBetweenCardsReturnsNull() {
        // Card 0 covers x [0, 60); card 1 covers [420, 480)
        Hand hand = humanHand(
                new Card(Suit.CLUBS, CardValue.FIVE),
                new Card(Suit.HEARTS, CardValue.ACE));
        assertNull(hand.cardAt(200, 500));
    }

    @Test
    public void topLeftCornerIsInsideCard() {
        Hand hand = humanHand(
                new Card(Suit.CLUBS, CardValue.FIVE),
                new Card(Suit.HEARTS, CardValue.ACE));
        assertSame(hand.getCard(0), hand.cardAt(0, HAND_Y));
    }

    @Test
    public void rightAndBottomEdgesAreExclusive() {
        Hand hand = humanHand(
                new Card(Suit.CLUBS, CardValue.FIVE),
                new Card(Suit.HEARTS, CardValue.ACE));
        // one pixel past card 0's right edge (x = 0 + WIDTH = 60)
        assertNull(hand.cardAt(Card.WIDTH, 500));
        // one pixel past the bottom edge (y = 480 + HEIGHT = 580)
        assertNull(hand.cardAt(30, HAND_Y + Card.HEIGHT));
        // last in-bounds bottom pixel still hits
        assertSame(hand.getCard(0), hand.cardAt(30, HAND_Y + Card.HEIGHT - 1));
    }

    @Test
    public void clickAboveHandReturnsNull() {
        Hand hand = humanHand(
                new Card(Suit.CLUBS, CardValue.FIVE),
                new Card(Suit.HEARTS, CardValue.ACE));
        assertNull(hand.cardAt(30, HAND_Y - 1));
    }

    @Test
    public void tenCardHandHitsMiddleCard() {
        // 10 cards: card i at x = 840 * i / 10 = 84 * i
        Card[] cards = new Card[10];
        CardValue[] values = CardValue.values();
        for (int i = 0; i < 10; i++) {
            cards[i] = new Card(Suit.SPADES, values[i]);
        }
        Hand hand = humanHand(cards);
        assertSame(hand.getCard(7), hand.cardAt(84 * 7 + 30, 500));
    }

    @Test
    public void emptyHandReturnsNull() {
        Hand hand = humanHand();
        assertNull(hand.cardAt(30, 500));
    }
}
