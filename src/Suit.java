public enum Suit {
    DIAMONDS("D"),
    CLUBS("C"),
    HEARTS("H"),
    SPADES("S");

    private String letter;

    Suit(String letter) {
        this.letter = letter;
    }

    public String getLetter() {
        return letter;
    }
}
