import java.util.List;
import java.util.Scanner;

public class Human extends Player{

    public Human(String name) {
        super(name);
    }


    @Override
    public void bet() {
        Scanner playerInput = new Scanner(System.in);
        setBet(playerInput.nextInt());
    }

    @Override
    public Card playCard(List<Card> cardsPlayed) {
        return null;
    }
}
