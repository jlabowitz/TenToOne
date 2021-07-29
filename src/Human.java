import java.util.List;
import java.util.Scanner;

public class Human extends Player{

    public Human(String name) {
        super(name);
        id = ID.HUMAN;
    }


    @Override
    public void bet(Suit trump) {
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

        while (!checkIndex(cardIndex, getHand()) || !legalCards(cardsPlayed, leading, trump, trumpBroken).contains(getHand().getCard(cardIndex))) {
            System.out.println(cardIndex + " is not a valid value");
            cardIndex = playerInput.nextInt();
        }


        Card played = getHand().playCard(cardIndex);
        System.out.println(getName() + " played the " + played);
        return played;
    }

    @Override
    public void nextTrick() {
        Scanner playerInput = new Scanner(System.in);
        System.out.println("Hit any key to move on to the next trick.");
        playerInput.next();
    }

    public boolean checkIndex(int cardIndex, Hand hand) {
        return 0 <= cardIndex && cardIndex < hand.getNumCards();
    }
}
