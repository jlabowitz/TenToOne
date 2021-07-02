import java.awt.*;

public class Card extends GameObject {
    private final Suit suit;
    private final CardValue value;

    public Card(Suit suit, CardValue value) {
        super(50, 50, ID.CARD);
        this.suit = suit;
        this.value = value;
    }

    public Suit getSuit() {
        return suit;
    }

    public CardValue getValue() {
        return value;
    }

    @Override
    public String toString() {
        String val;
        if (this.value.getValue() <= 10) {
            val = this.value.getValue().toString();
        } else {
            val = this.value.name();
        }
        return val + " of " + suit.name();
    }

    @Override
    public void tick() {

    }

    @Override
    public void render(Graphics g) {
        if (suit == Suit.DIAMONDS || suit == Suit.HEARTS) {
            g.setColor(Color.red);
        } else {
            g.setColor(Color.black);
        }
        g.drawString(toString(), x, y);
    }
}
