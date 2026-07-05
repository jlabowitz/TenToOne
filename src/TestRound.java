import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Round's trick-leader and leading-suit wiring (all-AI players so
 * bet()/playRound() run to completion synchronously with no GUI input to
 * block on).
 */
public class TestRound {
    @Test
    public void roundConstructorMakesStartingPlayerTheTrickLeader() {
        List<Player> players = new ArrayList<>();
        players.add(new AI_Zombie("A"));
        players.add(new AI_Zombie("B"));
        players.add(new AI_Zombie("C"));

        new Round(3, players, 1, Game.WIDTH, Game.HEIGHT, new Handler());

        assertFalse(players.get(0).isTrickLeader());
        assertTrue(players.get(1).isTrickLeader());
        assertFalse(players.get(2).isTrickLeader());
    }

    /**
     * At the winner.wonTrick() call site, the leader flag must move from
     * whoever led the trick to whoever actually won it. With a single-card
     * round (one trick), the winner is unambiguous via getTrickScore().
     */
    @Test
    public void trickLeaderMovesToTrickWinnerAfterPlayRound() {
        List<Player> players = new ArrayList<>();
        players.add(new AI_Zombie("A"));
        players.add(new AI_Zombie("B"));
        Round round = new Round(1, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        round.bet(0, new GameSettings());
        round.playRound();

        Player winner = players.get(0).getTrickScore() == 1 ? players.get(0) : players.get(1);
        Player loser = winner == players.get(0) ? players.get(1) : players.get(0);

        assertTrue(winner.isTrickLeader());
        assertFalse(loser.isTrickLeader());
    }

    /**
     * Regression guard for the explicit reset call right after
     * handler.removeAll(cardsPlayed): once a trick resolves, the led-suit
     * HUD must go back to "no suit led" immediately, not just when the next
     * Trick object happens to be constructed.
     */
    @Test
    public void leadingSuitResetsToNullAfterTrickResolves() {
        List<Player> players = new ArrayList<>();
        players.add(new AI_Zombie("A"));
        players.add(new AI_Zombie("B"));
        Round round = new Round(1, players, 0, Game.WIDTH, Game.HEIGHT, new Handler());

        round.bet(0, new GameSettings());
        round.playRound();

        for (Player player : players) {
            assertNull(player.getLeadingSuit());
        }
    }
}
