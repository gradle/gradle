import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public abstract class Base {
    @Test public void alwaysFails() {
        Assertions.fail("Oops, base failed again");
    }
}
