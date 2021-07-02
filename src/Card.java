import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Card extends GameObject {
    private final Suit suit;
    private final CardValue value;

    public Card(Suit suit, CardValue value) {
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
        String imgString = "img/" + value.getShortVal() + suit.getLetter() + ".png";


        BufferedImage img = null;
        try {
            img = ImageIO.read(new File(imgString));
        } catch (IOException e) {
            e.printStackTrace();
        }

        g.drawImage(img, x, y, 60, 100, null);
        g.drawString(toString(), x, y);
    }
}
