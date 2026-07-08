import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for SaveData (ROADMAP item 2): the persisted stats + achievement
 * unlock map. Covers the "once unlocked, permanent" contract -- a second
 * unlock() call for the same achievement must be a no-op, not overwrite the
 * original timestamp or re-fire as "newly unlocked."
 */
public class TestSaveData {

    @Test
    public void defaultsAreAllZeroedAndNothingUnlocked() {
        SaveData data = SaveData.defaults();
        assertEquals(0, data.gamesPlayed);
        assertEquals(0, data.gamesWon);
        assertEquals(0, data.highScore);
        assertEquals(0, data.currentWinStreak);
        assertEquals(0, data.bestWinStreakEver);
        assertEquals(0, data.totalPoints);
        assertEquals(0, data.totalRoundsBet);
        assertEquals(0, data.totalRoundsBetHit);
        assertEquals("", data.lastUsedName);
        assertEquals(SaveData.CURRENT_SAVE_FORMAT_VERSION, data.saveFormatVersion);
        for (Achievement achievement : Achievement.values()) {
            assertFalse(data.isUnlocked(achievement));
        }
    }

    @Test
    public void unlockMarksAchievementUnlockedWithATimestamp() {
        SaveData data = SaveData.defaults();
        assertNull(data.unlockedAt(Achievement.FIRST_VICTORY));

        boolean newlyUnlocked = data.unlock(Achievement.FIRST_VICTORY);

        assertTrue(newlyUnlocked);
        assertTrue(data.isUnlocked(Achievement.FIRST_VICTORY));
        assertNull("unrelated achievements must stay locked", data.unlockedAt(Achievement.TEN_GAMES_PLAYED));
    }

    @Test
    public void unlockingAnAlreadyUnlockedAchievementIsANoOpAndReturnsFalse() {
        SaveData data = SaveData.defaults();
        data.unlock(Achievement.WIN_STREAK_5);
        var firstTimestamp = data.unlockedAt(Achievement.WIN_STREAK_5);

        boolean unlockedAgain = data.unlock(Achievement.WIN_STREAK_5);

        assertFalse("re-unlocking must report nothing new happened", unlockedAgain);
        assertEquals("timestamp must not move on a repeat unlock", firstTimestamp, data.unlockedAt(Achievement.WIN_STREAK_5));
    }
}
