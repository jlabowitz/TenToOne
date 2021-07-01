import java.awt.*;
import java.awt.image.BufferStrategy;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/***
 Each game has up to 5 players and consists of 10 rounds with the number of cards in hand decreasing from 10 to 1.
 ***/
public class Game extends Canvas implements Runnable{
    @Serial
    private static final long serialVersionUID = 7694947508904043283L;
    public static final int WIDTH = 640, HEIGHT = WIDTH/12 * 9;
    private Thread thread;
    private boolean running = false;
    private Handler handler;

    private Deck deck;
    private List<Player> players;
    private int round;
    private int roundStartingPlayer;
    private boolean trumpBroken;
    private final int roundBonus = 10;

    private static List<String> names = new ArrayList<>() {{
        add("You");
        add("Player Two");
        add("Player Three");
        add("Player Four");
        add("Player Five");
    }};

    public Game() {
        this(names);
    }

    /*
    public Game(int numPlayers) {
        assert numPlayers <= 5 : "You cannot have more than 5 players";
        this(names.subList(0, numPlayers));
    }
    */

    public Game(List<String> playerNames) {
        new Window(WIDTH, HEIGHT, "Ten to One", this);
        handler = new Handler();

        int numPlayers = playerNames.size();
        assert numPlayers <= 5 : "You cannot have more than 5 players";


        players = new ArrayList<>();
        players.add(new Human(playerNames.get(0)));
        players.add(new Human(playerNames.get(1)));
        for (int i = 2; i < numPlayers; i++) {
            players.add(new Human(playerNames.get(i)));
        }
        round = 0;
        Random r = new Random();
        roundStartingPlayer = r.nextInt(numPlayers);
        trumpBroken = false;
    }

    private int numCardsThisRound() {
        return 10 - round;
    }

    private void deal() {
        for (Player player : players) {
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

    private void play() {
        //for each round
        while(round < 10) {
            //deal
            trumpBroken = false;
            deck = new Deck();
            deal();
            //set trump suit
            Suit trump = deck.draw().getSuit();

            System.out.println("The trump suit is " + trump);

            //bet
            int currentPlayer = roundStartingPlayer;
            bet(currentPlayer);

            //for each trick
            for (int i = 0; i < numCardsThisRound(); i++) {
                //for each player
                List<Card> cardsPlayed = new ArrayList<>();
                int trickStartPlayer = currentPlayer;
                Suit leading = null;
                for (int j = 0; j < numPlayers(); j++) {
                    //play a card
                    Card card = getPlayer(currentPlayer).playCard(cardsPlayed, leading, trump, trumpBroken);
                    cardsPlayed.add(card);
                    if (j == 0) {
                        leading = cardsPlayed.get(0).getSuit();
                    }
                    currentPlayer = nextPlayer(currentPlayer);
                }
                //determine winner of trick
                int winnerIndex = determineTrickWinner(cardsPlayed, trump);
                int actualWinner = convertWinnerIndex(winnerIndex, trickStartPlayer);

                Player winner = getPlayer(actualWinner);
                winner.wonTrick();
                System.out.println(winner.getName() + " won the trick.");

                //winner of trick starts next round;
                currentPlayer = winnerIndex;
            }
            //adjust scores accordingly
            adjustScores();
            printScores();
            roundStartingPlayer = nextPlayer(roundStartingPlayer);
            round++;
        }
        //determine winner
        Player winner = determineWinner();
        System.out.println(winner.getName() + " won the game!");
    }

    private void bet(int currentPlayer) {
        for (int i = 0; i < numPlayers(); i++) {
            // choose a bet
            getPlayer(currentPlayer).bet();
            currentPlayer = nextPlayer(currentPlayer);
        }
    }

    private int playTrick() {
        return 0;
    }

    static int determineTrickWinner(List<Card> cardsPlayed, Suit trump) {
        int winner = 0;
        Card winningCard = cardsPlayed.get(winner);
        for (int i = 1; i < cardsPlayed.size(); i++) {
            Card card = cardsPlayed.get(i);
            if (isHigher(winningCard, card, trump)) {
                winner = i;
                winningCard = card;
            }
        }
        return winner;
    }

    public int convertWinnerIndex(int winnerIndex, int trickStartPlayer) {
        return (winnerIndex + trickStartPlayer) % numPlayers();
    }

    /// Returns true if CARD is higher values than WINNINGCARD
    private static boolean isHigher(Card winningCard, Card card, Suit trump) {
        Suit suit = card.getSuit();
        if (suit != winningCard.getSuit()) {
            if (suit == trump) {
                return true;
            } else {
                return false;
            }
        } else if (card.getValue().compareTo(winningCard.getValue()) > 0) {
            return true;
        } else {
            return false;
        }
    }

    public void adjustScores() {
        for (Player player : getPlayers()) {
            if (player.getBet() == player.getTrickScore()) {
                player.increaseScore(player.getTrickScore() + roundBonus);
            } else {
                player.increaseScore(player.getTrickScore());
            }
            player.resetTrickScore();
        }
    }

    public void printScores() {
        for (Player player : getPlayers()) {
            System.out.println(player.getName() + " has " + player.getScore() + " points.");
        }
    }

    public Player determineWinner() {
        Player winner = getPlayer(0);
        for (Player player : players) {
            if (player.getScore() > winner.getScore()) {
                winner = player;
            }
        }
        return winner;
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

    private int numPlayers() {
        return players.size();
    }

    private int nextPlayer(int curr) {
        int next = (curr + 1) % numPlayers();
        return next;
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

    public synchronized void start() {
        thread = new Thread(this);
        thread.start();
        running = true;
    }

    public synchronized void stop() {
        try {
            thread.join();
            running = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        long lastTime = System.nanoTime();
        double amountOfTicks = 60.0;
        double ns = 1000000000 / amountOfTicks;
        double delta = 0;
        long timer = System.currentTimeMillis();
        int frames = 0;
        while (running) {
            long now = System.nanoTime();
            delta += (now - lastTime) / ns;
            lastTime = now;
            while (delta >= 1) {
                tick();
                delta--;
            }
            if (running) {
                render();
            }
            frames++;
            if (System.currentTimeMillis() - timer > 1000) {
                timer += 1000;
                System.out.println("FPS: " + frames);
                frames = 0;
            }
        }
        stop();
    }

    private void tick() {
        handler.tick();
    }

    private void render() {
        BufferStrategy bs = this.getBufferStrategy();
        if (bs == null) {
            this.createBufferStrategy(3);
            return;
        }

        Graphics g  = bs.getDrawGraphics();

        g.setColor(Color.black);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        handler.render(g);

        g.dispose();
        bs.show();
    }


    public static void main(String[] args) {
        Game game = new Game(names);
        game.play();
    }
}
