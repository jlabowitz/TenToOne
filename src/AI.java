import java.util.List;

public class AI extends Player{
    AI(String name) {
        super(name);
    }

    @Override
    public void bet() {
        setBet(getHand().getNumCards()/5);
    }

    @Override
    public Card playCard(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        System.out.println(getHand().getCard(0));
        return getHand().getCard(0);
    }
}
