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
}
