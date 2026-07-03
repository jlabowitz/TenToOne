import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for KeyInput.isAllowedNameChar, the pure filter deciding which
 * keystrokes reach a TypingTarget's typeChar (ROADMAP item 1). Backspace
 * ('\b') is handled separately in keyTyped before this filter runs, but is
 * included here to document that this predicate alone doesn't special-case
 * it (render() thread never routes it through typeChar either way).
 */
public class TestKeyInput {

    @Test
    public void letterIsAllowed() {
        assertTrue(KeyInput.isAllowedNameChar('A'));
        assertTrue(KeyInput.isAllowedNameChar('z'));
    }

    @Test
    public void digitIsAllowed() {
        assertTrue(KeyInput.isAllowedNameChar('7'));
    }

    @Test
    public void spaceIsAllowed() {
        assertTrue(KeyInput.isAllowedNameChar(' '));
    }

    @Test
    public void hyphenIsAllowed() {
        assertTrue(KeyInput.isAllowedNameChar('-'));
    }

    @Test
    public void apostropheIsAllowed() {
        assertTrue(KeyInput.isAllowedNameChar('\''));
    }

    @Test
    public void backspaceCharIsNotAllowedByThisFilter() {
        // keyTyped() intercepts '\b' before calling isAllowedNameChar, so
        // this predicate itself correctly rejects it -- it's not a letter,
        // digit, space, hyphen, or apostrophe.
        assertFalse(KeyInput.isAllowedNameChar('\b'));
    }

    @Test
    public void punctuationSymbolIsRejected() {
        assertFalse(KeyInput.isAllowedNameChar('@'));
        assertFalse(KeyInput.isAllowedNameChar('!'));
    }

    @Test
    public void controlCharIsRejected() {
        assertFalse(KeyInput.isAllowedNameChar('\n'));
        assertFalse(KeyInput.isAllowedNameChar('\t'));
    }

    @Test
    public void setTargetNullIsSafeNoOp() {
        KeyInput keyInput = new KeyInput();
        keyInput.setTarget(null);
        // no assertion beyond "doesn't throw" -- keyTyped's null-check guards
        // against this; there's no window to actually dispatch a KeyEvent
        // through in this test, so keyTyped itself isn't exercised here.
    }
}
