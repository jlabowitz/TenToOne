import java.util.List;
import java.util.Scanner;

public class Human extends Player{

    public Human(String name) {
        super(name);
    }


    @Override
    public void bet() {
        Scanner playerInput = new Scanner(System.in);
        System.out.println(getHand());
        System.out.println("What do you want to bet?");
        setBet(playerInput.nextInt());
    }

    @Override
    public Card playCard(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        Scanner playerInput = new Scanner(System.in);
        System.out.println(getHand());
        System.out.println("Which card do you want to play?");
        int cardIndex = playerInput.nextInt();
        return getHand().getCard(cardIndex);
    }
}
