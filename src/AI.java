import java.util.List;

public class AI extends Player{
    public AI(String name) {
        super(name);
        id = ID.AI;
    }

    @Override
    public void bet() {
        int bet = getHand().getNumCards()/5;
        System.out.println(getName() + " bets " + bet);
        setBet(bet);
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

    /*** Zombie AI ***/
    public Card strategy(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        List<Card> legalCards = legalCards(cardsPlayed, leading, trump, trumpBroken);
        return legalCards.get(0);
    }

}
