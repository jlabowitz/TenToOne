import java.util.List;

public abstract class Player {
    private String name;
    private Hand hand;
    private int bet;

    public Player() {
        this(null);
    }

    public Player(String name) {
        this.name = name;
        this.hand = null;
    }

    public String getName() {
        return name;
    }

    public Hand getHand() {
        return hand;
    }

    public void setHand(Hand hand) {
        this.hand = hand;
    }

    public int getBet() {
        return bet;
    }

    public void setBet(int bet) {
        this.bet = bet;
    }

    public abstract void bet();

    public abstract Card playCard(List<Card> cardsPlayed);
}
