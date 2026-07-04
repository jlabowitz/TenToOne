import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the static Achievement definitions (ROADMAP item 2). Locks in
 * the design spec's exact roster -- 11 non-hidden milestone achievements
 * plus the single hidden Bapi easter egg -- so an accidental addition/
 * removal/hidden-flag flip is caught rather than silently drifting from the
 * approved design.
 */
public class TestAchievement {

    // NOTE: ROADMAP.md's item 2 writeup says "11 total, non-hidden" milestone
    // achievements but its own bullet list only names 10 distinct ids
    // (FIRST_VICTORY, TEN_GAMES_PLAYED, WIN_STREAK_3/5/10, SCORE_OVER_50/100,
    // PERFECT_ROUND, FLAWLESS_GAME, COMEBACK_KID) -- a pre-existing count typo
    // in the "finalized" design doc itself. This locks in the actually-named
    // 10 milestone ids + 1 hidden easter egg = 11 total, matching the literal
    // enumerated list rather than the summary count; flagged back to
    // project-manager rather than inventing an unnamed 11th achievement.
    @Test
    public void exactlyElevenAchievementsExist() {
        assertEquals(11, Achievement.values().length);
    }

    @Test
    public void exactlyOneAchievementIsHidden() {
        long hiddenCount = 0;
        for (Achievement achievement : Achievement.values()) {
            if (achievement.isHidden()) {
                hiddenCount++;
            }
        }
        assertEquals(1, hiddenCount);
    }

    @Test
    public void bapiEasterEggIsTheOnlyHiddenAchievement() {
        assertTrue(Achievement.BAPI_EASTER_EGG.isHidden());
        assertFalse(Achievement.FIRST_VICTORY.isHidden());
        assertFalse(Achievement.SCORE_OVER_100.isHidden());
    }

    @Test
    public void bapiEasterEggDisplayNameIsOneAndOnly() {
        assertEquals("One and Only", Achievement.BAPI_EASTER_EGG.getDisplayName());
    }

    @Test
    public void everyAchievementHasNonBlankDisplayNameAndDescription() {
        for (Achievement achievement : Achievement.values()) {
            assertFalse(achievement.name() + " has a blank display name", achievement.getDisplayName().isBlank());
            assertFalse(achievement.name() + " has a blank description", achievement.getDescription().isBlank());
        }
    }

    @Test
    public void allElevenMilestoneIdsArePresent() {
        Achievement[] expectedNonHidden = {
                Achievement.FIRST_VICTORY,
                Achievement.TEN_GAMES_PLAYED,
                Achievement.WIN_STREAK_3,
                Achievement.WIN_STREAK_5,
                Achievement.WIN_STREAK_10,
                Achievement.SCORE_OVER_50,
                Achievement.SCORE_OVER_100,
                Achievement.PERFECT_ROUND,
                Achievement.FLAWLESS_GAME,
                Achievement.COMEBACK_KID,
        };
        assertEquals(10, expectedNonHidden.length); // sanity on this test itself
        int nonHiddenCount = 0;
        for (Achievement achievement : Achievement.values()) {
            if (!achievement.isHidden()) {
                nonHiddenCount++;
            }
        }
        // 10 non-hidden total -- see exactlyElevenAchievementsExist's comment
        // on the "11" vs. 10-named-ids discrepancy in ROADMAP.md.
        assertEquals(10, nonHiddenCount);
    }
}
