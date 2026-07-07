/**
 * design/persistent-game-state.md §2: a plain-data suit+value pair, the leaf
 * node of GameStateSnapshot's object graph. Mirrors this codebase's existing
 * plain-public-field data-holder convention (SaveData, RoundResultRow) --
 * no behavior beyond the class itself.
 */
public class CardSnapshot {
    public Suit suit;
    public CardValue value;

    public CardSnapshot() {
    }

    public CardSnapshot(Suit suit, CardValue value) {
        this.suit = suit;
        this.value = value;
    }
}
