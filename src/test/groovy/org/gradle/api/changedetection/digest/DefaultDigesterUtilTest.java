package org.gradle.api.changedetection.digest;

import org.gradle.util.JUnit4GroovyMockery;
import org.junit.Before;
import org.junit.Test;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;

import java.security.MessageDigest;
import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class DefaultDigesterUtilTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final String fileMockAbsolutePath = "dummPath";
    private final long okDirectorySize = 0;

    private DigesterUtilStrategy strategyMock;
    private MessageDigest digesterMock;
    private File fileMock;

    private DefaultDigesterUtil digesterUtil;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);

        strategyMock = context.mock(DigesterUtilStrategy.class);
        digesterMock = context.mock(MessageDigest.class);
        fileMock = context.mock(File.class);

        digesterUtil = new DefaultDigesterUtil(strategyMock);
    }

    @Test( expected = IllegalArgumentException.class )
    public void testNullStrategyCreation() {
        digesterUtil = new DefaultDigesterUtil(null);
    }

    @Test( expected = IllegalArgumentException.class )
    public void digestDirectoryWithNullDigester() {
        digesterUtil.digestDirectory(null, fileMock, okDirectorySize);
    }

    @Test( expected = IllegalArgumentException.class )
    public void digestDirectoryWithNullFile() {
        digesterUtil.digestDirectory(digesterMock, null, okDirectorySize);
    }

    @Test( expected = IllegalArgumentException.class )
    public void digestDirectoryWithNotExistingDirectory() {
        context.checking(new Expectations(){{
            one(fileMock).exists();will(returnValue(false));
            one(fileMock).getAbsolutePath();will(returnValue(fileMockAbsolutePath));
        }});

        digesterUtil.digestDirectory(digesterMock, fileMock, okDirectorySize);
    }

    @Test( expected = IllegalArgumentException.class )
    public void digestDirectoryForFile() {
        context.checking(new Expectations(){{
            one(fileMock).exists();will(returnValue(true));
            one(fileMock).isDirectory();will(returnValue(false));
            one(fileMock).getAbsolutePath();will(returnValue(fileMockAbsolutePath));
        }});

        digesterUtil.digestDirectory(digesterMock, fileMock, okDirectorySize);
    }

    @Test
    public void digestDirectory() {
        context.checking(new Expectations(){{
            one(fileMock).exists();will(returnValue(true));
            one(fileMock).isDirectory();will(returnValue(true));
            one(strategyMock).digestDirectory(digesterMock, fileMock, okDirectorySize);
        }});

        digesterUtil.digestDirectory(digesterMock, fileMock, okDirectorySize);
    }

    @Test( expected = IllegalArgumentException.class )
    public void digestFileWithNullDigester() {
        digesterUtil.digestFile(null, fileMock);
    }

    @Test( expected = IllegalArgumentException.class )
    public void digestFileWithNullFile() {
        digesterUtil.digestFile(digesterMock, null);
    }

    @Test( expected = IllegalArgumentException.class )
    public void digestFileWithNotExistingFile() {
        context.checking(new Expectations(){{
            one(fileMock).exists();will(returnValue(false));
            one(fileMock).getAbsolutePath();will(returnValue(fileMockAbsolutePath));
        }});

        digesterUtil.digestFile(digesterMock, fileMock);
    }

    @Test( expected = IllegalArgumentException.class )
    public void digestFileForDirectory() {
        context.checking(new Expectations(){{
            one(fileMock).exists();will(returnValue(true));
            one(fileMock).isFile();will(returnValue(false));
            one(fileMock).getAbsolutePath();will(returnValue(fileMockAbsolutePath));
        }});

        digesterUtil.digestFile(digesterMock, fileMock);
    }

    @Test
    public void digestFile() {
        context.checking(new Expectations(){{
            one(fileMock).exists();will(returnValue(true));
            one(fileMock).isFile();will(returnValue(true));
            one(strategyMock).digestFile(digesterMock, fileMock);
        }});

        digesterUtil.digestFile(digesterMock, fileMock);
    }
}
