import java.util.ArrayList;
import java.util.List;

public class AI_Easy extends AI{
    /**
     * ROADMAP item 27/persistent-game-state design doc §2a: permanent,
     * cosmetic-rename-proof identity id for save-file player-identity fields
     * -- see PlayerArchetypeRegistry. AI_Medium overrides archetypeId() to
     * return its own personality's id instead of inheriting this one.
     */
    static final String ARCHETYPE_ID = "ai_easy";

    private final int NUM_HIGH_TRUMP = 6;
    private final int NUM_HIGH_CARDS = 2;
    private final double HIGH_TRUMP_PERCENT = 1;
    private final double HIGH_CARDS_PERCENT = 1;

    public AI_Easy(String name) {
        super(name);
    }

    @Override
    public String archetypeId() {
        return ARCHETYPE_ID;
    }

    @Override
    public void bet(BettingContext context) {
        Hand hand = getHand();
        int naturalBet = naturalBet(context.trump());
        // ROADMAP item 1 (design/ai-and-polish.md §3): the one sanctioned
        // patch to this AI's bet output, not its strategy -- see AI.
        // roundAwayFromForbiddenBet's doc.
        int bet = roundAwayFromForbiddenBet(naturalBet, hand.getNumCards(), context.sumOfPriorBets(),
                context.isLastBettor(), context.totalBetsCannotEqualTricks());

        setBet(bet);
        System.out.println(getName() + " bets " + bet);
    }

    /**
     * ROADMAP item 1 (design/ai-and-polish.md §4.4 step 1): extracted
     * verbatim from this class's former inline bet() body so AI_Medium can
     * reuse the exact same hand-strength read as its own naturalBet, without
     * duplicating the formula -- pure behavior-preserving relocation, no
     * change to the computation or its output. AI_Medium also reuses this
     * for its last-round non-trump "is this card high enough" check (design
     * doc §5): for a 1-card hand, naturalBet(trump) on a non-trump card
     * reduces to exactly AI_Easy's own "high card" threshold (numHighTrump
     * is always 0 with no trump card held), so no separate threshold logic
     * is needed there either.
     */
    protected int naturalBet(Suit trump) {
        Hand hand = getHand();
        int numHighTrump = countTopValues(hand.getCardsOfSuit(trump), (int) (NUM_HIGH_TRUMP * numCardsFactor()));
        int numHighCards = countTopValues(hand.getCardsNotOfSuit(trump), (int) (NUM_HIGH_CARDS * numCardsFactor()));
        return (int) (numHighTrump * HIGH_TRUMP_PERCENT + numHighCards * HIGH_CARDS_PERCENT);
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
