import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Player's bet/hasBet/resetBet state, exercised via AI_Easy (the
 * simplest concrete Player subclass to construct without GUI dependencies).
 *
 * Round.bet() calls resetBet() on each player at the start of every round's
 * betting phase so a stale hasBet flag from a previous round doesn't leak
 * into HUD rendering (Player.render draws "-" instead of the bet when
 * !hasBet()).
 */
public class TestPlayer {
    @Test
    public void freshPlayerHasNotBet() {
        Player player = new AI_Easy("Test");
        assertFalse(player.hasBet());
    }

    @Test
    public void setBetMarksHasBetAndStoresValue() {
        Player player = new AI_Easy("Test");
        player.setBet(3);
        assertTrue(player.hasBet());
        assertEquals(3, player.getBet());
    }

    @Test
    public void resetBetClearsHasBetAndValue() {
        Player player = new AI_Easy("Test");
        player.setBet(3);
        player.resetBet();
        assertFalse(player.hasBet());
        assertEquals(0, player.getBet());
    }
}
