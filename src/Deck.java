import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Deck {

    private List<Card> cards = new ArrayList<>();

    public Deck() {
        generateDeck();
        shuffleDeck();
    }

    private void generateDeck() {
        Suit[] suits = Suit.values();
        CardValue[] values = CardValue.values();

        for (Suit suit : suits) {
            for (CardValue value : values) {
                cards.add(new Card(suit, value));
            }
        }
    }

    private void shuffleDeck() {
        Collections.shuffle(cards);
    }

    public Card draw() {
        assert cards.size() > 0 : "There are no cards in the deck";
        return cards.remove(0);
    }

    public int count() {
        return cards.size();
    }

    @Override
    public String toString() {
        return cards.toString();
    }
}
