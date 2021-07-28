import java.util.List;

public abstract class Player {
    private String name;
    private Hand hand;
    private int bet;
    private int score;
    private int trickScore;
    protected ID id;

    public Player() {
        this(null);
    }

    public Player(String name) {
        this.name = name;
        this.hand = null;
        this.score = 0;
        this.trickScore = 0;
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

    public int getScore() {
        return score;
    }

    public ID getID() {
        return id;
    }

    public void increaseScore(int n) {
        score += n;
    }

    public int getTrickScore() {
        return trickScore;
    }

    public void wonTrick() {
        trickScore++;
    }

    public void resetTrickScore() {
        trickScore = 0;
    }

    public abstract Card playCard(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken);

    public void nextTrick() {

    }

    //Method could be made static or put in another file
    public List<Card> legalCards(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        Hand hand = getHand();
        if (cardsPlayed.isEmpty()) {
            if (trumpBroken) {
                return hand.getCards();
            } else {
                List<Card> playable = hand.getCardsNotOfSuit(trump);
                if (!playable.isEmpty()) {
                    return playable;
                } else {
                    return hand.getCards();
                }
            }
        } else {
            if (hand.hasSuit(leading)) {
                return hand.getCardsOfSuit(leading);
            } else {
                return hand.getCards();
            }
        }
    }
}
