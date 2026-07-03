import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Card extends GameObject {
    /** Rendered card size in pixels; also used for click hit-testing. */
    public static final int WIDTH = 60;
    public static final int HEIGHT = 100;
    /** Padding on each side of a card for the trump highlight border. */
    private static final int BORDER = 10;
    /** Innermost offset of the concentric high-card rings (13/14). */
    private static final int HIGH_CARD_BORDER = 13;
    private static final int HIGH_CARD_RING_COUNT = 2;
    private static final Color HIGH_CARD_COLOR = new Color(204, 153, 0);

    private final Suit suit;
    private final CardValue value;
    private boolean trump;
    /**
     * Whether this card is the current highest-valued card in the
     * trick-in-progress. Written by Trick.play() (game-logic thread) after
     * each card lands, mid-trick, while the render thread concurrently reads
     * it every frame -- unlike `trump` (written once at deal time, before
     * any render thread contention), this needs volatile. See Game.running/
     * Handler's class doc for this codebase's established cross-thread field
     * pattern.
     */
    private volatile boolean highCard;

    public Card(Suit suit, CardValue value) {
        this.suit = suit;
        this.value = value;
        this.trump = false;
        this.highCard = false;
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

    public boolean isHighCard() {
        return highCard;
    }

    public void setHighCard(boolean highCard) {
        this.highCard = highCard;
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

    /**
     * Card images are a small, fixed set of static assets (52 files) that
     * never change during a run, so a single process-wide cache with no
     * eviction is sufficient. ConcurrentHashMap is used even though the
     * render thread is the only reader/writer in practice today: this
     * codebase has already had one unsynchronized-shared-state bug (see the
     * TestGame regression comments), so cheap thread-safety insurance here
     * costs nothing and removes one more thing to get wrong later.
     */
    private static final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();

    /**
     * Loads (and caches) the image at the given path. The first call for a
     * given path reads from disk; every subsequent call for that same path
     * returns the same cached BufferedImage instance.
     *
     * On IOException (e.g. missing file), preserves the original behavior:
     * prints the stack trace and yields a null image. Note: a null result
     * is NOT cached (Map.computeIfAbsent never stores null), so a
     * consistently-missing file will retry the disk read - and re-print the
     * stack trace - on every call, exactly as the pre-caching code did.
     */
    static BufferedImage loadImage(String path) {
        return imageCache.computeIfAbsent(path, Card::readImageFromDisk);
    }

    private static BufferedImage readImageFromDisk(String path) {
        try {
            return ImageIO.read(new File(path));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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

        BufferedImage img = loadImage(imgString);

        g.drawImage(img, x, y, WIDTH, HEIGHT, null);
        //g.drawString(toString(), x, y);
        g.setColor(Color.black);
        if (trump) {
            g.drawRect(x - BORDER, y - BORDER, WIDTH + 2 * BORDER, HEIGHT + 2 * BORDER);
        }
        if (highCard) {
            g.setColor(HIGH_CARD_COLOR);
            for (int i = 0; i < HIGH_CARD_RING_COUNT; i++) {
                int offset = HIGH_CARD_BORDER + i;
                g.drawRect(x - offset, y - offset, WIDTH + 2 * offset, HEIGHT + 2 * offset);
            }
        }
    }
}
