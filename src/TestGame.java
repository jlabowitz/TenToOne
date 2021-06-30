import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestGame {
    @Test
    public void determineTrickWinnerTest() {
        List<Card> cardsPlayed = new ArrayList<>();
        cardsPlayed.add(new Card(Suit.CLUBS, CardValue.TEN));
        cardsPlayed.add(new Card(Suit.CLUBS, CardValue.ACE));
        cardsPlayed.add(new Card(Suit.DIAMONDS, CardValue.FIVE));
        cardsPlayed.add(new Card(Suit.HEARTS, CardValue.ACE));
        cardsPlayed.add(new Card(Suit.CLUBS, CardValue.FOUR));

        Suit trump = Suit.DIAMONDS;
        int actual = Game.determineTrickWinner(cardsPlayed, trump);
        assertEquals(2, actual);
    }
}
