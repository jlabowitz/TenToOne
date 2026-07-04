import org.junit.Test;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests for Handler's object-list traversal.
 *
 * Handler.object is written by the game-logic thread (Round/Trick/Human call
 * addObject/removeObject as cards enter and leave play) while it is read
 * every frame by the render/tick thread (Game.run() -> tick()/render()).
 * True cross-thread races don't unit-test deterministically, but a
 * same-thread mutate-during-traversal test still catches the concrete
 * corruption the old index-based LinkedList walk was prone to: when one
 * object's tick() causes a removal from the list being walked, a later
 * object silently gets skipped that same pass -- see
 * mutatingHandlerDuringTickDoesNotSkipObjects below for the exact scenario.
 */
public class TestHandler {

    private static class RecordingObject extends GameObject {
        int tickCount = 0;
        int renderCount = 0;
        Runnable onTick;

        @Override
        public void tick() {
            tickCount++;
            if (onTick != null) {
                onTick.run();
            }
        }

        @Override
        public void render(Graphics g) {
            renderCount++;
        }
    }

    /** Records its own NAME onto a shared list every time render() runs, so a test can assert on relative render order across objects, not just call counts. */
    private static class OrderRecordingObject extends GameObject {
        final String name;
        final List<String> renderOrder;

        OrderRecordingObject(String name, List<String> renderOrder) {
            this.name = name;
            this.renderOrder = renderOrder;
        }

        @Override
        public void tick() {
            //not exercised by the render-order tests below
        }

        @Override
        public void render(Graphics g) {
            renderOrder.add(name);
        }
    }

    @Test
    public void tickCallsTickOnEveryAddedObject() {
        Handler handler = new Handler();
        RecordingObject a = new RecordingObject();
        RecordingObject b = new RecordingObject();
        handler.addObject(a);
        handler.addObject(b);

        handler.tick();

        assertEquals(1, a.tickCount);
        assertEquals(1, b.tickCount);
    }

    @Test
    public void renderCallsRenderOnEveryAddedObject() {
        Handler handler = new Handler();
        RecordingObject a = new RecordingObject();
        RecordingObject b = new RecordingObject();
        handler.addObject(a);
        handler.addObject(b);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();
        handler.render(g);
        g.dispose();

        assertEquals(1, a.renderCount);
        assertEquals(1, b.renderCount);
    }

    @Test
    public void removeObjectDropsItFromSubsequentTicks() {
        Handler handler = new Handler();
        RecordingObject a = new RecordingObject();
        handler.addObject(a);
        handler.removeObject(a);

        handler.tick();

        assertEquals(0, a.tickCount);
    }

    /**
     * Regression test for the index-based LinkedList walk's corruption bug.
     * With object list [mutator, victim, tail] and mutator's tick() removing
     * victim from the handler mid-traversal:
     *
     *  - old code (for (i=0; i<object.size(); i++) object.get(i)) walked by
     *    live index into a list that just shrank underneath it, so victim
     *    (originally at index 1) was never visited -- tail silently slid
     *    into index 1 and got ticked a pass early, and victim's tick() was
     *    skipped entirely that frame.
     *  - the fix must tick every object that was present at the start of
     *    this tick() call exactly once, regardless of removals triggered as
     *    a side effect of another object's tick() in the same pass.
     */
    @Test
    public void mutatingHandlerDuringTickDoesNotSkipObjects() {
        Handler handler = new Handler();
        RecordingObject victim = new RecordingObject();
        RecordingObject tail = new RecordingObject();
        RecordingObject mutator = new RecordingObject();
        mutator.onTick = () -> handler.removeObject(victim);

        handler.addObject(mutator);
        handler.addObject(victim);
        handler.addObject(tail);

        handler.tick();

        assertEquals(1, mutator.tickCount);
        assertEquals(1, victim.tickCount);
        assertEquals(1, tail.tickCount);
    }

    /**
     * Regression test for a review finding on ROADMAP item 2
     * (AchievementToast): render() paints strictly in insertion order, so a
     * later-added full-canvas object (a stand-in here for RoundSummaryPanel/
     * GameOverBanner/RulesView/AchievementsView/StartScreen) always used to
     * paint over an earlier-added one -- which meant a toast added once,
     * up-front, was invisible for the entire (unbounded, user-paced)
     * duration of literally every modal shown afterward. keepOnTop()
     * re-bumps its registered object to the end of the list every time
     * anything else is added, so it renders last across any number of
     * later-added/removed objects, not just the first one.
     */
    @Test
    public void keptOnTopObjectRendersLastEvenAsLaterObjectsComeAndGo() {
        Handler handler = new Handler();
        List<String> renderOrder = new ArrayList<>();
        OrderRecordingObject toast = new OrderRecordingObject("toast", renderOrder);
        OrderRecordingObject roundSummary = new OrderRecordingObject("roundSummary", renderOrder);
        OrderRecordingObject gameOverBanner = new OrderRecordingObject("gameOverBanner", renderOrder);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics g = image.getGraphics();

        // toast registered first, mirroring Game's constructor -- this is
        // exactly the ordering that used to bury it permanently.
        handler.keepOnTop(toast);
        handler.addObject(roundSummary);
        handler.render(g);
        assertEquals("toast must render after (on top of) the round summary panel",
                Arrays.asList("roundSummary", "toast"), renderOrder);

        // a later, unrelated modal (the round summary panel is dismissed,
        // the game-over banner shown instead) must still render underneath
        // the toast -- proving this isn't a one-shot fix that only helps the
        // very next object added.
        renderOrder.clear();
        handler.removeObject(roundSummary);
        handler.addObject(gameOverBanner);
        handler.render(g);
        assertEquals("toast must stay on top of whatever's added later too",
                Arrays.asList("gameOverBanner", "toast"), renderOrder);

        g.dispose();
    }

    /**
     * If the kept-on-top object is itself removed, later addObject() calls
     * must not resurrect it -- removeObject() clears the registration so a
     * stale reference to an object no longer in play doesn't get silently
     * re-added forever.
     */
    @Test
    public void removingTheKeptOnTopObjectStopsItFromBeingReBumped() {
        Handler handler = new Handler();
        RecordingObject toast = new RecordingObject();
        RecordingObject other = new RecordingObject();
        handler.keepOnTop(toast);

        handler.removeObject(toast);
        handler.addObject(other);
        handler.tick();

        assertEquals("removed kept-on-top object must not be ticked again", 0, toast.tickCount);
    }
}
