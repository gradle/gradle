package org.gradle.groovy.scripts;

import org.junit.Test;
import org.junit.Before;
import org.junit.Assert;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.util.HelperUtil;

import java.io.File;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class StringScriptSourceTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private ImportsReader importsReader;
    private File rootDir = HelperUtil.getTestDir();
    private StringScriptSource source;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        importsReader = context.mock(ImportsReader.class);
        source = new StringScriptSource("<description>", "<content>", rootDir, importsReader);
    }

    @Test
    public void prependsScriptContentWithImports() {
        context.checking(new Expectations(){{
            one(importsReader).getImports(rootDir);
            will(returnValue("<imports>"));
        }});

        assertThat(source.getText(), equalTo("<imports>\n<content>"));
    }

    @Test
    public void hasNoContentWhenScriptContentIsEmpty() {
        StringScriptSource source = new StringScriptSource("<description>", "", rootDir, importsReader);

        assertThat(source.getText(), nullValue());
    }

    @Test
    public void hasNoSourceFile() {
        assertThat(source.getSourceFile(), nullValue());
    }

    @Test
    public void hasHardcodedClassName() {
        assertThat(source.getClassName(), equalTo("script"));
    }

    @Test
    public void usesProvidedDescription() {
        assertThat(source.getDescription(), equalTo("<description>"));
    }
}
