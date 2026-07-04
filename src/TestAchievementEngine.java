import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for AchievementEngine (ROADMAP item 2) -- the pure, Swing-free
 * unlock-rule logic invoked from Game's round-end/game-end/name-submission
 * hooks. Every check() method mutates the given SaveData in place and
 * returns only the achievements *newly* unlocked by that call, so a caller
 * (the toast queue) never re-announces something already unlocked in an
 * earlier round/game.
 */
public class TestAchievementEngine {

    // --- checkRoundEnd: SCORE_OVER_50/_100, PERFECT_ROUND ---

    @Test
    public void scoreOver50UnlocksOnlyStrictlyAbove50() {
        SaveData data = SaveData.defaults();
        assertTrue(AchievementEngine.checkRoundEnd(data, 50, false).isEmpty());
        assertFalse(data.isUnlocked(Achievement.SCORE_OVER_50));

        List<Achievement> unlocked = AchievementEngine.checkRoundEnd(data, 51, false);

        assertTrue(unlocked.contains(Achievement.SCORE_OVER_50));
        assertTrue(data.isUnlocked(Achievement.SCORE_OVER_50));
    }

    /**
     * Boundary test: SCORE_OVER_100 is a strictly-greater-than-100 check
     * ("over 100", not "100 or more") -- a score of exactly 100 must not
     * unlock it, only 101+ does (see scoreOver100UnlocksBothTiersWhenCrossedAtOnce).
     */
    @Test
    public void scoreOver100DoesNotUnlockAtExactly100() {
        SaveData data = SaveData.defaults();

        List<Achievement> unlocked = AchievementEngine.checkRoundEnd(data, 100, false);

        assertFalse("100 is not strictly over 100", unlocked.contains(Achievement.SCORE_OVER_100));
        assertFalse(data.isUnlocked(Achievement.SCORE_OVER_100));
    }

    @Test
    public void scoreOver100UnlocksBothTiersWhenCrossedAtOnce() {
        SaveData data = SaveData.defaults();

        List<Achievement> unlocked = AchievementEngine.checkRoundEnd(data, 101, false);

        assertTrue(unlocked.contains(Achievement.SCORE_OVER_50));
        assertTrue(unlocked.contains(Achievement.SCORE_OVER_100));
    }

    @Test
    public void scoreThresholdsDoNotReUnlockOnASubsequentCall() {
        SaveData data = SaveData.defaults();
        AchievementEngine.checkRoundEnd(data, 101, false);

        List<Achievement> unlocked = AchievementEngine.checkRoundEnd(data, 105, false);

        assertTrue("already-unlocked score tiers must not be reported again", unlocked.isEmpty());
    }

    @Test
    public void perfectRoundUnlocksWhenBonusHit() {
        SaveData data = SaveData.defaults();

        List<Achievement> unlocked = AchievementEngine.checkRoundEnd(data, 10, true);

        assertTrue(unlocked.contains(Achievement.PERFECT_ROUND));
        assertTrue(data.isUnlocked(Achievement.PERFECT_ROUND));
    }

    @Test
    public void perfectRoundDoesNotUnlockWhenBonusMissed() {
        SaveData data = SaveData.defaults();

        List<Achievement> unlocked = AchievementEngine.checkRoundEnd(data, 10, false);

        assertFalse(unlocked.contains(Achievement.PERFECT_ROUND));
        assertFalse(data.isUnlocked(Achievement.PERFECT_ROUND));
    }

    // --- isSoleLastPlace ---

    @Test
    public void soleLastPlaceIsTrueWhenStrictlyBelowEveryOtherScore() {
        assertTrue(AchievementEngine.isSoleLastPlace(5, Arrays.asList(10, 20, 30)));
    }

    @Test
    public void soleLastPlaceIsFalseOnATieForLast() {
        assertFalse(AchievementEngine.isSoleLastPlace(5, Arrays.asList(5, 20, 30)));
    }

    @Test
    public void soleLastPlaceIsFalseWhenSomeoneElseIsLower() {
        assertFalse(AchievementEngine.isSoleLastPlace(5, Arrays.asList(1, 20, 30)));
    }

    @Test
    public void soleLastPlaceIsTrueWithNoOtherPlayers() {
        // vacuously true -- no other player is <= the human's score
        assertTrue(AchievementEngine.isSoleLastPlace(0, Collections.emptyList()));
    }

    // --- checkGameEnd: stats bookkeeping + FIRST_VICTORY/TEN_GAMES_PLAYED/WIN_STREAK/FLAWLESS_GAME/COMEBACK_KID ---

    @Test
    public void gameEndAlwaysIncrementsGamesPlayed() {
        SaveData data = SaveData.defaults();
        AchievementEngine.checkGameEnd(data, false, 20, false, false);
        assertEquals(1, data.gamesPlayed);
    }

    @Test
    public void winningIncrementsGamesWonAndStreakAndUnlocksFirstVictory() {
        SaveData data = SaveData.defaults();

        List<Achievement> unlocked = AchievementEngine.checkGameEnd(data, true, 30, false, false);

        assertEquals(1, data.gamesWon);
        assertEquals(1, data.currentWinStreak);
        assertEquals(1, data.bestWinStreakEver);
        assertTrue(unlocked.contains(Achievement.FIRST_VICTORY));
    }

    @Test
    public void losingResetsCurrentStreakButNotBestEver() {
        SaveData data = SaveData.defaults();
        AchievementEngine.checkGameEnd(data, true, 30, false, false);
        AchievementEngine.checkGameEnd(data, true, 30, false, false);
        assertEquals(2, data.currentWinStreak);

        AchievementEngine.checkGameEnd(data, false, 10, false, false);

        assertEquals(0, data.currentWinStreak);
        assertEquals(2, data.bestWinStreakEver);
    }

    @Test
    public void winStreakTiersUnlockAtThreeFiveAndTen() {
        SaveData data = SaveData.defaults();
        for (int i = 0; i < 2; i++) {
            AchievementEngine.checkGameEnd(data, true, 10, false, false);
        }
        assertFalse(data.isUnlocked(Achievement.WIN_STREAK_3));

        List<Achievement> unlockedAtThree = AchievementEngine.checkGameEnd(data, true, 10, false, false);
        assertTrue(unlockedAtThree.contains(Achievement.WIN_STREAK_3));

        for (int i = 0; i < 7; i++) { // 3 -> 10 more wins takes the streak from 3 to 10
            AchievementEngine.checkGameEnd(data, true, 10, false, false);
        }
        assertTrue(data.isUnlocked(Achievement.WIN_STREAK_5));
        assertTrue(data.isUnlocked(Achievement.WIN_STREAK_10));
    }

    /**
     * A win-streak reset (a later loss) must not re-lock an already-unlocked
     * tier -- the core "permanent unlock" contract this whole feature
     * depends on.
     */
    @Test
    public void winStreakResetDoesNotRelockAlreadyEarnedTiers() {
        SaveData data = SaveData.defaults();
        for (int i = 0; i < 5; i++) {
            AchievementEngine.checkGameEnd(data, true, 10, false, false);
        }
        assertTrue(data.isUnlocked(Achievement.WIN_STREAK_5));

        AchievementEngine.checkGameEnd(data, false, 0, false, false);

        assertTrue("WIN_STREAK_5 must stay unlocked after the streak resets", data.isUnlocked(Achievement.WIN_STREAK_5));
    }

    @Test
    public void tenGamesPlayedUnlocksOnTheTenthGame() {
        SaveData data = SaveData.defaults();
        for (int i = 0; i < 9; i++) {
            AchievementEngine.checkGameEnd(data, false, 0, false, false);
        }
        assertFalse(data.isUnlocked(Achievement.TEN_GAMES_PLAYED));

        List<Achievement> unlocked = AchievementEngine.checkGameEnd(data, false, 0, false, false);

        assertTrue(unlocked.contains(Achievement.TEN_GAMES_PLAYED));
    }

    @Test
    public void flawlessGameUnlocksWhenFlagSetOnAWin() {
        SaveData data = SaveData.defaults();

        List<Achievement> unlocked = AchievementEngine.checkGameEnd(data, true, 200, true, false);

        assertTrue(unlocked.contains(Achievement.FLAWLESS_GAME));
    }

    @Test
    public void flawlessGameCanUnlockEvenOnALoss() {
        // FLAWLESS_GAME is about hitting the bonus every round, independent
        // of whether the game was ultimately won.
        SaveData data = SaveData.defaults();

        List<Achievement> unlocked = AchievementEngine.checkGameEnd(data, false, 50, true, false);

        assertTrue(unlocked.contains(Achievement.FLAWLESS_GAME));
    }

    @Test
    public void comebackKidUnlocksOnlyWhenEligibleAndWon() {
        SaveData data = SaveData.defaults();

        List<Achievement> lostAnyway = AchievementEngine.checkGameEnd(data, false, 10, false, true);
        assertFalse("comeback-eligible but lost must not unlock", lostAnyway.contains(Achievement.COMEBACK_KID));

        List<Achievement> wonWithoutEligibility = AchievementEngine.checkGameEnd(data, true, 10, false, false);
        assertFalse("won but wasn't sole-last at halfway must not unlock", wonWithoutEligibility.contains(Achievement.COMEBACK_KID));

        List<Achievement> unlocked = AchievementEngine.checkGameEnd(data, true, 10, false, true);
        assertTrue(unlocked.contains(Achievement.COMEBACK_KID));
    }

    @Test
    public void highScoreTracksTheBestFinalScoreEverSeen() {
        SaveData data = SaveData.defaults();
        AchievementEngine.checkGameEnd(data, false, 40, false, false);
        assertEquals(40, data.highScore);

        AchievementEngine.checkGameEnd(data, false, 20, false, false);
        assertEquals("a lower score afterward must not overwrite the high score", 40, data.highScore);

        AchievementEngine.checkGameEnd(data, false, 90, false, false);
        assertEquals(90, data.highScore);
    }

    // --- checkNameSubmission: Bapi easter egg ---

    @Test
    public void bapiExactCaseInsensitiveMatchUnlocksTheEasterEgg() {
        SaveData data = SaveData.defaults();

        boolean newlyUnlocked = AchievementEngine.checkNameSubmission(data, "bApI");

        assertTrue(newlyUnlocked);
        assertTrue(data.isUnlocked(Achievement.BAPI_EASTER_EGG));
    }

    @Test
    public void bapiSubstringMatchDoesNotUnlock() {
        SaveData data = SaveData.defaults();

        assertFalse(AchievementEngine.checkNameSubmission(data, "Bapi Smith"));
        assertFalse(AchievementEngine.checkNameSubmission(data, "MyBapi"));
        assertFalse(data.isUnlocked(Achievement.BAPI_EASTER_EGG));
    }

    @Test
    public void reenteringBapiAfterUnlockDoesNotReReportANewUnlock() {
        SaveData data = SaveData.defaults();
        AchievementEngine.checkNameSubmission(data, "Bapi");

        boolean unlockedAgain = AchievementEngine.checkNameSubmission(data, "Bapi");

        assertFalse(unlockedAgain);
    }
}
