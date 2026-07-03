import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * Bridges keyboard input from the AWT event thread to whatever TypingTarget
 * currently wants it (e.g. StartScreen's name field). Mirrors MouseInput
 * extends MouseAdapter precisely, but deliberately has no blocking queue:
 * unlike a mouse click (a discrete event the game-logic thread blocks and
 * waits for), nothing here ever blocks waiting for "the next keystroke" --
 * the name buffer is live state, repeatedly read by the render thread every
 * frame, exactly like Player.trickLeader/leadingSuit's existing
 * write-on-one-thread/read-on-another volatile pattern. A direct synchronous
 * call from the AWT event thread straight into the target's volatile-backed
 * field is correct here -- no new synchronization primitive needed.
 */
public class KeyInput extends KeyAdapter {
    private volatile TypingTarget target;

    public void setTarget(TypingTarget target) {
        this.target = target;
    }

    @Override
    public void keyTyped(KeyEvent e) {
        TypingTarget t = target;
        if (t == null) return;
        char c = e.getKeyChar();
        if (c == '\b') {
            t.backspace();
            return;
        }
        if (isAllowedNameChar(c)) {
            t.typeChar(c);
        }
    }

    /** Pulled out as a pure static function so it's testable without a window -- mirrors IllegalPlayFeedback.colorAt()'s precedent. */
    static boolean isAllowedNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'';
    }
}
