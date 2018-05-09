import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class DefaultFileReaderIntegrationTest {
    private FileReader fileReader = new DefaultFileReader();
    private File testFile;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        testFile = folder.newFile("myfile.txt");
        writeFile(testFile, "Hello World!");
    }

    @Test
    public void testReadContent() throws IOException {
        assertEquals(fileReader.readContent(testFile.toPath()), "Hello World!");
    }

    private void writeFile(File destination, String content) throws IOException {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(destination));
            output.write(content);
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }
}
