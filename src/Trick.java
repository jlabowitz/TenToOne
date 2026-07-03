import java.util.ArrayList;
import java.util.List;

public class Trick {
    private final List<Player> players;
    private int currentPlayer;
    private final Suit trump;
    private boolean trumpBroken;

    private final int WIDTH, HEIGHT;
    private final Handler handler;


    public Trick(List<Player> players, int currentPlayer, Suit trump, boolean trumpBroken, int width, int height, Handler handler) {
        this.players = players;
        this.currentPlayer = currentPlayer;
        this.trump = trump;
        this.trumpBroken = trumpBroken;
        this.WIDTH = width;
        this.HEIGHT = height;
        this.handler = handler;
    }

    public List<Card> play() {
        List<Card> cardsPlayed = new ArrayList<>();
        Suit leading = null;
        Card runningHighCard = null;
        for (int i = 0; i < numPlayers(); i++) {
            //play a card

            Player player = getPlayer(currentPlayer);
            Card card = player.playCard(cardsPlayed, leading, trump, trumpBroken);

            if (player.getID() == ID.AI) {
                card.setX(player.getX());
                // +30, not +20: leaves enough clearance below the AI's name
                // text for the high-card ring (drawn up to 14px above the
                // card) to not visually collide with it. Player.render's
                // trickScore/score lines were pushed down (+165/+185) to
                // give the ring room below the card too.
                card.setY(player.getY() + 30);
            } else {
                card.setX((WIDTH - 100) / 2);
                card.setY(HEIGHT - 300);
            }
            handler.addObject(card);

            if (!trumpBroken && card.getSuit() == trump) {
                trumpBroken = true;
            }
            cardsPlayed.add(card);
            if (i == 0) {
                leading = cardsPlayed.get(0).getSuit();
                for (Player p : players) {
                    p.setLeadingSuit(leading);
                }
            }

            //recompute the running high card over cards played so far -- not
            //new game logic, just an earlier/more frequent call site for
            //Round.determineTrickWinner/isHigher, which already implements
            //this comparison for the trick's final card list
            int highIndex = Round.determineTrickWinner(cardsPlayed, trump);
            Card newHighCard = cardsPlayed.get(highIndex);
            if (newHighCard != runningHighCard) {
                if (runningHighCard != null) {
                    runningHighCard.setHighCard(false);
                }
                newHighCard.setHighCard(true);
                runningHighCard = newHighCard;
            }

            currentPlayer = nextPlayer(currentPlayer);
        }
        getPlayer(0).nextTrick();
        return cardsPlayed;
    }

    //Improve
    private Player getPlayer(int i) {
        assert 0 <= i && i < players.size() : i + " is not a valid player";
        //add other boundary as well
        return players.get(i);
    }

    private int numPlayers() {
        return players.size();
    }

    private int nextPlayer(int curr) {
        return (curr + 1) % numPlayers();
    }

    public boolean getTrumpBroken() {
        return trumpBroken;
    }
}
