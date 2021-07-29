import java.util.ArrayList;
import java.util.List;

public class AI_Easy extends AI{
    public AI_Easy(String name) {
        super(name);
    }

    //Tries to lose if their trickScore is equal to their bet, win otherwise
    @Override
    public Card strategy(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        int bet = getBet();
        int trickScore = getTrickScore();
        List<Card> legalCards = legalCards(cardsPlayed, leading, trump, trumpBroken);

        if (trickScore == bet) {
            // try to lose

            if (cardsPlayed.isEmpty()) {
                //if not all cards are trump, remove trump cards
                if (countSuit(legalCards, trump) != legalCards.size()) {
                    List<Card> legalNoTrump = new ArrayList<>();
                    for (Card card : legalCards) {
                        if (!card.getSuit().equals(trump)) {
                            legalNoTrump.add(card);
                        }
                    }
                    return findLowest(legalNoTrump);
                }
                return findLowest(legalCards);
            }

            Card highest = cardsPlayed.get(Round.determineTrickWinner(cardsPlayed, trump));

            List<Card> losers = findLose(highest, legalCards, trump);

            //if nothing in losers
            if (losers.isEmpty()) {
                //try to win by smallest possible margin
                return findLowest(legalCards);

            } else {
                //play highest card that will lose
                return findHighest(losers);

            }

        } else {
            if (cardsPlayed.isEmpty()) {
                //TODO: this could be smarter (either purposely select trump or not
                return findHighest(legalCards);
            }

            Card highest = cardsPlayed.get(Round.determineTrickWinner(cardsPlayed, trump));
            //try to win, unless something higher has already been played
            List<Card> winners = findWin(highest, legalCards, trump);

            //if nothing in winners
            if (winners.isEmpty()) {
                //play lowest card

                return findLowest(legalCards);
            } else {
                //play highest card
                return findHighest(winners);
            }
        }
    }


    /*** Get all cards that will win ***/
    public List<Card> findLose(Card highest, List<Card> legalCards, Suit trump) {
        List<Card> losers = new ArrayList<>();
        for (Card card : legalCards) {
            if (!Round.isHigher(highest, card, trump)) {
                losers.add(card);
            }
        }
        return losers;
    }

    /*** Get all cards that will win ***/
    public List<Card> findWin(Card highest, List<Card> legalCards, Suit trump) {
        List<Card> winners = new ArrayList<>();
        for (Card card : legalCards) {
            if (Round.isHigher(highest, card, trump)) {
                winners.add(card);
            }
        }
        return winners;
    }


    public Card findLowest(List<Card> cards) {
        /*//Don't remove all cards if they only have trump left
        if (countSuit(cards, trump) != cards.size()) {
            for (Card card: cards) {
                if (card.getSuit().equals(trump)) {
                    cards.remove(card);
                }
            }
        }*/

        Card lowest = cards.get(0);
        for (Card card : cards) {
            if (card.getValue().compareTo(lowest.getValue()) < 0) {
                lowest = card;
            }
        }
        return lowest;
    }


    public Card findHighest(List<Card> cards) {
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

}
