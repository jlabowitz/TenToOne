import java.awt.Color;

public enum Suit {
    DIAMONDS("D", "♦"),
    CLUBS("C", "♣"),
    HEARTS("H", "♥"),
    SPADES("S", "♠");

    private String letter;
    private final String glyph;

    Suit(String letter, String glyph) {
        this.letter = letter;
        this.glyph = glyph;
    }

    public String getLetter() {
        return letter;
    }

    /** Unicode suit symbol (e.g. "♥" for Hearts). */
    public String getGlyph() {
        return glyph;
    }

    /** Red for Hearts/Diamonds, black for Clubs/Spades -- matches card-face coloring convention. */
    public Color getColor() {
        return (this == HEARTS || this == DIAMONDS) ? Color.RED : Color.BLACK;
    }

    /** Capitalized display name, e.g. "Hearts", for text that must not rely on color alone. */
    public String getDisplayName() {
        String lower = name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
