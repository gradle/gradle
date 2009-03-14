package org.gradle.external.javadoc.optionfile;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class StringJavadocOptionFileOptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";

    private StringJavadocOptionFileOption stringOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        stringOption = new StringJavadocOptionFileOption(optionName);
    }

    @Test
    public void testWriteNullValue() throws IOException {
        context.checking(new Expectations() {{}});

        stringOption.write(writerContextMock);
    }

    @Test
    public void testWriteNoneNullValue() throws IOException {
        final String testValue = "testValue";

        stringOption.setValue(testValue);

        context.checking(new Expectations() {{
            one(writerContextMock).writeValueOption(optionName, testValue);
        }});

        stringOption.write(writerContextMock);
    }
}
