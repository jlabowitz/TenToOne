import java.util.ArrayList;
import java.util.List;

public abstract class AI extends Player{
    public AI(String name) {
        super(name);
        id = ID.AI;
    }

    @Override
    public Card playCard(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        Hand hand = getHand();
        System.out.println(hand);
        Card played = strategy(cardsPlayed, leading, trump, trumpBroken); //getHand().playCard(0);
        hand.playCard(played);
        System.out.println(getName() + " played " + played);
        return played;
    }

    public abstract Card strategy(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken);

    /*** Get all cards that will win ***/
    public List<Card> getLosingCards(Card highest, List<Card> legalCards, Suit trump) {
        List<Card> losers = new ArrayList<>();
        for (Card card : legalCards) {
            if (!Round.isHigher(highest, card, trump)) {
                losers.add(card);
            }
        }
        return losers;
    }

    /*** Get all cards that will win ***/
    public List<Card> getWinningCards(Card highest, List<Card> legalCards, Suit trump) {
        List<Card> winners = new ArrayList<>();
        for (Card card : legalCards) {
            if (Round.isHigher(highest, card, trump)) {
                winners.add(card);
            }
        }
        return winners;
    }


    public Card getLowestValue(List<Card> cards) {
        Card lowest = cards.get(0);
        for (Card card : cards) {
            if (card.getValue().compareTo(lowest.getValue()) < 0) {
                lowest = card;
            }
        }
        return lowest;
    }


    public Card getHighestValue(List<Card> cards) {
        Card highest = cards.get(0);
        for (Card card : cards) {
            if (card.getValue().compareTo(highest.getValue()) > 0) {
                highest = card;
            }
        }
        return highest;
    }

    public int countSuit(List<Card> cards, Suit suit) {
        int count = 0;
        for (Card card : cards) {
            if (card.getSuit().equals(suit)) {
                count++;
            }
        }
        return count;
    }

    private int countValue(List<Card> cards, CardValue value) {
        int count = 0;
        for (Card card : cards) {
            if (card.getValue().equals(value)) {
                count++;
            }
        }
        return count;
    }

    public int countTopValues(List<Card> cards, int numValues) {
        CardValue[] vals = CardValue.values();
        int total = 0;
        while (numValues > 0) {
            total += countValue(cards, vals[vals.length - numValues]);
            numValues--;
        }
        return total;
    }
}
