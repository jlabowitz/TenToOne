import java.util.ArrayList;
import java.util.List;

/**
 * design/persistent-game-state.md §2: one player's identity + per-game/
 * per-round state. archetypeId is the stable id from §2a (e.g. "human",
 * "ai_easy", "medium_balanced") -- never this player's cosmetic display
 * `name`, and never a Java class name.
 */
public class PlayerSnapshot {
    public String name;
    public String archetypeId;
    public int score;
    public int bet;
    public boolean hasBet;
    public int trickScore;
    public List<CardSnapshot> hand = new ArrayList<>();

    public PlayerSnapshot() {
    }
}
