import java.util.List;

/**
 * ROADMAP item 1 (design/ai-and-polish.md §3/§4/§5): consolidates every piece
 * of per-call betting context Round.bet()'s loop needs to thread down to each
 * bettor (human or AI). This grew out of the previous chunk's plain
 * (sumOfPriorBets, isLastBettor, totalBetsCannotEqualTricks) parameter list --
 * §4.1's opponent-aware signal needs each individual prior bettor's bet value
 * (not just their sum, to find the single most extreme outlier) plus
 * numPlayers (for expectedPerPlayer = numCardsThisRound / numPlayers), and
 * §5's last-round fix needs isFirstBettor. With this many pieces of context
 * needed, a single small object is clearer than continuing to add primitive
 * parameters one at a time.
 *
 * Round.bet()'s loop remains the single place that assembles this each
 * iteration -- no Player reaches into Round or another Player to get any of
 * this itself. numCardsThisRound isn't a field here, same as the previous
 * chunk's convention: it always equals the bettor's own hand size
 * (getHand().getNumCards()) for the whole round, already available to every
 * implementation without threading it separately.
 *
 * trump is still just the trump Suit, not the trump indicator Card's rank --
 * that gap (needed only for real card-counting, design doc §6.3) is unrelated
 * to what this chunk's betting logic needs.
 */
public record BettingContext(
        Suit trump,
        List<Integer> priorBets,
        int numPlayers,
        boolean isFirstBettor,
        boolean isLastBettor,
        boolean totalBetsCannotEqualTricks
) {
    /** Sum of priorBets -- kept as a convenience method rather than a separately-threaded field. */
    public int sumOfPriorBets() {
        int sum = 0;
        for (int bet : priorBets) {
            sum += bet;
        }
        return sum;
    }
}
