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

    /**
     * Positions each card from this hand's current x/y and the card's index,
     * exactly as render draws them. Card i sits at x = handX * i / numCards,
     * y = handY, and covers the half-open pixel ranges [x, x + Card.WIDTH)
     * by [y, y + Card.HEIGHT).
     */
    private void layoutCards() {
        int numCards = getNumCards();
        for (int i = 0; i < numCards; i++) {
            Card card = cards.get(i);
            card.setX(getX() * i / numCards);
            card.setY(getY());
        }
    }

    /**
     * Returns the card of this hand at pixel (px, py), or null if the point
     * hits no card. Uses the same layout as render. Iterates from the last
     * card backwards so the topmost-drawn card wins if cards ever overlap.
     */
    public Card cardAt(int px, int py) {
        layoutCards();
        for (int i = getNumCards() - 1; i >= 0; i--) {
            Card card = cards.get(i);
            if (px >= card.getX() && px < card.getX() + Card.WIDTH
                    && py >= card.getY() && py < card.getY() + Card.HEIGHT) {
                return card;
            }
        }
        return null;
    }

    @Override
    public void tick() {

    }

    @Override
    public void render(Graphics g) {
        if (this.id == ID.HUMAN) {
            layoutCards();
            for (int i = 0; i < getNumCards(); i++) {
                cards.get(i).render(g);
            }
        }
    }
}
