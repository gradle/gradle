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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

import static org.gradle.internal.resource.UriTextResource.extractCharacterEncoding
import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.*

class UriTextResourceTest extends Specification {
    private TestFile testDir;
    private File file;
    private URI fileUri;
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    def setup() {
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

    def canConstructResourceFromFile() {
        when:
        file.createNewFile()
        UriTextResource resource = new UriTextResource('<display-name>', file);

        then:
        resource.file == file
        resource.location.file == file
        resource.location.URI == fileUri
    }

    def canConstructResourceFromFileURI() {
        when:
        file.createNewFile()
        UriTextResource resource = new UriTextResource('<display-name>', fileUri);

        then:
        resource.file == file
        resource.location.file == file
        resource.location.URI == fileUri
    }

    def canConstructResourceFromJarURI() {
        when:
        URI jarUri = createJar()
        UriTextResource resource = new UriTextResource('<display-name>', jarUri);

        then:
        resource.file == null
        resource.charset == null
        resource.location.file == null
        resource.location.URI == jarUri
    }

    def readsFileContentWhenFileExists() throws IOException {
        when:
        file.text = '<content>'
        UriTextResource resource = new UriTextResource('<display-name>', file);

        then:
        resource.exists
        !resource.hasEmptyContent
        resource.text == '<content>'
        resource.asReader.text == '<content>'
    }

    def assumesFileIsEncodedUsingUtf8() throws IOException {
        when:
        file.setText('\u03b1', 'utf-8')

        UriTextResource resource = new UriTextResource('<display-name>', file);

        then:
        resource.exists
        resource.charset == Charset.forName("utf-8")
        resource.text == '\u03b1'
        resource.asReader.text == '\u03b1'
    }

    def hasNoContentWhenFileDoesNotExist() {
        when:
        UriTextResource resource = new UriTextResource('<display-name>', file);

        then:
        !resource.exists

        when:
        resource.text

        then:
        def e = thrown(MissingResourceException)
        e.message == "Could not read <display-name> '$file' as it does not exist." as String

        when:
        resource.asReader

        then:
        e = thrown(MissingResourceException)
        e.message == "Could not read <display-name> '$file' as it does not exist." as String

        when:
        resource.hasEmptyContent

        then:
        e = thrown(MissingResourceException)
        e.message == "Could not read <display-name> '$file' as it does not exist." as String
    }

    def hasNoContentWhenFileIsADirectory() {
        when:
        TestFile dir = testDir.file('somedir').createDir()
        UriTextResource resource = new UriTextResource('<display-name>', dir);

        then:
        resource.exists

        when:
        resource.text

        then:
        def e = thrown(ResourceIsAFolderException)
        e.message == "Could not read <display-name> '$dir' as it is a directory." as String

        when:
        resource.asReader

        then:
        e = thrown(ResourceIsAFolderException)
        e.message == "Could not read <display-name> '$dir' as it is a directory." as String

        when:
        resource.hasEmptyContent

        then:
        e = thrown(ResourceIsAFolderException)
        e.message == "Could not read <display-name> '$dir' as it is a directory." as String
    }

    def readsFileContentUsingFileUriWhenFileExists() {
        when:
        file.text = '<content>'

        UriTextResource resource = new UriTextResource('<display-name>', fileUri);

        then:
        resource.exists
        resource.text == '<content>'
        resource.asReader.text == '<content>'
    }

    def hasNoContentWhenUsingFileUriAndFileDoesNotExist() {
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

    def readsFileContentUsingJarUriWhenFileExists() {
        when:
        file.text = '<content>'

        UriTextResource resource = new UriTextResource('<display-name>', createJar());

        then:
        resource.exists
        resource.text == "<content>"
    }

    def hasNoContentWhenUsingJarUriAndFileDoesNotExistInJar() {
        when:
        URI jarUri = createJar()
        UriTextResource resource = new UriTextResource('<display-name>', jarUri);

        then:
        !resource.exists

        when:
        resource.text

        then:
        def e = thrown(MissingResourceException)
        e.message == "Could not read <display-name> '$jarUri' as it does not exist." as String

        when:
        resource.asReader

        then:
        e = thrown(MissingResourceException)
        e.message == "Could not read <display-name> '$jarUri' as it does not exist." as String

        when:
        resource.hasEmptyContent

        then:
        e = thrown(MissingResourceException)
        e.message == "Could not read <display-name> '$jarUri' as it does not exist." as String
    }

    def usesFilePathToBuildDisplayNameWhenUsingFile() {
        when:
        UriTextResource resource = new UriTextResource("<file-type>", file);

        then:
        resource.displayName == "<file-type> '${file.absolutePath}'" as String
    }

    def usesFilePathToBuildDisplayNameWhenUsingFileUri() {
        when:
        UriTextResource resource = new UriTextResource("<file-type>", fileUri);

        then:
        resource.displayName == "<file-type> '${file.absolutePath}'" as String
    }

    def usesUriToBuildDisplayNameWhenUsingHttpUri() {
        when:
        UriTextResource resource = new UriTextResource("<file-type>", new URI("http://www.gradle.org/unknown.txt"));

        then:
        resource.displayName == '<file-type> \'http://www.gradle.org/unknown.txt\''
    }

    def extractsCharacterEncodingFromContentType() {
        expect:
        extractCharacterEncoding('content/unknown', null) == null
        extractCharacterEncoding('content/unknown', Charset.defaultCharset()) == Charset.defaultCharset()
        extractCharacterEncoding(null, Charset.defaultCharset()) == Charset.defaultCharset()
        extractCharacterEncoding('text/html', null) == null
        extractCharacterEncoding('text/html; charset=UTF-8', null).name() == 'UTF-8'
        extractCharacterEncoding('text/html; other=value; other="value"; charset=US-ASCII', null).name() == 'US-ASCII'
        extractCharacterEncoding('text/plain; other=value;', null) == null
        extractCharacterEncoding('text/plain; charset="UTF-8"', null).name() == 'UTF-8'
        extractCharacterEncoding('text/plain; other="\\";\\="; charset="UTF-8"', null).name() == 'UTF-8'
        extractCharacterEncoding('text/plain; charset=', null) == null
        extractCharacterEncoding('text/plain; charset; other=;charset="UTF-8', null).name() == "UTF-8"
    }
}
