import java.util.ArrayList;
import java.util.List;

/**
 * ROADMAP item 2: pure, Swing-free achievement-unlock rules, invoked from
 * Game's three hook points (round-end, game-end, name-submission). Every
 * check*() method mutates the given SaveData in place (via SaveData.unlock,
 * which is itself permanent-unlock-safe) and returns only the achievements
 * *newly* unlocked by that specific call -- callers (the toast queue) use
 * this to avoid re-announcing something already unlocked earlier.
 *
 * Deliberately has no dependency on Game/Player/Round -- callers snapshot
 * whatever primitive values they need (scores, flags) before calling in,
 * keeping this class testable without any Swing/Handler machinery.
 */
public class AchievementEngine {

    /**
     * Round-end checks: SCORE_OVER_50/_100 against the human's running total
     * *after* this round's scoring, and PERFECT_ROUND if this round's bet
     * equalled tricks taken (bonusHitThisRound). Both thresholds are
     * evaluated every round (not just once) since the human's score only
     * grows monotonically within a game, but a match can cross both
     * thresholds in the same round.
     */
    public static List<Achievement> checkRoundEnd(SaveData data, int humanScoreAfterRound, boolean bonusHitThisRound) {
        List<Achievement> newlyUnlocked = new ArrayList<>();
        if (humanScoreAfterRound > 50 && data.unlock(Achievement.SCORE_OVER_50)) {
            newlyUnlocked.add(Achievement.SCORE_OVER_50);
        }
        if (humanScoreAfterRound > 100 && data.unlock(Achievement.SCORE_OVER_100)) {
            newlyUnlocked.add(Achievement.SCORE_OVER_100);
        }
        if (bonusHitThisRound && data.unlock(Achievement.PERFECT_ROUND)) {
            newlyUnlocked.add(Achievement.PERFECT_ROUND);
        }
        return newlyUnlocked;
    }

    /**
     * True only if the human's score is strictly lower than every other
     * player's score -- a tie for last, or anyone else being even lower,
     * both return false (COMEBACK_KID requires *sole* last place, no ties).
     * Vacuously true if there are no other players.
     */
    public static boolean isSoleLastPlace(int humanScore, List<Integer> otherScores) {
        for (int otherScore : otherScores) {
            if (otherScore <= humanScore) {
                return false;
            }
        }
        return true;
    }

    /**
     * Game-end checks: updates the continuously-tracked stats (gamesPlayed/
     * gamesWon/highScore/currentWinStreak/bestWinStreakEver) and then checks
     * FIRST_VICTORY, TEN_GAMES_PLAYED, WIN_STREAK_3/5/10, FLAWLESS_GAME, and
     * COMEBACK_KID against the just-updated values.
     *
     * flawlessGame and comebackKidEligible are pre-computed by the caller
     * (Game tracks the per-game ephemeral state these depend on -- rounds-
     * hit-bonus count and the sole-last-at-halfway snapshot -- since this
     * class has no notion of "a game in progress").
     */
    public static List<Achievement> checkGameEnd(SaveData data, boolean humanWon, int finalHumanScore,
                                                   boolean flawlessGame, boolean comebackKidEligible) {
        List<Achievement> newlyUnlocked = new ArrayList<>();

        data.gamesPlayed++;
        if (humanWon) {
            data.gamesWon++;
            data.currentWinStreak++;
        } else {
            data.currentWinStreak = 0;
        }
        data.bestWinStreakEver = Math.max(data.bestWinStreakEver, data.currentWinStreak);
        data.highScore = Math.max(data.highScore, finalHumanScore);

        if (humanWon && data.unlock(Achievement.FIRST_VICTORY)) {
            newlyUnlocked.add(Achievement.FIRST_VICTORY);
        }
        if (data.gamesPlayed >= 10 && data.unlock(Achievement.TEN_GAMES_PLAYED)) {
            newlyUnlocked.add(Achievement.TEN_GAMES_PLAYED);
        }
        if (data.currentWinStreak >= 3 && data.unlock(Achievement.WIN_STREAK_3)) {
            newlyUnlocked.add(Achievement.WIN_STREAK_3);
        }
        if (data.currentWinStreak >= 5 && data.unlock(Achievement.WIN_STREAK_5)) {
            newlyUnlocked.add(Achievement.WIN_STREAK_5);
        }
        if (data.currentWinStreak >= 10 && data.unlock(Achievement.WIN_STREAK_10)) {
            newlyUnlocked.add(Achievement.WIN_STREAK_10);
        }
        if (flawlessGame && data.unlock(Achievement.FLAWLESS_GAME)) {
            newlyUnlocked.add(Achievement.FLAWLESS_GAME);
        }
        if (humanWon && comebackKidEligible && data.unlock(Achievement.COMEBACK_KID)) {
            newlyUnlocked.add(Achievement.COMEBACK_KID);
        }

        return newlyUnlocked;
    }

    /**
     * Name-submission check: unlocks BAPI_EASTER_EGG on an exact,
     * case-insensitive match against "Bapi" -- NOT a substring match ("Bapi
     * Smith"/"MyBapi" must not trigger it). Returns whether this call is
     * what newly unlocked it, so the caller only enqueues a toast once.
     */
    public static boolean checkNameSubmission(SaveData data, String trimmedName) {
        if (!trimmedName.equalsIgnoreCase("Bapi")) {
            return false;
        }
        return data.unlock(Achievement.BAPI_EASTER_EGG);
    }
}
