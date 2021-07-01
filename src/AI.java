import java.util.List;

public class AI extends Player{
    AI(String name) {
        super(name);
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
        Card played = strategy1(cardsPlayed, leading, trump, trumpBroken); //getHand().playCard(0);
        hand.playCard(played);
        System.out.println(getName() + " played " + played);
        return played;
    }

    //Tries to lose if their trickScore is equal to their bet, win otherwise
    public Card strategy1(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        int bet = getBet();
        int trickScore = getTrickScore();

        List<Card> legalCards = legalCards(cardsPlayed, leading, trump, trumpBroken);

        //temp solution
        Card played = legalCards.get(0);
        //assert getHand().playCard(played) : played + " is not in " + getName() + "'s hand."; //playCard is a destructive method and should not be used in assert
        return played;

        /* Use this code for final strategy
        if (trickScore == bet) {
            // try to lose

        } else {
            //try to win, unless something higher has already been played
        }
         */
    }
}
