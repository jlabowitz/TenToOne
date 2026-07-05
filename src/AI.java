import java.util.ArrayList;
import java.util.Comparator;
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

    /**
     * ROADMAP item 1 (design/ai-and-polish.md §4.3): counts held trump cards
     * that form an unbroken run down from the Ace, entirely within this AI's
     * own hand -- e.g. holding the Ace and King counts both (2), since the
     * King can only be beaten by the Ace, and this AI already knows the Ace
     * isn't anywhere else (only one exists, and it's right here). Holding
     * only the King (no Ace) counts 0 -- the Ace could be elsewhere, so the
     * King isn't unconditionally safe. Needs nothing beyond the AI's own
     * hand -- no counting/observation of other players' cards at all.
     *
     * Shared here on the base AI class (not AI_Medium) rather than
     * duplicated per-tier, since AI_Hard/AI_Expert (design doc §4.3, §6) are
     * expected to upgrade this exact concept via real card-counting -- a
     * strict superset of this self-evident rule (counting can only add more
     * confirmed-safe cards, never fewer).
     */
    protected static int selfEvidentGuaranteedWins(Hand hand, Suit trump) {
        List<Card> held = new ArrayList<>(hand.getCardsOfSuit(trump));
        held.sort(Comparator.comparing(Card::getValue));

        CardValue[] values = CardValue.values();
        CardValue expectedNext = CardValue.ACE;
        int count = 0;
        for (int i = held.size() - 1; i >= 0; i--) {
            CardValue value = held.get(i).getValue();
            if (value != expectedNext) {
                break;
            }
            count++;
            int rankBelowIndex = expectedNext.ordinal() - 1;
            if (rankBelowIndex < 0) {
                break;
            }
            expectedNext = values[rankBelowIndex];
        }
        return count;
    }
}
