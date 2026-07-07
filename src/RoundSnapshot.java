/**
 * design/persistent-game-state.md §2: the round in progress (if any -- see
 * GameStateSnapshot.round, null between rounds). Deliberately has no
 * deckRemaining field -- per §1's explicit revision, the undealt deck is dead
 * once a round starts (nothing calls Deck.draw() again for the rest of that
 * round), so there is nothing left in it to reconstruct.
 */
public class RoundSnapshot {
    public CardSnapshot trumpCard;
    public boolean trumpBroken;
    /**
     * The seat leading the round's current (mid-trick) or next (between
     * tricks) trick -- Round's own currentPlayer field's existing double
     * duty (see Round.playRound()'s doc). A deliberate addition beyond
     * design/persistent-game-state.md §2's literal RoundSnapshot listing:
     * that section doesn't call this out explicitly, but without it a round
     * resumed between tricks (currentTrick == null, so TrickSnapshot.
     * trickStartPlayer isn't available either) has no way to know which seat
     * leads the next trick. When currentTrick != null this always equals
     * currentTrick.trickStartPlayer.
     */
    public int currentPlayer;
    /** Null between tricks (all players have played 0 cards, nobody's mid-trick). */
    public TrickSnapshot currentTrick;

    public RoundSnapshot() {
    }
}
