import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class ApplicationTest {
    private final Application application = new Application();

    @Test
    public void testReadBuildInfo() throws IOException {
        Properties properties = application.readBuildInfo();
        assertEquals(properties.getProperty("version").toString(), "1.0");
    }
}
