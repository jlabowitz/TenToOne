import java.awt.*;
import java.awt.image.BufferStrategy;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

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
    private final KeyInput keyInput;

    private final List<Player> players;
    private int roundIndex;
    private int roundStartingPlayer;
    private final int roundBonus = 10;

    //ROADMAP item 1 (play-again restart): kept so restartForNewGame() can
    //re-invoke the Start Screen name-capture seam a second time -- this was
    //a constructor parameter only before, never stored.
    private final List<String> aiNames;

    //ROADMAP item 2 (achievement system): loaded once at construction, saved
    //again after every round-end/game-end/name-submission hook -- see
    //SaveStore's class doc for why this is never deferred to process exit.
    private final SaveStore saveStore;
    private final SaveData saveData;

    //ROADMAP item 2: long-lived for the whole session (registered with the
    //Handler once in the constructor via keepOnTop(), never removed --
    //mirrors how `players` stay registered across Play-Again per
    //restartForNewGame()'s own doc) so an achievement queued during the
    //Start Screen (the Bapi easter egg fires at name-submission time,
    //before any Round exists) is never dropped. keepOnTop() (see Handler's
    //class doc) keeps it rendering above every full-canvas modal that comes
    //and goes over the session -- RoundSummaryPanel, GameOverBanner,
    //RulesView, AchievementsView, StartScreen -- not just whichever one
    //happened to exist when the toast was first added. Only starts actually
    //ticking/rendering once play() calls activate() -- see AchievementToast's
    //class doc.
    private final AchievementToast achievementToast;

    //ROADMAP item 2: ephemeral per-game tracking for FLAWLESS_GAME/
    //COMEBACK_KID -- reset every new game by restartForNewGame(), not
    //persisted (SaveData only stores permanent unlock state).
    private int roundsHitBonusThisGame;
    private boolean wasSoleLastAtHalfway;

    //AI-only: the human's name is captured live via the Start Screen
    //(captureHumanName), not passed in as a list slot -- see the constructor.
    private static final List<String> names = new ArrayList<>() {{
        add("Player One");
        add("Player Two");
        add("Player Three");
        add("Player Four");
    }};

    public Game() {
        this(names);
    }

    /**
     * Split out from the constructor so tests can skip popping a real
     * on-screen window (Window's own constructor calls game.start()) --
     * mirrors Window.buildFrame's split for the same reason. Package-
     * private and non-final so a test subclass can override it to a no-op.
     */
    void buildWindow() {
        new Window(WIDTH, HEIGHT, "Ten to One", this);
    }

    /**
     * ROADMAP item 2: new seam, package-private and non-final, mirroring
     * buildWindow()/captureHumanName()'s existing testability pattern -- a
     * test subclass overrides this to point at a temp file instead of the
     * real {@code ~/.tentoone}, so constructing a (Headless)Game in a test
     * never touches the real save file.
     */
    SaveStore buildSaveStore() {
        return new SaveStore();
    }

    public Game(List<String> aiNames) {
        handler = new Handler();
        mouseInput = new MouseInput();
        keyInput = new KeyInput();
        this.addMouseListener(mouseInput);
        this.addKeyListener(keyInput);
        this.setFocusable(true);

        saveStore = buildSaveStore();
        saveData = saveStore.load();
        achievementToast = new AchievementToast();
        handler.keepOnTop(achievementToast);

        buildWindow();

        //handler.addObject(new Card(Suit.HEARTS, CardValue.ACE));

        this.aiNames = aiNames;
        String humanName = captureHumanName(aiNames);

        int numPlayers = aiNames.size() + 1;
        assert numPlayers <= 5 : "You cannot have more than 5 players";

        players = new ArrayList<>();
        players.add(new Human(humanName, mouseInput, handler, achievementToast, saveData));
        for (String aiName : aiNames) {
            players.add(new AI_Easy(aiName));
        }
        roundIndex = 0;
        Random r = new Random();
        roundStartingPlayer = r.nextInt(numPlayers);
    }

    /**
     * New seam (ROADMAP item 1, backend half), package-private and
     * non-final -- mirrors buildWindow()'s existing testability seam so a
     * test subclass can override it to a canned name instead of blocking on
     * a real click nothing in a test ever delivers. Called right after
     * buildWindow() has made the frame visible, before any Player is
     * constructed.
     */
    String captureHumanName(List<String> aiNames) {
        return runStartScreen(aiNames);
    }

    /**
     * Blocking click-loop for the pre-launch Start Screen. Reuses the same
     * StartScreen instance across a Rules round-trip (nested RulesView is
     * shown on top via its own showBlocking call, then this loop resumes) so
     * a partially-typed name survives visiting Rules and coming back.
     */
    private String runStartScreen(List<String> aiNames) {
        //ROADMAP item 2: the stat line's data is a snapshot of saveData as of
        //this StartScreen's construction -- fine, since saveData only
        //changes via this same class's round-end/game-end/name-submission
        //hooks, none of which run while a StartScreen is on screen.
        StartScreen startScreen = new StartScreen(saveData.gamesPlayed, saveData.highScore, saveData.bestWinStreakEver);
        handler.addObject(startScreen);
        keyInput.setTarget(startScreen);
        this.requestFocusInWindow();
        try {
            mouseInput.clearClicks();
            while (true) {
                Point click = mouseInput.awaitClick();
                StartScreen.Control control = startScreen.controlAt(click.x, click.y);
                if (control == null) {
                    continue;
                }
                switch (control) {
                    case RULES:
                        RulesView.showBlocking(handler, mouseInput, achievementToast);
                        mouseInput.clearClicks();
                        continue;
                    case ACHIEVEMENTS:
                        AchievementsView.showBlocking(handler, mouseInput, saveData, achievementToast);
                        mouseInput.clearClicks();
                        continue;
                    case START:
                        String typedName = startScreen.getName().trim();
                        if (!typedName.isEmpty()) {
                            //ROADMAP item 2: the Bapi easter egg, checked at
                            //name-submission time -- same permanent-unlock
                            //semantics as every other achievement, so
                            //re-entering "Bapi" after it's already unlocked
                            //is a no-op here (checkNameSubmission returns
                            //false) and doesn't re-fire the toast.
                            if (AchievementEngine.checkNameSubmission(saveData, typedName)) {
                                saveStore.save(saveData);
                                achievementToast.enqueue(Achievement.BAPI_EASTER_EGG);
                            }
                            return typedName;
                        }
                        continue;
                }
            }
        } finally {
            handler.removeObject(startScreen);
            keyInput.setTarget(null);
        }
    }

    private int numCardsThisRound() {
        return 10 - roundIndex;
    }

    private void play() {
        renderPlayers();
        //ROADMAP item 2: real gameplay begins here -- flips the toast queue
        //live so anything enqueued during the Start Screen (the Bapi easter
        //egg fires at name-submission time, before this point) renders on
        //the first frame of actual play rather than during/underneath the
        //Start Screen or the transition into it. See AchievementToast's
        //class doc.
        achievementToast.activate();
        //ROADMAP item 1 (play-again restart): outer loop runs forever --
        //the only way this process ever exits is the player closing the
        //window (Window.java sets JFrame.EXIT_ON_CLOSE), same as before this
        //change. renderPlayers() above stays a one-time call: it does
        //handler.addObject(player) for every player, so calling it again in
        //here would double-add the same Player instances to the Handler's
        //CopyOnWriteArrayList, silently doubling every per-frame tick()/
        //render() call per player.
        while (true) {
            //for each round
            while (roundIndex < 10) {
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

                //ROADMAP item 2: round-end achievement checks -- SCORE_OVER_50/
                //_100 against the human's running score, PERFECT_ROUND for this
                //round's bonus hit, plus the two pieces of ephemeral per-game
                //state FLAWLESS_GAME/COMEBACK_KID need at game-end. Written to
                //disk immediately after (not deferred to game-end), matching
                //this feature's "no clean-shutdown hook" design.
                RoundResultRow humanRow = results.stream().filter(row -> row.isHuman).findFirst()
                        .orElseThrow(() -> new IllegalStateException("No human row in round results"));
                List<Achievement> newlyUnlockedThisRound =
                        AchievementEngine.checkRoundEnd(saveData, humanRow.totalAfter, humanRow.bonusHit);
                if (humanRow.bonusHit) {
                    roundsHitBonusThisGame++;
                }
                //Round-5 halfway snapshot: roundIndex is still 4 here (it
                //increments below, after this block), i.e. this is right
                //after the round where roundIndex was 4 -- the 5th of 10.
                if (roundIndex == 4) {
                    List<Integer> otherScores = results.stream()
                            .filter(row -> !row.isHuman)
                            .map(row -> row.totalAfter)
                            .collect(Collectors.toList());
                    wasSoleLastAtHalfway = AchievementEngine.isSoleLastPlace(humanRow.totalAfter, otherScores);
                }
                saveStore.save(saveData);
                achievementToast.enqueueAll(newlyUnlockedThisRound);

                showRoundSummary(roundIndex, results);

                roundStartingPlayer = nextPlayer(roundStartingPlayer);
                this.roundIndex++;
            }
            //determine winner
            Player winner = determineWinner();
            System.out.println(winner.getName() + " won the game!");

            //ROADMAP item 2: game-end achievement/stat checks -- updates
            //gamesPlayed/gamesWon/highScore/currentWinStreak/bestWinStreakEver
            //and checks FIRST_VICTORY/TEN_GAMES_PLAYED/WIN_STREAK_3/5/10/
            //FLAWLESS_GAME/COMEBACK_KID, all *before* showGameOverBanner so the
            //banner can reflect the just-updated values (new-high-score/
            //streak lines).
            boolean humanWon = isHumanWinner(winner);
            Player human = getHumanPlayer();
            int finalHumanScore = human.getScore();
            int previousHighScore = saveData.highScore;
            int previousWinStreak = saveData.currentWinStreak;
            boolean flawlessGame = isFlawlessGame(roundsHitBonusThisGame);

            List<Achievement> newlyUnlockedThisGame = AchievementEngine.checkGameEnd(
                    saveData, humanWon, finalHumanScore, flawlessGame, wasSoleLastAtHalfway);
            saveStore.save(saveData);
            achievementToast.enqueueAll(newlyUnlockedThisGame);

            boolean newHighScore = finalHumanScore > previousHighScore;
            int streakToReport = humanWon ? saveData.currentWinStreak : previousWinStreak;

            GameOverBanner banner = showGameOverBanner(winner, newHighScore, streakToReport);
            awaitPlayAgain(banner);
            handler.removeObject(banner);
            restartForNewGame();
        }
    }

    /**
     * ROADMAP item 1: blocks until the "Play Again" hotspot is clicked, same
     * exact shape as RulesView.showBlocking's click loop. Any other click
     * (e.g. a miss-click elsewhere on the frozen banner) is silently
     * ignored -- matches every other hotspot's convention in this codebase,
     * no error feedback on a miss-click.
     *
     * Code-review Finding 1: also checks ACHIEVEMENTTOAST's click-to-dismiss
     * hotspot ahead of the Play Again check, same as RulesView/
     * AchievementsView's showBlocking loops -- this is exactly the screen
     * (game-end streak/first-victory unlocks are enqueued right before the
     * banner shows) the feature most needed to be dismissible on, per that
     * finding. Package-private (not private), mirroring
     * buildWindow/captureHumanName's existing testability-seam pattern, so
     * TestGame can drive this directly -- see getMouseInput/
     * getAchievementToast below.
     */
    void awaitPlayAgain(GameOverBanner banner) {
        mouseInput.clearClicks();
        while (true) {
            Point click = mouseInput.awaitClick();
            if (achievementToast.isToastHotspot(click.x, click.y)) {
                achievementToast.dismiss();
                continue;
            }
            if (banner.isPlayAgainHotspot(click.x, click.y)) {
                return;
            }
        }
    }

    /** Test-only accessor (package-private) -- see awaitPlayAgain's dismiss-wiring test in TestGame. */
    MouseInput getMouseInput() {
        return mouseInput;
    }

    /** Test-only accessor (package-private) -- see awaitPlayAgain's dismiss-wiring test in TestGame. */
    AchievementToast getAchievementToast() {
        return achievementToast;
    }

    /**
     * ROADMAP item 1: resets every player's per-game state on the same
     * Player instances (never re-adds them to handler -- see play()'s
     * comment on the double-add trap), resets round bookkeeping, and sends
     * the player back through the Start Screen (the user's explicit
     * decision -- Play Again does not skip straight into a fresh game).
     * Package-private and non-final, mirroring buildWindow/captureHumanName's
     * existing testability seam, so a test can exercise this directly
     * without driving play()'s full click-driven loop.
     */
    void restartForNewGame() {
        for (Player player : getPlayers()) {
            player.resetForNewGame();
        }
        roundIndex = 0;
        //re-randomize rather than carry over wherever roundStartingPlayer
        //drifted to after 10 rounds of nextPlayer() cycling -- matches the
        //constructor's original logic exactly, confirmed by game-designer as
        //the fairer, more legible choice.
        roundStartingPlayer = new Random().nextInt(numPlayers());

        //ROADMAP item 2: fresh per-game achievement tracking for the new game
        //-- these are ephemeral (not persisted), so a new game must start
        //clean rather than carrying over the just-finished game's counts.
        roundsHitBonusThisGame = 0;
        wasSoleLastAtHalfway = false;

        String humanName = captureHumanName(aiNames);
        getPlayers().stream()
                .filter(player -> player.getID() == ID.HUMAN)
                .findFirst()
                .ifPresent(human -> human.setName(humanName));
    }

    /** Test-only accessor (package-private) -- see restartForNewGame()'s tests in TestGame. */
    int getRoundStartingPlayer() {
        return roundStartingPlayer;
    }

    /** Test-only accessor (package-private) -- see restartForNewGame()'s tests in TestGame. */
    int getRoundIndex() {
        return roundIndex;
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
     * ROADMAP item 2: FLAWLESS_GAME's real boundary -- true only if every
     * one of the game's 10 rounds hit the bonus, not e.g. 9 of 10.
     * roundsHitBonusThisGame is accumulated round-by-round in play()'s loop
     * (incremented once per round whose bonus was hit) and this is checked
     * once at game-end. Pulled out as its own package-private + static
     * method, mirroring snapshotRoundResults/applyTotals's existing
     * testability-seam pattern, so this exact "all 10, not almost-all"
     * threshold is unit-testable without driving play()'s full
     * click-scripted 10-round loop.
     */
    static boolean isFlawlessGame(int roundsHitBonusThisGame) {
        return roundsHitBonusThisGame == 10;
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
     * Adds the end-of-game outcome banner (ROADMAP item 1c). Since ROADMAP
     * item 1's play-again restart, this is no longer forever -- the banner
     * is removed once the player clicks Play Again (see play()'s
     * awaitPlayAgain/removeObject/restartForNewGame sequence); the return
     * value lets the caller pass this exact instance to awaitPlayAgain and
     * then hand it back to handler.removeObject().
     */
    private GameOverBanner showGameOverBanner(Player winner, boolean newHighScore, int streakToReport) {
        boolean humanWon = isHumanWinner(winner);
        //stable sort (List.sort/TimSort) so ties keep seat order, not an
        //arbitrary reordering
        List<Player> standings = new ArrayList<>(getPlayers());
        standings.sort(Comparator.comparingInt(Player::getScore).reversed());
        GameOverBanner banner = new GameOverBanner(winner, humanWon, standings, newHighScore, streakToReport);
        handler.addObject(banner);
        return banner;
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

    /**
     * ROADMAP item 2: shared by play()'s game-end achievement checks and
     * showGameOverBanner (which used to compute this same thing inline) --
     * true if the given winner is the human seat, not an AI.
     */
    private boolean isHumanWinner(Player winner) {
        return getPlayers().stream()
                .filter(p -> p.getID() == ID.HUMAN)
                .findFirst()
                .map(human -> human == winner)
                .orElse(false);
    }

    /** ROADMAP item 2: the human player is always seated first (see the constructor), but looked up defensively rather than assuming index 0. */
    private Player getHumanPlayer() {
        return getPlayers().stream()
                .filter(p -> p.getID() == ID.HUMAN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No human player found"));
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
