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
        for (int i = 0; i < numPlayers(); i++) {
            //play a card

            Player player = getPlayer(currentPlayer);
            Card card = player.playCard(cardsPlayed, leading, trump, trumpBroken);

            if (player.getID() == ID.AI) {
                card.setX(player.getX());
                card.setY(player.getY() + 20);
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
