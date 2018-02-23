import org.junit.Test;
import com.sun.jna.platform.win32.Shell32;

public class OkTest {
    @Test
    public void ok() {
        assert Shell32.INSTANCE != null;
    }
}