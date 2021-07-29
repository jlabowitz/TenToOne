import java.awt.*;
import java.awt.image.BufferStrategy;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/***
 Each game has up to 5 players and consists of 10 rounds with the number of cards in hand decreasing from 10 to 1.
 ***/
public class Game extends Canvas implements Runnable{
    @Serial
    private static final long serialVersionUID = 7694947508904043283L;
    public static final int WIDTH = 840, HEIGHT = WIDTH/12 * 9;
    private Thread thread;
    private boolean running = false;
    private final Handler handler;

    private final List<Player> players;
    private int roundIndex;
    private int roundStartingPlayer;
    private final int roundBonus = 10;

    private static final List<String> names = new ArrayList<>() {{
        add("Jacob");
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
        handler = new Handler();
        new Window(WIDTH, HEIGHT, "Ten to One", this);

        //handler.addObject(new Card(Suit.HEARTS, CardValue.ACE));

        int numPlayers = playerNames.size();
        assert numPlayers <= 5 : "You cannot have more than 5 players";


        players = new ArrayList<>();
        players.add(new Human(playerNames.get(0)));
        //players.add(new Human(playerNames.get(1)));
        for (int i = 1; i < numPlayers; i++) {
            players.add(new AI_Easy(playerNames.get(i)));
        }
        roundIndex = 0;
        Random r = new Random();
        roundStartingPlayer = r.nextInt(numPlayers);
    }

    private int numCardsThisRound() {
        return 10 - roundIndex;
    }

    private void play() {
        //for each round
        while(roundIndex < 10) {
            int currentPlayer = roundStartingPlayer;
            Round round = new Round(numCardsThisRound(), getPlayers(), currentPlayer, WIDTH, HEIGHT, handler);

            //bet
            round.bet(currentPlayer);

            //play round
            round.playRound();

            handler.removeAll();

            //adjust scores accordingly
            adjustScores();
            printScores();
            roundStartingPlayer = nextPlayer(roundStartingPlayer);
            this.roundIndex++;
        }
        //determine winner
        Player winner = determineWinner();
        System.out.println(winner.getName() + " won the game!");
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
        assert 0 <= i && i < players.size() : i + " is not a valid player";
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
        return (curr + 1) % numPlayers();
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
                //System.out.println("FPS: " + frames);
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

        g.setColor(Color.white);
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
