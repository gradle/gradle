package org.gradle.external.javadoc.optionfile;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public class GroupsJavadocOptionFileOptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";

    private GroupsJavadocOptionFileOption groupsFile;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        groupsFile = new GroupsJavadocOptionFileOption(optionName);
    }

    @Test
    public void testWriteNullValue() throws IOException {
        context.checking(new Expectations() {{}});

        groupsFile.write(writerContextMock);
    }

    @Test
    public void testWriteNotNullValue() throws IOException {
        final String groupName = "testGroup";
        final List<String> groupElements = Arrays.asList("java.lang", "java.util*");

        groupsFile.getValue().put(groupName, groupElements);

        context.checking(new Expectations() {{
            one(writerContextMock).writeOptionHeader(optionName);
            one(writerContextMock).write("\"testGroup\"");
            one(writerContextMock).write(" ");
            one(writerContextMock).write("\"java.lang:java.util*\"");
            one(writerContextMock).newLine();
        }});

        groupsFile.write(writerContextMock);
    }

}
