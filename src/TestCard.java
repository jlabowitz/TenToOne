import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Card's image loading/caching.
 *
 * Card.render previously called ImageIO.read(new File(path)) from disk on
 * every single render call (~60fps, once per visible card). These tests
 * prove Card.loadImage(String) (a) actually loads a real image for a valid
 * card path, and (b) returns the SAME cached BufferedImage instance on a
 * second call for the same path, proving no second disk read happens.
 */
public class TestCard {
    // A card image known to exist in img/ (five of clubs).
    private static final String VALID_PATH = "img/5C.png";

    @Test
    public void loadImageReturnsRealImageForValidPath() {
        BufferedImage img = Card.loadImage(VALID_PATH);
        assertNotNull(img);
        assertTrue(img.getWidth() > 0);
        assertTrue(img.getHeight() > 0);
    }

    @Test
    public void loadImageCachesSameInstanceOnSecondCall() {
        BufferedImage first = Card.loadImage(VALID_PATH);
        BufferedImage second = Card.loadImage(VALID_PATH);
        assertSame(first, second);
    }

    @Test
    public void freshCardIsNotHighCard() {
        Card card = new Card(Suit.CLUBS, CardValue.FIVE);
        assertFalse(card.isHighCard());
    }

    @Test
    public void setHighCardTogglesFlag() {
        Card card = new Card(Suit.CLUBS, CardValue.FIVE);
        card.setHighCard(true);
        assertTrue(card.isHighCard());
        card.setHighCard(false);
        assertFalse(card.isHighCard());
    }
}
