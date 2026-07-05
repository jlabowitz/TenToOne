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

    /**
     * ROADMAP item 1 (design/ai-and-polish.md §3): the one sanctioned patch
     * to AI_Easy/AI_Zombie's bet *output* -- a rounding/tie-break fix so
     * neither AI ever lands on the forbidden bet value, without changing
     * either's hand-strength *strategy* that produced naturalBet. Shared here
     * (rather than duplicated in each subclass) since the rule is identical
     * for both, per design doc §3:
     *
     * <ol>
     *   <li>If this AI isn't the last bettor, the setting is off, or
     *       naturalBet doesn't hit the forbidden value, naturalBet is
     *       returned unchanged.</li>
     *   <li>Otherwise: bump up to 1 if the forbidden value is 0 (can't round
     *       down); bump down to maxBet-1 if it's maxBet (can't round up);
     *       otherwise round down to forbiddenBet-1 (the conservative
     *       default -- an Easy/Zombie-tier AI shouldn't be reasoning about
     *       which direction is better, that's what distinguishes higher
     *       tiers).</li>
     * </ol>
     */
    protected static int roundAwayFromForbiddenBet(int naturalBet, int maxBet, int sumOfPriorBets,
                                                     boolean isLastBettor, boolean totalBetsCannotEqualTricks) {
        if (!totalBetsCannotEqualTricks || !isLastBettor) {
            return naturalBet;
        }
        // numCardsThisRound == maxBet here: every player's hand size equals
        // the round's card count (see Round.deal()), so this AI's own maxBet
        // already is the value Round.isLegalBet would call numCardsThisRound.
        int forbiddenBet = maxBet - sumOfPriorBets;
        if (naturalBet != forbiddenBet) {
            return naturalBet;
        }
        if (forbiddenBet == 0) {
            return 1;
        }
        if (forbiddenBet == maxBet) {
            return maxBet - 1;
        }
        return forbiddenBet - 1;
    }
}
