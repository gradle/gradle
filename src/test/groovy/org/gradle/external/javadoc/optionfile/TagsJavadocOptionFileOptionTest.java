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
public class TagsJavadocOptionFileOptionTest {

    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";
    private final String joinBy = ";";
    
    private TagsJavadocOptionFileOption tagsOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        tagsOption = new TagsJavadocOptionFileOption(optionName);
    }

    @Test
    public void writeNullValue() throws IOException {
        context.checking(new Expectations() {{}});

        tagsOption.write(writerContextMock);
    }

    @Test
    public void writeNoneNullValue() throws IOException {
        tagsOption.getValue().add("testTag");
        tagsOption.getValue().add("testTaglet:testTagletOne");
        tagsOption.getValue().add("testTaglet\"testTagletTwo");

        context.checking(new Expectations() {{
            one(writerContextMock).writeValueOption("taglet", "testTag");
            one(writerContextMock).writeValueOption("tag", "testTaglet:testTagletOne");
            one(writerContextMock).writeValueOption("tag", "testTaglet\"testTagletTwo");
        }});

        tagsOption.write(writerContextMock);
    }

}
