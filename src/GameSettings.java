/**
 * ROADMAP item 1 (AI & polish, design/ai-and-polish.md §3): a plain,
 * code-only config holder for player-tunable rules -- mirrors SaveData's
 * plain-public-field data-holder convention (no getters/setters, no behavior
 * beyond the class itself). There is no Settings/Options UI yet; a future
 * one would read/write this same object rather than a parallel mechanism.
 * Only one field exists today, but this class is the intended home for
 * future settings to accumulate into, not a single-field one-off -- don't
 * fold new settings into some other class once they show up.
 *
 * ROADMAP item 10 follow-up: gameplay (Round.bet()) reads
 * totalBetsCannotEqualTricks live, once per bettor, every round -- if
 * SettingsView mutated that same field directly, toggling the in-game
 * Settings screen mid-round would change the rule for the very next bettor
 * in the *same* round, which is confusing (the user explicitly flagged this
 * as a risk). Instead, SettingsView mutates pendingTotalBetsCannotEqualTricks
 * -- a staged value -- and applyPending() is the only thing that copies it
 * onto the live field, called only at the moments Game.java already
 * establishes a genuinely fresh game's starting state (restartForNewGame(),
 * establishFreshGameState()), never mid-round and never when resuming an
 * in-progress game (that must keep whatever setting was in effect when the
 * game was checkpointed).
 */
public class GameSettings {
    /**
     * "Total bets cannot equal number of tricks" -- the standard trick-taking
     * house rule (see Round.isLegalBet) requiring at least one bettor to miss
     * their bet every round. Default on, per the user's explicit decision.
     * This is the effective value gameplay reads -- see applyPending().
     */
    public boolean totalBetsCannotEqualTricks = true;

    /**
     * The value the Settings screen actually toggles -- copied onto
     * totalBetsCannotEqualTricks only by applyPending(), so a change made
     * mid-game doesn't take effect until the next full game starts. Same
     * default as totalBetsCannotEqualTricks, so a fresh GameSettings has
     * nothing "pending" to apply.
     */
    public boolean pendingTotalBetsCannotEqualTricks = true;

    /**
     * Copies the staged toggle value onto the live one gameplay reads --
     * called at the start of a genuinely fresh game (see this class's own
     * doc for exactly which call sites, and which must NOT call this).
     */
    public void applyPending() {
        totalBetsCannotEqualTricks = pendingTotalBetsCannotEqualTricks;
    }
}
