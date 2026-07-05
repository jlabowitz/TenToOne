/**
 * ROADMAP item 1 (AI & polish, design/ai-and-polish.md §3): a plain,
 * code-only config holder for player-tunable rules -- mirrors SaveData's
 * plain-public-field data-holder convention (no getters/setters, no behavior
 * beyond the class itself). There is no Settings/Options UI yet; a future
 * one would read/write this same object rather than a parallel mechanism.
 * Only one field exists today, but this class is the intended home for
 * future settings to accumulate into, not a single-field one-off -- don't
 * fold new settings into some other class once they show up.
 */
public class GameSettings {
    /**
     * "Total bets cannot equal number of tricks" -- the standard trick-taking
     * house rule (see Round.isLegalBet) requiring at least one bettor to miss
     * their bet every round. Default on, per the user's explicit decision.
     */
    public boolean totalBetsCannotEqualTricks = true;
}
