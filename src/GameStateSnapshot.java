import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * design/persistent-game-state.md §2: the top-level, plain-data
 * representation of "everything needed to reconstruct a game in progress".
 * Written by GameStateCodec.toSnapshot from the live Game/Round/Trick/Player
 * object graph, and read back by GameStateCodec.fromSnapshot. Mirrors this
 * codebase's existing plain-public-field data-holder convention (SaveData,
 * RoundResultRow) -- no behavior beyond the class itself.
 */
public class GameStateSnapshot {
    public static final String CURRENT_SAVE_FORMAT_VERSION = "1";

    public String saveFormatVersion = CURRENT_SAVE_FORMAT_VERSION;
    public Instant savedAt;
    public int roundIndex;
    public int roundStartingPlayer;
    public int roundsHitBonusThisGame;
    public boolean wasSoleLastAtHalfway;
    public List<PlayerSnapshot> players = new ArrayList<>();
    /** Null if saved between rounds (e.g. right after game-end, before the next Round is constructed). */
    public RoundSnapshot round;

    public GameStateSnapshot() {
    }
}
