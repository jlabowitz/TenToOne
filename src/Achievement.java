/**
 * ROADMAP item 2: static achievement definitions -- id (the enum constant
 * itself), display name, description, hidden flag, and category. Persisted
 * unlock state (whether/when each id was unlocked) is deliberately NOT here;
 * see SaveData's unlockedAchievements map, which stores only an Instant per
 * id and treats absence as locked. Mirrors this codebase's existing
 * enum-with-per-constant-data convention (see Suit.java).
 *
 * 11 non-hidden milestone achievements plus one hidden easter egg
 * (BAPI_EASTER_EGG) -- see this item's ROADMAP writeup for the full design
 * rationale. SCORE_OVER_50/_100 are checked against a strictly-greater-than
 * threshold ("over 50"/"over 100", not "50 or more").
 */
public enum Achievement {
    FIRST_VICTORY("First Victory", "Win your first game.", Category.PROGRESS),
    TEN_GAMES_PLAYED("Ten-Timer", "Play 10 games.", Category.PROGRESS),
    WIN_STREAK_3("On a Roll", "Win 3 games in a row.", Category.STREAK),
    WIN_STREAK_5("Hot Streak", "Win 5 games in a row.", Category.STREAK),
    WIN_STREAK_10("Unstoppable", "Win 10 games in a row.", Category.STREAK),
    SCORE_OVER_50("Half Century", "Finish a single game with a score over 50.", Category.SCORE),
    SCORE_OVER_100("Century", "Finish a single game with a score over 100.", Category.SCORE),
    PERFECT_ROUND("Right on the Money", "Bet exactly the tricks you took in a round.", Category.ROUND),
    FLAWLESS_GAME("Flawless", "Bet exactly the tricks you took in every round of a game.", Category.ROUND),
    COMEBACK_KID("Comeback Kid", "Come back from sole last place at the game's halfway point to win.", Category.PROGRESS),
    BAPI_EASTER_EGG("One and Only", "Enter a very special name when signing in.", true, Category.SPECIAL);

    public enum Category { PROGRESS, STREAK, SCORE, ROUND, SPECIAL }

    private final String displayName;
    private final String description;
    private final boolean hidden;
    private final Category category;

    Achievement(String displayName, String description, Category category) {
        this(displayName, description, false, category);
    }

    Achievement(String displayName, String description, boolean hidden, Category category) {
        this.displayName = displayName;
        this.description = description;
        this.hidden = hidden;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** Omitted from the Achievements list entirely until unlocked -- see AchievementsView. */
    public boolean isHidden() {
        return hidden;
    }

    public Category getCategory() {
        return category;
    }
}
