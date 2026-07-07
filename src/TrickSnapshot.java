import java.util.ArrayList;
import java.util.List;

/**
 * design/persistent-game-state.md §2: the trick currently in progress (if
 * any -- see RoundSnapshot.currentTrick, null between tricks). The running
 * high-card pointer is deliberately not stored -- it's derivable from
 * cardsPlayedBySeat + trump via Round.determineTrickWinner, per §1.
 */
public class TrickSnapshot {
    public int trickStartPlayer;
    public int currentPlayer;
    /** Null if the trick just started and no card has landed yet. */
    public Suit leadingSuit;
    /** Ordered as played. */
    public List<SeatCardPlay> cardsPlayedBySeat = new ArrayList<>();

    public TrickSnapshot() {
    }
}
