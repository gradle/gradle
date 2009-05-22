package org.gradle.api.changedetection.state;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.io.IoFactory;
import org.gradle.api.io.DefaultIoFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.BufferedReader;

/**
 * @author Tom Eyckmans
 */
public class StateFileWriterTest {
    private final String okKey = "okKey";
    private final String okDigest = "okDigest";

    private IoFactory ioFactoryMock;

    private File testStateFile;
    private StateFileWriter stateFileWriter;

    @Before
    public void setUp() throws IOException {
        testStateFile = File.createTempFile("gradle", "test");

        ioFactoryMock = new DefaultIoFactory();

        stateFileWriter = new StateFileWriter(ioFactoryMock, testStateFile);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void createWithNullFile() {
        stateFileWriter = new StateFileWriter(ioFactoryMock, null);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void createWithNullIoFactory() {
        stateFileWriter = new StateFileWriter(null, testStateFile);
    }

    @Test ( expected = IllegalArgumentException.class )
    public void addDigestNullKey() throws IOException {
        stateFileWriter.addDigest(null, okDigest);
    }

    @Test
    public void addDigestEmptyKey() throws IOException {
        stateFileWriter.addDigest("", okDigest);

        stateFileWriter.lastFileDigestAdded();

        assertTrue(stateFileContentOk("", okDigest));
    }

    @Test ( expected = IllegalArgumentException.class )
    public void addDigestNullDigest() throws IOException {
        stateFileWriter.addDigest(okKey, null);

    }

    @Test ( expected = IllegalArgumentException.class )
    public void addDigestEmptyDigest() throws IOException {
        stateFileWriter.addDigest(okKey, "");
    }

    @Test
    public void addDigest() throws IOException {
        assertEquals(testStateFile, stateFileWriter.getStateFile());

        stateFileWriter.addDigest(okKey, okDigest);

        stateFileWriter.lastFileDigestAdded();

        assertTrue(stateFileContentOk(okKey, okDigest));
    }

    @Test
    public void addMultipleDigests() throws IOException {
        stateFileWriter.addDigest(okKey, okDigest);
        stateFileWriter.addDigest(okKey, okDigest);

        stateFileWriter.lastFileDigestAdded();

        assertTrue(stateFileContentOk(okKey, okDigest,okKey, okDigest));
    }

    @After
    public void tearDown() throws IOException {
        if ( stateFileWriter != null ) {
            stateFileWriter.close();
            FileUtils.forceDelete(testStateFile);
        }
    }

    private boolean stateFileContentOk(String...lines) throws IOException {
        String testStateFileContent = FileUtils.readFileToString(testStateFile);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new StringReader(testStateFileContent));
            String line = null;
            int lineIndex = 0;
            while ( (line = reader.readLine()) != null ) {
                if ( !line.equals(lines[lineIndex++]) )
                    return false;
            }
            return true;
        }
        finally {
            IOUtils.closeQuietly(reader);
        }
    }
}
