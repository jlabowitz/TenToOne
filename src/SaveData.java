import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * ROADMAP item 2: the whole shape of what SaveStore persists -- continuously-
 * tracked stats, the achievement unlock map, and a saveFormatVersion escape
 * hatch for future migrations. Deliberately a plain public-field data holder
 * (no getters/setters), same convention as RoundResultRow -- this is a
 * transport/state object, not a behavior-owning class, and this codebase
 * already has that shape for exactly this kind of thing.
 *
 * unlockedAchievements maps Achievement.name() -> the Instant it was
 * unlocked; absence means locked. unlock() is the only mutator for that map
 * -- see its own doc for the "once unlocked, permanent" contract every
 * achievement in this system depends on (e.g. a win-streak resetting to 0
 * must not touch WIN_STREAK_5's already-set unlock).
 */
public class SaveData {
    public static final String CURRENT_SAVE_FORMAT_VERSION = "1";

    public String saveFormatVersion = CURRENT_SAVE_FORMAT_VERSION;
    public int gamesPlayed = 0;
    public int gamesWon = 0;
    public int highScore = 0;
    public int currentWinStreak = 0;
    public int bestWinStreakEver = 0;
    public final Map<String, Instant> unlockedAchievements = new HashMap<>();

    /** Sensible defaults for a missing/corrupt save file -- zeroed stats, nothing unlocked. */
    public static SaveData defaults() {
        return new SaveData();
    }

    public boolean isUnlocked(Achievement achievement) {
        return unlockedAchievements.containsKey(achievement.name());
    }

    public Instant unlockedAt(Achievement achievement) {
        return unlockedAchievements.get(achievement.name());
    }

    /**
     * Marks ACHIEVEMENT unlocked at the current instant, unless it's already
     * unlocked -- achievements are permanent once earned, so a repeat call
     * (e.g. hitting SCORE_OVER_50 again next game) is a no-op that neither
     * moves the original timestamp nor reports a fresh unlock. Returns true
     * only when this call is what actually unlocked it, so callers (e.g. the
     * toast queue) can tell "already had this" from "just earned this" and
     * only announce the latter.
     */
    public boolean unlock(Achievement achievement) {
        if (isUnlocked(achievement)) {
            return false;
        }
        unlockedAchievements.put(achievement.name(), Instant.now());
        return true;
    }
}
