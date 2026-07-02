import java.awt.Point;
import java.util.List;
import java.util.Scanner;

public class Human extends Player{
    private final MouseInput mouseInput;

    public Human(String name, MouseInput mouseInput) {
        super(name);
        this.mouseInput = mouseInput;
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
        System.out.println(getHand());
        System.out.println(getName() + ", click the card you want to play.");

        List<Card> legal = legalCards(cardsPlayed, leading, trump, trumpBroken);
        mouseInput.clearClicks();
        while (true) {
            Point click = mouseInput.awaitClick();
            Card card = getHand().cardAt(click.x, click.y);
            if (card == null) {
                continue;
            }
            if (!legal.contains(card)) {
                System.out.println("The " + card + " is not a legal play.");
                continue;
            }
            getHand().playCard(card);
            System.out.println(getName() + " played the " + card);
            return card;
        }
    }

    @Override
    public void nextTrick() {
        System.out.println("Click anywhere to move on to the next trick.");
        mouseInput.clearClicks();
        mouseInput.awaitClick();
    }
}
