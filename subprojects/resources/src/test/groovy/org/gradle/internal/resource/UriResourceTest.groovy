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


package org.gradle.internal.resource

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.TestPrecondition
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.nullValue
import static org.junit.Assert.*

class UriResourceTest {
    private TestFile testDir;
    private File file;
    private URI fileUri;
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Before
    public void setUp() throws URISyntaxException {
        testDir = tmpDir.createDir('dir');
        file = new File(testDir, 'build.script');
        fileUri = file.toURI();
    }

    private URI createJar() throws URISyntaxException {
        TestFile jarFile = tmpDir.testDirectory.file('test.jar');
        testDir.file('ignoreme').write('content');
        testDir.zipTo(jarFile);
        return new URI("jar:${jarFile.toURI()}!/build.script")
    }

    @Test
    public void canConstructResourceFromFile() {
        UriResource resource = new UriResource('<display-name>', file);
        assertThat(resource.file, equalTo(file));
        assertThat(resource.URI, equalTo(fileUri));
    }

    @Test
    public void canConstructResourceFromFileURI() {
        UriResource resource = new UriResource('<display-name>', fileUri);
        assertThat(resource.file, equalTo(file));
        assertThat(resource.URI, equalTo(fileUri));
    }

    @Test
    public void canConstructResourceFromJarURI() {
        URI jarUri = createJar()
        UriResource resource = new UriResource('<display-name>', jarUri);
        assertThat(resource.file, nullValue());
        assertThat(resource.URI, equalTo(jarUri));
    }

    @Test
    public void readsFileContentWhenFileExists() throws IOException {
        file.text = '<content>'

        UriResource resource = new UriResource('<display-name>', file);
        assertTrue(resource.exists)
        assertThat(resource.text, equalTo('<content>'));
    }

    @Test
    public void assumesFileIsEncodedUsingUtf8() throws IOException {
        file.setText('\u03b1', 'utf-8')

        UriResource resource = new UriResource('<display-name>', file);
        assertTrue(resource.exists)
        assertThat(resource.text, equalTo('\u03b1'));
    }

    @Test
    public void hasNoContentWhenFileDoesNotExist() {
        UriResource resource = new UriResource('<display-name>', file);
        assertFalse(resource.exists)
        try {
            resource.text
            fail()
        } catch (ResourceNotFoundException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$file' as it does not exist." as String))
        }
    }

    @Test
    public void hasNoContentWhenFileIsADirectory() {
        TestFile dir = testDir.file('somedir').createDir()
        UriResource resource = new UriResource('<display-name>', dir);
        assertTrue(resource.exists)
        try {
            resource.text
            fail()
        } catch (ResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$dir' as it is a directory." as String))
        }
    }

    @Test
    public void readsFileContentUsingFileUriWhenFileExists() {
        file.text = '<content>'

        UriResource resource = new UriResource('<display-name>', fileUri);
        assertTrue(resource.exists)
        assertThat(resource.text, equalTo('<content>'));
    }

    @Test
    public void hasNoContentWhenUsingFileUriAndFileDoesNotExist() {
        UriResource resource = new UriResource('<display-name>', fileUri);
        assertFalse(resource.exists)
        try {
            resource.text
            fail()
        } catch (ResourceNotFoundException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$file' as it does not exist." as String))
        }
    }

    @Test
    @LeaksFileHandles
    public void readsFileContentUsingJarUriWhenFileExists() {
        file.text = '<content>'

        UriResource resource = new UriResource('<display-name>', createJar());
        assertTrue(resource.exists)
        assertThat(resource.text, equalTo('<content>'));
    }

    @Test
    @LeaksFileHandles
    public void hasNoContentWhenUsingJarUriAndFileDoesNotExistInJar() {
        URI jarUri = createJar()
        UriResource resource = new UriResource('<display-name>', jarUri);
        assertFalse(resource.exists)
        try {
            resource.text
            fail()
        } catch (ResourceNotFoundException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$jarUri' as it does not exist." as String))
        }
    }

    @Test
    public void hasNoContentWhenUsingHttpUriAndFileDoesNotExist() {
        Assume.assumeTrue(TestPrecondition.ONLINE.fulfilled) // when this test moves to spock, ignore this test instead of just passing.

        UriResource resource = new UriResource('<display-name>', new URI("http://www.gradle.org/unknown.txt"));
        assertFalse(resource.exists)
        try {
            resource.text
            fail()
        } catch (ResourceNotFoundException e) {
            assertThat(e.message, equalTo("Could not read <display-name> 'http://www.gradle.org/unknown.txt' as it does not exist." as String))
        }
    }

    @Test
    public void usesFilePathToBuildDisplayNameWhenUsingFile() {
        UriResource resource = new UriResource("<file-type>", file);
        assertThat(resource.displayName, equalTo(String.format("<file-type> '%s'", file.absolutePath)));
    }

    @Test
    public void usesFilePathToBuildDisplayNameWhenUsingFileUri() {
        UriResource resource = new UriResource("<file-type>", fileUri);
        assertThat(resource.displayName, equalTo(String.format("<file-type> '%s'", file.absolutePath)));
    }

    @Test
    public void usesUriToBuildDisplayNameWhenUsingHttpUri() {
        UriResource resource = new UriResource("<file-type>", new URI("http://www.gradle.org/unknown.txt"));
        assertThat(resource.displayName, equalTo('<file-type> \'http://www.gradle.org/unknown.txt\''))
    }

    @Test
    public void extractsCharacterEncodingFromContentType() {
        assertThat(UriResource.extractCharacterEncoding('content/unknown', null), nullValue())
        assertThat(UriResource.extractCharacterEncoding('content/unknown', 'default'), equalTo('default'))
        assertThat(UriResource.extractCharacterEncoding(null, 'default'), equalTo('default'))
        assertThat(UriResource.extractCharacterEncoding('text/html', null), nullValue())
        assertThat(UriResource.extractCharacterEncoding('text/html; charset=UTF-8', null), equalTo('UTF-8'))
        assertThat(UriResource.extractCharacterEncoding('text/html; other=value; other="value"; charset=US-ASCII', null), equalTo('US-ASCII'))
        assertThat(UriResource.extractCharacterEncoding('text/plain; other=value;', null), equalTo(null))
        assertThat(UriResource.extractCharacterEncoding('text/plain; charset="charset"', null), equalTo('charset'))
        assertThat(UriResource.extractCharacterEncoding('text/plain; charset="\\";\\="', null), equalTo('";\\='))
        assertThat(UriResource.extractCharacterEncoding('text/plain; charset=', null), equalTo(null))
        assertThat(UriResource.extractCharacterEncoding('text/plain; charset; charset=;charset="missing-quote', null), equalTo("missing-quote"))
    }
}
