package org.gradle.external.javadoc.optionfile;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.File;

/**
 * @author Tom Eyckmans
 */
public class FileJavadocOptionFileOptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";
    private FileJavadocOptionFileOption fileOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        fileOption = new FileJavadocOptionFileOption(optionName);
    }

    @Test
    public void testWriteNullValue() throws IOException {
        context.checking(new Expectations() {{}});

        fileOption.write(writerContextMock);
    }

    @Test
    public void testWriteNoneNullValue() throws IOException {
        final File testValue = new File("dummyTestFileValue");

        context.checking(new Expectations() {{
            one(writerContextMock).writeValueOption(optionName, testValue.getAbsolutePath());
        }});
        
        fileOption.write(writerContextMock);
    }
}
