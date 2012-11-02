import java.util.List;

public class House {
    private final Person owner;

    public House(Person owner) {
        this.owner = owner;
    }

    public Person getOwner() {
        return owner;
    }
}
