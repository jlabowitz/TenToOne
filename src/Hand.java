import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Hand extends GameObject{
    private List<Card> cards;

    public Hand(int width, int height, ID id) {
        super(width, height, id);
        this.cards = new ArrayList<>();
    }

    public int getNumCards() {
        return cards.size();
    }

    public List<Card> getCards() {
        return cards;
    }

    /** Sorts card on insertion **/
    public void addCard(Card newCard) {
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            if (newCard.getSuit() == card.getSuit()) {
                if (newCard.getValue().compareTo(card.getValue()) < 0) {
                    cards.add(i, newCard);
                    return;
                }
            }
            else if (newCard.getSuit().compareTo(card.getSuit()) < 0) {
                cards.add(i, newCard);
                return;
            }
        }
        cards.add(newCard);
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

    @Override
    public void tick() {

    }

    @Override
    public void render(Graphics g) {
        int x = getX();
        int y = getY();
        if (this.id == ID.HUMAN) {
            for (int i = 0; i < getNumCards(); i++) {
                cards.get(i).setX(x * i / getNumCards());
                cards.get(i).setY(y);
                cards.get(i).render(g);
            }
        }
    }
}
