import java.util.List;

public class AI_Zombie extends AI {
    /** ROADMAP item 27/persistent-game-state design doc §2a -- see AI_Easy.ARCHETYPE_ID's doc. */
    static final String ARCHETYPE_ID = "ai_zombie";

    public AI_Zombie(String name) {
        super(name);
    }

    @Override
    public String archetypeId() {
        return ARCHETYPE_ID;
    }

    @Override
    public void bet(BettingContext context) {
        int maxBet = getHand().getNumCards();
        int naturalBet = maxBet / 5;
        // ROADMAP item 1 (design/ai-and-polish.md §3): the one sanctioned
        // patch to this AI's bet output, not its strategy -- see AI.
        // roundAwayFromForbiddenBet's doc.
        int bet = roundAwayFromForbiddenBet(naturalBet, maxBet, context.sumOfPriorBets(),
                context.isLastBettor(), context.totalBetsCannotEqualTricks());
        setBet(bet);
        System.out.println(getName() + " bets " + bet);
    }

    /*** Zombie AI ***/
    @Override
    public Card strategy(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        List<Card> legalCards = legalCards(cardsPlayed, leading, trump, trumpBroken);
        return legalCards.get(0);
    }
}
