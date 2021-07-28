import java.util.ArrayList;
import java.util.List;

public class Players {

    private List<Player> players;

    public Players() {
        players = new ArrayList<>();
    }

    public void addHuman(String name) {
        players.add(new Human(name));
    }

    public void addAI() {
        players.add(new AI("Player " + (players.size() + 1)));
    }
}
