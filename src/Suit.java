public enum Suit {
    HEARTS("H"),
    DIAMONDS("D"),
    SPADES("S"),
    CLUBS("C");

    private String letter;

    Suit(String letter) {
        this.letter = letter;
    }

    public String getLetter() {
        return letter;
    }
}
