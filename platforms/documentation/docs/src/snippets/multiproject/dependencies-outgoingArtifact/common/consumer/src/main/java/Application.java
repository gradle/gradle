import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Application {
    public Properties readBuildInfo() throws IOException {
        Properties prop = new Properties();
        InputStream inputStream = null;

        try {
            inputStream = Application.class.getClassLoader().getResourceAsStream("build-info.properties");
            prop.load(inputStream);
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return prop;
    }
}
