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
        System.out.println(getName() + ", what do you want to bet?");
        setBet(playerInput.nextInt());
    }

    @Override
    public Card playCard(List<Card> cardsPlayed, Suit leading, Suit trump, boolean trumpBroken) {
        Scanner playerInput = new Scanner(System.in);
        System.out.println(getHand());
        System.out.println(getName() + ", which card do you want to play?");

        int cardIndex = playerInput.nextInt();

        while (!checkIndex(cardIndex, getHand())) {
            System.out.println(cardIndex + " is not a valid value");
            cardIndex = playerInput.nextInt();
        }

        Card played = getHand().getCard(cardIndex);
        System.out.println(getName() + " played the " + played);
        return played;
    }

    public boolean checkIndex(int cardIndex, Hand hand) {
        return 0 <= cardIndex && cardIndex < hand.getNumCards();
    }
}
