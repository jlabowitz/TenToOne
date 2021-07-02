import java.util.ArrayList;
import java.util.List;

public class Hand {
    private List<Card> cards;

    public Hand() {
        this.cards = new ArrayList<>();
    }

    public Hand(List<Card> cards) {
        this.cards = cards;
    }

    public int getNumCards() {
        return cards.size();
    }

    public List<Card> getCards() {
        return cards;
    }

    public void addCard(Card card) {
        cards.add(card);
    }

    public Card getCard(int index) {
        return cards.get(index);
    }

    public Card playCard(int index) {
        return cards.remove(index);
    }

    public void playCard(Card card) {
        cards.remove(card);
    }

    @Override
    public String toString() {
        return cards.toString();
    }

    public boolean hasSuit(Suit suit) {
        for (Card card: cards) {
            if (card.getSuit() == suit) {
                return true;
            }
        }
        return false;
    }

    public List<Card> getCardsOfSuit(Suit suit) {
        List<Card> suitCards = new ArrayList<>();
        for (Card card: cards) {
            if (card.getSuit() == suit) {
                suitCards.add(card);
            }
        }
        return suitCards;
    }

    public List<Card> getCardsNotOfSuit(Suit suit) {
        List<Card> suitCards = new ArrayList<>();
        for (Card card: cards) {
            if (card.getSuit() != suit) {
                suitCards.add(card);
            }
        }
        return suitCards;
    }
}
