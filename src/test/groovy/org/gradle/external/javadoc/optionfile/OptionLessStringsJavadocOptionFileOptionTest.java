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
public class OptionLessStringsJavadocOptionFileOptionTest {

    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";

    private OptionLessStringsJavadocOptionFileOption optionLessStringsOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        optionLessStringsOption = new OptionLessStringsJavadocOptionFileOption();
    }

    @Test
    public void writeNullValue() throws IOException {
        final String firstValue = "firstValue";
        final String secondValue = "secondValue";

        context.checking(new Expectations() {{
            one(writerContextMock).write(firstValue);
            one(writerContextMock).newLine();
            one(writerContextMock).write(secondValue);
            one(writerContextMock).newLine();
        }});

        optionLessStringsOption.write(writerContextMock);
    }

}
