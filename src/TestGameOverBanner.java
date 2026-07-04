import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for GameOverBanner.isPlayAgainHotspot, added by ROADMAP item 1 (no
 * test file previously existed for this class -- it had no testable logic
 * before this hotspot was added). Layout contract: the hotspot covers x in
 * [340, 500), y in [460, 494), same half-open-rect convention as
 * NextTrickPrompt.isRulesHotspot/RulesView.isBackButton.
 */
public class TestGameOverBanner {
    private static final int TOP = 460;
    private static final int BOTTOM = 494;
    private static final int LEFT = 340;
    private static final int RIGHT = 500;

    private static GameOverBanner newBanner() {
        Player winner = new AI_Easy("Winner");
        List<Player> standings = Collections.singletonList(winner);
        return new GameOverBanner(winner, false, standings);
    }

    @Test
    public void clickInsideHotspotReturnsTrue() {
        GameOverBanner banner = newBanner();
        assertTrue(banner.isPlayAgainHotspot(420, 477));
    }

    @Test
    public void leftAndTopBoundaryIsInclusive() {
        GameOverBanner banner = newBanner();
        assertTrue(banner.isPlayAgainHotspot(LEFT, TOP));
    }

    @Test
    public void rightAndBottomBoundaryIsExclusive() {
        GameOverBanner banner = newBanner();
        assertFalse(banner.isPlayAgainHotspot(RIGHT, 477));
        assertFalse(banner.isPlayAgainHotspot(420, BOTTOM));
        // last in-bounds pixel still hits
        assertTrue(banner.isPlayAgainHotspot(RIGHT - 1, BOTTOM - 1));
    }

    @Test
    public void clickAboveOrBelowHotspotReturnsFalse() {
        GameOverBanner banner = newBanner();
        assertFalse(banner.isPlayAgainHotspot(420, TOP - 1));
        assertFalse(banner.isPlayAgainHotspot(420, BOTTOM));
    }

    @Test
    public void clickLeftOrRightOfHotspotReturnsFalse() {
        GameOverBanner banner = newBanner();
        assertFalse(banner.isPlayAgainHotspot(LEFT - 1, 477));
        assertFalse(banner.isPlayAgainHotspot(RIGHT, 477));
    }

    @Test
    public void clickOnFooterTextAreaReturnsFalse() {
        GameOverBanner banner = newBanner();
        // center of the panel's content span at the footer's baseline,
        // where "Game Over -- close this window to exit." is drawn
        assertFalse(banner.isPlayAgainHotspot(420, 430));
    }
}
