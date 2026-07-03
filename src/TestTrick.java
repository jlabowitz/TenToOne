import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Trick.play()'s incremental running-high-card tracking and
 * leading-suit broadcast, exercised via AI_Zombie (whose deterministic
 * strategy -- always play legalCards.get(0) -- lets a card sequence be
 * pinned down exactly by hand-building each player's Hand).
 *
 * Card played order here is player index order (0, 1, 2, ...), matching
 * Trick's currentPlayer rotation starting at 0.
 */
public class TestTrick {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private AI_Zombie playerWithHand(String name, Card... cards) {
        AI_Zombie player = new AI_Zombie(name);
        Hand hand = new Hand(0, 0, ID.AI);
        for (Card card : cards) {
            hand.addCard(card);
        }
        player.setHand(hand);
        return player;
    }

    /**
     * The invariant called out in the approved design spec: the card
     * holding the running high-card flag right after Trick.play() finishes
     * must be the same card Round.determineTrickWinner names as the winner
     * when run over the same (complete) card list -- and no other card in
     * the trick should be flagged.
     */
    @Test
    public void runningHighCardMatchesDetermineTrickWinnerAtTrickEnd() {
        List<Player> players = new ArrayList<>();
        players.add(playerWithHand("A", new Card(Suit.CLUBS, CardValue.FIVE)));
        players.add(playerWithHand("B", new Card(Suit.CLUBS, CardValue.ACE)));
        players.add(playerWithHand("C", new Card(Suit.HEARTS, CardValue.TWO))); // trump, wins
        Handler handler = new Handler();
        Trick trick = new Trick(players, 0, Suit.HEARTS, false, WIDTH, HEIGHT, handler);

        List<Card> cardsPlayed = trick.play();

        int winnerIndex = Round.determineTrickWinner(cardsPlayed, Suit.HEARTS);
        Card winningCard = cardsPlayed.get(winnerIndex);

        assertTrue(winningCard.isHighCard());
        for (Card card : cardsPlayed) {
            if (card != winningCard) {
                assertFalse(card + " should not be flagged as the high card", card.isHighCard());
            }
        }
    }

    /**
     * Exercises a case where the running high card must move mid-trick: B's
     * ace of clubs overtakes A's five of clubs, then C's trump two overtakes
     * B in turn -- by the end, only C's card is flagged.
     */
    @Test
    public void highCardMovesAsHigherCardsAreLaidDown() {
        List<Player> players = new ArrayList<>();
        Card five = new Card(Suit.CLUBS, CardValue.FIVE);
        Card ace = new Card(Suit.CLUBS, CardValue.ACE);
        Card trumpTwo = new Card(Suit.HEARTS, CardValue.TWO);
        players.add(playerWithHand("A", five));
        players.add(playerWithHand("B", ace));
        players.add(playerWithHand("C", trumpTwo));
        Handler handler = new Handler();
        Trick trick = new Trick(players, 0, Suit.HEARTS, false, WIDTH, HEIGHT, handler);

        trick.play();

        assertFalse(five.isHighCard());
        assertFalse(ace.isHighCard());
        assertTrue(trumpTwo.isHighCard());
    }

    /** No comparison is possible for the very first card -- it's automatically the running high card. */
    @Test
    public void firstCardPlayedBecomesRunningHighCard() {
        List<Player> players = new ArrayList<>();
        Card first = new Card(Suit.CLUBS, CardValue.TWO);
        players.add(playerWithHand("A", first));
        players.add(playerWithHand("B", new Card(Suit.CLUBS, CardValue.THREE)));
        Handler handler = new Handler();
        Trick trick = new Trick(players, 0, Suit.HEARTS, false, WIDTH, HEIGHT, handler);

        List<Card> cardsPlayed = trick.play();

        // B's three of clubs beats A's two of clubs, so by trick end A's
        // card should no longer be flagged -- confirming the flag did move
        // off the first card rather than sticking there permanently.
        assertFalse(first.isHighCard());
        assertTrue(cardsPlayed.get(1).isHighCard());
    }

    @Test
    public void leadingSuitIsBroadcastToAllPlayersAfterFirstCardLands() {
        List<Player> players = new ArrayList<>();
        players.add(playerWithHand("A", new Card(Suit.CLUBS, CardValue.FIVE)));
        players.add(playerWithHand("B", new Card(Suit.CLUBS, CardValue.ACE)));
        Handler handler = new Handler();
        Trick trick = new Trick(players, 0, Suit.HEARTS, false, WIDTH, HEIGHT, handler);

        trick.play();

        for (Player player : players) {
            assertEquals(Suit.CLUBS, player.getLeadingSuit());
        }
    }
}
