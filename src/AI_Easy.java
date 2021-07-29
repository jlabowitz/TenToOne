import java.util.ArrayList;
import java.util.List;

public class AI_Easy extends AI{
    private final int NUM_HIGH_TRUMP = 6;
    private final int NUM_HIGH_CARDS = 2;
    private final double HIGH_TRUMP_PERCENT = 1;
    private final double HIGH_CARDS_PERCENT = 1;

    public AI_Easy(String name) {
        super(name);
    }

    @Override
    public void bet(Suit trump) {
        Hand hand = getHand();
        int numHighTrump = countTopValues(hand.getCardsOfSuit(trump), (int) (NUM_HIGH_TRUMP * numCardsFactor()));
        int numHighCards = countTopValues(hand.getCardsNotOfSuit(trump), (int) (NUM_HIGH_CARDS * numCardsFactor()));

        int bet = (int) (numHighTrump * HIGH_TRUMP_PERCENT + numHighCards * HIGH_CARDS_PERCENT);

        setBet(bet);
        System.out.println(getName() + " bets " + bet);
    }

    private double numCardsFactor() {
        int numCards = getHand().getNumCards();
        //if numCards is higher, then the factor should be lower
        return (-1.0 / 9) * numCards + 19.0 / 9;
    }

    //Tries to lose if their trickScore is equal to their bet, win otherwise
    @Override
    public Card strategy(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        int bet = getBet();
        int trickScore = getTrickScore();
        List<Card> legalCards = legalCards(cardsPlayed, leading, trump, trumpBroken);
        if (trickScore == bet) {
            return tryToLose(cardsPlayed, legalCards, trump);
        } else {
            return tryToWin(cardsPlayed, legalCards, trump);
        }
    }

    public Card tryToLose(List<Card> cardsPlayed, List<Card> legalCards, Suit trump) {
        if (cardsPlayed.isEmpty()) {
            //if not all cards are trump, remove trump cards
            //consider removing this if statement
            if (countSuit(legalCards, trump) != legalCards.size()) {
                List<Card> legalNoTrump = new ArrayList<>();
                for (Card card : legalCards) {
                    if (!card.getSuit().equals(trump)) {
                        legalNoTrump.add(card);
                    }
                }
                return getLowestValue(legalNoTrump);
            }
            return getLowestValue(legalCards);
        }

        Card highest = cardsPlayed.get(Round.determineTrickWinner(cardsPlayed, trump));
        List<Card> losers = getLosingCards(highest, legalCards, trump);

        if (losers.isEmpty()) {
            //try to win by smallest possible margin
            return getLowestValue(legalCards);

        } else {
            //play highest card that will lose
            return getHighestValue(losers);
        }
    }

    public Card tryToWin(List<Card> cardsPlayed, List<Card> legalCards, Suit trump) {
        if (cardsPlayed.isEmpty()) {
            //TODO: this could be smarter (either purposely select trump or not)
            return getHighestValue(legalCards);
        }
        Card highest = cardsPlayed.get(Round.determineTrickWinner(cardsPlayed, trump));
        List<Card> winners = getWinningCards(highest, legalCards, trump);

        if (winners.isEmpty()) {
            //try to lose by largest possible margin
            return getLowestValue(legalCards);
        } else {
            return getHighestValue(winners);
        }
    }
}
