package org.gradle.api.changedetection.digest;

import org.junit.Before;
import org.junit.Test;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.HelperUtil;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;

/**
 * @author Tom Eyckmans
 */
public class MetaDigesterUtilStrategyTest {

    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final long okDirectorySize = 0;

    private MessageDigest digesterMock;

    private MetaDigesterUtilStrategy strategy;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        digesterMock = context.mock(MessageDigest.class);

        strategy = new MetaDigesterUtilStrategy();
    }

    @Test
    public void digestFile() throws IOException {
        final File tempFile = File.createTempFile("gradle", "test");
        try {
            context.checking(new Expectations(){{
                one(digesterMock).update(tempFile.getAbsolutePath().getBytes());
                one(digesterMock).update(((Long)tempFile.lastModified()).byteValue());
                one(digesterMock).update(((Long)tempFile.length()).byteValue());
            }});

            strategy.digestFile(digesterMock, tempFile);

        }
        finally {
            assertTrue(tempFile.delete());
        }
    }

    @Test
    public void digestDirectory() throws IOException {
        final File tempDir = HelperUtil.makeNewTestDir();
        try {
            context.checking(new Expectations(){{
                one(digesterMock).update(tempDir.getAbsolutePath().getBytes());
                one(digesterMock).update(((Long)tempDir.lastModified()).byteValue());
                one(digesterMock).update(((Long)okDirectorySize).byteValue());
            }});

            strategy.digestDirectory(digesterMock, tempDir, okDirectorySize);
        }
        finally {
            assertTrue(tempDir.delete());
        }
    }

}
