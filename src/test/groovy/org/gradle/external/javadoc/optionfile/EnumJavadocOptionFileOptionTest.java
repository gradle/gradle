package org.gradle.external.javadoc.optionfile;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import org.gradle.external.javadoc.JavadocMemberLevel;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class EnumJavadocOptionFileOptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";
    private EnumJavadocOptionFileOption<JavadocMemberLevel> enumOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        enumOption = new EnumJavadocOptionFileOption<JavadocMemberLevel>(optionName);
    }

    @Test
    public void testWriteNullValue() throws IOException {
        context.checking(new Expectations() {{}});

        enumOption.write(writerContextMock);
    }

    @Test
    public void testWriteNoneNullValue() throws IOException {
        enumOption.setValue(JavadocMemberLevel.PUBLIC);

        context.checking(new Expectations() {{
            one(writerContextMock).writeOption("public");
        }});

        enumOption.write(writerContextMock);
    }
}
