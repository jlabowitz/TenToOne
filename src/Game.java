import java.awt.*;
import java.awt.image.BufferStrategy;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/***
 Each game has up to 5 players and consists of 10 rounds with the number of cards in hand decreasing from 10 to 1.
 ***/
public class Game extends Canvas implements Runnable{
    @Serial
    private static final long serialVersionUID = 7694947508904043283L;
    public static final int WIDTH = 840, HEIGHT = WIDTH/12 * 9;
    private volatile Thread thread;
    //written by stop() (from either the game thread or an external caller)
    //and read every loop iteration by run() on the game thread -- volatile
    //so a write is guaranteed visible to run()'s while(running) check
    //without relying on stop()'s (now removed) synchronized lock for it.
    private volatile boolean running = false;
    private final Handler handler;
    private final MouseInput mouseInput;

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

    /**
     * Split out from the constructor so tests can skip popping a real
     * on-screen window (Window's own constructor calls game.start()) --
     * mirrors Window.buildFrame's split for the same reason. Package-
     * private and non-final so a test subclass can override it to a no-op.
     */
    void buildWindow() {
        new Window(WIDTH, HEIGHT, "Ten to One", this);
    }

    public Game(List<String> playerNames) {
        handler = new Handler();
        mouseInput = new MouseInput();
        this.addMouseListener(mouseInput);
        buildWindow();

        //handler.addObject(new Card(Suit.HEARTS, CardValue.ACE));

        int numPlayers = playerNames.size();
        assert numPlayers <= 5 : "You cannot have more than 5 players";


        players = new ArrayList<>();
        players.add(new Human(playerNames.get(0), mouseInput, handler));
        //players.add(new Human(playerNames.get(1), mouseInput));
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
        renderPlayers();
        //for each round
        while(roundIndex < 10) {
            int currentPlayer = roundStartingPlayer;
            Round round = new Round(numCardsThisRound(), getPlayers(), currentPlayer, WIDTH, HEIGHT, handler);

            //bet
            round.bet(currentPlayer);

            //play round
            round.playRound();

            //handler.removeAll();

            //snapshot bet/tricksTaken before adjustScores() resets each
            //player's trickScore to 0 -- see RoundResultRow's class doc
            List<RoundResultRow> results = snapshotRoundResults(getPlayers(), roundBonus);

            //adjust scores accordingly
            adjustScores();
            printScores();
            applyTotals(results, getPlayers());

            showRoundSummary(roundIndex, results);

            roundStartingPlayer = nextPlayer(roundStartingPlayer);
            this.roundIndex++;
        }
        //determine winner
        Player winner = determineWinner();
        System.out.println(winner.getName() + " won the game!");
        showGameOverBanner(winner);
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

    /**
     * Captures each player's name/isHuman/bet/tricksTaken before
     * adjustScores() resets trickScore to 0. Package-private + static so
     * TestGame can exercise it directly against a scripted bet/trickScore
     * setup without depending on the rest of play()'s flow.
     */
    static List<RoundResultRow> snapshotRoundResults(List<Player> players, int roundBonus) {
        List<RoundResultRow> rows = new ArrayList<>();
        for (Player player : players) {
            rows.add(new RoundResultRow(player.getName(), player.getID() == ID.HUMAN,
                    player.getBet(), player.getTrickScore(), roundBonus));
        }
        return rows;
    }

    /**
     * Fills in each row's totalAfter from players' current score, in the
     * same order snapshotRoundResults() produced rows -- must be called
     * after adjustScores() has actually run.
     */
    static void applyTotals(List<RoundResultRow> rows, List<Player> players) {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).totalAfter = players.get(i).getScore();
        }
    }

    /**
     * Adds the round-summary modal (ROADMAP item 1a) and blocks until the
     * player clicks anywhere to dismiss it, same click-anywhere convention
     * Human.nextTrick() already uses. Removed in a finally, mirroring
     * BetStepper's add-before/remove-after lifecycle.
     */
    private void showRoundSummary(int roundIndex, List<RoundResultRow> results) {
        RoundSummaryPanel panel = new RoundSummaryPanel(roundIndex, results);
        handler.addObject(panel);
        try {
            mouseInput.clearClicks();
            mouseInput.awaitClick();
        } finally {
            handler.removeObject(panel);
        }
    }

    /**
     * Adds the end-of-game outcome banner (ROADMAP item 1c) and never
     * removes it -- the render thread keeps drawing the final frame forever
     * after play() returns, so this is the last thing the player sees.
     */
    private void showGameOverBanner(Player winner) {
        boolean humanWon = getPlayers().stream()
                .filter(p -> p.getID() == ID.HUMAN)
                .findFirst()
                .map(human -> human == winner)
                .orElse(false);
        //stable sort (List.sort/TimSort) so ties keep seat order, not an
        //arbitrary reordering
        List<Player> standings = new ArrayList<>(getPlayers());
        standings.sort(Comparator.comparingInt(Player::getScore).reversed());
        handler.addObject(new GameOverBanner(winner, humanWon, standings));
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

    public boolean isRunning() {
        return running;
    }

    public synchronized void start() {
        thread = new Thread(this);
        thread.start();
        running = true;
    }

    /**
     * Not synchronized: run() itself calls stop() (on the game thread) right
     * after its own while(running) loop exits. If stop() were a synchronized
     * instance method, an external caller blocked here inside thread.join()
     * would still be holding this instance's monitor, and the game thread's
     * own self-invoked stop() call would block forever trying to enter that
     * same synchronized method -- a deadlock distinct from (and in addition
     * to) the self-join case below. Neither running nor thread needs the
     * monitor for correctness here: running is volatile (see field comment)
     * and thread is only ever written by start().
     */
    public void stop() {
        //flip this unconditionally, and before the join below, so run()'s
        //while(running) loop -- possibly still spinning on another thread
        //right now -- can observe it and exit. The old code set this only
        //after thread.join() returned, so a stop() call from any thread
        //other than `thread` itself would block forever: nothing would ever
        //flip running to let the loop that join() is waiting on finish.
        running = false;
        try {
            //a thread can't join itself: run() reaches this same stop() call
            //on `thread` once its own loop exits, so skip the join in that
            //case -- there's nothing left to wait for, run() is already
            //returning right after this call.
            if (thread != null && Thread.currentThread() != thread) {
                thread.join();
            }
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
                //a failed frame must not kill the render thread for the rest of the session
                try {
                    render();
                } catch (Exception e) {
                    e.printStackTrace();
                }
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

    public void renderPlayers() {
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            if (player.getID() == ID.AI) {
                int x = (WIDTH * (i - 1)) / (numPlayers() - 1);
                int y = 50;
                player.setX(x);
                player.setY(y);
                handler.addObject(player);
            }
            else if (player.getID() == ID.HUMAN) {
                int x = WIDTH;
                int y = HEIGHT - 150;
                player.setX(x);
                player.setY(y);
                handler.addObject(player);
            }
        }
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
