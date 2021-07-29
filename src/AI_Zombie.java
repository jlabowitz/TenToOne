import java.util.List;

public class AI_Zombie extends AI {

    public AI_Zombie(String name) {
        super(name);
    }

    @Override
    public void bet(Suit trump) {
        int bet = getHand().getNumCards()/5;
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
