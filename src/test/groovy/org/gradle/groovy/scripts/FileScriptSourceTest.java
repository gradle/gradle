package org.gradle.groovy.scripts;

import org.junit.Test;
import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import org.junit.runner.RunWith;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.jmock.lib.legacy.ClassImposteriser;
import org.gradle.util.HelperUtil;
import org.gradle.api.internal.project.ImportsReader;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class FileScriptSourceTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private ImportsReader importsReader;
    private File testDir;
    private File scriptFile;
    private FileScriptSource source;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        importsReader = context.mock(ImportsReader.class);
        testDir = HelperUtil.makeNewTestDir();
        scriptFile = new File(testDir, "build.script");
        source = new FileScriptSource("<file-type>", scriptFile, importsReader);
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }
    
    @Test
    public void loadsScriptFileContentAndPrependsImports() throws IOException {
        FileUtils.writeStringToFile(scriptFile, "<content>");

        context.checking(new Expectations(){{
            one(importsReader).getImports(testDir);
            will(returnValue("<imports>"));
        }});

        assertThat(source.getText(), equalTo("<imports>\n<content>"));
    }

    @Test
    public void hasNoContentWhenScriptFileDoesNotExist() {
        assertThat(source.getText(), nullValue());
    }

    @Test
    public void usesScriptFileNameToBuildDescription() {
        assertThat(source.getDescription(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
    }

    @Test
    public void encodesScriptFileBaseNameToClassName() {
        assertThat(source.getClassName(), equalTo("build_script"));

        source = new FileScriptSource("<file-type>", new File(testDir, "name with-some^reserved\nchars"), importsReader);
        assertThat(source.getClassName(), equalTo("name_with_some_reserved_chars"));

        source = new FileScriptSource("<file-type>", new File(testDir, "123"), importsReader);
        assertThat(source.getClassName(), equalTo("_123"));
    }
}
