import java.util.ArrayList;
import java.util.List;
/***
 Each game has up to 5 players and consists of 10 rounds with the number of cards in hand decreasing from 10 to 1.
 ***/
public class Game {
    private Deck deck;
    private List<Player> players;
    private int round;

    private static List<String> names = new ArrayList<>() {{
        add("Player One");
        add("Player Two");
        add("Player Three");
        add("Player Four");
        add("Player Five");
    }};

    /*
    public Game(int numPlayers) {
        assert numPlayers <= 5 : "You cannot have more than 5 players";
        this(names.subList(0, numPlayers));
    }
    */

    public Game(List<String> playerNames) {
        int numPlayers = playerNames.size();
        assert numPlayers <= 5 : "You cannot have more than 5 players";

        deck = new Deck();
        players = new ArrayList();
        for (int i = 0; i < numPlayers; i++) {
            players.add(new Player(playerNames.get(i)));
        }
        round = 0;
        deal();
    }

    private int numCardsThisRound() {
        return 10 - round;
    }

    private void deal() {
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            String name = player.getName();
            assert player.getHand() == null || player.getHand().getNumCards() == 0 : String.format("Player '%s' hand is not empty", name);
        }
        initializeHands();

        int numCards = numCardsThisRound();

        for (int i = 0; i < numCards; i++) {
            for (Player player : players) {
                Card card = deck.draw();
                //Should not be using getter method to add card
                player.getHand().addCard(card);
            }
        }
    }

    //Improve
    public Player getPlayer(int i) {
        assert i < players.size();
        //add other boundary as well
        return players.get(i);
    }

    public List<Player> getPlayers() {
        return players;
    }

    public Deck getDeck() {
        return deck;
    }

    private void initializeHands() {
        for (Player player : players) {
            if (player.getHand() == null) {
                player.setHand(new Hand());
            }
        }
    }




    public static void main(String[] args) {
        Game game = new Game(names);
        for (Player player : game.players) {
            Hand hand = player.getHand();
            System.out.println(hand.getCards());
        }
        System.out.println(game.getDeck());
    }
}
