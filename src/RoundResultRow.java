/**
 * One row of the round-summary panel's table: a single player's bet-vs-
 * tricks-taken outcome for the round just finished.
 *
 * bonusHit/roundDelta reuse the exact same bet == tricksTaken equality check
 * and bonus Game.adjustScores() already applies when it updates the running
 * score -- no new comparison logic invented here, just captured for display.
 *
 * Data-lifecycle note (see Game.snapshotRoundResults): bet/tricksTaken must
 * be captured *before* Game.adjustScores() runs, since adjustScores() calls
 * player.resetTrickScore() right after computing each player's delta --
 * after that point getTrickScore() reads 0 for everyone. totalAfter, on the
 * other hand, can only be known *after* adjustScores() has run, so it's left
 * unset (0) here and filled in later by Game.applyTotals() once the score
 * update has actually happened.
 */
public class RoundResultRow {
    public final String name;
    public final boolean isHuman;
    public final int bet;
    public final int tricksTaken;
    public final boolean bonusHit;
    public final int roundDelta;
    /** Filled in after the fact by Game.applyTotals(); 0 until then. */
    public int totalAfter;

    public RoundResultRow(String name, boolean isHuman, int bet, int tricksTaken, int roundBonus) {
        this.name = name;
        this.isHuman = isHuman;
        this.bet = bet;
        this.tricksTaken = tricksTaken;
        this.bonusHit = (bet == tricksTaken);
        this.roundDelta = tricksTaken + (bonusHit ? roundBonus : 0);
    }
}
