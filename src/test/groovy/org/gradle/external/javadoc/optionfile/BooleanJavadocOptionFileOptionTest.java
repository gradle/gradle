package org.gradle.external.javadoc.optionfile;

import org.junit.Before;
import org.junit.Test;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class BooleanJavadocOptionFileOptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";
    private BooleanJavadocOptionFileOption booleanOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        booleanOption = new BooleanJavadocOptionFileOption(optionName);
    }

    @Test
    public void testWriteNullValue() throws IOException {
        context.checking(new Expectations() {{}});

        booleanOption.write(writerContextMock);
    }

    @Test
    public void testWriteFalseValue() throws IOException {
        booleanOption.setValue(false);

        context.checking(new Expectations() {{}});

        booleanOption.write(writerContextMock);
    }

    @Test
    public void testWriteTrueValue() throws IOException {
        booleanOption.setValue(true);

        context.checking(new Expectations() {{
            one(writerContextMock).writeOption(optionName);
        }});

        booleanOption.write(writerContextMock);
    }
}
