import java.awt.*;

public abstract class GameObject {

    protected int x, y;
    protected ID id;

    public GameObject() {
        this.x = 0;
        this.y = 50;
        id = null;
    }

    public GameObject(int x, int y, ID id) {
        this.x = x;
        this.y = y;
        this.id = id;
    }

    public abstract void tick();
    public abstract void render(Graphics g);

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public ID getID() {
        return id;
    }

    public void setID(ID id) {
        this.id = id;
    }
}
