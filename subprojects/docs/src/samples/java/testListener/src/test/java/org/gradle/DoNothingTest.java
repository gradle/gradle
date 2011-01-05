import org.junit.Test;
import static org.junit.Assert.*;

public class DoNothingTest {
    @Test
    public void doNothing() {
    }

    @Test
    public void doNothingButFail() {
       fail("I always fail");
    }

    @Test
    public void doNothingButError() {
       throw new RuntimeException("I always throw exceptions");
    }
}
