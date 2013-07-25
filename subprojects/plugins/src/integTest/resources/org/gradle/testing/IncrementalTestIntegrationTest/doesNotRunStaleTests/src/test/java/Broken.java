import org.junit.Test;

public class Broken {
    @Test
    public void broken() {
        throw new RuntimeException("broken");
    }
}