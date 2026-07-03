/**
 * ROADMAP item 1: a live-updating text buffer that keyboard input can be
 * routed into. First-ever keyboard-input consumer in this codebase (see
 * KeyInput's class doc) -- StartScreen is the initial implementer, feeding
 * the name-entry field.
 */
public interface TypingTarget {
    void typeChar(char c);
    void backspace();
}
