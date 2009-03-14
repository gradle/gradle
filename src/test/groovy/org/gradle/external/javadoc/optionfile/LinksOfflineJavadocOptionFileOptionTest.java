package org.gradle.external.javadoc.optionfile;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.junit.Before;
import org.junit.Test;
import org.gradle.external.javadoc.JavadocOfflineLink;

import java.io.IOException;

/**
 * @author Tom Eyckmans
 */
public class LinksOfflineJavadocOptionFileOptionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private JavadocOptionFileWriterContext writerContextMock;
    private final String optionName = "testOption";

    private LinksOfflineJavadocOptionFileOption linksOfflineOption;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        writerContextMock = context.mock(JavadocOptionFileWriterContext.class);

        linksOfflineOption = new LinksOfflineJavadocOptionFileOption(optionName);
    }

    @Test
    public void writeNullValue() throws IOException {
        context.checking(new Expectations() {{}});

        linksOfflineOption.write(writerContextMock);
    }

    @Test
    public void writeNoneNullValue() throws IOException {
        final String extDocUrl = "extDocUrl";
        final String packageListLoc = "packageListLoc";

        linksOfflineOption.getValue().add(new JavadocOfflineLink(extDocUrl, packageListLoc));

        context.checking(new Expectations() {{
            one(writerContextMock).writeValueOption(optionName, extDocUrl + " " + packageListLoc);
        }});

        linksOfflineOption.write(writerContextMock);
    }
}
