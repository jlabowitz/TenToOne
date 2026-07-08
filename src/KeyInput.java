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
 *
 * ROADMAP item 10 follow-up: added keyPressed alongside the original
 * keyTyped, since only keyPressed carries modifier-key state (isControlDown)
 * and a stable getKeyCode() -- keyTyped's keyChar stream can't reliably
 * distinguish e.g. Ctrl+A (a real "select all" request) from a plain typed
 * character. Left/Right arrow keys route to moveCursorLeft()/
 * moveCursorRight() (or moveWordLeft()/moveWordRight() when Ctrl is held,
 * matching the standard OS word-jump shortcut) the same way -- arrows never
 * produce a keyTyped event at all, so there's no equivalent keyTyped
 * conflict to guard against for them.
 * Ctrl+Backspace is special-cased further: AWT still fires a
 * plain keyTyped('\b') for that same physical key regardless of the Ctrl
 * modifier, so without suppressNextBackspace, a Ctrl+Backspace would run
 * BOTH deleteWord() (from keyPressed) AND a plain backspace() (from the
 * keyTyped that immediately follows it) -- the flag swallows exactly that
 * one follow-on keyTyped call. Ctrl+A and Enter need no equivalent
 * suppression: Ctrl+A's keyTyped delivers a control character (SOH)
 * that isAllowedNameChar already rejects, and Enter's keyTyped delivers
 * '\n', also already rejected (see TestKeyInput.controlCharIsRejected) --
 * neither was ever reaching typeChar in the first place.
 *
 * Bug fix (user-reported, live playthrough): the Ctrl-lag problem. AWT
 * reports isControlDown() based on whether the physical Ctrl key is *still
 * held down* at the moment a later key is pressed -- and a real user's Ctrl
 * release routinely lags a few milliseconds behind the very next keystroke
 * (e.g. immediately after Ctrl+A, releasing Ctrl and pressing the intended
 * replacement letter are two independent physical actions whose timing
 * isn't perfectly synced). When that happens, AWT delivers *that* keystroke
 * too with isControlDown()==true and a Ctrl-translated ASCII control
 * character in getKeyChar() (confirmed live: Ctrl+Z arrived as keyChar=26,
 * not 'z', in both keyPressed and keyTyped -- there is no event field that
 * reports "what this key would have produced without Ctrl"). Previously,
 * isAllowedNameChar correctly rejected that control character, but with
 * nothing else to catch it, the user's intended letter was silently
 * dropped instead of typed -- indistinguishable, from the user's side, from
 * "select-all didn't replace the name; nothing happened."
 *
 * Regression fix (code review): the first attempt at the above scoped the
 * plain-character-reconstruction fallback to "any Ctrl-held keystroke that
 * isn't Ctrl+A/Ctrl+Backspace", reasoning that this class defines no other
 * Ctrl shortcuts. That's true of *this* class, but not of the platform: it
 * meant a genuine Ctrl+V/C/X/Z/S etc. typed on its own (not preceded by a
 * lagging Ctrl+A/Backspace) inserted a stray literal letter into the name
 * field instead of being the harmless no-op it was before. The fallback is
 * now scoped to awaitingCtrlLagFollowUp below: only the single keyPressed
 * event immediately following an actual Ctrl+A/Ctrl+Backspace gets the
 * reconstruction treatment; every other unrecognized Ctrl-combo goes back
 * to being a no-op. Deliberately keyTyped-agnostic: the flag is armed and
 * consumed entirely within keyPressed, because the Ctrl+A/Backspace
 * keystroke's own trailing keyTyped (a rejected control character, e.g.
 * SOH for Ctrl+A) always fires before the *next physical key's*
 * keyPressed -- if keyTyped also cleared the flag, that harmless trailing
 * event would close the window before the real lagging keystroke ever
 * arrives, reintroducing the original bug.
 */
public class KeyInput extends KeyAdapter {
    private volatile TypingTarget target;

    /**
     * AWT event-thread-only state (keyPressed/keyTyped for the same physical
     * key both fire synchronously on that thread, in that order) -- no
     * volatile/synchronization needed, unlike `target` itself (written by
     * the game-logic thread via setTarget, read here on the event thread).
     */
    private boolean suppressNextBackspace = false;

    /**
     * Armed (true) only by a Ctrl+A/Ctrl+Backspace keyPressed, and always
     * consumed by the very next keyPressed call -- whatever that call turns
     * out to be -- via the capture-then-clear at the top of keyPressed
     * below. This gives the Ctrl-lag reconstruction fallback a one-keystroke
     * window instead of applying to every unrecognized Ctrl-combo forever
     * (see class doc's "Regression fix" section for why keyTyped
     * deliberately does not also clear this).
     */
    private boolean awaitingCtrlLagFollowUp = false;

    public void setTarget(TypingTarget target) {
        this.target = target;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        TypingTarget t = target;
        if (t == null) return;
        boolean wasAwaitingCtrlLagFollowUp = awaitingCtrlLagFollowUp;
        awaitingCtrlLagFollowUp = false;
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {
            t.selectAll();
            awaitingCtrlLagFollowUp = true;
            return;
        }
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            t.deleteWord();
            suppressNextBackspace = true;
            awaitingCtrlLagFollowUp = true;
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            t.submit();
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_LEFT) {
            if (e.isControlDown()) {
                t.moveWordLeft();
            } else {
                t.moveCursorLeft();
            }
            return;
        }
        if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
            if (e.isControlDown()) {
                t.moveWordRight();
            } else {
                t.moveCursorRight();
            }
            return;
        }
        if (e.isControlDown() && wasAwaitingCtrlLagFollowUp) {
            // Bug fix (user-reported, live playthrough): see class doc's
            // "Ctrl-lag" section -- a stray Ctrl held into the keystroke
            // immediately following a Ctrl+A/Ctrl+Backspace must still type
            // the plain character, not silently drop it. Outside this one-
            // keystroke window, an unrecognized Ctrl-combo is a no-op (see
            // class doc's "Regression fix" section).
            char plain = plainNameCharFor(e.getKeyCode(), e.isShiftDown());
            if (plain != 0) {
                t.typeChar(plain);
            }
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        TypingTarget t = target;
        if (t == null) return;
        char c = e.getKeyChar();
        if (c == '\b') {
            if (suppressNextBackspace) {
                suppressNextBackspace = false;
                return;
            }
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

    /**
     * Reconstructs the plain (non-Ctrl-modified) character for a name-field
     * keystroke's keyCode -- used only from keyPressed's Ctrl-lag recovery
     * branch above, where the delivered keyChar can't be trusted (it's a
     * Ctrl-translated ASCII control code, not the letter/digit actually
     * pressed). Covers exactly the characters isAllowedNameChar accepts;
     * anything else (e.g. a real shortcut key, or punctuation this field
     * doesn't allow anyway) returns 0, which the caller treats as "not a
     * plain-typeable key" and leaves untyped -- same net effect
     * isAllowedNameChar's own rejection would have had, just reached by a
     * different path. Deliberately ignores CapsLock (this codebase has no
     * existing CapsLock-awareness to mirror) -- only Shift flips case, same
     * as every other convention in this file.
     *
     * Bug fix (code review): digits/hyphen/apostrophe previously ignored
     * shiftDown entirely and always returned the bare unshifted character.
     * On a real keyboard, Shift held on those keys normally types a
     * different, punctuation character instead (Shift+3 -> '#', Shift+- ->
     * '_', Shift+' -> '"'), all of which isAllowedNameChar already rejects
     * -- so genuinely holding Shift into one of these keys must return 0
     * here too, matching what the normal (non-Ctrl-lag) typing path would
     * have produced, rather than inserting a character that path never
     * would have.
     */
    static char plainNameCharFor(int keyCode, boolean shiftDown) {
        if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) {
            char upper = (char) keyCode;
            return shiftDown ? upper : Character.toLowerCase(upper);
        }
        if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) {
            return shiftDown ? 0 : (char) keyCode;
        }
        if (keyCode == KeyEvent.VK_SPACE) {
            return ' ';
        }
        if (keyCode == KeyEvent.VK_MINUS || keyCode == KeyEvent.VK_SUBTRACT) {
            return shiftDown ? 0 : '-';
        }
        if (keyCode == KeyEvent.VK_QUOTE) {
            return shiftDown ? 0 : '\'';
        }
        return 0;
    }
}
