import java.awt.Point;
import java.util.function.BiPredicate;

/**
 * ROADMAP item 10 follow-up: the "a click outside the panel dismisses with no
 * other action" convention every full-canvas showBlocking loop in this
 * codebase now follows (originally hand-copied into AchievementsView/
 * PauseView's own showBlocking loops as an isInsidePanel(px,py) hit-test plus
 * an inline "if not inside, log + return" check) -- pulled out here so
 * RulesView/SettingsView/StatsView (and any future modal) can opt in with one
 * line instead of re-deriving that same shape by hand, and so the exact log
 * message format ("outside-panel dismiss (ViewName)") only lives in one
 * place.
 *
 * Deliberately a static helper, not a shared base class: none of
 * RulesView/AchievementsView/SettingsView/StatsView/PauseView extend a common
 * superclass today (see RulesView's own class doc for why they don't extend
 * ModalOverlay either -- its panel is fixed at 640x430, too small for these
 * full-canvas views), and their panel geometries differ too much (fixed
 * full-canvas 760x590-ish vs PauseView's small centered 300x160) to justify
 * introducing one now just for this. Each caller keeps owning its own
 * isInsidePanel(px,py) hit-test (already implemented per-class, and already
 * exercised directly by that class's own tests) -- this class owns only the
 * "click outside -> log + return true" glue.
 *
 * HamburgerMenu is NOT retrofitted onto this helper: it already has its own
 * outside-dismiss (predating this pass, with an extra confirmingRestart-aware
 * branch in its own isInsidePanel), and the polish-pass brief that introduced
 * this class explicitly scoped HamburgerMenu (and its restart-confirm
 * sub-view) out.
 */
public final class ModalDismiss {
    private ModalDismiss() {
    }

    /**
     * Returns true (and logs the standard outside-panel-dismiss message) iff
     * CLICK falls outside ISINSIDEPANEL's hit-test -- callers should
     * {@code return} immediately when this returns true, mirroring every
     * existing showBlocking loop's own "if outside, log + return" shape.
     * VIEWNAME is used verbatim in the log message, e.g. "RulesView" produces
     * "outside-panel dismiss (RulesView)".
     */
    public static boolean isOutsidePanel(Point click, BiPredicate<Integer, Integer> isInsidePanel, String viewName) {
        if (isInsidePanel.test(click.x, click.y)) {
            return false;
        }
        InteractionLog.logClick(click.x, click.y, "outside-panel dismiss (" + viewName + ")");
        return true;
    }
}
