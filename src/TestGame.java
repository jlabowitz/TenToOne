import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestGame {
    //AI-only, since the human's name is now captured via the Start Screen
    //flow (captureHumanName), not passed in as a list slot -- see
    //HeadlessGame.captureHumanName below.
    private static final List<String> HEADLESS_PLAYER_NAMES = new ArrayList<>() {{
        add("Bot");
    }};

    /**
     * Test-only subclass that skips popping a real on-screen JFrame (and
     * thus the auto-start Window's constructor otherwise triggers via
     * game.start()), so start()/stop()/run() can be driven and observed
     * directly and deterministically from a test. Mirrors the same
     * testability rationale as Window.buildFrame (see TestWindowSizing):
     * split the window-creation step out so it can be skipped in tests
     * without touching the thread-lifecycle logic under test.
     */
    private static class HeadlessGame extends Game {
        HeadlessGame(List<String> aiNames) {
            super(aiNames);
        }

        @Override
        void buildWindow() {
            //Reuse Window.buildFrame (the same helper TestWindowSizing uses)
            //to give the Canvas a real, realized peer -- needed so run()'s
            //render() path behaves normally (bs.show() vsync-paces the loop)
            //instead of spinning unthrottled on a perpetually-failing
            //createBufferStrategy call. Skips frame.setVisible(true) and the
            //auto game.start() Window's real constructor performs, so
            //nothing is ever shown on screen and start()/stop() stay under
            //this test's manual control.
            Window.buildFrame(WIDTH, HEIGHT, "Ten to One", this);
        }

        /**
         * ROADMAP item 2: points every test-constructed Game at a fresh
         * JUnit-independent temp directory instead of the real
         * {@code ~/.tentoone} -- mirrors buildWindow()/captureHumanName's
         * existing testability-seam pattern. A fresh temp dir per instance
         * (rather than a shared fixture) keeps each test's save state
         * isolated from every other test in this file.
         */
        @Override
        SaveStore buildSaveStore() {
            try {
                Path dir = Files.createTempDirectory("tentoone-test");
                return new SaveStore(dir.resolve("save.properties"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * design/persistent-game-state.md Phase 6/7: same rationale as
         * buildSaveStore() above -- points every test-constructed Game at a
         * fresh temp file instead of the real {@code ~/.tentoone} game-state
         * save, so checkpoint saves fired during a test (and the
         * game-completion/restart clear() calls) never touch the real file.
         */
        @Override
        GameStateStore buildGameStateStore() {
            try {
                Path dir = Files.createTempDirectory("tentoone-test-gamestate");
                return new GameStateStore(dir.resolve("gamestate.json"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        /**
         * Skips the real Start Screen's blocking click-loop -- nothing in a
         * test ever delivers a click, so without this override every test
         * that constructs a HeadlessGame would hang forever at construction
         * time waiting on mouseInput.awaitClick(). Canned name mirrors
         * HEADLESS_PLAYER_NAMES' old human-name slot ("You") so existing
         * assertions keyed on that name don't need to change.
         */
        @Override
        String captureHumanName(List<String> aiNames) {
            return "You";
        }
    }

    /**
     * design/persistent-game-state.md Phase 8: same buildWindow() override
     * as HeadlessGame above (skip popping a real on-screen JFrame), but
     * routed through the new snapshot-reconstruction constructor instead of
     * the normal one -- there's no Start Screen/captureHumanName seam to
     * override here since that whole flow is skipped by construction.
     */
    private static class HeadlessGameFromSnapshot extends Game {
        HeadlessGameFromSnapshot(GameStateSnapshot snapshot) throws GameStateReconstructionException {
            super(snapshot);
        }

        @Override
        void buildWindow() {
            Window.buildFrame(WIDTH, HEIGHT, "Ten to One", this);
        }

        @Override
        SaveStore buildSaveStore() {
            try {
                Path dir = Files.createTempDirectory("tentoone-test-fromsnapshot");
                return new SaveStore(dir.resolve("save.properties"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        GameStateStore buildGameStateStore() {
            try {
                Path dir = Files.createTempDirectory("tentoone-test-gamestate-fromsnapshot");
                return new GameStateStore(dir.resolve("gamestate.json"));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /**
     * Regression test for two bugs in Game.stop(): (1) a self-join deadlock
     * -- run() calls stop() on its own background thread after its loop
     * exits, and a thread can't join itself; (2) an ordering bug -- running
     * used to flip false only *after* thread.join() returned, so a stop()
     * call from any other thread would block forever with nothing left to
     * flip running to let run()'s loop exit. Calling stop() from this
     * (external) test thread while the background thread is actively
     * looping exercises both paths: the external join only completes if
     * running flips promptly, and run()'s own subsequent self-invoked
     * stop() must not deadlock either. A hang here fails via the JUnit
     * timeout rather than blocking the suite forever.
     */
    @Test(timeout = 5000)
    public void stopFromExternalThreadDoesNotHangAndFlipsRunningFalse() throws Exception {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        game.start();
        //give the background thread a moment to actually enter its loop so
        //this exercises a real concurrent stop, not a stop-before-start race
        Thread.sleep(50);

        game.stop();

        assertFalse(game.isRunning());
    }

    /** stop() called before start() (thread still null) must not NPE. */
    @Test(timeout = 2000)
    public void stopWithoutStartDoesNotThrow() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);

        game.stop();

        assertFalse(game.isRunning());
    }

    @Test
    public void determineTrickWinnerTest() {
        List<Card> cardsPlayed = new ArrayList<>();
        cardsPlayed.add(new Card(Suit.DIAMONDS, CardValue.QUEEN)); //P5
        cardsPlayed.add(new Card(Suit.CLUBS, CardValue.FIVE)); //YOU
        cardsPlayed.add(new Card(Suit.SPADES, CardValue.ACE));
        cardsPlayed.add(new Card(Suit.SPADES, CardValue.FIVE));
        cardsPlayed.add(new Card(Suit.CLUBS, CardValue.THREE));

        Suit trump = Suit.HEARTS;
        int actual = Round.determineTrickWinner(cardsPlayed, trump);
        assertEquals(0, actual);
    }

    /**
     * Regression test: the human hand must already be at the player's
     * on-screen position when the round is dealt (on the game-logic thread),
     * before any click can be hit-tested. Previously the hand kept the
     * constructor's y = Game.HEIGHT (offscreen) until the render thread's
     * unsynchronized setY, so cardAt could miss every click and softlock.
     */
    @Test
    public void dealtHumanHandUsesPlayersOnScreenPosition() {
        Human human = new Human("You", new MouseInput(), new Handler(), new AchievementToast(), new SaveData());
        //Game.renderPlayers positions the human at (WIDTH, HEIGHT - 150)
        //on the game-logic thread before any Round is constructed
        human.setX(Game.WIDTH);
        human.setY(Game.HEIGHT - 150);

        List<Player> players = new ArrayList<>();
        players.add(human);
        players.add(new AI_Easy("Bot"));

        new Round(10, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        assertEquals(Game.WIDTH, human.getHand().getX());
        assertEquals(Game.HEIGHT - 150, human.getHand().getY());
    }

    /**
     * ROADMAP item 2: FLAWLESS_GAME's real boundary -- play() accumulates
     * roundsHitBonusThisGame by incrementing it once per round whose bonus
     * was hit, then checks isFlawlessGame() against that running count at
     * game-end. This drives that exact accumulate-then-threshold sequence
     * over a simulated 10-round game (mirroring play()'s own
     * `if (humanRow.bonusHit) roundsHitBonusThisGame++;` step) rather than
     * just asserting isFlawlessGame(9)/isFlawlessGame(10) in isolation: 9 of
     * 10 rounds hitting the bonus must NOT unlock FLAWLESS_GAME, only a
     * clean 10-for-10 does.
     */
    @Test
    public void flawlessGameRequiresAllTenRoundsToHitBonusNotNine() {
        boolean[] nineOfTenRoundsHitBonus =
                {true, true, true, true, true, true, true, true, true, false};
        int roundsHitBonusThisGame = 0;
        for (boolean bonusHit : nineOfTenRoundsHitBonus) {
            if (bonusHit) {
                roundsHitBonusThisGame++;
            }
        }
        assertFalse("9 of 10 rounds hitting the bonus must not count as flawless",
                Game.isFlawlessGame(roundsHitBonusThisGame));

        boolean[] tenOfTenRoundsHitBonus =
                {true, true, true, true, true, true, true, true, true, true};
        roundsHitBonusThisGame = 0;
        for (boolean bonusHit : tenOfTenRoundsHitBonus) {
            if (bonusHit) {
                roundsHitBonusThisGame++;
            }
        }
        assertTrue("all 10 rounds hitting the bonus must count as flawless",
                Game.isFlawlessGame(roundsHitBonusThisGame));
    }

    /**
     * Regression/spec test for the round-summary data snapshot (ROADMAP item
     * 1a): bet/tricksTaken must be captured before adjustScores() resets
     * trickScore to 0, bonusHit/roundDelta must reuse the same equality
     * check adjustScores() itself uses, and totalAfter must reflect the
     * score *after* adjustScores() has actually run. Exercises both sides of
     * the bonus-hit boundary in one round: one player bets exactly what they
     * take (bonus), the other doesn't (no bonus).
     */
    @Test
    public void snapshotRoundResultsCapturesBonusHitBoundary() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        List<Player> players = game.getPlayers();
        Player human = players.get(0); // "You" -- Game always seats the human first
        Player bot = players.get(1); // "Bot"

        human.setBet(3);
        for (int i = 0; i < 3; i++) {
            human.wonTrick();
        }
        bot.setBet(2);
        for (int i = 0; i < 4; i++) {
            bot.wonTrick();
        }

        int roundBonus = 10;
        List<RoundResultRow> results = Game.snapshotRoundResults(players, roundBonus);

        game.adjustScores();
        Game.applyTotals(results, players);

        RoundResultRow humanRow = results.get(0);
        assertEquals("You", humanRow.name);
        assertTrue(humanRow.isHuman);
        assertEquals(3, humanRow.bet);
        assertEquals(3, humanRow.tricksTaken);
        assertTrue("bet == tricksTaken must count as a bonus hit", humanRow.bonusHit);
        assertEquals(13, humanRow.roundDelta); // tricksTaken (3) + bonus (10)
        assertEquals(13, humanRow.totalAfter);
        //bonusHit must not have left trickScore un-reset for later rounds
        assertEquals(0, human.getTrickScore());

        RoundResultRow botRow = results.get(1);
        assertEquals("Bot", botRow.name);
        assertFalse(botRow.isHuman);
        assertEquals(2, botRow.bet);
        assertEquals(4, botRow.tricksTaken);
        assertFalse("bet != tricksTaken must not count as a bonus hit", botRow.bonusHit);
        assertEquals(4, botRow.roundDelta); // tricksTaken only, no bonus
        assertEquals(4, botRow.totalAfter);
    }

    /**
     * Regression/spec test for the new captureHumanName seam (ROADMAP item
     * 1, backend half): proves the override path is actually exercised at
     * construction time -- a distinct name (not the default "You" canned
     * value HeadlessGame otherwise returns) must end up as the Human
     * player's name, and the human must still be seated first ahead of the
     * AI players built from aiNames.
     */
    @Test
    public void captureHumanNameOverrideDeterminesHumanPlayerName() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES) {
            @Override
            String captureHumanName(List<String> aiNames) {
                return "Distinctly Named";
            }
        };

        Player human = game.getPlayers().get(0);
        assertEquals("Distinctly Named", human.getName());
        assertEquals(ID.HUMAN, human.getID());
        assertEquals("Bot", game.getPlayers().get(1).getName());
    }

    /**
     * ROADMAP item 1 (play-again restart): restartForNewGame() must reset
     * every player's per-game state (not just some), and must re-invoke the
     * captureHumanName seam and apply the result only to the human seat --
     * an AI's name must be untouched. Package-private (same testability
     * seam convention as buildWindow/captureHumanName) so this can be
     * exercised directly without driving play()'s full click-driven loop.
     */
    @Test
    public void restartForNewGameResetsAllPlayersAndReappliesHumanNameOnly() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES) {
            @Override
            String captureHumanName(List<String> aiNames) {
                return "Restarted Name";
            }
        };
        List<Player> players = game.getPlayers();
        for (Player player : players) {
            player.increaseScore(50);
            player.setBet(2);
            player.wonTrick();
            player.setTrickLeader(true);
            player.setLeadingSuit(Suit.HEARTS);
        }

        game.restartForNewGame();

        for (Player player : players) {
            assertEquals(0, player.getScore());
            assertFalse(player.hasBet());
            assertEquals(0, player.getBet());
            assertEquals(0, player.getTrickScore());
            assertFalse(player.isTrickLeader());
            assertNull(player.getLeadingSuit());
        }
        Player human = players.get(0);
        assertEquals(ID.HUMAN, human.getID());
        assertEquals("Restarted Name", human.getName());
        Player bot = players.get(1);
        assertEquals(ID.AI, bot.getID());
        assertEquals("Bot", bot.getName());
    }

    /**
     * ROADMAP item 1: roundStartingPlayer must be re-randomized on restart
     * (matching the constructor's own `new Random().nextInt(numPlayers())`
     * logic exactly, not carried over from wherever it drifted to after the
     * prior game's 10 rounds of nextPlayer() cycling) -- game-designer
     * confirmed re-randomizing is the fairer, more legible choice. Asserts
     * the result stays in bounds across many restarts rather than asserting
     * a specific value, since this is intentionally randomized.
     */
    @Test
    public void restartForNewGameReRandomizesRoundStartingPlayerWithinBounds() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        int numPlayers = game.getPlayers().size();
        for (int i = 0; i < 30; i++) {
            game.restartForNewGame();
            int startingPlayer = game.getRoundStartingPlayer();
            assertTrue(startingPlayer >= 0 && startingPlayer < numPlayers);
        }
    }

    /** roundIndex must go back to 0 so a restarted game replays all 10 rounds. */
    @Test
    public void restartForNewGameResetsRoundIndexToZero() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        game.restartForNewGame();
        assertEquals(0, game.getRoundIndex());
    }

    /**
     * design/persistent-game-state.md Phase 8: restartForNewGame() must clear
     * any resumable saved game -- a completed/restarted game shouldn't offer
     * resume. saveGameStateCheckpoint() is exercised directly (package-private
     * seam, mirroring this file's other direct-method-call testability
     * convention) rather than driving a full click-scripted round.
     */
    @Test
    public void restartForNewGameClearsAnyResumableGameStateSave() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        game.saveGameStateCheckpoint();
        assertTrue("a checkpoint save must make hasResumableGame() true", game.hasResumableGame());

        game.restartForNewGame();

        assertFalse("restartForNewGame() must clear the resumable save", game.hasResumableGame());
    }

    /** The hand persists between rounds, so every round must re-position it. */
    @Test
    public void redealtHumanHandIsRepositionedEachRound() {
        Human human = new Human("You", new MouseInput(), new Handler(), new AchievementToast(), new SaveData());
        human.setX(Game.WIDTH);
        human.setY(Game.HEIGHT - 150);

        List<Player> players = new ArrayList<>();
        players.add(human);
        players.add(new AI_Easy("Bot"));

        new Round(10, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        //simulate the round finishing (hands emptied) and the hand position
        //having gone stale before the next round is dealt
        for (Player player : players) {
            while (player.getHand().getNumCards() > 0) {
                player.getHand().playCard(0);
            }
        }
        human.getHand().setX(0);
        human.getHand().setY(Game.HEIGHT);

        new Round(9, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        assertEquals(Game.WIDTH, human.getHand().getX());
        assertEquals(Game.HEIGHT - 150, human.getHand().getY());
    }

    /**
     * Code-review Finding 1 (achievement-toast click-to-dismiss): awaitPlayAgain
     * is the third of the three missed call sites -- and per the finding, the
     * one this matters most for, since game-end streak/first-victory unlocks
     * are enqueued right before the banner this loop guards ever shows. Same
     * wiring-only smoke test as TestRulesView/TestAchievementsView's
     * showBlocking tests -- AchievementToast's own predicates are already
     * covered by TestAchievementToast. awaitPlayAgain was made package-private
     * (from private) specifically so this test can drive it directly, mirroring
     * this file's existing buildWindow/captureHumanName testability-seam
     * convention.
     */
    @Test(timeout = 5000)
    public void awaitPlayAgainDismissesToastHotspotClickBeforeCheckingPlayAgainHotspot() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        AchievementToast toast = game.getAchievementToast();
        toast.activate();
        toast.enqueue("Test Achievement");
        toast.tick();
        assertTrue("toast must actually be showing for isToastHotspot() to fire", toast.isShowingSomething());

        Player winner = game.getPlayers().get(0);
        GameOverBanner banner = new GameOverBanner(winner, true, new ArrayList<>(game.getPlayers()), false, 0);
        MouseInput mouseInput = game.getMouseInput();

        Thread clicker = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            deliverClick(mouseInput, 400, 20); // inside the toast's dismiss band, clear of Play Again
            deliverClick(mouseInput, 420, 477); // the real Play Again hotspot (see TestGameOverBanner)
        });
        clicker.start();

        game.awaitPlayAgain(banner);
        clicker.join();

        assertFalse("the first click (inside the toast band) must dismiss the toast rather than being ignored",
                toast.isShowingSomething());
    }

    /**
     * design/persistent-game-state.md Phase 8: reconstructs a live Game
     * directly from a GameStateSnapshot (no Start Screen, no normal
     * constructor path) and asserts its round/player/hand state matches the
     * snapshot -- including that the human's hand ends up positioned at the
     * player's actual on-screen position (Game.renderPlayers'
     * (WIDTH, HEIGHT - 150)), not left at whatever position the Round
     * reconstruction constructor saw before renderPlayers() had run (see
     * Round.repositionHumanHand()'s doc for why that ordering matters).
     */
    @Test
    public void constructsFromSnapshotWithPlayersAndRoundState() throws GameStateReconstructionException {
        GameStateSnapshot snapshot = new GameStateSnapshot();
        snapshot.roundIndex = 3;
        snapshot.roundStartingPlayer = 1;
        snapshot.roundsHitBonusThisGame = 2;
        snapshot.wasSoleLastAtHalfway = true;

        PlayerSnapshot human = new PlayerSnapshot();
        human.name = "Resumed Player";
        human.archetypeId = "human";
        human.score = 30;
        human.bet = 2;
        human.hasBet = true;
        human.trickScore = 1;
        human.hand.add(new CardSnapshot(Suit.HEARTS, CardValue.ACE));
        human.hand.add(new CardSnapshot(Suit.SPADES, CardValue.TWO));
        snapshot.players.add(human);

        PlayerSnapshot ai = new PlayerSnapshot();
        ai.name = "Bot";
        ai.archetypeId = "ai_easy";
        ai.score = 12;
        ai.bet = 1;
        ai.hasBet = true;
        ai.trickScore = 0;
        ai.hand.add(new CardSnapshot(Suit.CLUBS, CardValue.KING));
        ai.hand.add(new CardSnapshot(Suit.DIAMONDS, CardValue.THREE));
        snapshot.players.add(ai);

        RoundSnapshot round = new RoundSnapshot();
        round.trumpCard = new CardSnapshot(Suit.HEARTS, CardValue.QUEEN);
        round.trumpBroken = false;
        round.currentPlayer = 0;
        snapshot.round = round;

        HeadlessGameFromSnapshot game = new HeadlessGameFromSnapshot(snapshot);

        assertEquals(3, game.getRoundIndex());
        assertEquals(1, game.getRoundStartingPlayer());
        assertEquals(2, game.getPlayers().size());

        Player rebuiltHuman = game.getPlayers().get(0);
        assertEquals("Resumed Player", rebuiltHuman.getName());
        assertEquals(ID.HUMAN, rebuiltHuman.getID());
        assertEquals(30, rebuiltHuman.getScore());
        assertTrue(rebuiltHuman.hasBet());
        assertEquals(2, rebuiltHuman.getBet());

        Player rebuiltAi = game.getPlayers().get(1);
        assertEquals("Bot", rebuiltAi.getName());
        assertEquals(2, rebuiltAi.getHand().getNumCards());

        Round liveRound = game.getCurrentRound();
        assertTrue(liveRound != null);
        assertEquals(Suit.HEARTS, liveRound.getTrump());
        assertFalse(liveRound.getTrumpBroken());
        assertNull(liveRound.getCurrentTrick());

        assertEquals(Game.WIDTH, rebuiltHuman.getHand().getX());
        assertEquals(Game.HEIGHT - 150, rebuiltHuman.getHand().getY());
    }

    /** Synthesizes a left-click MouseEvent and delivers it straight to mouseInput's listener, same as an AWT click would. */
    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        Component dummy = new Canvas();
        mouseInput.mousePressed(new MouseEvent(dummy, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
