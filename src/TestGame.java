import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestGame {
    @Test
    public void determineTrickWinnerTest() {
        List<Card> cardsPlayed = new ArrayList<>();
        cardsPlayed.add(new Card(Suit.DIAMONDS, CardValue.QUEEN)); //P5
        cardsPlayed.add(new Card(Suit.CLUBS, CardValue.FIVE)); //YOU
        cardsPlayed.add(new Card(Suit.SPADES, CardValue.ACE));
        cardsPlayed.add(new Card(Suit.SPADES, CardValue.FIVE));
        cardsPlayed.add(new Card(Suit.CLUBS, CardValue.THREE));

        Suit trump = Suit.HEARTS;
        int actual = Round.determineTrickWinner(cardsPlayed, trump);
        assertEquals(0, actual);
    }

    /**
     * Regression test: the human hand must already be at the player's
     * on-screen position when the round is dealt (on the game-logic thread),
     * before any click can be hit-tested. Previously the hand kept the
     * constructor's y = Game.HEIGHT (offscreen) until the render thread's
     * unsynchronized setY, so cardAt could miss every click and softlock.
     */
    @Test
    public void dealtHumanHandUsesPlayersOnScreenPosition() {
        Human human = new Human("You", new MouseInput());
        //Game.renderPlayers positions the human at (WIDTH, HEIGHT - 150)
        //on the game-logic thread before any Round is constructed
        human.setX(Game.WIDTH);
        human.setY(Game.HEIGHT - 150);

        List<Player> players = new ArrayList<>();
        players.add(human);
        players.add(new AI_Easy("Bot"));

        new Round(10, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        assertEquals(Game.WIDTH, human.getHand().getX());
        assertEquals(Game.HEIGHT - 150, human.getHand().getY());
    }

    /** The hand persists between rounds, so every round must re-position it. */
    @Test
    public void redealtHumanHandIsRepositionedEachRound() {
        Human human = new Human("You", new MouseInput());
        human.setX(Game.WIDTH);
        human.setY(Game.HEIGHT - 150);

        List<Player> players = new ArrayList<>();
        players.add(human);
        players.add(new AI_Easy("Bot"));

        new Round(10, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        //simulate the round finishing (hands emptied) and the hand position
        //having gone stale before the next round is dealt
        for (Player player : players) {
            while (player.getHand().getNumCards() > 0) {
                player.getHand().playCard(0);
            }
        }
        human.getHand().setX(0);
        human.getHand().setY(Game.HEIGHT);

        new Round(9, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        assertEquals(Game.WIDTH, human.getHand().getX());
        assertEquals(Game.HEIGHT - 150, human.getHand().getY());
    }
}
