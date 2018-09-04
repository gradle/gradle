import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DefaultFileReader implements FileReader {
    @Override
    public String readContent(Path path) throws IOException {
        return new String(Files.readAllBytes(path));
    }
}
