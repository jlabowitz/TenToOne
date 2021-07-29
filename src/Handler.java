import java.awt.*;
import java.util.List;
import java.util.LinkedList;

public class Handler {
    LinkedList<GameObject> object = new LinkedList<>();

    public void tick() {
        for (int i = 0; i < object.size(); i++) {
            GameObject tempObject = object.get(i);
            tempObject.tick();
        }
    }

    public void render(Graphics g) {
        for (int i = 0; i < object.size(); i++) {
            GameObject tempObject = object.get(i);
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
