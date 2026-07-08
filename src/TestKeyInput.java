import org.junit.Test;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for KeyInput.isAllowedNameChar, the pure filter deciding which
 * keystrokes reach a TypingTarget's typeChar (ROADMAP item 1). Backspace
 * ('\b') is handled separately in keyTyped before this filter runs, but is
 * included here to document that this predicate alone doesn't special-case
 * it (render() thread never routes it through typeChar either way).
 *
 * ROADMAP item 10 follow-up: also covers keyPressed's new modifier-aware
 * routing (Ctrl+A -> selectAll, Ctrl+Backspace -> deleteWord, Enter ->
 * submit) via a small recording FakeTypingTarget, including the
 * suppressNextBackspace interaction (a Ctrl+Backspace's keyTyped('\b') that
 * AWT still fires for the same physical key must not also run a plain
 * backspace() -- see KeyInput's own class doc).
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

    // --- ROADMAP item 10 follow-up: keyPressed's modifier-aware routing ---

    @Test
    public void ctrlAOnKeyPressedCallsSelectAll() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_A));

        assertEquals(1, target.selectAllCalls);
        assertEquals(0, target.deleteWordCalls);
        assertEquals(0, target.submitCalls);
    }

    @Test
    public void plainAOnKeyPressedDoesNotCallSelectAll() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(plainKeyPress(KeyEvent.VK_A));

        assertEquals(0, target.selectAllCalls);
    }

    @Test
    public void ctrlBackspaceOnKeyPressedCallsDeleteWord() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_BACK_SPACE));

        assertEquals(1, target.deleteWordCalls);
        assertEquals(0, target.backspaceCalls);
    }

    /**
     * The interaction this codebase's own KeyInput doc calls out: AWT fires a
     * plain keyTyped('\b') for the same physical Ctrl+Backspace press
     * regardless of the modifier -- without suppressNextBackspace, that
     * follow-on keyTyped would also run a plain backspace() on top of
     * deleteWord(), double-processing one physical key press.
     */
    @Test
    public void ctrlBackspaceSuppressesTheFollowingPlainBackspaceKeyTyped() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_BACK_SPACE));
        keyInput.keyTyped(charKeyTyped('\b'));

        assertEquals(1, target.deleteWordCalls);
        assertEquals("the keyTyped('\\b') that AWT still fires for the same physical key must be swallowed",
                0, target.backspaceCalls);
    }

    @Test
    public void plainBackspaceKeyTypedAfterACtrlBackspaceStillWorksNormally() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_BACK_SPACE));
        keyInput.keyTyped(charKeyTyped('\b')); // suppressed, as above
        keyInput.keyTyped(charKeyTyped('\b')); // a genuinely new backspace -- must NOT be suppressed too

        assertEquals(1, target.deleteWordCalls);
        assertEquals(1, target.backspaceCalls);
    }

    @Test
    public void enterOnKeyPressedCallsSubmit() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(plainKeyPress(KeyEvent.VK_ENTER));

        assertEquals(1, target.submitCalls);
    }

    @Test
    public void nullTargetIsSafeNoOpForKeyPressed() {
        KeyInput keyInput = new KeyInput();
        keyInput.setTarget(null);
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_A));
        // no assertion beyond "doesn't throw"
    }

    // --- Bug fix (user-reported, live playthrough): Ctrl-lag recovery,
    // scoped to a one-keystroke window (code review regression fix) ---
    // A real user's Ctrl release routinely lags a few ms behind the very
    // next keystroke immediately after Ctrl+A/Ctrl+Backspace, so AWT still
    // reports that next key as Ctrl-held too -- confirmed live via a Robot-
    // driven repro against the actual running game (Ctrl+Z arrived with
    // keyChar=26, not 'z') before this fix existed. The first version of
    // this fix over-applied the reconstruction fallback to *any*
    // unrecognized Ctrl-combo, which meant a genuine standalone Ctrl+Z/V/
    // C/etc. (not preceded by Ctrl+A/Backspace) wrongly typed a stray
    // letter instead of being a no-op -- see KeyInput's own class doc for
    // the full writeup of both the original bug and the regression.

    @Test
    public void laggingCtrlImmediatelyAfterCtrlATypesTheIntendedLowercaseLetter() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_A)); // primes the one-keystroke window
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_Z)); // the lagging-Ctrl artifact

        assertEquals(1, target.typeCharCalls);
        assertEquals("z", target.typedChars);
    }

    @Test
    public void laggingCtrlShiftImmediatelyAfterCtrlATypesTheIntendedUppercaseLetter() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_A));
        keyInput.keyPressed(controlShiftKeyPress(KeyEvent.VK_Z));

        assertEquals(1, target.typeCharCalls);
        assertEquals("Z", target.typedChars);
    }

    @Test
    public void laggingCtrlImmediatelyAfterCtrlATypesTheIntendedDigit() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_A));
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_7));

        assertEquals("7", target.typedChars);
    }

    @Test
    public void laggingCtrlImmediatelyAfterCtrlBackspaceTypesTheIntendedLetter() {
        // The window is armed by Ctrl+Backspace too, not just Ctrl+A.
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_BACK_SPACE));
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_Z));

        assertEquals(1, target.typeCharCalls);
        assertEquals("z", target.typedChars);
    }

    @Test
    public void ctrlATrailingControlCharKeyTypedDoesNotCloseTheLagRecoveryWindow() {
        // Real AWT sequence for one physical Ctrl+A press is keyPressed
        // (handled above, arms the window) immediately followed by its own
        // keyTyped delivering a rejected control character (SOH) -- that
        // trailing keyTyped must NOT consume the window, or the real
        // lagging keystroke arriving after it (the whole point of the
        // window) would never get the fallback. See KeyInput's own class
        // doc ("Regression fix" section) for why keyTyped is deliberately
        // not involved in clearing this flag.
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_A));
        keyInput.keyTyped(charKeyTyped((char) 1)); // SOH -- rejected, harmless
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_Z));

        assertEquals(1, target.typeCharCalls);
        assertEquals("z", target.typedChars);
    }

    @Test
    public void lagRecoveryWindowClosesAfterOneKeyPressedEvent() {
        // The window only ever covers the single keyPressed immediately
        // following Ctrl+A/Backspace -- a second, later keyPressed must not
        // still benefit from it.
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_A));
        keyInput.keyPressed(plainKeyPress(KeyEvent.VK_X)); // consumes the window, itself not Ctrl-held
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_Z)); // window already closed

        assertEquals(0, target.typeCharCalls);
    }

    @Test
    public void standaloneCtrlHeldLetterWithoutPriorSelectAllOrDeleteWordIsSafeNoOp() {
        // The regression this fix removes: a genuine Ctrl+Z on its own
        // (not preceded by Ctrl+A/Backspace) must insert nothing, same as
        // before the Ctrl-lag fallback existed at all.
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_Z));

        assertEquals(0, target.typeCharCalls);
        assertEquals("", target.typedChars);
    }

    @Test
    public void standaloneCommonCtrlShortcutsWithoutPriorSelectAllOrDeleteWordAreSafeNoOps() {
        // The review's own repro: Ctrl+V/C/X/S typed normally must not
        // insert "v"/"c"/"x"/"s" into the name field.
        int[] shortcutKeys = {KeyEvent.VK_V, KeyEvent.VK_C, KeyEvent.VK_X, KeyEvent.VK_S};
        for (int keyCode : shortcutKeys) {
            KeyInput keyInput = new KeyInput();
            FakeTypingTarget target = new FakeTypingTarget();
            keyInput.setTarget(target);

            keyInput.keyPressed(controlKeyPress(keyCode));

            assertEquals("Ctrl+" + KeyEvent.getKeyText(keyCode) + " must not type anything",
                    0, target.typeCharCalls);
        }
    }

    @Test
    public void ctrlHeldUnmappedKeyAfterCtrlAIsSafeNoOp() {
        // e.g. a genuine Ctrl+shortcut this app doesn't define (Ctrl+Tab) --
        // plainNameCharFor returns 0 for it, so nothing is typed even
        // within the lag-recovery window, matching isAllowedNameChar's own
        // rejection of whatever control character that combination would
        // otherwise have delivered.
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_A));
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_TAB));

        assertEquals(0, target.typeCharCalls);
    }

    @Test
    public void plainNameCharForCoversLettersDigitsAndPunctuation() {
        assertEquals('a', KeyInput.plainNameCharFor(KeyEvent.VK_A, false));
        assertEquals('A', KeyInput.plainNameCharFor(KeyEvent.VK_A, true));
        assertEquals('5', KeyInput.plainNameCharFor(KeyEvent.VK_5, false));
        assertEquals(' ', KeyInput.plainNameCharFor(KeyEvent.VK_SPACE, false));
        assertEquals('-', KeyInput.plainNameCharFor(KeyEvent.VK_MINUS, false));
        assertEquals('\'', KeyInput.plainNameCharFor(KeyEvent.VK_QUOTE, false));
        assertEquals(0, KeyInput.plainNameCharFor(KeyEvent.VK_TAB, false));
    }

    /**
     * Bug fix (code review): Shift genuinely held on a digit/hyphen/
     * apostrophe key normally types a different punctuation character on a
     * real keyboard (Shift+3 -> '#', Shift+- -> '_', Shift+' -> '"'), none
     * of which isAllowedNameChar accepts -- plainNameCharFor must return 0
     * for these rather than the bare unshifted character, or the Ctrl-lag
     * fallback could insert a character the normal typing path would never
     * have produced.
     */
    @Test
    public void plainNameCharForRejectsDigitHyphenApostropheWhenShiftIsHeld() {
        assertEquals(0, KeyInput.plainNameCharFor(KeyEvent.VK_3, true));
        assertEquals(0, KeyInput.plainNameCharFor(KeyEvent.VK_MINUS, true));
        assertEquals(0, KeyInput.plainNameCharFor(KeyEvent.VK_QUOTE, true));
    }

    @Test
    public void nullTargetIsSafeNoOpForCtrlHeldPlainLetter() {
        KeyInput keyInput = new KeyInput();
        keyInput.setTarget(null);
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_A));
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_Z));
        // no assertion beyond "doesn't throw"
    }

    private static KeyEvent controlKeyPress(int keyCode) {
        Component dummy = new Canvas();
        return new KeyEvent(dummy, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                InputEvent.CTRL_DOWN_MASK, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private static KeyEvent controlShiftKeyPress(int keyCode) {
        Component dummy = new Canvas();
        return new KeyEvent(dummy, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private static KeyEvent plainKeyPress(int keyCode) {
        Component dummy = new Canvas();
        return new KeyEvent(dummy, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                0, keyCode, KeyEvent.CHAR_UNDEFINED);
    }

    private static KeyEvent charKeyTyped(char c) {
        Component dummy = new Canvas();
        return new KeyEvent(dummy, KeyEvent.KEY_TYPED, System.currentTimeMillis(),
                0, KeyEvent.VK_UNDEFINED, c);
    }

    // --- ROADMAP item 10 follow-up (second pass): Left/Right arrow routing ---

    @Test
    public void leftArrowOnKeyPressedCallsMoveCursorLeft() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(plainKeyPress(KeyEvent.VK_LEFT));

        assertEquals(1, target.moveCursorLeftCalls);
        assertEquals(0, target.moveCursorRightCalls);
    }

    @Test
    public void rightArrowOnKeyPressedCallsMoveCursorRight() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(plainKeyPress(KeyEvent.VK_RIGHT));

        assertEquals(1, target.moveCursorRightCalls);
        assertEquals(0, target.moveCursorLeftCalls);
    }

    @Test
    public void nullTargetIsSafeNoOpForArrowKeys() {
        KeyInput keyInput = new KeyInput();
        keyInput.setTarget(null);
        keyInput.keyPressed(plainKeyPress(KeyEvent.VK_LEFT));
        keyInput.keyPressed(plainKeyPress(KeyEvent.VK_RIGHT));
        // no assertion beyond "doesn't throw"
    }

    // --- ROADMAP item 10 follow-up (third pass): Ctrl+Left/Right word-jump ---

    @Test
    public void ctrlLeftArrowCallsMoveWordLeftNotMoveCursorLeft() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_LEFT));

        assertEquals(1, target.moveWordLeftCalls);
        assertEquals(0, target.moveCursorLeftCalls);
    }

    @Test
    public void ctrlRightArrowCallsMoveWordRightNotMoveCursorRight() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_RIGHT));

        assertEquals(1, target.moveWordRightCalls);
        assertEquals(0, target.moveCursorRightCalls);
    }

    @Test
    public void plainLeftArrowStillCallsMoveCursorLeftNotMoveWordLeft() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(plainKeyPress(KeyEvent.VK_LEFT));

        assertEquals(1, target.moveCursorLeftCalls);
        assertEquals(0, target.moveWordLeftCalls);
    }

    @Test
    public void plainRightArrowStillCallsMoveCursorRightNotMoveWordRight() {
        KeyInput keyInput = new KeyInput();
        FakeTypingTarget target = new FakeTypingTarget();
        keyInput.setTarget(target);

        keyInput.keyPressed(plainKeyPress(KeyEvent.VK_RIGHT));

        assertEquals(1, target.moveCursorRightCalls);
        assertEquals(0, target.moveWordRightCalls);
    }

    @Test
    public void nullTargetIsSafeNoOpForCtrlArrowKeys() {
        KeyInput keyInput = new KeyInput();
        keyInput.setTarget(null);
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_LEFT));
        keyInput.keyPressed(controlKeyPress(KeyEvent.VK_RIGHT));
        // no assertion beyond "doesn't throw"
    }

    /** Records every call instead of mutating a real buffer -- simplest possible TypingTarget for asserting KeyInput's own routing/suppression logic in isolation. */
    private static class FakeTypingTarget implements TypingTarget {
        int typeCharCalls = 0;
        int backspaceCalls = 0;
        int selectAllCalls = 0;
        int deleteWordCalls = 0;
        int submitCalls = 0;
        int moveCursorLeftCalls = 0;
        int moveCursorRightCalls = 0;
        int moveWordLeftCalls = 0;
        int moveWordRightCalls = 0;
        String typedChars = "";

        @Override
        public void typeChar(char c) {
            typeCharCalls++;
            typedChars += c;
        }

        @Override
        public void backspace() {
            backspaceCalls++;
        }

        @Override
        public void selectAll() {
            selectAllCalls++;
        }

        @Override
        public void deleteWord() {
            deleteWordCalls++;
        }

        @Override
        public void submit() {
            submitCalls++;
        }

        @Override
        public void moveCursorLeft() {
            moveCursorLeftCalls++;
        }

        @Override
        public void moveCursorRight() {
            moveCursorRightCalls++;
        }

        @Override
        public void moveWordLeft() {
            moveWordLeftCalls++;
        }

        @Override
        public void moveWordRight() {
            moveWordRightCalls++;
        }
    }
}
