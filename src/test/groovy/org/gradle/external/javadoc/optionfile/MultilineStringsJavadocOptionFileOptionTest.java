package org.gradle.external.javadoc.optionfile;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import org.gradle.external.javadoc.JavadocOfflineLink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

/**
 * @author Melanie Pfautz
 */
public class MultilineStringsJavadocOptionFileOptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";

    private MultilineStringsJavadocOptionFileOption linksOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);
        linksOption = new MultilineStringsJavadocOptionFileOption(optionName);
    }

    @Test
    public void writeNullValue() throws IOException {
        context.checking(new Expectations() {{}});
       
        linksOption.writeCollectionValue(writerContextMock);
    }

    @Test
    public void writeNonNullValue() throws IOException {
        final String extDocUrl = "extDocUrl";

        linksOption.getValue().add(extDocUrl);
        context.checking(new Expectations() {{
            final List<String> tempList = new ArrayList<String>();
            tempList.add(extDocUrl);
            one(writerContextMock).writeMultilineValuesOption(optionName, tempList);
        }});

        linksOption.writeCollectionValue(writerContextMock);
    }

    @Test
    public void writeMultipleValues() throws IOException {
        final List<String> tempList = new ArrayList<String>();
        final String docUrl1 = "docUrl1";
        final String docUrl2 = "docUrl2";

        linksOption.getValue().add(docUrl1);
        linksOption.getValue().add(docUrl2);
        context.checking(new Expectations() {{
            tempList.add(docUrl1);
            tempList.add(docUrl2);
            one(writerContextMock).writeMultilineValuesOption(optionName, tempList);
        }});
       
        linksOption.writeCollectionValue(writerContextMock);
    }
}
