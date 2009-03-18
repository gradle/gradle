package org.gradle.external.javadoc.optionfile;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import org.gradle.util.WrapUtil;

import java.io.BufferedWriter;
import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class JavadocOptionFileWriterContextTest {

    private final JUnit4Mockery context = new JUnit4Mockery();
    private BufferedWriter bufferedWriterMock;

    private JavadocOptionFileWriterContext writerContext;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        bufferedWriterMock = context.mock(BufferedWriter.class);

        writerContext = new JavadocOptionFileWriterContext(bufferedWriterMock);
    }

    @Test
    public void testWrite() throws IOException {
        final String writeValue = "dummy";

        context.checking(new Expectations() {{
            one(bufferedWriterMock).write(writeValue);
        }});

        writerContext.write(writeValue);
    }

    @Test
    public void testNewLine() throws IOException {
        context.checking(new Expectations() {{
            one(bufferedWriterMock).newLine();
        }});

        writerContext.newLine();
    }

    @Test
    public void quotesAndEscapesOptionValue() throws IOException {
        context.checking(new Expectations(){{
            one(bufferedWriterMock).write("-");
            one(bufferedWriterMock).write("key");
            one(bufferedWriterMock).write(" ");
            one(bufferedWriterMock).write("'");
            one(bufferedWriterMock).write("1\\\\2\\\\");
            one(bufferedWriterMock).write("'");
            one(bufferedWriterMock).newLine();
        }});

        writerContext.writeValueOption("key", "1\\2\\");
    }

    @Test
    public void quotesAndEscapesOptionValues() throws IOException {
        context.checking(new Expectations(){{
            one(bufferedWriterMock).write("-");
            one(bufferedWriterMock).write("key");
            one(bufferedWriterMock).write(" ");
            one(bufferedWriterMock).write("'");
            one(bufferedWriterMock).write("a\\\\b:c");
            one(bufferedWriterMock).write("'");
            one(bufferedWriterMock).newLine();
        }});

        writerContext.writeValuesOption("key", WrapUtil.toList("a\\b", "c"), ":");
    }
}
