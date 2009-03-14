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
public class StringsJavadocOptionFileOptionTest {

    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";
    private final String joinBy = ";";

    private StringsJavadocOptionFileOption stringsOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        stringsOption = new StringsJavadocOptionFileOption(optionName, joinBy);
    }

    @Test
    public void writeNullValue() throws IOException {
        context.checking(new Expectations() {{}});

        stringsOption.write(writerContextMock);
    }

    @Test
    public void writeNoneNullValue() throws IOException {
        final String valueOne = "valueOne";
        final String valueTwo = "valueTwo";

        stringsOption.getValue().add(valueOne);
        stringsOption.getValue().add(valueTwo);

        context.checking(new Expectations() {{
            one(writerContextMock).writeValuesOption(optionName, stringsOption.getValue(), joinBy);
        }});

        stringsOption.write(writerContextMock);
    }
}
