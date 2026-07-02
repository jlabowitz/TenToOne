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
 */
public class Handler {
    CopyOnWriteArrayList<GameObject> object = new CopyOnWriteArrayList<>();

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
    }

    public void addAll(List<? extends GameObject> objects) {
        for (GameObject object : objects) {
            addObject(object);
        }
    }

    public void removeObject(GameObject object) {
        this.object.remove(object);
    }

    public void removeAll(List<? extends GameObject> objects) {
        for (GameObject object : objects) {
            removeObject(object);
        }
    }
}
