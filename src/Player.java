import java.util.List;

public abstract class Player {
    private String name;
    private Hand hand;
    private int bet;
    private int score;
    private int trickScore;

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
}
