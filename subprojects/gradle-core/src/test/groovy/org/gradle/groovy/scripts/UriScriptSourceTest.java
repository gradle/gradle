/*
 * Copyright 2009 the original author or authors.
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

import static org.gradle.util.Matchers.*;

import org.gradle.integtests.TestFile;
import org.gradle.util.TemporaryFolder;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class UriScriptSourceTest {
    private TestFile testDir;
    private File scriptFile;
    private URI scriptFileUri;
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Before
    public void setUp() throws URISyntaxException {
        testDir = tmpDir.dir("scripts");
        scriptFile = new File(testDir, "build.script");
        scriptFileUri = scriptFile.toURI();
        createJar();
    }

    private URI createJar() throws URISyntaxException {
        TestFile jarFile = tmpDir.getDir().file("test.jar");
        testDir.zipTo(jarFile);
        return new URI(String.format("jar:%s!/build.script", jarFile.toURI()));
    }

    @Test
    public void canConstructSourceFromFile() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFile);
        assertThat(source.getSourceFile(), equalTo(scriptFile));
    }

    @Test
    public void canConstructSourceFromFileURI() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFileUri);
        assertThat(source.getSourceFile(), equalTo(scriptFile));
    }

    @Test
    public void canConstructSourceFromJarURI() throws URISyntaxException {
        UriScriptSource source = new UriScriptSource("<file-type>", createJar());
        assertThat(source.getSourceFile(), nullValue());
    }

    @Test
    public void loadsScriptFileContentWhenFileExists() throws IOException {
        FileUtils.writeStringToFile(scriptFile, "<content>");

        UriScriptSource source = new UriScriptSource("<file-type>", scriptFile);
        assertThat(source.getText(), equalTo("<content>"));
    }

    @Test
    public void hasNoContentWhenScriptFileDoesNotExist() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFile);
        assertThat(source.getText(), equalTo(""));
    }

    @Test
    public void loadsScriptFileContentUsingFileUriWhenFileExists() throws IOException {
        FileUtils.writeStringToFile(scriptFile, "<content>");

        UriScriptSource source = new UriScriptSource("<file-type>", scriptFileUri);
        assertThat(source.getText(), equalTo("<content>"));
    }

    @Test
    public void hasNoContentWhenUsingFileUriAndFileDoesNotExist() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFileUri);
        assertThat(source.getText(), equalTo(""));
    }

    @Test
    public void loadsScriptFileContentUsingJarUriWhenFileExists() throws Exception {
        FileUtils.writeStringToFile(scriptFile, "<content>");

        UriScriptSource source = new UriScriptSource("<file-type>", createJar());
        assertThat(source.getText(), equalTo("<content>"));
    }

    @Test
    public void hasNoContentWhenUsingJarUriAndFileDoesNotExist() throws URISyntaxException {
        UriScriptSource source = new UriScriptSource("<file-type>", createJar());
        assertThat(source.getText(), equalTo(""));
    }

    @Test
    public void hasNoContentWhenUsingHttpUriAndFileDoesNotExist() throws URISyntaxException {
        UriScriptSource source = new UriScriptSource("<file-type>", new URI("http://www.gradle.org/unknown.txt"));
        assertThat(source.getText(), equalTo(""));
    }

    @Test
    public void usesScriptFileNameToBuildDescription() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFile);
        assertThat(source.getDisplayName(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
    }

    @Test
    public void usesScriptFileNameToBuildDescriptionWhenUsingFileUri() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFileUri);
        assertThat(source.getDisplayName(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
    }

    @Test
    public void usesScriptFileNameToBuildDescriptionWhenUsingHttpUri() throws URISyntaxException {
        UriScriptSource source = new UriScriptSource("<file-type>", new URI("http://www.gradle.org/unknown.txt"));
        assertThat(source.getDisplayName(), equalTo(String.format("<file-type> 'http://www.gradle.org/unknown.txt'")));
    }

    @Test
    public void encodesScriptFileBaseNameToClassName() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFile);
        assertThat(source.getClassName(), matchesRegexp("build_script_[0-9a-z]+"));

        source = new UriScriptSource("<file-type>", new File(testDir, "name with-some + invalid.chars"));
        assertThat(source.getClassName(), matchesRegexp("name_with_some___invalid_chars_[0-9a-z]+"));

        source = new UriScriptSource("<file-type>", new File(testDir, "123"));
        assertThat(source.getClassName(), matchesRegexp("_123_[0-9a-z]+"));

        source = new UriScriptSource("<file-type>", new File(testDir, "-"));
        assertThat(source.getClassName(), matchesRegexp("__[0-9a-z]+"));
    }

    @Test
    public void filesWithSameNameAndDifferentPathHaveDifferentClassName() {
        ScriptSource source1 = new UriScriptSource("<file-type>", new File(testDir, "build.gradle"));
        ScriptSource source2 = new UriScriptSource("<file-type>", new File(testDir, "subdir/build.gradle"));
        assertThat(source1.getClassName(), not(equalTo(source2.getClassName())));

        ScriptSource source3 = new UriScriptSource("<file-type>", new File(testDir, "build.gradle"));
        assertThat(source1.getClassName(), equalTo(source3.getClassName()));
    }
    
    @Test
    public void filesWithSameNameAndUriHaveDifferentClassName() throws URISyntaxException {
        ScriptSource source1 = new UriScriptSource("<file-type>", new File(testDir, "build.gradle"));
        ScriptSource source2 = new UriScriptSource("<file-type>", new URI("http://localhost/build.gradle"));
        assertThat(source1.getClassName(), not(equalTo(source2.getClassName())));

        ScriptSource source3 = new UriScriptSource("<file-type>", new File(testDir, "build.gradle"));
        assertThat(source1.getClassName(), equalTo(source3.getClassName()));
    }
}
