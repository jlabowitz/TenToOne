import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Card extends GameObject {
    /** Rendered card size in pixels; also used for click hit-testing. */
    public static final int WIDTH = 60;
    public static final int HEIGHT = 100;
    /** Padding on each side of a card for the trump highlight border. */
    private static final int BORDER = 10;

    private final Suit suit;
    private final CardValue value;
    private boolean trump;

    public Card(Suit suit, CardValue value) {
        this.suit = suit;
        this.value = value;
        this.trump = false;
    }

    public Suit getSuit() {
        return suit;
    }

    public CardValue getValue() {
        return value;
    }

    public void setTrump() {
        trump = true;
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
        /*
        if (suit == Suit.DIAMONDS || suit == Suit.HEARTS) {
            g.setColor(Color.red);
        } else {
            g.setColor(Color.black);
        }
        */
        String imgString = "img/" + value.getShortVal() + suit.getLetter() + ".png";

        BufferedImage img = null;
        try {
            img = ImageIO.read(new File(imgString));
        } catch (IOException e) {
            e.printStackTrace();
        }

        g.drawImage(img, x, y, WIDTH, HEIGHT, null);
        //g.drawString(toString(), x, y);
        g.setColor(Color.black);
        if (trump) {
            g.drawRect(x - BORDER, y - BORDER, WIDTH + 2 * BORDER, HEIGHT + 2 * BORDER);
        }
    }
}
