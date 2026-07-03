import org.junit.Test;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @Test
    public void freshPlayerIsNotTrickLeader() {
        Player player = new AI_Easy("Test");
        assertFalse(player.isTrickLeader());
    }

    @Test
    public void setTrickLeaderTogglesFlag() {
        Player player = new AI_Easy("Test");
        player.setTrickLeader(true);
        assertTrue(player.isTrickLeader());
        player.setTrickLeader(false);
        assertFalse(player.isTrickLeader());
    }

    @Test
    public void freshPlayerHasNoLeadingSuit() {
        Player player = new AI_Easy("Test");
        assertNull(player.getLeadingSuit());
    }

    @Test
    public void setLeadingSuitStoresAndClearsValue() {
        Player player = new AI_Easy("Test");
        player.setLeadingSuit(Suit.HEARTS);
        assertEquals(Suit.HEARTS, player.getLeadingSuit());
        player.setLeadingSuit(null);
        assertNull(player.getLeadingSuit());
    }

    /**
     * Geometry tests for the trick-leader dot's bounding box. Revised after
     * user feedback that the original above/left placement (centered at
     * nameX+5, nameY-14) collided with the human's "Led:" HUD line one row
     * above and risked left-edge clipping for the leftmost AI seat: the dot
     * now sits to the right of the rendered name text, sized off the same
     * FontMetrics used elsewhere in Player (see renderLeadingSuit), obtained
     * headlessly via a 1x1 BufferedImage's Graphics -- same technique
     * TestHandler already uses to get a Graphics without popping a window.
     */
    private static FontMetrics defaultFontMetrics() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        FontMetrics metrics = g.getFontMetrics();
        g.dispose();
        return metrics;
    }

    @Test
    public void trickLeaderDotSitsRightOfNameTextWithAGap() {
        FontMetrics metrics = defaultFontMetrics();
        String name = "Player Three";
        Rectangle bounds = Player.trickLeaderDotBounds(metrics, name, 100, 50);

        int textEndX = 100 + metrics.stringWidth(name);
        assertEquals(10, bounds.width);
        assertEquals(10, bounds.height);
        assertTrue("dot must not overlap the name text", bounds.x >= textEndX);
    }

    @Test
    public void trickLeaderDotStaysWithinNameTextVerticalSpan() {
        FontMetrics metrics = defaultFontMetrics();
        String name = "Jacob";
        int nameY = 358;
        Rectangle bounds = Player.trickLeaderDotBounds(metrics, name, 140, nameY);

        int centerY = bounds.y + bounds.height / 2;
        assertTrue("dot must not stray above the name's cap-height",
                centerY >= nameY - metrics.getAscent());
        assertTrue("dot must not sit below the name's baseline",
                centerY <= nameY);
    }

    @Test
    public void trickLeaderDotNeverGoesNegativeForLeftmostAiSeat() {
        // Leftmost AI seat renders at x=0 (Game.renderPlayers) -- right-of-
        // text placement means the dot can never clip off the left edge
        // regardless of name length, unlike the original left-of-name idea.
        FontMetrics metrics = defaultFontMetrics();
        Rectangle bounds = Player.trickLeaderDotBounds(metrics, "Player Two", 0, 50);
        assertTrue(bounds.x >= 0);
    }
}
