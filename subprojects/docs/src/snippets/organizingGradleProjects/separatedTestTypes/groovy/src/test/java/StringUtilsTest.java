import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class StringUtilsTest {
    @Test
    public void testCapitalize() {
        assertEquals(StringUtils.capitalize("test"), "Test");
    }
}
