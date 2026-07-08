import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.KeyEvent;
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
import static org.junit.Assert.fail;

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
         * assertions keyed on that name don't need to change. Still used
         * directly by establishFreshGameState()'s/resumeFromSavedGame()'s own
         * fallback branches.
         */
        @Override
        String captureHumanName(List<String> aiNames) {
            return "You";
        }

        /**
         * ROADMAP item 14: the constructor's primary path now calls this
         * (not captureHumanName() above) to also offer Resume at boot -- same
         * hang risk captureHumanName's own override exists to prevent, just
         * on the newer call site. Always reports a fresh New Game (never
         * "resumed"). Delegates to captureHumanName(aiNames) rather than a
         * second hardcoded "You" literal, so a test subclass that overrides
         * only captureHumanName (e.g. to supply a distinct canned name) still
         * gets that name applied via this, the constructor's actual primary
         * path -- see captureHumanNameOverrideDeterminesHumanPlayerName.
         */
        @Override
        StartScreenOutcome captureStartScreenOutcome(List<String> aiNames, boolean offerResume) {
            return StartScreenOutcome.name(captureHumanName(aiNames));
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
     * User-reported bug (live playthrough, 2026-07-07), root cause: every
     * normal fresh game boot -- not just a Menu/Resume round trip --
     * silently double-registered every Player with the Handler. The
     * constructor's own declined-Resume/corrupt-snapshot-fallback branches
     * called establishFreshGameState(), which calls renderPlayers() itself;
     * play()'s own unconditional first-thing renderPlayers() call then ran a
     * second time on top of it. Invisible to every other test in this file
     * because Player.tick()/render() have no logic side effects a JUnit
     * assertion can observe -- only visible on an actual screen, as doubled/
     * overlapping HUD text and garbled name rendering. Constructing a
     * HeadlessGame here already exercises the exact buggy branch (its
     * captureStartScreenOutcome override always reports a fresh New Game,
     * never "resumed"); this reproduces play()'s own call explicitly rather
     * than actually invoking play() (which blocks forever in its own
     * game loop).
     */
    @Test
    public void constructorDoesNotDoubleRegisterPlayersBeforePlayCallsRenderPlayers() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Handler handler = game.getHandler();
        for (Player player : game.getPlayers()) {
            assertEquals(player.getName() + " must not already be registered before play()'s own "
                            + "renderPlayers() call runs",
                    0, handler.object.stream().filter(o -> o == player).count());
        }

        game.renderPlayers(); // mirrors play()'s own first-thing call

        for (Player player : game.getPlayers()) {
            assertEquals(player.getName() + " must be registered exactly once after renderPlayers(), not doubled",
                    1, handler.object.stream().filter(o -> o == player).count());
        }
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

    // --- Review finding B: runStartScreen()'s Enter-submit polling loop had
    // zero test coverage -- every other test in this file bypasses it
    // entirely via HeadlessGame's captureStartScreenOutcome override. These
    // drive the real method directly (made package-private for exactly this,
    // mirroring awaitPlayAgain's own testability-seam precedent above),
    // injecting keystrokes via getKeyInput() the same way the toast test
    // above injects clicks via getMouseInput(). ---

    /**
     * The simplest real path through runStartScreen(): type a name and press
     * Enter, with no nested view or Resume involved. Confirms
     * consumeSubmitRequested()/attemptStartSubmit() actually wire together
     * correctly end to end -- previously only exercised piecemeal (StartScreen's
     * own submit()/consumeSubmitRequested() in isolation, attemptStartSubmit's
     * validation logic not at all from this loop).
     */
    @Test(timeout = 5000)
    public void runStartScreenEnterKeySubmitsTypedNameAsOutcome() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        KeyInput keyInput = game.getKeyInput();

        Thread typer = new Thread(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            typeChar(keyInput, 'A');
            typeChar(keyInput, 'l');
            typeChar(keyInput, 'e');
            typeChar(keyInput, 'x');
            pressEnter(keyInput);
        });
        typer.start();

        Game.StartScreenOutcome outcome = game.runStartScreen(HEADLESS_PLAYER_NAMES, false);
        typer.join();

        assertFalse(outcome.resumed);
        assertEquals("Alex", outcome.humanName);
    }

    /**
     * Code-review Finding A: a stray Enter pressed while a nested view
     * (Rules/Achievements/Settings/Stats) is showing on top of the Start
     * Screen must not leak through and fire the backgrounded StartScreen's
     * submit once the nested view closes -- keyInput.setTarget(startScreen)
     * stays pinned to the same instance the whole time nested views are
     * shown, so before this fix, a stray Enter's submitRequested flag
     * survived RulesView.showBlocking() returning and was wrongly consumed
     * on the very next loop iteration.
     *
     * Made maximally observable via offerResume=true + a real resumable
     * checkpoint: if the stray Enter leaks, runStartScreen() returns
     * StartScreenOutcome.resume() the instant Rules' Back button is clicked
     * (resumable's branch returns unconditionally, no validation) -- long
     * before the driver thread ever reaches its later, deliberate
     * type-a-name-and-click-Start steps below. The final deliberate step
     * uses a mouse click on Start (not Enter) deliberately: with
     * resumable=true, Enter *always* resolves to Resume by design (see
     * runStartScreen's own doc), so a second Enter here couldn't distinguish
     * "leaked" from "not leaked" -- both would return resume(). A real click
     * on Start's paired slot forces attemptStartSubmit's name path instead,
     * giving a StartScreenOutcome.name("Al") result that's only reachable if
     * the loop was still alive and waiting normally, not one that already
     * returned early.
     */
    @Test(timeout = 5000)
    public void staleEnterSubmitWhileNestedViewIsOpenDoesNotLeakIntoNextIteration() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        game.saveGameStateCheckpoint();
        assertTrue("a checkpoint save must make hasResumableGame() true", game.hasResumableGame());

        MouseInput mouseInput = game.getMouseInput();
        KeyInput keyInput = game.getKeyInput();

        Thread driver = new Thread(() -> {
            try {
                Thread.sleep(50);
                deliverClick(mouseInput, 300, 385); // Row 2's Rules button (always at this spot)
                Thread.sleep(50);
                pressEnter(keyInput); // stray Enter while RulesView is blocking on its own click loop
                Thread.sleep(50);
                deliverClick(mouseInput, 720, 549); // RulesView's Back button
                Thread.sleep(50);
                typeChar(keyInput, 'A');
                typeChar(keyInput, 'l');
                deliverClick(mouseInput, 500, 335); // Start's paired-with-Resume slot (Row 1)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        driver.start();

        Game.StartScreenOutcome outcome = game.runStartScreen(HEADLESS_PLAYER_NAMES, true);
        driver.join();

        assertFalse("a stray Enter pressed while Rules was open must not have leaked into an unconditional Resume",
                outcome.resumed);
        assertEquals("Al", outcome.humanName);
    }

    private static void typeChar(KeyInput keyInput, char c) {
        Component dummy = new Canvas();
        keyInput.keyTyped(new KeyEvent(dummy, KeyEvent.KEY_TYPED, System.currentTimeMillis(),
                0, KeyEvent.VK_UNDEFINED, c));
    }

    private static void pressEnter(KeyInput keyInput) {
        Component dummy = new Canvas();
        keyInput.keyPressed(new KeyEvent(dummy, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                0, KeyEvent.VK_ENTER, KeyEvent.CHAR_UNDEFINED));
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

    /**
     * User-reported bug (live playthrough, 2026-07-07): "closed the game,
     * then reopened the game and not all my cards show up in my hand." Root
     * cause -- a process-boot Resume (java Game's main()/run(), the
     * constructor's captureStartScreenOutcome()-reports-resumed branch) goes
     * through reconstructFromSnapshot(), which deliberately does NOT call
     * repositionHumanHand() (see its own doc: that's deferred to play()'s
     * first-thing renderPlayers() call). But play()'s renderPlayers() call
     * was never actually followed by a repositionHumanHand() call -- unlike
     * every other resume path (the Game(GameStateSnapshot) constructor,
     * resumeFromSavedGame()), which both already do this. Without it, the
     * human's Hand stays positioned at whatever positionHumanHand() saw
     * inside Round's reconstruction constructor -- i.e. the player's stale
     * pre-renderPlayers() (0,0) position -- so Hand.layoutCards()'s
     * `getX() * i / numCards` places every card at x=0, stacking them
     * exactly on top of each other. Exercises establishInitialRenderState()
     * directly (play()'s own extracted one-time render setup) rather than
     * actually invoking play() (which blocks forever in its own game loop).
     */
    @Test
    public void establishInitialRenderStateRepositionsHumanHandAfterBootTimeResume() throws IOException {
        Path gameStateFile = Files.createTempFile("tentoone-test-bootresume-gamestate", ".json");
        GameStateStore preloadStore = new GameStateStore(gameStateFile);

        GameStateSnapshot snapshot = new GameStateSnapshot();
        snapshot.roundIndex = 0;
        snapshot.roundStartingPlayer = 0;

        PlayerSnapshot human = new PlayerSnapshot();
        human.name = "You";
        human.archetypeId = "human";
        human.hasBet = true;
        human.bet = 2;
        human.hand.add(new CardSnapshot(Suit.HEARTS, CardValue.ACE));
        human.hand.add(new CardSnapshot(Suit.SPADES, CardValue.TWO));
        snapshot.players.add(human);

        PlayerSnapshot ai = new PlayerSnapshot();
        ai.name = "Bot";
        ai.archetypeId = "ai_easy";
        ai.hasBet = true;
        ai.bet = 1;
        ai.hand.add(new CardSnapshot(Suit.CLUBS, CardValue.KING));
        ai.hand.add(new CardSnapshot(Suit.DIAMONDS, CardValue.THREE));
        snapshot.players.add(ai);

        RoundSnapshot round = new RoundSnapshot();
        round.trumpCard = new CardSnapshot(Suit.HEARTS, CardValue.QUEEN);
        round.trumpBroken = false;
        round.currentPlayer = 0;
        snapshot.round = round;

        preloadStore.save(snapshot);

        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES) {
            @Override
            GameStateStore buildGameStateStore() {
                return new GameStateStore(gameStateFile);
            }

            @Override
            StartScreenOutcome captureStartScreenOutcome(List<String> aiNames, boolean offerResume) {
                return offerResume ? StartScreenOutcome.resume() : StartScreenOutcome.name(captureHumanName(aiNames));
            }
        };

        Player rebuiltHuman = game.getPlayers().get(0);
        //sanity check: reproduces the bug as it shipped -- right after boot-
        //time reconstruction and before establishInitialRenderState() runs,
        //the hand is still at Round reconstruction's stale pre-renderPlayers()
        //position, not the real on-screen one.
        assertFalse(Game.WIDTH == rebuiltHuman.getHand().getX());

        game.establishInitialRenderState();

        assertEquals(Game.WIDTH, rebuiltHuman.getHand().getX());
        assertEquals(Game.HEIGHT - 150, rebuiltHuman.getHand().getY());
    }

    // --- ROADMAP item 10: hamburger menu (Menu/Restart/Resume) ---

    /**
     * Acceptance criterion 3 (Restart confirmation, part 1): selecting
     * Restart from the hamburger menu and clicking Yes must propagate a
     * RestartGameSignal all the way out of Human.bet() -- proving the "two
     * clicks" contract is really wired end-to-end (HamburgerMenu's own
     * confirmation-requires-a-second-click behavior is unit-tested directly
     * in TestHamburgerMenu; this exercises the real Human/Game wiring on top
     * of it).
     */
    @Test(timeout = 5000)
    public void restartSelectionFromHamburgerMenuPropagatesRestartGameSignalOnlyAfterConfirmation() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Human human = (Human) game.getPlayers().get(0);
        Hand hand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        hand.addCard(new Card(Suit.HEARTS, CardValue.ACE));
        human.setHand(hand);
        MouseInput mouseInput = game.getMouseInput();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 20, 20); // hamburger icon (BetStepper's hotspot)
            // HamburgerMenu.showBlocking() clears the click queue again on
            // entry (same nested-dialog clearClicks() every showBlocking
            // view in this codebase does) -- a short pause avoids a race
            // where this thread's next click gets queued and then wiped by
            // that clearClicks() before the nested loop starts awaiting it.
            sleep50();
            deliverClick(mouseInput, 30, 145); // Restart Game (single column, row 4: y=[131,161))
            sleep50();
            deliverClick(mouseInput, 350, 340); // Yes
        });
        clicker.start();

        try {
            human.bet(new BettingContext(Suit.HEARTS, List.of(), 2, true, false, true));
            fail("expected RestartGameSignal to propagate out of bet()");
        } catch (RestartGameSignal expected) {
            // expected
        }
        clicker.join();
    }

    /**
     * Acceptance criterion 3 (Restart confirmation, part 2): once confirmed,
     * abandonAndRestart() must reset currentWinStreak to 0 and persist it
     * (verified indirectly -- SaveStore's own save()/load() round-trip is
     * already covered by TestSaveStore) while leaving gamesPlayed/gamesWon/
     * highScore/every achievement-unlock state untouched (this is an
     * abandoned game, not a finished one -- AchievementEngine.checkGameEnd
     * must not run), and must clear the resumable save. Existing tests
     * around restartForNewGame()/AchievementEngine.checkGameEnd/the natural
     * Play-Again flow are unmodified elsewhere in this file and stay green.
     */
    @Test
    public void abandonAndRestartResetsWinStreakOnlyNotGamesPlayedOrAchievements() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        SaveData saveData = game.getSaveData();
        saveData.currentWinStreak = 5;
        saveData.gamesPlayed = 3;
        saveData.gamesWon = 2;
        saveData.highScore = 80;
        saveData.unlock(Achievement.FIRST_VICTORY);

        game.saveGameStateCheckpoint();
        assertTrue("a checkpoint save must make hasResumableGame() true", game.hasResumableGame());

        game.abandonAndRestart();

        assertEquals("currentWinStreak must reset to 0", 0, saveData.currentWinStreak);
        assertEquals("gamesPlayed must be untouched -- this is an abandoned game, not a finished one",
                3, saveData.gamesPlayed);
        assertEquals("gamesWon must be untouched", 2, saveData.gamesWon);
        assertEquals("highScore must be untouched", 80, saveData.highScore);
        assertTrue("existing achievement unlocks must be untouched", saveData.isUnlocked(Achievement.FIRST_VICTORY));
        assertFalse("abandonAndRestart() must clear the resumable save", game.hasResumableGame());
    }

    /**
     * Regression test for a bug found via this item's own manual
     * verification pass (not caught by any other automated test): confirming
     * Restart while a round was genuinely in progress used to leave Game's
     * currentRound field pointing at the just-abandoned Round -- restartForNewGame()
     * resets every player's state (including nulling each player's Hand) but
     * was never responsible for nulling currentRound, since before this item
     * it was only ever reachable once a round had already finished normally.
     * Left unfixed, the next playOneRound() call's resume-aware
     * currentRound == null check would treat that stale Round as still in
     * progress and call Round.bet() on players whose hands had just been
     * reset to null -- an NPE (confirmed live: AI_Medium.bet() ->
     * getHand().getNumCards() on a null Hand).
     */
    @Test
    public void abandonAndRestartNullsOutCurrentRoundEvenWhenRestartedMidRound() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        List<Player> players = game.getPlayers();
        for (Player player : players) {
            Hand hand = new Hand(Game.WIDTH, Game.HEIGHT, player.getID());
            hand.addCard(new Card(Suit.HEARTS, CardValue.ACE));
            player.setHand(hand);
        }

        // a genuinely in-progress round on this live Game instance --
        // simulates "Restart confirmed while a round was still in progress"
        // (exactly what exposed this bug).
        Round round = new Round(1, players, 0, Game.WIDTH, Game.HEIGHT, game.getHandler());
        game.setCurrentRoundForTest(round);
        assertTrue("test setup must leave a genuinely in-progress round", game.getCurrentRound() != null);

        game.abandonAndRestart();

        assertNull("abandonAndRestart() must null out currentRound -- otherwise the next "
                        + "playOneRound() call would treat a stale Round (whose players' hands "
                        + "were just reset to null by restartForNewGame()) as still in progress",
                game.getCurrentRound());
    }

    /**
     * Regression test for the second bug found via this item's manual
     * verification pass: cleanupHandlerForMenuOrRestart() removes every
     * player from the Handler, but restartForNewGame() (reused unmodified by
     * abandonAndRestart()) never re-adds them, since it was only ever
     * reachable before this item via the natural Play-Again flow, which
     * never removes players from the Handler in the first place. Left
     * unfixed, a mid-round Restart left every player permanently
     * deregistered -- still fully functional as game-logic objects, but
     * invisible on screen (no HUD text/name/score rendered) for the rest of
     * the session.
     */
    @Test
    public void abandonAndRestartReRegistersPlayersWithTheHandler() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Handler handler = game.getHandler();
        List<Player> players = game.getPlayers();
        game.renderPlayers(); // normally done once by play(); this test drives abandonAndRestart() directly instead

        for (Player player : players) {
            assertTrue("test setup: every player must start registered with the handler",
                    handler.object.contains(player));
        }

        game.abandonAndRestart();

        for (Player player : players) {
            assertTrue("player " + player.getName() + " must be re-registered with the handler after "
                            + "abandonAndRestart(), or it silently stops rendering/ticking for the rest of the session",
                    handler.object.contains(player));
        }
    }

    /**
     * User-reported bug (live playthrough, 2026-07-07): after confirming
     * Restart Game mid-betting-phase, the very next screen (fresh hand + bet
     * stepper) still showed every AI seat's played card from the previous,
     * interrupted trick -- one of them carrying the gold "highest card so
     * far" ring -- rendered on top. Root cause: Trick.play() registers each
     * played Card directly with the Handler (its own handler.addObject(card)
     * calls) and only ever removes them via Round.playRound()'s
     * handler.removeAll(cardsPlayed), reached once currentTrick.play()
     * returns *normally* -- but Restart confirmed from inside Human.playCard()'s
     * blocking click-loop (this test's clicker thread) throws RestartGameSignal
     * straight out of that loop, unwinding past playRound()'s post-trick
     * cleanup entirely. cleanupHandlerForMenuOrRestart() (run by
     * abandonAndRestart(), below) only ever removed Players/the trump card/
     * the human's Hand -- never these stray Trick-played Card objects -- so
     * a seat that had already played this trick before Restart was confirmed
     * stayed registered with the Handler forever, rendering on top of every
     * later screen. Reproduces the exact shape: Bot (seat 1) leads and plays
     * its one card; the human's own playCard() blocking loop is then
     * interrupted via Restart before the human plays.
     */
    @Test(timeout = 5000)
    public void abandonAndRestartMidTrickRemovesStaleTrickPlayedCards() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Player human = game.getPlayers().get(0);
        Player bot = game.getPlayers().get(1);

        Hand humanHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        humanHand.addCard(new Card(Suit.HEARTS, CardValue.ACE));
        human.setHand(humanHand);

        Hand botHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        botHand.addCard(new Card(Suit.DIAMONDS, CardValue.TWO));
        bot.setHand(botHand);

        List<Player> players = game.getPlayers();
        // Bot (seat 1) leads; currentTrick left null so Round.playRound()
        // builds a fresh Trick and Bot's own real (non-reconstructed)
        // playCard() call registers its Card with the Handler via
        // Trick.play()'s own handler.addObject(card) -- the exact live code
        // path, not the reconstruction-constructor shortcut the earlier
        // mid-trick resume tests above use.
        Round round = new Round(1, players, 1, Game.WIDTH, Game.HEIGHT, game.getHandler(),
                new Card(Suit.HEARTS, CardValue.KING), Suit.HEARTS, false);
        game.setCurrentRoundForTest(round);

        MouseInput mouseInput = game.getMouseInput();
        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 20, 20); // hamburger icon (IllegalPlayFeedback's hotspot, Human.playCard())
            sleep50();
            deliverClick(mouseInput, 30, 145); // Restart Game (single column, row 4: y=[131,161))
            sleep50();
            deliverClick(mouseInput, 350, 340); // Yes
        });
        clicker.start();

        try {
            round.playRound();
            fail("expected RestartGameSignal to propagate out of playRound() -- Bot must have already "
                    + "played this trick before Human's blocking playCard() loop is interrupted");
        } catch (RestartGameSignal expected) {
            // expected
        }
        clicker.join();

        boolean botsCardStillRegistered = game.getHandler().object.stream()
                .anyMatch(o -> o instanceof Card c && c.getSuit() == Suit.DIAMONDS && c.getValue() == CardValue.TWO);
        assertTrue("test setup: Bot's already-played card must actually be stuck in the Handler at the "
                        + "moment Restart is confirmed, proving this reproduces the live bug's root cause",
                botsCardStillRegistered);

        game.abandonAndRestart();

        long cardsStillRegistered = game.getHandler().object.stream().filter(o -> o instanceof Card).count();
        assertEquals("abandonAndRestart() must remove every Trick-played Card object left over from an "
                        + "interrupted trick (Bot's stray card here) -- otherwise it renders forever on top "
                        + "of every later screen, including the next round's fresh deal/bet stepper (the "
                        + "user-reported 'ghost played cards + gold highlight ring' bug)",
                0, cardsStillRegistered);
    }

    /**
     * Code-review Finding 2: the same Handler re-registration bug class
     * abandonAndRestartReRegistersPlayersWithTheHandler proves fixed for the
     * Restart path had no dedicated regression test for the Menu -> fresh
     * New Game path (establishFreshGameState(), reached when the player
     * picks Menu then declines Resume in favor of a fresh name/New Game).
     * cleanupHandlerForMenuOrRestart() removes the old players from the
     * Handler the same way it does before a Restart; establishFreshGameState()
     * builds a brand-new player list and must register every one of them via
     * renderPlayers() at its end, or they'd be fully functional game-logic
     * objects that silently never render/tick again.
     */
    @Test
    public void establishFreshGameStateRegistersNewPlayersWithTheHandler() {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Handler handler = game.getHandler();
        // Bug fix (user-reported, live playthrough): the constructor itself
        // no longer registers players (that used to double-register every
        // player, stacked with play()'s own first-thing renderPlayers()
        // call) -- normally done once by play(); this test drives
        // establishFreshGameState()/cleanup directly instead, same
        // convention abandonAndRestartReRegistersPlayersWithTheHandler above
        // already uses.
        game.renderPlayers();
        List<Player> oldPlayers = new ArrayList<>(game.getPlayers());

        for (Player player : oldPlayers) {
            assertTrue("test setup: every player must start registered with the handler",
                    handler.object.contains(player));
        }

        game.cleanupHandlerForMenuOrRestart();
        for (Player player : oldPlayers) {
            assertFalse("test setup: cleanup must actually deregister the old players first",
                    handler.object.contains(player));
        }

        game.establishFreshGameState("Fresh Name");

        List<Player> newPlayers = game.getPlayers();
        assertEquals(2, newPlayers.size());
        assertEquals("Fresh Name", newPlayers.get(0).getName());
        for (Player player : newPlayers) {
            assertTrue("player " + player.getName() + " must be registered with the handler after "
                            + "establishFreshGameState(), or it silently stops rendering/ticking for the rest of the session",
                    handler.object.contains(player));
        }
    }

    /**
     * Acceptance criterion 2 (Menu -> Resume round-trip), part 1: selecting
     * Menu must checkpoint the in-progress state (Human/Game's own
     * onReturnToMenu wiring) before propagating ReturnToMenuSignal out of
     * Human.bet() -- mirrors the RestartGameSignal wiring test above.
     */
    @Test(timeout = 5000)
    public void menuSelectionFromHamburgerMenuCheckpointsThenPropagatesReturnToMenuSignal() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Human human = (Human) game.getPlayers().get(0);
        Hand hand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        hand.addCard(new Card(Suit.HEARTS, CardValue.ACE));
        human.setHand(hand);
        MouseInput mouseInput = game.getMouseInput();

        assertFalse("no checkpoint should exist before Menu is clicked", game.hasResumableGame());

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 20, 20); // hamburger icon
            // see the Restart-confirmation test's identical comment above --
            // HamburgerMenu.showBlocking()'s own nested clearClicks() races
            // against this thread without a short pause here.
            sleep50();
            deliverClick(mouseInput, 30, 175); // Go to Menu (single column, row 5: y=[161,191))
        });
        clicker.start();

        try {
            human.bet(new BettingContext(Suit.HEARTS, List.of(), 2, true, false, true));
            fail("expected ReturnToMenuSignal to propagate out of bet()");
        } catch (ReturnToMenuSignal expected) {
            // expected
        }
        clicker.join();

        assertTrue("Menu must checkpoint before throwing, so a resumable save exists", game.hasResumableGame());
    }

    /**
     * Same root cause as abandonAndRestartMidTrickRemovesStaleTrickPlayedCards
     * above, but for the "Go to Menu" path -- cleanupHandlerForMenuOrRestart()
     * is the single method shared by both Menu and Restart (see play()'s own
     * ReturnToMenuSignal/RestartGameSignal catch blocks), so this proves the
     * exact same latent leak the user only actually triggered via Restart was
     * equally present on Menu, and is fixed for both by the same change.
     */
    @Test(timeout = 5000)
    public void menuMidTrickCleanupRemovesStaleTrickPlayedCards() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Player human = game.getPlayers().get(0);
        Player bot = game.getPlayers().get(1);

        Hand humanHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        humanHand.addCard(new Card(Suit.HEARTS, CardValue.ACE));
        human.setHand(humanHand);

        Hand botHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        botHand.addCard(new Card(Suit.DIAMONDS, CardValue.TWO));
        bot.setHand(botHand);

        List<Player> players = game.getPlayers();
        Round round = new Round(1, players, 1, Game.WIDTH, Game.HEIGHT, game.getHandler(),
                new Card(Suit.HEARTS, CardValue.KING), Suit.HEARTS, false);
        game.setCurrentRoundForTest(round);

        MouseInput mouseInput = game.getMouseInput();
        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 20, 20); // hamburger icon
            sleep50();
            deliverClick(mouseInput, 30, 175); // Go to Menu (single column, row 5: y=[161,191))
        });
        clicker.start();

        try {
            round.playRound();
            fail("expected ReturnToMenuSignal to propagate out of playRound() -- Bot must have already "
                    + "played this trick before Human's blocking playCard() loop is interrupted");
        } catch (ReturnToMenuSignal expected) {
            // expected
        }
        clicker.join();

        boolean botsCardStillRegistered = game.getHandler().object.stream()
                .anyMatch(o -> o instanceof Card c && c.getSuit() == Suit.DIAMONDS && c.getValue() == CardValue.TWO);
        assertTrue("test setup: Bot's already-played card must actually be stuck in the Handler",
                botsCardStillRegistered);

        game.cleanupHandlerForMenuOrRestart();

        long cardsStillRegistered = game.getHandler().object.stream().filter(o -> o instanceof Card).count();
        assertEquals("cleanupHandlerForMenuOrRestart() must remove every Trick-played Card object left "
                        + "over from an interrupted trick -- shared by both the Menu and Restart paths",
                0, cardsStillRegistered);
    }

    /**
     * Acceptance criterion 2 (Menu -> Resume round-trip), part 2: once a
     * checkpoint exists and the stale player/hand registrations have been
     * cleaned up (play()'s own catch-block sequence, exercised directly here
     * since this test drives the pieces individually rather than the whole
     * play() loop), resumeFromSavedGame() must rebuild players/hands/bets/
     * scores/round bookkeeping matching the checkpointed state, and must not
     * leave any player double-registered with the Handler (each resumed
     * Player instance must appear exactly once in the handler's live object
     * list -- a double-registration would silently double every per-frame
     * tick()/render() call for that player).
     */
    @Test
    public void resumeFromSavedGameRestoresStateWithoutDuplicatePlayerRegistration() throws GameStateReconstructionException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Player human = game.getPlayers().get(0);
        Player bot = game.getPlayers().get(1);

        Hand humanHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        humanHand.addCard(new Card(Suit.HEARTS, CardValue.KING));
        human.setHand(humanHand);
        human.increaseScore(15);

        Hand botHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        botHand.addCard(new Card(Suit.CLUBS, CardValue.TWO));
        bot.setHand(botHand);
        bot.setBet(2);
        bot.increaseScore(9);

        game.saveGameStateCheckpoint();
        assertTrue(game.hasResumableGame());

        // mirrors play()'s own ReturnToMenuSignal catch-block cleanup step,
        // exercised directly here since this test drives the pieces
        // individually rather than the whole play() loop.
        game.cleanupHandlerForMenuOrRestart();

        game.resumeFromSavedGame();

        List<Player> resumedPlayers = game.getPlayers();
        assertEquals(2, resumedPlayers.size());

        Player resumedHuman = resumedPlayers.get(0);
        assertEquals(ID.HUMAN, resumedHuman.getID());
        assertEquals(15, resumedHuman.getScore());
        assertEquals(1, resumedHuman.getHand().getNumCards());

        Player resumedBot = resumedPlayers.get(1);
        assertEquals(9, resumedBot.getScore());
        assertTrue(resumedBot.hasBet());
        assertEquals(2, resumedBot.getBet());
        assertEquals(1, resumedBot.getHand().getNumCards());

        for (Player player : resumedPlayers) {
            long registrationCount = game.getHandler().object.stream().filter(o -> o == player).count();
            assertEquals("player " + player.getName() + " must be registered exactly once, not doubled",
                    1, registrationCount);
        }
    }

    /**
     * User-reported bug (live playthrough, 2026-07-07): "I could see too many
     * cards" after Menu -> Resume, reproducible on the very next Menu click.
     * Root cause: a checkpoint taken mid-betting-phase (before every player
     * has bet) resumes with playOneRound()'s "!allPlayersHaveBet()" branch
     * re-invoking Round.bet() on the already-reconstructed Round -- but the
     * reconstruction constructor already registered the human's Hand (and
     * the trump card) with the Handler once (so betting has something to
     * render), and bet() unconditionally re-registers both again at its own
     * top. The same Hand instance ends up added to the Handler twice.
     * Harmless-looking on its own (same object, same layout, same pixels),
     * but Handler.removeObject only removes a single occurrence per call --
     * so the *next* cleanupHandlerForMenuOrRestart() (the next Menu click)
     * only strips one of the two registrations, permanently leaking a stale
     * duplicate Hand that keeps rendering its own (increasingly stale) card
     * set on top of whatever hand replaces it afterward. That stacked,
     * never-cleaned-up stale hand is what actually reads as "too many
     * cards" on screen.
     */
    @Test
    public void resumingMidBettingPhaseDoesNotDoubleRegisterHumanHandOrTrumpCard() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Player human = game.getPlayers().get(0);
        Player bot = game.getPlayers().get(1);

        Hand humanHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        humanHand.addCard(new Card(Suit.HEARTS, CardValue.KING));
        human.setHand(humanHand);

        Hand botHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        botHand.addCard(new Card(Suit.CLUBS, CardValue.TWO));
        bot.setHand(botHand);

        List<Player> players = game.getPlayers();
        // Betting genuinely incomplete (neither player has bet yet) -- mirrors
        // a checkpoint taken right as Human.bet()'s blocking click-loop starts.
        // Human (seat 0) is the round's first bettor.
        Round round = new Round(1, players, 0, Game.WIDTH, Game.HEIGHT, game.getHandler(),
                new Card(Suit.HEARTS, CardValue.ACE), Suit.HEARTS, false);
        game.setCurrentRoundForTest(round);

        game.saveGameStateCheckpoint();
        game.cleanupHandlerForMenuOrRestart();
        game.resumeFromSavedGame();

        Round resumedRound = game.getCurrentRound();
        Player resumedHuman = game.getPlayers().get(0);
        MouseInput mouseInput = game.getMouseInput();

        Thread clicker = new Thread(() -> {
            sleep50();
            // Bet button, default stepper value 0 -- Human is the first
            // bettor (not last), so no forbidden-bet restriction applies.
            deliverClick(mouseInput, 750, 600);
        });
        clicker.start();
        // Mirrors playOneRound()'s own mid-betting-resume call exactly.
        resumedRound.bet(resumedRound.getCurrentPlayer(), game.getGameSettings());
        clicker.join();

        long handRegistrations = game.getHandler().object.stream()
                .filter(o -> o == resumedHuman.getHand())
                .count();
        assertEquals("resumed human's hand must be registered exactly once with the Handler, not doubled",
                1, handRegistrations);

        long trumpCardRegistrations = game.getHandler().object.stream()
                .filter(o -> o == resumedRound.getTrumpCard())
                .count();
        assertEquals("resumed round's trump card must be registered exactly once with the Handler, not doubled",
                1, trumpCardRegistrations);
    }

    /**
     * User-reported bug (live playthrough, 2026-07-07): after Menu -> Resume
     * mid-trick, playing one more card left TWO players simultaneously
     * showing the trick-leader dot, and the human's "Led:" HUD line rendered
     * doubled/garbled. Reproduces the exact mid-trick-resume shape (one
     * player already played this trick, the other -- the human -- has the
     * single remaining play) and checks trickLeader ends up on exactly the
     * real winner, not left stuck on the trick's original leader too.
     */
    @Test(timeout = 5000)
    public void resumingMidTrickThenCompletingItLeavesExactlyOneTrickLeader() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Player human = game.getPlayers().get(0);
        Player bot = game.getPlayers().get(1);

        Hand humanHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        humanHand.addCard(new Card(Suit.HEARTS, CardValue.SEVEN)); // trump -- guaranteed to win over Bot's plain lead
        human.setHand(humanHand);

        Hand botHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        bot.setHand(botHand); // already played its only remaining card this trick

        List<Player> players = game.getPlayers();
        List<SeatCardPlay> alreadyPlayed = List.of(new SeatCardPlay(1, new CardSnapshot(Suit.DIAMONDS, CardValue.TWO)));
        Trick trick = new Trick(players, 1, 0, Suit.HEARTS, false, Suit.DIAMONDS, alreadyPlayed,
                Game.WIDTH, Game.HEIGHT, game.getHandler());
        Round round = new Round(1, players, 1, Game.WIDTH, Game.HEIGHT, game.getHandler(),
                new Card(Suit.HEARTS, CardValue.KING), Suit.HEARTS, false);
        round.setCurrentTrick(trick);
        game.setCurrentRoundForTest(round);

        // Bot led (seat 1) -- initializeTrickLeader() (inside the Round
        // constructor above) already marked it; confirm before driving the
        // rest of the trick, so a failure below is attributable to
        // playRound()'s post-trick transition, not to setup.
        assertTrue("test setup: Bot must start as trick leader", bot.isTrickLeader());
        assertFalse("test setup: Human must not start as trick leader", human.isTrickLeader());

        game.saveGameStateCheckpoint();
        game.cleanupHandlerForMenuOrRestart();
        game.resumeFromSavedGame();

        Player resumedHuman = game.getPlayers().get(0);
        Player resumedBot = game.getPlayers().get(1);
        MouseInput mouseInput = game.getMouseInput();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 30, (int) resumedHuman.getY()); // resumedHuman's single hand card, leftmost (only) slot
            // Trick.play()'s own trailing getPlayer(0).nextTrick() call blocks
            // on a second, separate "click anywhere to continue" -- without
            // this, playRound() never returns and the test hangs.
            sleep50();
            deliverClick(mouseInput, 400, 300);
        });
        clicker.start();
        game.getCurrentRound().playRound();
        clicker.join();

        assertTrue("Human must have actually won the trick (played trump over Bot's plain lead)",
                resumedHuman.getTrickScore() == 1);
        assertFalse("Bot (the trick's original leader) must be cleared once the trick resolves to a different winner",
                resumedBot.isTrickLeader());
        assertTrue("Human (the actual winner) must be marked trick leader",
                resumedHuman.isTrickLeader());
    }

    /**
     * User-reported bug, take 2: the single-trick repro above passed, so this
     * tests the shape that repro couldn't -- resuming BETWEEN tricks (not
     * mid-trick) with more than one trick still remaining, and the human NOT
     * seated last in turn order, so a second trick plays out automatically
     * within the same playRound() call after the human's one click. Hands are
     * rigged (via forced suit-following, everyone down to at most one legal
     * card per turn) so both tricks' winners are fully deterministic
     * regardless of AI's own choice among any legal cards it does have:
     * Bot2 leads and wins trick A (leader == winner, the case that can't
     * expose a stale-leader bug), then Bot2 leads trick B again but Human
     * -- forced onto its last card, a trump -- wins it (leader != winner,
     * the case that can).
     */
    @Test(timeout = 5000)
    public void resumingBetweenTricksThenPlayingASecondTrickLeavesExactlyOneTrickLeader() throws InterruptedException {
        Game game = new HeadlessGame(List.of("Bot1", "Bot2"));
        Player human = game.getPlayers().get(0);
        Player bot1 = game.getPlayers().get(1);
        Player bot2 = game.getPlayers().get(2);

        Hand humanHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        humanHand.addCard(new Card(Suit.DIAMONDS, CardValue.TWO));
        humanHand.addCard(new Card(Suit.SPADES, CardValue.THREE)); // trump
        human.setHand(humanHand);

        Hand bot1Hand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        bot1Hand.addCard(new Card(Suit.DIAMONDS, CardValue.FIVE));
        bot1Hand.addCard(new Card(Suit.CLUBS, CardValue.FOUR));
        bot1.setHand(bot1Hand);

        Hand bot2Hand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        bot2Hand.addCard(new Card(Suit.DIAMONDS, CardValue.ACE));
        bot2Hand.addCard(new Card(Suit.DIAMONDS, CardValue.KING));
        bot2.setHand(bot2Hand);

        List<Player> players = game.getPlayers();
        // Between tricks (currentTrick left null): Bot2 (seat 2) leads the next trick.
        Round round = new Round(2, players, 2, Game.WIDTH, Game.HEIGHT, game.getHandler(),
                new Card(Suit.SPADES, CardValue.KING), Suit.SPADES, false);
        game.setCurrentRoundForTest(round);
        assertTrue("test setup: Bot2 must lead (and thus start as trick leader for) the next trick", bot2.isTrickLeader());

        game.saveGameStateCheckpoint();
        game.cleanupHandlerForMenuOrRestart();
        game.resumeFromSavedGame();

        Player resumedHuman = game.getPlayers().get(0);
        Player resumedBot1 = game.getPlayers().get(1);
        Player resumedBot2 = game.getPlayers().get(2);
        MouseInput mouseInput = game.getMouseInput();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 0, (int) resumedHuman.getY()); // trick A: forced TWO of DIAMONDS (index 0)
            sleep50();
            deliverClick(mouseInput, 400, 300); // trick A: dismiss "click anywhere to continue"
            sleep50();
            deliverClick(mouseInput, 0, (int) resumedHuman.getY()); // trick B: forced THREE of SPADES (only card left, index 0)
            sleep50();
            deliverClick(mouseInput, 400, 300); // trick B: dismiss "click anywhere to continue"
        });
        clicker.start();
        game.getCurrentRound().playRound();
        clicker.join();

        assertEquals("test setup: Bot2 (with two diamonds, both beating everyone else's) must have won trick A",
                1, resumedBot2.getTrickScore());
        assertEquals("Human's forced trump on trick B must have won it over Bot2's plain diamond lead",
                1, resumedHuman.getTrickScore());

        assertFalse("Bot2 led trick B but lost it -- must not still show as trick leader", resumedBot2.isTrickLeader());
        assertFalse("Bot1 never led or won anything -- must never show as trick leader", resumedBot1.isTrickLeader());
        assertTrue("Human actually won the last trick (trick B) -- must be the one shown as trick leader",
                resumedHuman.isTrickLeader());
    }

    /**
     * User-reported bug, take 3: mirrors the exact seat composition and
     * leader/winner pair from the user's own screenshot (5 total players --
     * human + Medium Balanced/Bold/Cautious + Easy -- with Easy leading and
     * Medium Cautious ending up the actual winner, the same two seats that
     * both showed a trick-leader dot in the bug report) in case the earlier,
     * smaller-player-count repros missed a wraparound-specific issue.
     */
    @Test(timeout = 5000)
    public void resumingBetweenTricksWithFivePlayersMatchesBugReportSeatsExactly() throws InterruptedException {
        Game game = new HeadlessGame(List.of("Medium Balanced", "Medium Bold", "Medium Cautious", "Easy"));
        Player human = game.getPlayers().get(0);
        Player balanced = game.getPlayers().get(1);
        Player bold = game.getPlayers().get(2);
        Player cautious = game.getPlayers().get(3);
        Player easy = game.getPlayers().get(4);

        Hand humanHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        humanHand.addCard(new Card(Suit.CLUBS, CardValue.TWO));
        human.setHand(humanHand);

        Hand balancedHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        balancedHand.addCard(new Card(Suit.CLUBS, CardValue.THREE));
        balanced.setHand(balancedHand);

        Hand boldHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        boldHand.addCard(new Card(Suit.CLUBS, CardValue.FOUR));
        bold.setHand(boldHand);

        Hand cautiousHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        cautiousHand.addCard(new Card(Suit.SPADES, CardValue.FIVE)); // trump, void of the led suit
        cautious.setHand(cautiousHand);

        Hand easyHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        easyHand.addCard(new Card(Suit.CLUBS, CardValue.TEN));
        easy.setHand(easyHand);

        List<Player> players = game.getPlayers();
        // Between tricks (currentTrick left null): Easy (seat 4) leads the next trick.
        Round round = new Round(1, players, 4, Game.WIDTH, Game.HEIGHT, game.getHandler(),
                new Card(Suit.SPADES, CardValue.KING), Suit.SPADES, false);
        game.setCurrentRoundForTest(round);
        assertTrue("test setup: Easy must lead (and thus start as trick leader for) the next trick", easy.isTrickLeader());

        game.saveGameStateCheckpoint();
        game.cleanupHandlerForMenuOrRestart();
        game.resumeFromSavedGame();

        List<Player> resumed = game.getPlayers();
        Player resumedHuman = resumed.get(0);
        Player resumedCautious = resumed.get(3);
        Player resumedEasy = resumed.get(4);
        MouseInput mouseInput = game.getMouseInput();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 0, (int) resumedHuman.getY()); // Human's single card (index 0), turn order: Easy -> Human -> Balanced -> Bold -> Cautious
            sleep50();
            deliverClick(mouseInput, 400, 300); // dismiss "click anywhere to continue"
        });
        clicker.start();
        game.getCurrentRound().playRound();
        clicker.join();

        assertEquals("test setup: Medium Cautious's trump must have won over everyone else's plain clubs",
                1, resumedCautious.getTrickScore());

        for (Player player : resumed) {
            if (player == resumedCautious) {
                assertTrue("Medium Cautious actually won -- must be the one shown as trick leader",
                        player.isTrickLeader());
            } else {
                assertFalse(player.getName() + " did not win this trick -- must not show a trick-leader dot",
                        player.isTrickLeader());
            }
        }
        assertFalse("Easy led but lost -- must not still show as trick leader (this is the bug's exact reported symptom)",
                resumedEasy.isTrickLeader());
    }

    /**
     * User-reported bug, take 4: the case none of the earlier repros
     * covered -- Menu clicked exactly during Human.nextTrick()'s "click
     * anywhere to continue" prompt, i.e. every player has ALREADY played
     * this trick (hands already reduced, cardsPlayedBySeat already has one
     * entry per player) and only the trick's bookkeeping/dismissal remains.
     * At checkpoint time Round.playRound()'s post-trick code (winner
     * determination, trickLeader clear/set, currentPlayer update) has NOT
     * run yet for this trick -- it only runs after currentTrick.play()
     * returns, which is blocked on this exact prompt.
     */
    @Test(timeout = 5000)
    public void resumingATrickThatWasFullyPlayedButNotYetResolvedStillPlaysTheFinalTrick() throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Player human = game.getPlayers().get(0);
        Player bot = game.getPlayers().get(1);

        // Both players' hands already reflect this trick's card being played
        // -- one card left each, for the round's real final trick. Human
        // leads that final trick (see the actualWinner=0 computation below)
        // with the higher remaining diamond, so Human -- not Bot -- also
        // wins it; keeps this test's assertions about who ends up shown as
        // trick leader unambiguous (a single, consistent winner across both
        // tricks) rather than incidentally exercising a second, different
        // leader transition this test isn't about.
        Hand humanHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        humanHand.addCard(new Card(Suit.DIAMONDS, CardValue.FOUR));
        human.setHand(humanHand);

        Hand botHand = new Hand(Game.WIDTH, Game.HEIGHT, ID.AI);
        botHand.addCard(new Card(Suit.DIAMONDS, CardValue.THREE));
        bot.setHand(botHand);

        List<Player> players = game.getPlayers();
        List<SeatCardPlay> alreadyPlayed = List.of(
                new SeatCardPlay(1, new CardSnapshot(Suit.DIAMONDS, CardValue.TWO)),   // Bot led
                new SeatCardPlay(0, new CardSnapshot(Suit.HEARTS, CardValue.SEVEN))    // Human trumped in, both already played
        );
        Trick trick = new Trick(players, 1, 0, Suit.HEARTS, false, Suit.DIAMONDS, alreadyPlayed,
                Game.WIDTH, Game.HEIGHT, game.getHandler());
        Round round = new Round(2, players, 1, Game.WIDTH, Game.HEIGHT, game.getHandler(),
                new Card(Suit.HEARTS, CardValue.KING), Suit.HEARTS, false);
        round.setCurrentTrick(trick);
        game.setCurrentRoundForTest(round);

        game.saveGameStateCheckpoint();
        game.cleanupHandlerForMenuOrRestart();
        game.resumeFromSavedGame();

        Player resumedHuman = game.getPlayers().get(0);
        Player resumedBot = game.getPlayers().get(1);
        MouseInput mouseInput = game.getMouseInput();

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 400, 300); // dismiss the interrupted "click anywhere to continue" prompt
            sleep50();
            deliverClick(mouseInput, 0, (int) resumedHuman.getY()); // the round's real final trick: resumedHuman's one remaining card
            sleep50();
            deliverClick(mouseInput, 400, 300); // dismiss that trick's own "click anywhere to continue"
        });
        clicker.start();
        game.getCurrentRound().playRound();
        clicker.join();

        assertEquals("Human must win both the already-played trick (trump) and the round's real final "
                        + "trick (higher diamond, led once currentPlayer correctly advanced) -- both must "
                        + "be recorded as trick wins",
                2, resumedHuman.getTrickScore());
        assertEquals("Bot must not have won either trick", 0, resumedBot.getTrickScore());
        assertFalse("Bot never won a trick -- must not show as trick leader",
                resumedBot.isTrickLeader());
        assertTrue("Human won the last trick played -- must be shown as trick leader",
                resumedHuman.isTrickLeader());

        assertEquals("the round's real final trick must actually be played, not silently skipped -- "
                        + "Human's hand must end up empty",
                0, resumedHuman.getHand().getNumCards());
        assertEquals("Bot's hand must also end up empty once the final trick is actually played",
                0, resumedBot.getHand().getNumCards());
    }

    /**
     * Acceptance criterion 4 (Settings toggle): drives the real hamburger
     * menu -> Settings -> toggle -> Back sequence through Human.bet()'s
     * actual click loop (not a direct SettingsView.showBlocking call, which
     * TestSettingsView already covers in isolation) and confirms the flip is
     * visible on game.getGameSettings() -- the exact same instance
     * Game.playOneRound() passes into every Round.bet() call. If Human's own
     * internal gameSettings field were ever a disconnected copy instead of
     * the same reference Game holds, this test would fail even though
     * TestSettingsView's own direct test would still pass.
     *
     * ROADMAP item 10 follow-up: rewritten for the gated-apply behavior --
     * toggling Settings mid-round now only flips the *pending* value; the
     * live totalBetsCannotEqualTricks field Round.bet() actually reads must
     * NOT change until a genuinely fresh game starts (restartForNewGame()/
     * establishFreshGameState()). This test proves both halves: the
     * mid-round no-op on the live field, and that the exact same
     * GameSettings instance does pick up the change once a fresh game
     * actually starts.
     */
    @Test(timeout = 5000)
    public void settingsToggleFromHamburgerMenuStagesOnTheSameGameSettingsInstanceGameUsesButDoesNotApplyMidRound()
            throws InterruptedException {
        Game game = new HeadlessGame(HEADLESS_PLAYER_NAMES);
        Human human = (Human) game.getPlayers().get(0);
        Hand hand = new Hand(Game.WIDTH, Game.HEIGHT, ID.HUMAN);
        hand.addCard(new Card(Suit.HEARTS, CardValue.ACE));
        human.setHand(hand);
        MouseInput mouseInput = game.getMouseInput();

        assertTrue("default is ON per GameSettings' own doc", game.getGameSettings().totalBetsCannotEqualTricks);
        assertTrue("pending default is also ON", game.getGameSettings().pendingTotalBetsCannotEqualTricks);

        Thread clicker = new Thread(() -> {
            sleep50();
            deliverClick(mouseInput, 20, 20); // hamburger icon
            sleep50();
            deliverClick(mouseInput, 30, 85); // Settings (single column, row 2: y=[71,101))
            sleep50();
            deliverClick(mouseInput, 550, 135); // the toggle
            sleep50();
            deliverClick(mouseInput, 700, 590); // Back
            sleep50();
            deliverClick(mouseInput, 760, 600); // finish the bet (Bet button, value 0)
        });
        clicker.start();

        human.bet(new BettingContext(Suit.HEARTS, List.of(), 2, true, false, true));
        clicker.join();

        assertFalse("toggling Settings via the hamburger menu must mutate the exact GameSettings "
                        + "instance's pending value",
                game.getGameSettings().pendingTotalBetsCannotEqualTricks);
        assertTrue("the live/effective value must NOT change mid-round just from visiting Settings",
                game.getGameSettings().totalBetsCannotEqualTricks);

        game.restartForNewGame();

        assertFalse("once a fresh game actually starts, the staged toggle must now be applied",
                game.getGameSettings().totalBetsCannotEqualTricks);
    }

    private static void sleep50() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Synthesizes a left-click MouseEvent and delivers it straight to mouseInput's listener, same as an AWT click would. */
    private static void deliverClick(MouseInput mouseInput, int x, int y) {
        Component dummy = new Canvas();
        mouseInput.mousePressed(new MouseEvent(dummy, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, x, y, 1, false, MouseEvent.BUTTON1));
    }
}
