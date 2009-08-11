/*
 * Copyright 2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.groovy.scripts;

import org.apache.commons.io.FileUtils;
import org.gradle.util.HelperUtil;
import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class FileScriptSourceTest {
    private File testDir;
    private File scriptFile;
    private FileScriptSource source;

    @Before
    public void setUp() {
        testDir = HelperUtil.makeNewTestDir();
        scriptFile = new File(testDir, "build.script");
        source = new FileScriptSource("<file-type>", scriptFile);
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }
    
    @Test
    public void loadsScriptFileContentWhenFileExists() throws IOException {
        FileUtils.writeStringToFile(scriptFile, "<content>");
        assertThat(source.getText(), equalTo("<content>"));
    }

    @Test
    public void hasNoContentWhenScriptFileDoesNotExist() {
        assertThat(source.getText(), equalTo(""));
    }

    @Test
    public void usesScriptFileNameToBuildDescription() {
        assertThat(source.getDisplayName(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
    }

    @Test
    public void encodesScriptFileBaseNameToClassName() {
        assertThat(source.getClassName(), matchesRegexp("build_script_[0-9a-z]+"));

        source = new FileScriptSource("<file-type>", new File(testDir, "name with-some + invalid.chars"));
        assertThat(source.getClassName(), matchesRegexp("name_with_some___invalid_chars_[0-9a-z]+"));

        source = new FileScriptSource("<file-type>", new File(testDir, "123"));
        assertThat(source.getClassName(), matchesRegexp("_123_[0-9a-z]+"));

        source = new FileScriptSource("<file-type>", new File(testDir, "-"));
        assertThat(source.getClassName(), matchesRegexp("__[0-9a-z]+"));
    }
    
    @Test
    public void filesWithSameNameAndDifferentPathHaveDifferentClassName() {
        ScriptSource source1 = new FileScriptSource("<file-type>", new File(testDir, "build.gradle"));
        ScriptSource source2 = new FileScriptSource("<file-type>", new File(testDir, "subdir/build.gradle"));
        assertThat(source1.getClassName(), not(equalTo(source2.getClassName())));

        ScriptSource source3 = new FileScriptSource("<file-type>", new File(testDir, "build.gradle"));
        assertThat(source1.getClassName(), equalTo(source3.getClassName()));
    }
}
