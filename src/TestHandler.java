import org.junit.Test;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

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
}
