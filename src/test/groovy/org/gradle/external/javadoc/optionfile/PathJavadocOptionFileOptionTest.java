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
public class PathJavadocOptionFileOptionTest {

    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";
    private final String joinBy = ";";

    private PathJavadocOptionFileOption pathOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        pathOption = new PathJavadocOptionFileOption(optionName, joinBy);
    }

    @Test
    public void testWriteNullValue() throws IOException {
        context.checking(new Expectations() {{}});

        pathOption.write(writerContextMock);
    }

    @Test
    public void testWriteNoneNullValue() throws IOException {
        final File fileOne = new File("fileOne");
        final File fileTwo = new File("fileTwo");

        pathOption.getValue().add(fileOne);
        pathOption.getValue().add(fileTwo);

        context.checking(new Expectations() {{
            one(writerContextMock).writePathOption(optionName, pathOption.getValue(), joinBy);
        }});

        pathOption.write(writerContextMock);
    }
}
