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

import org.gradle.api.resources.MissingResourceException
import org.gradle.api.resources.ResourceException
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestPrecondition
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import java.nio.charset.Charset

import static org.hamcrest.Matchers.equalTo
import static org.hamcrest.Matchers.nullValue
import static org.junit.Assert.*

class UriTextResourceTest {
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
        file.createNewFile()
        UriTextResource resource = new UriTextResource('<display-name>', file);
        assertThat(resource.file, equalTo(file));
        assertThat(resource.location.file, equalTo(file));
        assertThat(resource.location.URI, equalTo(fileUri));
    }

    @Test
    public void canConstructResourceFromFileURI() {
        file.createNewFile()
        UriTextResource resource = new UriTextResource('<display-name>', fileUri);
        assertThat(resource.file, equalTo(file));
        assertThat(resource.location.file, equalTo(file));
        assertThat(resource.location.URI, equalTo(fileUri));
    }

    @Test
    public void canConstructResourceFromJarURI() {
        URI jarUri = createJar()
        UriTextResource resource = new UriTextResource('<display-name>', jarUri);
        assertNull(resource.file)
        assertNull(resource.charset)
        assertNull(resource.location.file)
        assertThat(resource.location.URI, equalTo(jarUri));
    }

    @Test
    public void readsFileContentWhenFileExists() throws IOException {
        file.text = '<content>'

        UriTextResource resource = new UriTextResource('<display-name>', file);
        assertTrue(resource.exists)
        assertFalse(resource.hasEmptyContent)
        assertThat(resource.text, equalTo('<content>'));
        assertThat(resource.asReader.text, equalTo('<content>'));
    }

    @Test
    public void assumesFileIsEncodedUsingUtf8() throws IOException {
        file.setText('\u03b1', 'utf-8')

        UriTextResource resource = new UriTextResource('<display-name>', file);
        assertTrue(resource.exists)
        assertThat(resource.charset, equalTo(Charset.forName("utf-8")))
        assertThat(resource.text, equalTo('\u03b1'));
        assertThat(resource.asReader.text, equalTo('\u03b1'));
    }

    @Test
    public void hasNoContentWhenFileDoesNotExist() {
        UriTextResource resource = new UriTextResource('<display-name>', file);
        assertFalse(resource.exists)
        assertNull(resource.file)
        assertNull(resource.charset)
        try {
            resource.text
            fail()
        } catch (MissingResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$file' as it does not exist." as String))
        }
        try {
            resource.asReader
            fail()
        } catch (MissingResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$file' as it does not exist." as String))
        }
        try {
            resource.hasEmptyContent
            fail()
        } catch (MissingResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$file' as it does not exist." as String))
        }
    }

    @Test
    public void hasNoContentWhenFileIsADirectory() {
        TestFile dir = testDir.file('somedir').createDir()
        UriTextResource resource = new UriTextResource('<display-name>', dir);
        assertTrue(resource.exists)
        assertNull(resource.file)
        assertNull(resource.charset)
        try {
            resource.text
            fail()
        } catch (ResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$dir' as it is a directory." as String))
        }
        try {
            resource.asReader
            fail()
        } catch (ResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$dir' as it is a directory." as String))
        }
        try {
            resource.hasEmptyContent
            fail()
        } catch (ResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$dir' as it is a directory." as String))
        }
    }

    @Test
    public void readsFileContentUsingFileUriWhenFileExists() {
        file.text = '<content>'

        UriTextResource resource = new UriTextResource('<display-name>', fileUri);
        assertTrue(resource.exists)
        assertThat(resource.text, equalTo('<content>'));
        assertThat(resource.asReader.text, equalTo('<content>'));
    }

    @Test
    public void hasNoContentWhenUsingFileUriAndFileDoesNotExist() {
        UriTextResource resource = new UriTextResource('<display-name>', fileUri);
        assertFalse(resource.exists)
        assertNull(resource.file)
        try {
            resource.text
            fail()
        } catch (MissingResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$file' as it does not exist." as String))
        }
    }

    @Test
    @LeaksFileHandles
    public void readsFileContentUsingJarUriWhenFileExists() {
        file.text = '<content>'

        UriTextResource resource = new UriTextResource('<display-name>', createJar());
        assertTrue(resource.exists)
        assertThat(resource.text, equalTo('<content>'));
    }

    @Test
    @LeaksFileHandles
    public void hasNoContentWhenUsingJarUriAndFileDoesNotExistInJar() {
        URI jarUri = createJar()
        UriTextResource resource = new UriTextResource('<display-name>', jarUri);
        assertFalse(resource.exists)
        try {
            resource.text
            fail()
        } catch (MissingResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$jarUri' as it does not exist." as String))
        }
        try {
            resource.asReader
            fail()
        } catch (MissingResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$jarUri' as it does not exist." as String))
        }
        try {
            resource.hasEmptyContent
            fail()
        } catch (MissingResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> '$jarUri' as it does not exist." as String))
        }
    }

    @Test
    public void hasNoContentWhenUsingHttpUriAndFileDoesNotExist() {
        Assume.assumeTrue(TestPrecondition.ONLINE.fulfilled) // when this test moves to spock, ignore this test instead of just passing.

        UriTextResource resource = new UriTextResource('<display-name>', new URI("http://www.gradle.org/unknown.txt"));
        assertFalse(resource.exists)
        try {
            resource.text
            fail()
        } catch (MissingResourceException e) {
            assertThat(e.message, equalTo("Could not read <display-name> 'http://www.gradle.org/unknown.txt' as it does not exist." as String))
        }
    }

    @Test
    public void usesFilePathToBuildDisplayNameWhenUsingFile() {
        UriTextResource resource = new UriTextResource("<file-type>", file);
        assertThat(resource.displayName, equalTo(String.format("<file-type> '%s'", file.absolutePath)));
    }

    @Test
    public void usesFilePathToBuildDisplayNameWhenUsingFileUri() {
        UriTextResource resource = new UriTextResource("<file-type>", fileUri);
        assertThat(resource.displayName, equalTo(String.format("<file-type> '%s'", file.absolutePath)));
    }

    @Test
    public void usesUriToBuildDisplayNameWhenUsingHttpUri() {
        UriTextResource resource = new UriTextResource("<file-type>", new URI("http://www.gradle.org/unknown.txt"));
        assertThat(resource.displayName, equalTo('<file-type> \'http://www.gradle.org/unknown.txt\''))
    }

    @Test
    public void extractsCharacterEncodingFromContentType() {
        assertThat(UriTextResource.extractCharacterEncoding('content/unknown', null), nullValue())
        assertThat(UriTextResource.extractCharacterEncoding('content/unknown', 'default'), equalTo('default'))
        assertThat(UriTextResource.extractCharacterEncoding(null, 'default'), equalTo('default'))
        assertThat(UriTextResource.extractCharacterEncoding('text/html', null), nullValue())
        assertThat(UriTextResource.extractCharacterEncoding('text/html; charset=UTF-8', null), equalTo('UTF-8'))
        assertThat(UriTextResource.extractCharacterEncoding('text/html; other=value; other="value"; charset=US-ASCII', null), equalTo('US-ASCII'))
        assertThat(UriTextResource.extractCharacterEncoding('text/plain; other=value;', null), equalTo(null))
        assertThat(UriTextResource.extractCharacterEncoding('text/plain; charset="charset"', null), equalTo('charset'))
        assertThat(UriTextResource.extractCharacterEncoding('text/plain; charset="\\";\\="', null), equalTo('";\\='))
        assertThat(UriTextResource.extractCharacterEncoding('text/plain; charset=', null), equalTo(null))
        assertThat(UriTextResource.extractCharacterEncoding('text/plain; charset; charset=;charset="missing-quote', null), equalTo("missing-quote"))
    }
}
