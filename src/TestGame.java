import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestGame {
    private static final List<String> HEADLESS_PLAYER_NAMES = new ArrayList<>() {{
        add("You");
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
        HeadlessGame(List<String> playerNames) {
            super(playerNames);
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
        Human human = new Human("You", new MouseInput(), new Handler());
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

    /** The hand persists between rounds, so every round must re-position it. */
    @Test
    public void redealtHumanHandIsRepositionedEachRound() {
        Human human = new Human("You", new MouseInput(), new Handler());
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
}
