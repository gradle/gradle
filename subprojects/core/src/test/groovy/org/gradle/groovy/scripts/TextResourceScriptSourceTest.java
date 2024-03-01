/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.internal.resource.DefaultTextFileResourceLoader;
import org.gradle.internal.resource.EmptyFileTextResource;
import org.gradle.internal.resource.StringTextResource;
import org.gradle.internal.resource.UriTextResource;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

import static org.gradle.util.Matchers.matchesRegexp;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TextResourceScriptSourceTest {
    private TestFile testDir;
    private File scriptFile;
    private URI scriptFileUri;
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass());
    private final FileResolver resolver = TestFiles.resolver(tmpDir.getTestDirectory());
    private final DefaultTextFileResourceLoader resourceLoader = new DefaultTextFileResourceLoader(resolver);

    @Before
    public void setUp() throws URISyntaxException {
        testDir = tmpDir.createDir("scripts");
        scriptFile = new File(testDir, "build.script");
        scriptFileUri = scriptFile.toURI();
        createJar();
    }

    private URI createJar() throws URISyntaxException {
        TestFile jarFile = tmpDir.getTestDirectory().file("test.jar");
        testDir.file("ignoreme").write("content");
        testDir.zipTo(jarFile);
        return new URI(String.format("jar:%s!/build.script", jarFile.toURI()));
    }

    @Test
    public void canConstructSourceFromFile() throws IOException {
        scriptFile.createNewFile();
        ScriptSource source = forFile(scriptFile);
        assertThat(source.getResource(), instanceOf(UriTextResource.class));
        assertThat(source.getResource().getFile(), equalTo(this.scriptFile));
        assertThat(source.getResource().getLocation().getFile(), equalTo(this.scriptFile));
        assertThat(source.getResource().getLocation().getURI(), equalTo(scriptFileUri));
    }

    @Test
    public void convenienceMethodScriptForFileThatHasContent() {
        new TestFile(scriptFile).write("content");
        ScriptSource source = forFile(scriptFile);
        assertThat(source, instanceOf(TextResourceScriptSource.class));
        assertThat(source.getResource().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getCharset(), equalTo(StandardCharsets.UTF_8));
        assertThat(source.getResource().getLocation().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getLocation().getURI(), equalTo(scriptFileUri));
        assertThat(source.getResource().getText(), equalTo("content"));
        assertFalse(source.getResource().isContentCached());
        assertFalse(source.getResource().getHasEmptyContent());
        assertTrue(source.getResource().getExists());
    }

    @Test
    public void convenienceMethodReplacesFileThatDoesNotExistWithEmptyScript() {
        ScriptSource source = forFile(scriptFile);
        assertThat(source.getResource(), instanceOf(EmptyFileTextResource.class));
        // resource file is null, even though resource location file is not
        assertNull(source.getResource().getFile());
        assertNull(source.getResource().getCharset());
        assertThat(source.getResource().getLocation().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getLocation().getURI(), equalTo(scriptFileUri));
        assertThat(source.getResource().getText(), equalTo(""));
        assertTrue(source.getResource().isContentCached());
        assertTrue(source.getResource().getHasEmptyContent());
        assertTrue(source.getResource().getExists()); // exists == has content
    }

    @Test
    public void canConstructSourceFromFileURI() throws IOException {
        scriptFile.createNewFile();
        ScriptSource source = forUri(scriptFileUri);
        assertThat(source.getResource(), instanceOf(UriTextResource.class));
        assertThat(source.getResource().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getCharset(), equalTo(StandardCharsets.UTF_8));
        assertThat(source.getResource().getLocation().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getLocation().getURI(), equalTo(this.scriptFileUri));
    }

    @Test
    public void canConstructSourceFromJarURI() throws URISyntaxException {
        URI uri = createJar();
        ScriptSource source = forUri(uri);
        assertThat(source.getResource(), instanceOf(UriTextResource.class));
        assertNull(source.getResource().getFile());
        assertNull(source.getResource().getCharset());
        assertNull(source.getResource().getLocation().getFile());
        assertThat(source.getResource().getLocation().getURI(), equalTo(uri));
    }

    @Test
    public void usesScriptFileNameToBuildDescription() {
        ScriptSource source = forFile(scriptFile);
        assertThat(source.getDisplayName(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
        assertThat(source.getShortDisplayName().getDisplayName(), equalTo(String.format("<file-type> 'scripts%s%s'", File.separator, scriptFile.getName())));
        assertThat(source.getLongDisplayName().getDisplayName(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
    }

    @Test
    public void usesScriptFileNameToBuildDescriptionWhenUsingFileUri() {
        ScriptSource source = forUri(scriptFileUri);
        assertThat(source.getDisplayName(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
        assertThat(source.getShortDisplayName().getDisplayName(), equalTo(String.format("<file-type> 'scripts%s%s'", File.separator, scriptFile.getName())));
        assertThat(source.getLongDisplayName().getDisplayName(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
    }

    @Test
    public void usesScriptFileNameToBuildDescriptionWhenUsingHttpUri() throws URISyntaxException {
        ScriptSource source = forUri(new URI("http://www.gradle.org/unknown.txt"));
        assertThat(source.getDisplayName(), equalTo("<file-type> 'http://www.gradle.org/unknown.txt'"));
        assertThat(source.getShortDisplayName().getDisplayName(), equalTo("<file-type> 'http://www.gradle.org/unknown.txt'"));
        assertThat(source.getLongDisplayName().getDisplayName(), equalTo("<file-type> 'http://www.gradle.org/unknown.txt'"));
    }

    @Test
    public void usesScriptFilePathForFileNameUsingFile() {
        ScriptSource source = forFile(scriptFile);
        assertThat(source.getFileName(), equalTo(scriptFile.getAbsolutePath()));
    }

    @Test
    public void usesScriptFilePathForFileNameUsingFileUri() {
        ScriptSource source = forUri(scriptFileUri);
        assertThat(source.getFileName(), equalTo(scriptFile.getAbsolutePath()));
    }

    @Test
    public void usesScriptUriForFileNameUsingHttpUri() throws URISyntaxException {
        ScriptSource source = forUri(new URI("http://www.gradle.org/unknown.txt"));
        assertThat(source.getFileName(), equalTo("http://www.gradle.org/unknown.txt"));
    }

    @Test
    public void generatesClassNameFromFileNameByRemovingExtensionAndAddingHashOfFileURL() {
        ScriptSource source = forFile(scriptFile);
        assertThat(source.getClassName(), matchesRegexp("build_[0-9a-z]+"));
    }

    @Test
    public void generatesClassNameFromFileNameByRemovingExtensionAndAddingHashOfJarURL() throws Exception {
        ScriptSource source = forUri(createJar());
        assertThat(source.getClassName(), matchesRegexp("build_[0-9a-z]+"));
    }

    @Test
    public void truncatesClassNameAt30Characters() {
        ScriptSource source = forFile(new File(testDir, "a-long-file-name-12345678901234567890.gradle"));
        assertThat(source.getClassName(), matchesRegexp("a_long_file_name_1234567890123_[0-9a-z]+"));
    }

    @Test
    public void encodesReservedCharactersInClassName() {
        ScriptSource source = forFile(new File(testDir, "name-+.chars.gradle"));
        assertThat(source.getClassName(), matchesRegexp("name___chars_[0-9a-z]+"));
    }

    @Test
    public void prefixesClassNameWhenFirstCharacterIsNotValidIdentifierStartChar() {
        ScriptSource source = forFile(new File(testDir, "123"));
        assertThat(source.getClassName(), matchesRegexp("_123_[0-9a-z]+"));

        source = forFile(new File(testDir, "-"));
        assertThat(source.getClassName(), matchesRegexp("__[0-9a-z]+"));
    }

    @Test
    public void filesWithSameNameAndDifferentPathHaveDifferentClassName() {
        ScriptSource source1 = forFile(new File(testDir, "build.gradle"));
        ScriptSource source2 = forFile(new File(testDir, "subdir/build.gradle"));
        assertThat(source1.getClassName(), not(equalTo(source2.getClassName())));

        ScriptSource source3 = forFile(new File(testDir, "build.gradle"));
        assertThat(source1.getClassName(), equalTo(source3.getClassName()));
    }

    @Test
    public void filesWithSameNameAndUriHaveDifferentClassName() throws URISyntaxException {
        ScriptSource source1 = forFile(new File(testDir, "build.gradle"));
        ScriptSource source2 = forUri(new URI("http://localhost/build.gradle"));
        assertThat(source1.getClassName(), not(equalTo(source2.getClassName())));

        ScriptSource source3 = forFile(new File(testDir, "build.gradle"));
        assertThat(source1.getClassName(), equalTo(source3.getClassName()));
    }

    @Test
    public void canConstructSourceFromStringResource() {
        ScriptSource source = new TextResourceScriptSource(new StringTextResource("<string>", "resource content"));
        assertThat(source.getResource(), instanceOf(StringTextResource.class));
        assertThat(source.getResource().getFile(), nullValue());
        assertThat(source.getResource().getLocation().getFile(), nullValue());
        assertThat(source.getResource().getLocation().getURI(), nullValue());
        assertThat(source.getDisplayName(), equalTo("<string>"));
        assertThat(source.getLongDisplayName().getDisplayName(), equalTo("<string>"));
        assertThat(source.getShortDisplayName().getDisplayName(), equalTo("<string>"));
        assertThat(source.getClassName(), equalTo("script_5z2up7fl2zfks7lm6sqlalp1q"));
    }

    @Test
    public void stringResourcesWithDifferentContentHaveDifferentClassNames() {
        ScriptSource source1 = new TextResourceScriptSource(new StringTextResource("<string>", "resource content 1"));
        ScriptSource source2 = new TextResourceScriptSource(new StringTextResource("<string>", "resource content 2"));
        assertFalse(source1.getClassName().equals(source2.getClassName()));
    }

    private ScriptSource forFile(File scriptFile) {
        return new TextResourceScriptSource(resourceLoader.loadFile("<file-type>", scriptFile));
    }

    private ScriptSource forUri(URI scriptFileUri) {
        return new TextResourceScriptSource(new UriTextResource("<file-type>", scriptFileUri, resolver));
    }

}
