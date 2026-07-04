import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * object is written by the game-logic thread (Round/Trick/Human add and
 * remove cards/hands/UI elements as play progresses) and read every frame by
 * the render/tick thread (Game.run() -> tick()/render()). CopyOnWriteArrayList
 * gives tick()/render() a stable, exception-free snapshot to iterate even
 * while the logic thread concurrently mutates the live list -- appropriate
 * here since this list is small (players plus a handful of in-play objects)
 * and mutated far less often than it's read (60x/sec).
 *
 * ROADMAP item 2 (fixing a review finding): render() paints strictly in
 * insertion order, so a later-added full-canvas object (RoundSummaryPanel,
 * GameOverBanner, RulesView, AchievementsView, StartScreen -- see
 * ModalOverlay's class doc) always paints over an earlier-added one.
 * AchievementToast needs the opposite guarantee -- it must render on top of
 * *whichever* of those screens happens to be showing at the moment, not just
 * the one screen that existed when it was first added -- and several of
 * those screens are added to this Handler from different classes (Game,
 * RulesView.showBlocking, AchievementsView.showBlocking, Human's mid-game
 * Rules hotspot) that have no reference to the toast. keepOnTop() solves
 * this once, here, instead of requiring every call site that shows a new
 * screen to remember to re-bump the toast itself: addObject() re-appends the
 * registered object to the end of the list every time anything else is
 * added, so it stays the most-recently-added (and therefore last-rendered)
 * entry for as long as it's registered.
 */
public class Handler {
    CopyOnWriteArrayList<GameObject> object = new CopyOnWriteArrayList<>();

    /** See keepOnTop()'s doc. Only ever read/written from the game-logic thread (the same thread every addObject/removeObject call already comes from), never from the render/tick thread -- no extra synchronization needed beyond the CopyOnWriteArrayList backing `object` itself. */
    private GameObject alwaysOnTop;

    public void tick() {
        for (GameObject tempObject : object) {
            tempObject.tick();
        }
    }

    public void render(Graphics g) {
        for (GameObject tempObject : object) {
            tempObject.render(g);
        }
    }

    public void addObject(GameObject object) {
        this.object.add(object);
        if (alwaysOnTop != null && object != alwaysOnTop) {
            bumpToEnd(alwaysOnTop);
        }
    }

    public void addAll(List<? extends GameObject> objects) {
        for (GameObject object : objects) {
            addObject(object);
        }
    }

    public void removeObject(GameObject object) {
        this.object.remove(object);
        if (object == alwaysOnTop) {
            alwaysOnTop = null;
        }
    }

    public void removeAll(List<? extends GameObject> objects) {
        for (GameObject object : objects) {
            removeObject(object);
        }
    }

    /**
     * Registers OBJECT to always render (and tick) last, re-bumped to the
     * end of the list every time anything else is added to this Handler --
     * see this class's doc for why. Adds OBJECT itself if it isn't already
     * present. Only one object can be kept on top at a time; a second call
     * replaces the first (unneeded today -- this codebase has exactly one
     * caller, AchievementToast).
     */
    public void keepOnTop(GameObject object) {
        this.alwaysOnTop = object;
        bumpToEnd(object);
    }

    private void bumpToEnd(GameObject object) {
        this.object.remove(object);
        this.object.add(object);
    }
}
