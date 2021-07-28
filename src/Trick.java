import java.util.ArrayList;
import java.util.List;

public class Trick {
    private final int numCards;
    private final List<Player> players;
    private final int currentPlayer;
    private final Suit trump;
    private boolean trumpBroken;

    private final int WIDTH, HEIGHT;
    private final Handler handler;


    public Trick(int numCards, List<Player> players, int currentPlayer, Suit trump, boolean trumpBroken, int width, int height, Handler handler) {
        this.numCards = numCards;
        this.players = players;
        this.currentPlayer = currentPlayer;
        this.trump = trump;
        this.trumpBroken = trumpBroken;
        this.WIDTH = width;
        this.HEIGHT = height;
        this.handler = handler;
    }

    private List<Card> playTrick(int currentPlayer, Suit trump) {
        List<Card> cardsPlayed = new ArrayList<>();
        Suit leading = null;
        for (int j = 0; j < numPlayers(); j++) {
            //play a card

            /*
            Hand currentHand = getPlayer(currentPlayer).getHand();
            if (currentHand.getId() == ID.HUMAN) {
                HANDLER.addObject(getPlayer(currentPlayer).getHand());

                for (Card card : currentHand.getCards()) {
                    HANDLER.addObject(card);
                }
            }
             */

            Card card = getPlayer(currentPlayer).playCard(cardsPlayed, leading, trump, trumpBroken);


            //would be easier if trick was its own class first
            //int cardIndex = convertCardIndex(j);


            Player player = getPlayer(currentPlayer);
            if (player.getID() == ID.AI) {
                card.setX(WIDTH * j / (numPlayers() - 1));
                card.setY(50);
                handler.addObject(card);
            }



            if (!trumpBroken && card.getSuit() == trump) {
                //TODO: NEED TO PASS THIS BOOLEAN OUT SOMEHOW (maybe make trump object?)
                trumpBroken = true;
            }
            cardsPlayed.add(card);
            if (j == 0) {
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


}
